package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.AnthropicFormat
import com.ctodrs.saqrcode.agent.Budget
import com.ctodrs.saqrcode.agent.Checkpoints
import com.ctodrs.saqrcode.agent.ModelPrice
import com.ctodrs.saqrcode.agent.Msg
import com.ctodrs.saqrcode.agent.OpenAiFormat
import com.ctodrs.saqrcode.agent.PriceTable
import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.agent.SlashCommands
import com.ctodrs.saqrcode.agent.SlashContext
import com.ctodrs.saqrcode.agent.StreamListener
import com.ctodrs.saqrcode.agent.Usage
import com.ctodrs.saqrcode.agent.UsageLedger
import com.ctodrs.saqrcode.agent.UsageParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

class CheckpointsTest {
    private val root: File = Files.createTempDirectory("cp-proj").toFile()
    private val store: File = Files.createTempDirectory("cp-store").toFile()
    private fun f(p: String) = File(root, p)
    private fun write(p: String, t: String) = f(p).apply { parentFile?.mkdirs(); writeText(t) }

    @Test fun createdFileIsDeletedOnRestore() {
        val cp = Checkpoints(root, store)
        val p = cp.record("t1", "add util", listOf("src/new/Util.kt"))
        assertEquals(Checkpoints.State.ABSENT, p.files.single().state)
        write("src/new/Util.kt", "object Util")
        val plan = cp.preview(p)
        assertEquals(listOf("src/new/Util.kt"), plan.deleted)
        cp.restore(p)
        assertFalse(f("src/new/Util.kt").exists())
        assertFalse("empty folders created by the agent are removed", f("src/new").exists())
    }

    @Test fun editedFileIsRestored() {
        write("a.txt", "before")
        val cp = Checkpoints(root, store)
        val p = cp.record("t1", "edit a", listOf("a.txt"))
        write("a.txt", "after")
        assertEquals(listOf("a.txt"), cp.preview(p).restored)
        cp.restore(p)
        assertEquals("before", f("a.txt").readText())
        assertTrue(cp.list().isEmpty())
    }

    @Test fun deletedFileIsRecreated() {
        write("docs/readme.md", "# hi\nمرحبا")
        val cp = Checkpoints(root, store)
        val p = cp.record("t1", "delete readme", listOf("docs/readme.md"))
        f("docs/readme.md").delete(); f("docs").delete()
        cp.restore(p)
        assertEquals("# hi\nمرحبا", f("docs/readme.md").readText())
    }

    @Test fun restoreRevertsNewerTurnsAndKeepsOlder() {
        write("a.txt", "v1")
        val cp = Checkpoints(root, store)
        val p1 = cp.record("t1", "first", listOf("a.txt"))
        write("a.txt", "v2")
        val p2 = cp.record("t2", "second", listOf("a.txt", "b.txt"))
        write("a.txt", "v3"); write("b.txt", "b")
        cp.record("t3", "third", listOf("a.txt"))
        write("a.txt", "v4")
        val plan = cp.restore(p2)
        assertEquals("v2", f("a.txt").readText())
        assertFalse(f("b.txt").exists())
        assertEquals(listOf("a.txt"), plan.restored)
        assertEquals(listOf("b.txt"), plan.deleted)
        assertEquals(listOf(p1.id), cp.list().map { it.id })
    }

    @Test fun sameTurnKeepsEarliestContent() {
        write("a.txt", "orig")
        val cp = Checkpoints(root, store)
        val p = cp.record("t1", "multi-step", listOf("a.txt"))
        write("a.txt", "step1")
        val again = cp.record("t1", "multi-step", listOf("a.txt", "c.txt"))
        assertEquals(p.id, again.id)
        assertEquals(1, cp.list().size)
        assertEquals(listOf("a.txt", "c.txt"), again.files.map { it.path })
        write("a.txt", "step2"); write("c.txt", "new")
        cp.restore(cp.list().single())
        assertEquals("orig", f("a.txt").readText())
        assertFalse(f("c.txt").exists())
    }

    @Test fun manifestHasIdTimestampPromptAndFiles() {
        write("a.txt", "x")
        val cp = Checkpoints(root, store)
        val p = cp.record("t1", "  fix   the\nbug  ", listOf("a.txt", "gone.txt"))
        val m = JSONObject(File(store, "${p.id}/manifest.json").readText())
        assertEquals(p.id, m.getString("id"))
        assertTrue(m.getLong("timestamp") > 0)
        assertEquals("fix the bug", m.getString("prompt"))
        val files = m.getJSONArray("files")
        assertEquals("saved", files.getJSONObject(0).getString("state"))
        assertEquals("absent", files.getJSONObject(1).getString("state"))
    }

    @Test fun skipsLargeAndBinaryFiles() {
        write("big.txt", "x".repeat(2048))
        f("img.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0, 0, 1, 2))
        write("ok.txt", "fine")
        val cp = Checkpoints(root, store, maxFileBytes = 1024)
        val p = cp.record("t1", "touch all", listOf("big.txt", "img.png", "ok.txt"))
        val states = p.files.associate { it.path to it.state }
        assertEquals(Checkpoints.State.SKIPPED, states["big.txt"])
        assertEquals(Checkpoints.State.SKIPPED, states["img.png"])
        assertEquals(Checkpoints.State.SAVED, states["ok.txt"])
        assertEquals("too_large", p.files.first { it.path == "big.txt" }.reason)
        assertEquals("binary", p.files.first { it.path == "img.png" }.reason)
        write("big.txt", "changed"); write("ok.txt", "changed")
        val plan = cp.restore(p)
        assertEquals(listOf("big.txt", "img.png"), plan.skipped)
        assertEquals("changed", f("big.txt").readText()) // skipped files are left alone
        assertEquals("fine", f("ok.txt").readText())
    }

    @Test fun prunesByCount() {
        val cp = Checkpoints(root, store, maxCount = 3)
        val ids = (1..5).map { i -> write("f$i.txt", "$i"); cp.record("t$i", "p$i", listOf("f$i.txt")).id }
        assertEquals(ids.takeLast(3).reversed(), cp.list().map { it.id })
    }

    @Test fun prunesBySize() {
        val cp = Checkpoints(root, store, maxBytes = 2500)
        (1..4).forEach { i -> write("f$i.txt", "x".repeat(1000)); cp.record("t$i", "p$i", listOf("f$i.txt")) }
        assertTrue(cp.sizeBytes() <= 2500 + 4096) // manifests are small
        assertTrue(cp.list().size in 1..2)
        assertEquals("p4", cp.list().first().prompt)
    }

    @Test fun defaultsMatchSpec() {
        assertEquals(50, Checkpoints.MAX_COUNT)
        assertEquals(50L * 1024 * 1024, Checkpoints.MAX_BYTES)
        assertEquals(2L * 1024 * 1024, Checkpoints.MAX_FILE_BYTES)
    }
}

class SlashCommandsTest {
    private val ctx = SlashContext(currentFile = "src/Main.kt")

    @Test fun parsesBuiltinsWithArgs() {
        val inv = SlashCommands.parse("/fix the null check please")!!
        assertEquals(SlashCommands.FIX, inv.command.name)
        assertEquals("the null check please", inv.args)
        val p = SlashCommands.expand(inv, ctx)
        assertTrue(p.contains("@src/Main.kt"))
        assertTrue(p.contains("Additional instructions: the null check please"))
    }

    @Test fun notACommand() {
        assertNull(SlashCommands.parse("fix /this"))
        assertNull(SlashCommands.parse("/"))
        assertNull(SlashCommands.parse("/unknown thing"))
        assertNull(SlashCommands.parse("/usr/bin/env is broken"))
    }

    @Test fun arabicAliases() {
        val pairs = mapOf("اصلح" to SlashCommands.FIX, "اشرح" to SlashCommands.EXPLAIN, "اختبر" to SlashCommands.TEST,
            "راجع" to SlashCommands.REVIEW, "خطة" to SlashCommands.PLAN)
        pairs.forEach { (alias, name) -> assertEquals(name, SlashCommands.parse("/$alias")!!.command.name) }
        // hamza forms are normalized
        assertEquals(SlashCommands.FIX, SlashCommands.parse("/أصلح الخطأ")!!.command.name)
        val explain = SlashCommands.parse("/اشرح")!!
        assertTrue(explain.arabicAlias)
        assertTrue(SlashCommands.expand(explain, ctx).contains("in Arabic"))
        assertTrue(SlashCommands.expand(SlashCommands.parse("/راجع")!!, ctx).endsWith("Reply in Arabic."))
    }

    @Test fun explainUsesSelectionAndUserLanguage() {
        val p = SlashCommands.expand(SlashCommands.parse("/explain")!!, ctx.copy(selection = "val x = 1"))
        assertTrue(p.contains("```\nval x = 1\n```"))
        assertTrue(p.contains("in the language I write in"))
        assertTrue(SlashCommands.expand(SlashCommands.parse("/explain")!!, ctx.copy(arabicUi = true)).contains("in Arabic"))
    }

    @Test fun planAndActions() {
        val plan = SlashCommands.expand(SlashCommands.parse("/plan add dark mode")!!, ctx)
        assertTrue(plan.contains("for: add dark mode"))
        assertTrue(plan.contains("update_plan"))
        assertTrue(plan.contains("Do NOT create, edit or delete"))
        assertEquals(SlashCommand.Action.CLEAR, SlashCommands.parse("/clear")!!.command.action)
        assertEquals(SlashCommand.Action.MODEL, SlashCommands.parse("/MODEL")!!.command.action)
        assertTrue(SlashCommands.expand(SlashCommands.parse("/commit")!!, ctx).contains("git_commit"))
        assertTrue(SlashCommands.expand(SlashCommands.parse("/review")!!, ctx).contains("git_diff"))
    }

    @Test fun fixWithoutOpenFileTargetsProject() {
        val p = SlashCommands.expand(SlashCommands.parse("/fix")!!, SlashContext())
        assertTrue(p.contains("the project"))
        val t = SlashCommands.expand(SlashCommands.parse("/test")!!, SlashContext())
        assertTrue(t.contains("ask me which one"))
    }

    @Test fun suggestions() {
        assertEquals(SlashCommands.builtins.size, SlashCommands.suggestions("/").size)
        assertEquals(listOf(SlashCommands.REVIEW, SlashCommands.REFACTOR), SlashCommands.suggestions("/re").map { it.name })
        assertEquals(setOf(SlashCommands.FIX, SlashCommands.EXPLAIN, SlashCommands.TEST, SlashCommands.PERF, SlashCommands.SECURE), SlashCommands.suggestions("/ا").map { it.name }.toSet())
        assertTrue(SlashCommands.suggestions("/fix ").isEmpty())
        assertTrue(SlashCommands.suggestions("hello").isEmpty())
    }

    @Test fun customCommands() {
        val custom = listOf(SlashCommand("deploy", "Deploy {file} to staging. {args}"), SlashCommand("docs", "Write KDoc for {file}"))
        assertEquals(listOf("document", "debug", "deploy", "docs"), SlashCommands.suggestions("/d", custom).map { it.name })
        val inv = SlashCommands.parse("/deploy now", custom)!!
        assertEquals("Deploy @src/Main.kt to staging. now", SlashCommands.expand(inv, ctx))
        // args are appended when the template has no {args}
        assertEquals("Write KDoc for @src/Main.kt\n\npublic API only", SlashCommands.expand(SlashCommands.parse("/docs public API only", custom)!!, ctx))
        // round trip through prefs JSON
        assertEquals(custom, SlashCommands.decode(SlashCommands.encode(custom)))
        assertTrue(SlashCommands.decode("not json").isEmpty())
    }

    @Test fun customValidation() {
        val existing = listOf(SlashCommand("deploy", "x"))
        assertNull(SlashCommands.validate("/ship", "Ship it", existing))
        assertNull(SlashCommands.validate("انشر", "انشر المشروع", existing))
        assertEquals("builtin", SlashCommands.validate("fix", "x", existing))
        assertEquals("builtin", SlashCommands.validate("اشرح", "x", existing))
        assertEquals("duplicate", SlashCommands.validate("Deploy", "x", existing))
        assertEquals("name", SlashCommands.validate("two words", "x", existing))
        assertEquals("template", SlashCommands.validate("ok", "  ", existing))
    }
}

class UsageTest {
    private class L : StreamListener { override fun onText(delta: String) {} }

    @Test fun openAiUsage() {
        val u = UsageParser.openAi(JSONObject("""{"choices":[],"usage":{"prompt_tokens":1200,"completion_tokens":85,"total_tokens":1285}}"""))!!
        assertEquals(Usage(1200, 85), u)
        assertNull(UsageParser.openAi(JSONObject("""{"choices":[],"usage":null}""")))
    }

    @Test fun openAiStreamPicksUpFinalUsageChunk() {
        val acc = OpenAiFormat.StreamAccumulator(L())
        acc.onData("""{"choices":[{"delta":{"content":"hi"}}],"usage":null}""")
        acc.onData("""{"choices":[{"delta":{},"finish_reason":"stop"}],"usage":null}""")
        acc.onData("""{"choices":[],"usage":{"prompt_tokens":40,"completion_tokens":2}}""")
        acc.onData("[DONE]")
        assertEquals(Usage(40, 2), acc.result().usage)
        val req = OpenAiFormat.buildRequest("m", "s", listOf(Msg.User("x")), emptyList(), includeUsage = true)
        assertTrue(req.getJSONObject("stream_options").getBoolean("include_usage"))
        assertFalse(OpenAiFormat.buildRequest("m", "s", listOf(Msg.User("x")), emptyList()).has("stream_options"))
    }

    @Test fun anthropicUsage() {
        val acc = AnthropicFormat.StreamAccumulator(L())
        acc.onEvent("message_start", """{"type":"message_start","message":{"usage":{"input_tokens":500,"cache_read_input_tokens":100,"output_tokens":1}}}""")
        acc.onEvent("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""")
        acc.onEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"hey"}}""")
        acc.onEvent("message_delta", """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":42}}""")
        val t = acc.result()
        assertEquals(Usage(600, 42), t.usage)
        assertEquals("end_turn", t.stopReason)
    }

    @Test fun anthropicWithoutUsageIsNull() {
        val acc = AnthropicFormat.StreamAccumulator(L())
        acc.onEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"x"}}""")
        assertNull(acc.result().usage)
    }

    @Test fun geminiUsage() {
        val o = JSONObject("""{"candidates":[],"usageMetadata":{"promptTokenCount":900,"candidatesTokenCount":120,"thoughtsTokenCount":30,"totalTokenCount":1050}}""")
        assertEquals(Usage(900, 150), UsageParser.gemini(o))
        assertEquals(Usage(900, 150), UsageParser.any(o))
        // also picked up from an OpenAI-compatible stream
        val acc = OpenAiFormat.StreamAccumulator(L())
        acc.onData(o.toString())
        assertEquals(Usage(900, 150), acc.result().usage)
    }

    @Test fun estimation() {
        val e = UsageParser.estimate(4001, 10)
        assertEquals(Usage(1001, 3, estimated = true), e)
        assertTrue((Usage(1, 1) + e).estimated)
        assertNull(UsageParser.any(JSONObject("""{"foo":1}""")))
    }

    @Test fun prices() {
        val table = PriceTable.merge(listOf(ModelPrice("my-local", 0.0, 0.0), ModelPrice("gpt-4o", 5.0, 15.0)))
        assertEquals(5.0, PriceTable.find("gpt-4o", table)!!.inputPerM, 0.0)
        assertEquals("claude-sonnet-4-5", PriceTable.find("anthropic/claude-sonnet-4.5", table)!!.model)
        assertEquals("claude-sonnet-4-5", PriceTable.find("claude-sonnet-4-5-20250929", table)!!.model)
        assertEquals("gpt-4o-mini", PriceTable.find("openai/gpt-4o-mini", table)!!.model)
        // 1M in + 1M out on sonnet = 3 + 15
        assertEquals(18.0, PriceTable.cost("claude-sonnet-4-5", Usage(1_000_000, 1_000_000), table)!!, 1e-9)
        assertNull(PriceTable.cost("unknown-model", Usage(10, 10), table))
        assertNull(PriceTable.cost("my-local", Usage(10, 10), table))
        assertNull(PriceTable.cost(mapOf("unknown" to Usage(5, 5)), table))
        assertEquals(table.size, PriceTable.decode(PriceTable.encode(table)).size)
    }

    @Test fun ledgerTotals() {
        val ledger = UsageLedger(File(Files.createTempDirectory("ledger").toFile(), "usage.json"))
        val d1 = LocalDate.of(2026, 11, 2); val d2 = LocalDate.of(2026, 11, 23); val old = LocalDate.of(2026, 10, 31)
        ledger.record("gpt-4o", Usage(100, 10), d1)
        ledger.record("gpt-4o", Usage(50, 5, estimated = true), d2)
        ledger.record("deepseek-chat", Usage(7, 3), d2)
        ledger.record("gpt-4o", Usage(1000, 1000), old)
        assertEquals(Usage(50, 5, true), ledger.day(d2)["gpt-4o"])
        val month = ledger.month(d2)
        assertEquals(Usage(150, 15, true), month["gpt-4o"])
        assertEquals(Usage(7, 3), month["deepseek-chat"])
        assertEquals(Usage(1000, 1000), ledger.month(old)["gpt-4o"])
    }
}

class BudgetTest {
    @Test fun thresholds() {
        assertEquals(Budget.Level.NONE, Budget.level(50.0, null))
        assertEquals(Budget.Level.NONE, Budget.level(50.0, 0.0))
        assertEquals(Budget.Level.OK, Budget.level(7.99, 10.0))
        assertEquals(Budget.Level.WARN, Budget.level(8.0, 10.0))
        assertEquals(Budget.Level.WARN, Budget.level(9.99, 10.0))
        assertEquals(Budget.Level.OVER, Budget.level(10.0, 10.0))
        assertEquals(Budget.Level.OVER, Budget.level(12.0, 10.0))
        assertFalse(Budget.needsConfirmation(9.99, 10.0))
        assertTrue(Budget.needsConfirmation(10.0, 10.0))
        assertFalse(Budget.needsConfirmation(1000.0, null))
        assertEquals(1f, Budget.fraction(20.0, 10.0), 0f)
        assertEquals(0.5f, Budget.fraction(5.0, 10.0), 1e-6f)
    }
}
