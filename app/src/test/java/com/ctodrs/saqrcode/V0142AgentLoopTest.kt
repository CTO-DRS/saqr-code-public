package com.ctodrs.saqrcode

import android.os.Looper
import com.ctodrs.saqrcode.agent.AgentSession
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.LlmException
import com.ctodrs.saqrcode.agent.ModelClient
import com.ctodrs.saqrcode.agent.ModelFactory
import com.ctodrs.saqrcode.agent.Msg
import com.ctodrs.saqrcode.agent.Providers
import com.ctodrs.saqrcode.agent.StreamListener
import com.ctodrs.saqrcode.agent.ToolCall
import com.ctodrs.saqrcode.agent.ToolSpec
import com.ctodrs.saqrcode.agent.Turn
import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.terminal.LinuxEnv
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * v0.1.42: the whole agent loop (AgentSession) driven by a scripted fake model, no network.
 * Covers a plain reply, a read-only tool round trip and a provider failure.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V0142AgentLoopTest {
    private class Fake(private val script: List<() -> Turn>) : ModelClient {
        val seen = mutableListOf<List<Msg>>()
        private var i = 0
        override suspend fun complete(system: String, messages: List<Msg>, tools: List<ToolSpec>, l: StreamListener): Turn {
            seen += messages.toList()
            val t = script[minOf(i++, script.lastIndex)]()
            if (t.text.isNotEmpty()) l.onText(t.text)
            return t
        }
    }

    private fun session(fake: Fake, override: ModelClient? = null, rootDir: File? = null): AgentSession {
        val app = RuntimeEnvironment.getApplication()
        // Robolectric leaves nativeLibraryDir null; LinuxEnv reads it at construction.
        app.applicationInfo.nativeLibraryDir = app.cacheDir.absolutePath
        val settings = SettingsStore(app, SecureStore(app))
        val id = Providers.all.first().id
        settings.activeProvider = id
        settings.saveProvider(id, Providers.byId(id).defaultBaseUrl, "test-model", "test-key")
        settings.retryLlm = false
        settings.approveAll = true
        val root = (rootDir ?: File(app.cacheDir, "loop-${System.nanoTime()}")).apply { mkdirs() }
        val project = File(root, "proj").apply { mkdirs() }
        File(project, "hello.txt").writeText("hi")
        lastProject = project
        return AgentSession(
            projectName = "proj", projectDir = project, checkpointRoot = File(root, "cp"),
            settings = settings, linux = LinuxEnv(app),
            store = rootDir?.let { com.ctodrs.saqrcode.agent.SessionStore(File(it, "sessions")) },
            modelFactory = ModelFactory { _, _, _, _ -> override ?: fake },
        )
    }

    private fun awaitUntil(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if (cond()) return
            Thread.sleep(20)
        } while (System.currentTimeMillis() < end)
        throw AssertionError("timed out waiting for $what")
    }

    private lateinit var lastProject: File

    private fun projectFile(s: AgentSession, name: String): File {
        return File(lastProject, name)
    }

    private val writeCall = ToolCall("w1", "write_file", """{"path":"new.txt","content":"fresh"}""")

    private fun AgentSession.await() {
        val end = System.currentTimeMillis() + 30_000
        do {
            shadowOf(Looper.getMainLooper()).idle()
            if (!running) return
            Thread.sleep(20)
        } while (System.currentTimeMillis() < end)
        throw AssertionError("agent loop did not finish")
    }

    @Test fun `a plain reply lands as an assistant item`() {
        val fake = Fake(listOf({ Turn("مرحبا", emptyList(), "stop") }))
        val s = session(fake)
        s.send("hello")
        s.await()
        val reply = s.items.filterIsInstance<ChatItem.Assistant>().lastOrNull()
        assertEquals("مرحبا", reply?.text)
        assertEquals(1, fake.seen.size)
    }

    @Test fun `a read-only tool call runs and its result goes back to the model`() {
        val call = ToolCall("c1", "list_files", """{"path":"."}""")
        val fake = Fake(listOf({ Turn("", listOf(call), "tool_use") }, { Turn("done", emptyList(), "stop") }))
        val s = session(fake)
        s.send("what files are here")
        s.await()
        assertEquals(2, fake.seen.size)
        val result = fake.seen[1].filterIsInstance<Msg.ToolResult>().firstOrNull()
        assertTrue(result != null && result.content.contains("hello.txt"))
        assertEquals("done", s.items.filterIsInstance<ChatItem.Assistant>().last().text)
    }

    @Test fun `a provider failure ends the run with an error notice`() {
        val fake = Fake(listOf({ throw LlmException("HTTP 503: overloaded") }))
        val s = session(fake)
        s.send("hello")
        s.await()
        assertTrue(!s.running)
        assertTrue(s.items.filterIsInstance<ChatItem.Notice>().any { it.kind == ChatItem.Notice.Kind.ERROR })
    }

    @Test fun `stop cancels a model call in flight and leaves a stopped notice`() {
        val gate = object : ModelClient {
            override suspend fun complete(system: String, messages: List<Msg>, tools: List<ToolSpec>, l: StreamListener): Turn =
                kotlinx.coroutines.awaitCancellation()
        }
        val s = session(Fake(emptyList()), gate)
        s.send("hello")
        awaitUntil("run to start") { s.running }
        s.stop()
        s.await()
        assertTrue(s.items.filterIsInstance<ChatItem.Notice>().any { it.kind == ChatItem.Notice.Kind.STOPPED })
    }

    @Test fun `rejecting a file change writes nothing and tells the model`() {
        val fake = Fake(listOf({ Turn("", listOf(writeCall), "tool_use") }, { Turn("ok", emptyList(), "stop") }))
        val s = session(fake)
        s.send("make a file")
        awaitUntil("approval card") { s.items.any { it is ChatItem.Approval } }
        s.decide(s.items.filterIsInstance<ChatItem.Approval>().first(), false)
        s.await()
        assertTrue(!projectFile(s, "new.txt").exists())
        val result = fake.seen.last().filterIsInstance<Msg.ToolResult>().first()
        assertTrue(result.content.contains("REJECTED"))
    }

    @Test fun `approving a file change writes it`() {
        val fake = Fake(listOf({ Turn("", listOf(writeCall), "tool_use") }, { Turn("ok", emptyList(), "stop") }))
        val s = session(fake)
        s.send("make a file")
        awaitUntil("approval card") { s.items.any { it is ChatItem.Approval } }
        s.decide(s.items.filterIsInstance<ChatItem.Approval>().first(), true)
        s.await()
        assertEquals("fresh", projectFile(s, "new.txt").readText())
        assertTrue(fake.seen.last().filterIsInstance<Msg.ToolResult>().first().content.startsWith("OK"))
    }

    @Test fun `each reply carries the usage the provider reported`() {
        val fake = Fake(listOf({ Turn("hi", emptyList(), "stop", com.ctodrs.saqrcode.agent.Usage(100, 20)) }))
        val s = session(fake)
        s.send("hello")
        s.await()
        val reply = s.items.filterIsInstance<ChatItem.Assistant>().last()
        assertEquals(120L, reply.usage?.total)
        assertTrue(reply.costUsd == null) // "test-model" has no price
    }

    @Test fun `a turn cut off by a restart is resumed from the saved chat`() {
        val app = RuntimeEnvironment.getApplication()
        val root = File(app.cacheDir, "resume-${System.nanoTime()}")
        // first process: the model call never returns, as if the app was killed mid-turn
        val hang = object : ModelClient {
            override suspend fun complete(system: String, messages: List<Msg>, tools: List<ToolSpec>, l: StreamListener): Turn =
                kotlinx.coroutines.awaitCancellation()
        }
        val first = session(Fake(emptyList()), hang, root)
        first.send("build me a page")
        awaitUntil("run to start") { first.running }
        // second process: a new session over the same saved chat
        val fake = Fake(listOf({ Turn("finished", emptyList(), "stop") }))
        val second = session(fake, null, root)
        assertTrue(second.interrupted)
        assertTrue(second.items.filterIsInstance<ChatItem.Notice>().any { it.kind == ChatItem.Notice.Kind.INTERRUPTED })
        assertTrue(second.resumeInterrupted())
        second.await()
        assertEquals("finished", second.items.filterIsInstance<ChatItem.Assistant>().last().text)
        // the model saw the original request again
        assertTrue(fake.seen.first().filterIsInstance<Msg.User>().any { it.text.contains("build me a page") })
        first.stop()
    }

    @Test fun `an approval card pending at a restart does not leave the history broken`() {
        val app = RuntimeEnvironment.getApplication()
        val root = File(app.cacheDir, "pending-${System.nanoTime()}")
        val first = session(Fake(listOf({ Turn("", listOf(writeCall), "tool_use") })), null, root)
        first.send("make a file")
        awaitUntil("approval card") { first.items.any { it is ChatItem.Approval } }
        first.saveNow(blocking = true) // what is on disk if the process dies right here
        val fake = Fake(listOf({ Turn("recovered", emptyList(), "stop") }))
        val second = session(fake, null, root)
        // nothing is left waiting on a card nobody can answer
        assertTrue(second.items.filterIsInstance<ChatItem.Approval>().none { it.decision == null })
        assertTrue(second.interrupted)
        assertTrue(second.resumeInterrupted())
        second.await()
        // every tool call the model made is answered, so a provider accepts the request
        val sent = fake.seen.first()
        val calls = sent.filterIsInstance<Msg.Assistant>().flatMap { it.toolCalls }.map { it.id }
        val answered = sent.filterIsInstance<Msg.ToolResult>().map { it.callId }.toSet()
        assertTrue("unanswered calls: ${calls.filter { it !in answered }}", calls.all { it in answered })
        // the change that was never approved was not written
        assertTrue(!projectFile(second, "new.txt").exists())
        first.stop()
    }

    @Test fun `a failure notice never shows the user's key`() {
        val fake = Fake(listOf({ throw LlmException("HTTP 401: bad key test-key rejected") }))
        val s = session(fake)
        s.send("hello")
        s.await()
        val n = s.items.filterIsInstance<ChatItem.Notice>().first { it.kind == ChatItem.Notice.Kind.ERROR }
        assertTrue(n.text.contains("401"))
        assertTrue(!n.text.contains("test-key"))
    }
}
