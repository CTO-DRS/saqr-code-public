package com.ctodrs.saqrcode

import android.os.Looper
import com.ctodrs.saqrcode.agent.*
import com.ctodrs.saqrcode.cloud.*
import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.terminal.LinuxEnv
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** v0.1.65: the agent loop with the cloud workspace tools, a scripted model and a fake provider. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V0165CloudLoopTest {
    private class Fake(private val script: List<() -> Turn>) : ModelClient {
        val seen = mutableListOf<List<Msg>>()
        private var i = 0
        override suspend fun complete(system: String, messages: List<Msg>, tools: List<ToolSpec>, l: StreamListener): Turn {
            seen += messages.toList()
            return script[minOf(i++, script.lastIndex)]()
        }
    }

    private class Box(val log: MutableList<String>) : CloudSandbox {
        var closed = 0
        override suspend fun upload(files: List<CloudFile>) { log += "upload:" + files.map { it.path }.sorted().joinToString(",") }
        override suspend fun exec(command: String, timeoutSec: Int, onLine: (String) -> Unit) =
            CloudExecResult(0, "built fine", false, 1_000).also { log += "exec:$command" }
        override suspend fun close() { closed++ }
    }

    @After fun reset() { CloudProviders.factory = { _ -> null } }

    private fun session(fake: Fake, enabled: Boolean): AgentSession {
        val app = RuntimeEnvironment.getApplication()
        app.applicationInfo.nativeLibraryDir = app.cacheDir.absolutePath
        val settings = SettingsStore(app, SecureStore(app))
        val id = Providers.all.first().id
        settings.activeProvider = id
        settings.saveProvider(id, Providers.byId(id).defaultBaseUrl, "test-model", "test-key")
        settings.retryLlm = false
        settings.cloudEnabled = enabled
        settings.cloudProxyUrl = "https://proxy.example"
        val root = File(app.cacheDir, "cloud-${System.nanoTime()}").apply { mkdirs() }
        val project = File(root, "proj").apply { mkdirs() }
        File(project, "app.py").writeText("print('hi')")
        File(project, ".env").writeText("TOKEN=abc")
        return AgentSession("proj", project, File(root, "cp"), settings, LinuxEnv(app),
            modelFactory = ModelFactory { _, _, _, _ -> fake })
    }

    private fun AgentSession.pump(until: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        do {
            shadowOf(Looper.getMainLooper()).idle()
            items.filterIsInstance<ChatItem.Approval>().filter { it.decision == null }.forEach { it.decision = true; it.deferred.complete(true) }
            if (until()) return
            Thread.sleep(20)
        } while (System.currentTimeMillis() < end)
        throw AssertionError("timed out")
    }

    private val call = ToolCall("c1", "cloud_run", """{"command":"python app.py"}""")

    @Test fun `cloud tools say plainly when the workspace is off`() {
        val fake = Fake(listOf({ Turn("", listOf(call), "tool_use") }, { Turn("ok", emptyList(), "stop") }))
        val s = session(fake, enabled = false)
        s.send("run it")
        s.pump { !s.running }
        val r = fake.seen[1].filterIsInstance<Msg.ToolResult>().first().content
        assertTrue(r, r.contains("cloud workspace is off"))
    }

    @Test fun `enabled but no proxy address is reported as not connected`() {
        val fake = Fake(listOf({ Turn("", listOf(call), "tool_use") }, { Turn("ok", emptyList(), "stop") }))
        val s = session(fake, enabled = true)
        s.send("run it")
        s.pump { !s.running }
        val r = fake.seen[1].filterIsInstance<Msg.ToolResult>().first().content
        assertTrue(r, r.contains("not connected yet"))
    }

    @Test fun `with a provider the run is approved, never uploads secrets, and the sandbox closes after the turn`() {
        val log = mutableListOf<String>()
        val box = Box(log)
        CloudProviders.factory = { _ -> CloudProvider { box } }
        val fake = Fake(listOf({ Turn("", listOf(call), "tool_use") }, { Turn("ok", emptyList(), "stop") }))
        val s = session(fake, enabled = true)
        s.send("run it")
        s.pump { !s.running }
        val r = fake.seen[1].filterIsInstance<Msg.ToolResult>().first().content
        assertTrue(r, r.startsWith("[cloud · exit 0]") && r.contains("built fine"))
        assertEquals(listOf("upload:app.py", "exec:python app.py"), log)
        assertEquals(1, box.closed)
        assertTrue(s.items.filterIsInstance<ChatItem.Approval>().any { it.action?.contains("cloud workspace") == true })
    }
}
