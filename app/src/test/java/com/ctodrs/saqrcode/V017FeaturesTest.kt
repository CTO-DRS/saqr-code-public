package com.ctodrs.saqrcode

import android.content.Context
import com.ctodrs.saqrcode.agent.AvailableProvider
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.CostEstimate
import com.ctodrs.saqrcode.agent.DirImageRepo
import com.ctodrs.saqrcode.agent.Failover
import com.ctodrs.saqrcode.agent.FallbackTarget
import com.ctodrs.saqrcode.agent.HistoryRebuilder
import com.ctodrs.saqrcode.agent.LlmException
import com.ctodrs.saqrcode.agent.MemoryBank
import com.ctodrs.saqrcode.agent.ModelCatalog
import com.ctodrs.saqrcode.agent.ModelTier
import com.ctodrs.saqrcode.agent.Msg
import com.ctodrs.saqrcode.agent.PriceTable
import com.ctodrs.saqrcode.agent.ProjectIndex
import com.ctodrs.saqrcode.agent.Providers
import com.ctodrs.saqrcode.agent.RouteRequest
import com.ctodrs.saqrcode.agent.RouteTarget
import com.ctodrs.saqrcode.agent.SessionCodec
import com.ctodrs.saqrcode.agent.SessionData
import com.ctodrs.saqrcode.agent.SessionMeta
import com.ctodrs.saqrcode.agent.SmartRouter
import com.ctodrs.saqrcode.agent.SpendCap
import com.ctodrs.saqrcode.agent.ToolCall
import com.ctodrs.saqrcode.agent.Tools
import com.ctodrs.saqrcode.agent.Turn
import com.ctodrs.saqrcode.agent.Usage
import com.ctodrs.saqrcode.agent.memory.Lessons
import com.ctodrs.saqrcode.agent.memory.MemoryEngine
import com.ctodrs.saqrcode.agent.memory.MemoryRecall
import com.ctodrs.saqrcode.agent.memory.MemorySafety
import com.ctodrs.saqrcode.agent.memory.SessionMemory
import com.ctodrs.saqrcode.agent.memory.SessionSummary
import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** v0.17 «محرك الذاكرة والموجّه» · Saqr Memory Engine & Model Router (Phase 4). */
class V017FeaturesTest {

    @get:Rule val tmp = TemporaryFolder()

    private var n = 0
    private fun dir() = File(tmp.root, "p${n++}").apply { mkdirs() }
    private fun bank(d: File = dir()) = MemoryBank(File(d, ".saqr").apply { mkdirs() })
    private val prices = PriceTable.seeds

    // ------------------------------------------------------------------ project layer: migration & typed codec

    @Test fun oldV1MemoryJsonLoadsAsNotesAndRendersUnchanged() {
        val d = dir()
        val saqr = File(d, ".saqr").apply { mkdirs() }
        // exactly the v0.13–v0.16 on-disk shape (schema 1, no type/source/files)
        val v1 = JSONObject().put("v", 1).put("entries", JSONArray()
            .put(JSONObject().put("key", "اللغة").put("value", "كل الردود بالعربية").put("kind", "preference").put("createdAt", 10L).put("updatedAt", 20L))
            .put(JSONObject().put("key", "state").put("value", "Riverpod").put("kind", "decision").put("createdAt", 11L).put("updatedAt", 21L))
            .put(JSONObject().put("key", "entry").put("value", "main.dart").put("kind", "fact").put("createdAt", 12L).put("updatedAt", 22L)))
        File(saqr, "memory.json").writeText(v1.toString())
        val b = MemoryBank(saqr)
        assertEquals(3, b.count)
        assertTrue(b.all.all { it.type == MemoryBank.Type.NOTE && it.source == "legacy" && it.files.isEmpty() })
        assertEquals(MemoryBank.Kind.PREFERENCE, b.all.first { it.key == "اللغة" }.kind)
        assertEquals(10L, b.all.first { it.key == "اللغة" }.createdAt)
        val out = b.render()
        assertTrue(out.contains("[User preferences]\n- اللغة — كل الردود بالعربية"))
        assertTrue(out.contains("- state — Riverpod") && out.contains("- entry — main.dart"))
        // the next write upgrades the file to schema 2 and keeps everything
        b.save("build", "gradle", MemoryBank.Kind.FACT)
        val o = JSONObject(File(saqr, "memory.json").readText())
        assertEquals(2, o.getInt("v"))
        val again = MemoryBank(saqr)
        assertEquals(4, again.count)
        assertEquals("note", again.all.first { it.key == "state" }.type.id)
        assertEquals("legacy", again.all.first { it.key == "state" }.source)
    }

    @Test fun typedEntriesRoundTripWithFilesSourceAndHits() {
        val d = dir()
        val b = bank(d)
        assertTrue(b.save("network layer", "Retrofit + OkHttp in NetworkModule", MemoryBank.Kind.FACT, MemoryBank.Type.ARCHITECTURE, "agent",
            listOf("./app/src/Net.kt", "/workspace/app/src/Api.kt")).startsWith("Remembered"))
        b.save("ktlint", "4-space indent, no wildcard imports", MemoryBank.Kind.FACT, MemoryBank.Type.CONVENTION)
        b.save("okhttp", "4.12.0", MemoryBank.Kind.FACT, MemoryBank.Type.DEPENDENCY)
        b.save("network layer", "Retrofit + OkHttp (updated)", MemoryBank.Kind.FACT, MemoryBank.Type.ARCHITECTURE)
        val r = bank(d) // fresh object = from disk
        val net = r.all.first { it.key == "network layer" }
        assertEquals(MemoryBank.Type.ARCHITECTURE, net.type)
        assertEquals(MemoryBank.Kind.DECISION, net.kind) // architecture is a kind of decision
        assertEquals(listOf("app/src/Net.kt", "app/src/Api.kt"), net.files) // ./ and /workspace/ prefixes dropped, refs kept on update
        assertEquals(2, net.hits)
        assertEquals("agent", net.source)
        assertEquals(MemoryBank.Type.CONVENTION, r.all.first { it.key == "ktlint" }.type)
        assertEquals(MemoryBank.Type.DEPENDENCY, MemoryBank.Type.byId("dependency"))
        assertNull(MemoryBank.Type.byId("bogus"))
        assertTrue(r.render().contains("(architecture) network layer — Retrofit + OkHttp (updated) [files: app/src/Net.kt, app/src/Api.kt]"))
        assertTrue(MemoryBank.MAX_ENTRIES >= 200 && MemoryBank.SCHEMA == 2)
    }

    @Test fun staleFileRefsAreFlaggedWhenTheProjectIndexRebuilds() {
        val d = dir()
        File(d, "src").mkdirs()
        val f = File(d, "src/Net.kt").apply { writeText("class Net") }
        val b = bank(d)
        b.save("net", "the HTTP client lives here", MemoryBank.Kind.FACT, MemoryBank.Type.ARCHITECTURE, files = listOf("src/Net.kt"))
        val idx = ProjectIndex(d)
        idx.onRebuilt = { b.refreshStale { File(d, it).exists() } }
        idx.rebuild()
        assertFalse(b.all.single().stale)
        f.delete()
        idx.invalidate()
        assertTrue(idx.render("Net").isNotEmpty()) // a query after invalidate() rebuilds
        assertTrue(b.all.single().stale)
        assertTrue(b.render().contains("[stale: a referenced file no longer exists]"))
        // relevance halves a stale entry's score
        val e = b.all.single()
        val fresh = MemoryRecall.Index(listOf(e.copy(stale = false))).scores("net client")[0]
        val stale = MemoryRecall.Index(listOf(e)).scores("net client")[0]
        assertEquals(fresh / 2, stale, 1e-9)
    }

    // ------------------------------------------------------------------ BM25 retrieval

    @Test fun bm25RanksArabicWithNormalizationAndLightStemming() {
        val b = bank()
        b.save("الشبكة", "نستخدم Retrofit مع OkHttp للطلبات", MemoryBank.Kind.DECISION)
        b.save("قاعدة البيانات", "Room مع ترحيلات يدوية", MemoryBank.Kind.DECISION)
        b.save("الواجهة", "Compose مع ثيم داكن", MemoryBank.Kind.FACT)
        val idx = MemoryRecall.Index(b.all)
        // «الشبكات» (plural + article) and «الشبكه» (taa marbouta variant) both find «الشبكة»
        assertEquals("الشبكة", idx.rank("مشكلة في الشبكات").first().first.key)
        assertEquals("الشبكة", idx.rank("الشبكه بطيئة").first().first.key)
        assertEquals("قاعدة البيانات", idx.rank("أضف عمودًا لقاعدة البيانات").first().first.key)
        assertTrue(idx.rank("django").isEmpty())
        assertEquals(MemoryRecall.tokens("الشبكة"), MemoryRecall.tokens("الشّبكه"))
    }

    @Test fun bm25RanksEnglishWithStemsCamelCaseAndIdf() {
        val b = bank()
        b.save("test runner", "run tests with ./gradlew testDebugUnitTest", MemoryBank.Kind.FACT)
        b.save("NetworkModule", "provides the OkHttp client", MemoryBank.Kind.FACT)
        b.save("theme", "dark theme by default", MemoryBank.Kind.PREFERENCE)
        val idx = MemoryRecall.Index(b.all)
        assertEquals("test runner", idx.rank("the failing tests").first().first.key)
        assertEquals("NetworkModule", idx.rank("network module crash").first().first.key)
        assertTrue(MemoryRecall.tokens("NetworkModule").containsAll(listOf("network", "module")))
        assertEquals("test", MemoryRecall.stem("tests"))
        assertEquals("class", MemoryRecall.stem("class"))
    }

    @Test fun topKInjectionKeepsPreferencesAndOnlyRelevantEntries() {
        val b = bank()
        b.save("لغة الردود", "العربية دائمًا", MemoryBank.Kind.PREFERENCE)
        (1..20).forEach { b.save("note $it", "unrelated detail number $it about topic$it", MemoryBank.Kind.FACT) }
        b.save("payments", "Moyasar sandbox for payments, never live keys in debug", MemoryBank.Kind.FACT)
        b.save("routing", "single activity, Navigation Compose", MemoryBank.Kind.DECISION, MemoryBank.Type.ARCHITECTURE)
        val on = MemoryEngine.block(b, null, MemoryEngine.Config(relevance = true, topK = 3), "fix the payments screen")
        assertTrue(on.contains("لغة الردود")) // pinned preference
        assertTrue(on.contains("payments — Moyasar"))
        assertTrue(on.contains("routing")) // standing architecture fills the remaining K
        assertFalse(on.contains("note 7"))
        val lines = on.lines().count { it.startsWith("- ") }
        assertTrue("preference + at most K", lines <= 1 + 3)
        // off = exactly the v0.13 rendering (inject all within the budget)
        val off = MemoryEngine.block(b, null, MemoryEngine.Config(relevance = false), "fix the payments screen")
        assertEquals(b.render(), off)
        assertEquals(8, MemoryRecall.DEFAULT_K)
    }

    // ------------------------------------------------------------------ lessons

    @Test fun lessonCaptureSignatureAndDedupe() {
        val b = bank()
        val tests1 = "[exit 1]\n> Task :app:test\nFAILED\napp/src/test/CartTest.kt:42:17 AssertionError: expected:<3> but was:<2>\n"
        val tests2 = "[exit 1]\nsrc/test/CartTest.kt:57:9 AssertionError: expected:<3> but was:<2>\n"
        val s1 = Lessons.signature(null, tests1)!!
        val s2 = Lessons.signature(null, tests2)!!
        assertFalse(s1.contains("42") || s1.contains("/"))
        assertEquals(s1, s2) // line numbers and paths do not make a new lesson
        val sol = Lessons.solution(listOf("app/src/main/Cart.kt"), "## Fixed the off-by-one in Cart.total()\nmore text")
        assertEquals("Fixed by changing app/src/main/Cart.kt: Fixed the off-by-one in Cart.total().", sol)
        val (r1, d1) = b.saveLesson(s1, sol, listOf("app/src/main/Cart.kt"))
        assertTrue(r1.startsWith("Remembered")); assertFalse(d1)
        val (r2, d2) = b.saveLesson(s2, "Fixed by changing Cart.kt again.")
        assertTrue(r2.startsWith("Lesson updated")); assertTrue(d2)
        assertEquals(1, b.byType(MemoryBank.Type.LESSON).size)
        assertEquals(2, b.byType(MemoryBank.Type.LESSON).single().hits)
        assertEquals("auto:gate", b.byType(MemoryBank.Type.LESSON).single().source)
        // a different error is a different lesson
        b.saveLesson(Lessons.signature("error: Unresolved reference: retrofit", null)!!, "Added the retrofit dependency.")
        assertEquals(2, b.byType(MemoryBank.Type.LESSON).size)
        // surfaced to the Debugger for the same error text, not for an unrelated one
        val match = Lessons.matching(b, "CartTest.kt:88 AssertionError: expected:<3> but was:<2>")
        assertEquals(1, match.size)
        assertTrue(Lessons.section(match).contains("Lessons from earlier fixes"))
        assertTrue(Lessons.matching(b, "NullPointerException in LoginActivity").isEmpty())
        assertNull(Lessons.signature("Lint OK", "[exit 0]\nall good"))
    }

    // ------------------------------------------------------------------ layers: isolation & the global layer

    @Test fun projectMemoryNeverLeaksAndGlobalIsOptIn() {
        val a = bank(); val bProj = bank()
        val gdir = File(tmp.root, "app/memory")
        val global = MemoryBank(gdir, MemoryBank.GLOBAL_FILE, MemoryBank.Layer.GLOBAL)
        // project A's memory is A's only
        MemoryEngine.save(JSONObject().put("key", "db").put("value", "Room"), a, global, false)
        assertEquals(1, a.count); assertTrue(bProj.isEmpty()); assertTrue(global.isEmpty())
        // global off (the default): a global save is refused and nothing is written
        val off = MemoryEngine.save(JSONObject().put("key", "tone").put("value", "short answers").put("scope", "global"), a, global, false)
        assertTrue(off.startsWith("Refused: global"))
        assertFalse(File(gdir, MemoryBank.GLOBAL_FILE).exists())
        assertFalse(MemoryEngine.block(a, global, MemoryEngine.Config(global = false), "").contains(MemoryEngine.GLOBAL_HEADER))
        // global on: an explicit global save lands in the global file only and both projects see it
        val on = MemoryEngine.save(JSONObject().put("key", "tone").put("value", "short answers").put("scope", "global"), a, global, true)
        assertTrue(on.contains("global"))
        assertEquals(1, a.count)
        val cfg = MemoryEngine.Config(global = true)
        assertTrue(MemoryEngine.block(a, global, cfg, "").contains("tone — short answers"))
        assertTrue(MemoryEngine.block(bProj, global, cfg, "").contains("tone — short answers"))
        assertFalse(MemoryEngine.block(bProj, global, cfg, "").contains("Room"))
        val gfile = File(gdir, MemoryBank.GLOBAL_FILE).readText()
        assertFalse(gfile.contains("Room"))
        // the global layer keeps preferences only, never file refs; promotion needs the layer on
        assertTrue(global.save("x", "y", MemoryBank.Kind.FACT, files = listOf("a.kt")).startsWith("Refused"))
        assertEquals(MemoryBank.Kind.PREFERENCE, global.all.single().kind)
        a.save("indent", "tabs", MemoryBank.Kind.PREFERENCE)
        assertTrue(MemoryEngine.promote(a.all.first { it.key == "indent" }, global, false).startsWith("Refused: global"))
        assertTrue(MemoryEngine.promote(a.all.first { it.key == "db" }, global, true).startsWith("Refused"))
        assertTrue(MemoryEngine.promote(a.all.first { it.key == "indent" }, global, true).startsWith("Remembered"))
        // memory_forget keeps working (project by default, global only on request)
        assertTrue(MemoryEngine.forget(JSONObject().put("key", "db"), a, global, true).startsWith("Forgot"))
        assertTrue(MemoryEngine.forget(JSONObject().put("key", "tone").put("scope", "global"), a, global, true).startsWith("Forgot"))
        // the shared global object is one per file
        assertTrue(MemoryBank.shared(gdir, "g2.json", MemoryBank.Layer.GLOBAL) === MemoryBank.shared(gdir, "g2.json", MemoryBank.Layer.GLOBAL))
    }

    @Test fun secretGuardOnEveryLayer() {
        val p = bank()
        val g = MemoryBank(File(tmp.root, "g"), MemoryBank.GLOBAL_FILE, MemoryBank.Layer.GLOBAL)
        val apiKey = "zq7Lr2Pp9Wm4Xc8Vb1Nf" // a configured key with no telltale prefix
        // project layer: heuristic + the user's real configured secret
        assertTrue(MemoryEngine.save(JSONObject().put("key", "deploy").put("value", "token: ghp_ABCDEFGHIJKLMNOPQRSTUVWX12"), p, g, true).startsWith("Refused"))
        assertTrue(MemoryEngine.save(JSONObject().put("key", "openai").put("value", "use $apiKey"), p, g, true, apiKey).startsWith("Refused"))
        // global layer
        assertTrue(MemoryEngine.save(JSONObject().put("key", "كلمة السر").put("value", "x").put("scope", "global"), p, g, true).startsWith("Refused"))
        assertTrue(MemoryEngine.save(JSONObject().put("key", "k").put("value", apiKey).put("scope", "global"), p, g, true, apiKey).startsWith("Refused"))
        // lesson layer (auto-captured) and file refs
        assertTrue(p.saveLesson("AuthError token rejected", "set password=hunter2 in env").first.startsWith("Refused"))
        assertTrue(p.save("k", "v", MemoryBank.Kind.FACT, files = listOf("sk-ABCDEFGHIJKLMNOP.txt")).startsWith("Refused"))
        assertTrue(p.isEmpty() && g.isEmpty())
        // scrub (lesson capture / session summary input): configured secrets redacted, secret-looking lines dropped
        val scrubbed = MemorySafety.scrub("ok line\nAuthorization uses $apiKey here\napi_key = abcdef123\nlast", apiKey)
        assertFalse(scrubbed.contains(apiKey)); assertFalse(scrubbed.contains("abcdef123"))
        assertTrue(scrubbed.contains("ok line") && scrubbed.contains("last"))
        assertTrue(MemorySafety.allowed("prefer Kotlin coroutines", apiKey))
    }

    // ------------------------------------------------------------------ session layer

    private fun turn(i: Int, big: Int): List<ChatItem> {
        val u = ChatItem.User("طلب رقم $i", prompt = "request $i")
        val a = ChatItem.Assistant().apply {
            text = "answer $i " + "x".repeat(big); streaming = false
            calls = listOf(ToolCall("c$i", Tools.READ, "{\"path\":\"f$i.kt\"}"))
            results["c$i"] = "content $i"
        }
        return listOf(u, a)
    }

    @Test fun rollingSessionSummaryRebuildsAndStaysBackwardCompatible() {
        val items = (1..8).flatMap { turn(it, 2_000) }
        assertNull(SessionMemory.planRoll(items, null, threshold = 1_000_000)) // short → nothing to roll
        val roll = SessionMemory.planRoll(items, null, threshold = 5_000, keep = 3)!!
        assertEquals(5, roll.turns) // turns 1..5 summarised, 6..8 kept verbatim
        assertTrue(roll.rendered.contains("request 1") && !roll.rendered.contains("request 6"))
        val s = SessionMemory.apply(roll, "The user asked for eight small things; files f1–f5 were read; nothing pending.", now = 99L)!!
        val h = SessionMemory.rebuild(items, s)
        assertTrue((h.first() as Msg.User).text.startsWith(SessionMemory.MARKER))
        assertEquals(1 + HistoryRebuilder.rebuild(items.subList(10, items.size)).size, h.size)
        assertTrue(h.filterIsInstance<Msg.ToolResult>().all { it.callId in setOf("c6", "c7", "c8") }) // no split tool pairs
        // a summary whose anchor no longer matches (restored / edited chat) falls back to the full history
        val wrong = s.copy(anchor = "something else")
        assertEquals(HistoryRebuilder.rebuild(items), SessionMemory.rebuild(items, wrong))
        assertEquals(HistoryRebuilder.rebuild(items), SessionMemory.rebuild(items, null))
        // rolling again folds the previous summary in
        val more = items + (9..12).flatMap { turn(it, 2_000) }
        val roll2 = SessionMemory.planRoll(more, s, threshold = 5_000, keep = 3)!!
        assertTrue(roll2.rendered.startsWith("PREVIOUS SUMMARY"))
        assertEquals(9, roll2.turns)
        // codec: stored with the session; an old session file without it still loads
        val repo = DirImageRepo(File(tmp.root, "img"))
        val meta = SessionMeta("s1", "t", true, 1L, 2L, 3, "")
        val json = SessionCodec.encode(SessionData(meta, items, summary = s), repo)
        assertEquals(s, SessionCodec.decode(JSONObject(json.toString()), repo).summary)
        json.remove("summary")
        assertNull(SessionCodec.decode(json, repo).summary)
        assertNull(SessionSummary.fromJson(JSONObject().put("text", "").put("turns", 2)))
    }

    // ------------------------------------------------------------------ model router

    private val anthropic = AvailableProvider("anthropic", listOf("claude-sonnet-4-5", "claude-opus-4-1", "claude-haiku-4-5"), 200_000)
    private val openai = AvailableProvider("openai", listOf("gpt-4.1-mini", "gpt-4.1", "gpt-4o", "o4-mini"), 1_000_000)
    private val deepseek = AvailableProvider("deepseek", listOf("deepseek-chat", "deepseek-reasoner"), 128_000)

    @Test fun capabilityTableUsesKnownModelsPricesAndVision() {
        val c = ModelCatalog.caps("anthropic/claude-sonnet-4.5", 200_000, prices)
        assertTrue(c.known && c.vision && c.tools)
        assertEquals(200_000, c.contextWindow)
        assertEquals(18.0, c.unitPrice!!, 1e-9) // 3 + 15 from the price table
        val r = ModelCatalog.caps("deepseek-reasoner", 128_000, prices)
        assertFalse(r.vision); assertFalse(r.tools)
        val unknown = ModelCatalog.caps("my-local-llm", 32_000, prices)
        assertFalse(unknown.known); assertNull(unknown.unitPrice); assertEquals(32_000, unknown.contextWindow)
        // the user's vision override wins
        assertTrue(ModelCatalog.caps("my-local-llm", 32_000, prices, mapOf("my-local-llm" to "on")).vision)
        // every model the app ships has a row
        Providers.all.filter { it.id != "custom" && it.id != "openrouter" }.flatMap { it.suggestedModels }.forEach { assertTrue(it, ModelCatalog.knows(it)) }
    }

    @Test fun routerPicksCheapForPlanningAndKeepsTheStrongCoder() {
        val primary = RouteTarget("anthropic", "claude-sonnet-4-5")
        val cheap = SmartRouter.route(RouteRequest(ModelTier.CHEAP, primary, needsTools = false), listOf(anthropic), prices)
        assertEquals(RouteTarget("anthropic", "claude-haiku-4-5"), cheap.target)
        assertTrue(cheap.routed)
        val strong = SmartRouter.route(RouteRequest(ModelTier.STRONG, primary), listOf(anthropic, openai), prices)
        assertEquals(primary, strong.target)
        assertFalse(strong.routed)
        assertEquals("your coder model", strong.reason)
        // cheapest across configured providers: gpt-4.1-mini (0.4+1.6) beats haiku (1+5)
        val cross = SmartRouter.route(RouteRequest(ModelTier.CHEAP, primary, needsTools = false), listOf(anthropic, openai), prices)
        assertEquals(RouteTarget("openai", "gpt-4.1-mini"), cross.target)
        // an unpriced coder never routes "for savings"
        val unpriced = SmartRouter.route(RouteRequest(ModelTier.CHEAP, RouteTarget("custom", "my-llm")),
            listOf(AvailableProvider("custom", listOf("my-llm"), 32_000), anthropic), prices)
        assertEquals(RouteTarget("custom", "my-llm"), unpriced.target)
    }

    @Test fun routerSendsImagesToAVisionModelAndBigPromptsToABigWindow() {
        val primary = RouteTarget("deepseek", "deepseek-chat")
        val vis = SmartRouter.route(RouteRequest(ModelTier.STRONG, primary, needsVision = true), listOf(deepseek, openai), prices)
        assertEquals("openai", vis.target.provider)
        assertTrue(ModelCatalog.caps(vis.target.model, 1_000_000, prices).vision)
        assertEquals("images need a vision model", vis.reason)
        // no vision model configured anywhere → stays on the user's model, says so
        val none = SmartRouter.route(RouteRequest(ModelTier.STRONG, primary, needsVision = true), listOf(deepseek), prices)
        assertEquals(primary, none.target)
        assertTrue(none.reason.startsWith("no capable model"))
        // ~300k tokens do not fit gpt-4o's 128k → a 1M-window model
        val big = SmartRouter.route(RouteRequest(ModelTier.STRONG, RouteTarget("openai", "gpt-4o"), contextTokens = 300_000), listOf(openai, anthropic), prices)
        assertTrue(big.target.model.startsWith("gpt-4.1"))
        assertTrue(big.reason.contains("exceeds"))
        // tool-using steps never go to a model without tool calling
        val tools = SmartRouter.route(RouteRequest(ModelTier.CHEAP, RouteTarget("deepseek", "deepseek-chat"), needsTools = true), listOf(deepseek), prices)
        assertNotEquals("deepseek-reasoner", tools.target.model)
    }

    @Test fun independentReviewerDiffersFromTheCoder() {
        val coder = RouteTarget("anthropic", "claude-sonnet-4-5")
        val other = SmartRouter.route(RouteRequest(ModelTier.INDEPENDENT_REVIEW, coder, needsTools = false, avoid = coder), listOf(anthropic, openai), prices)
        assertEquals("openai", other.target.provider) // another provider/family preferred
        assertTrue(FalconRouterTierAtLeastMid(other.target.model))
        val same = SmartRouter.route(RouteRequest(ModelTier.INDEPENDENT_REVIEW, coder, needsTools = false, avoid = coder), listOf(anthropic), prices)
        assertEquals("anthropic", same.target.provider)
        assertNotEquals(coder.model, same.target.model)
        assertTrue(same.reason.contains("different model"))
        val alone = SmartRouter.route(RouteRequest(ModelTier.INDEPENDENT_REVIEW, coder, needsTools = false, avoid = coder),
            listOf(AvailableProvider("anthropic", listOf("claude-sonnet-4-5"), 200_000)), prices)
        assertEquals(coder, alone.target)
        assertTrue(alone.reason.startsWith("no independent model"))
    }

    private fun FalconRouterTierAtLeastMid(m: String) =
        com.ctodrs.saqrcode.agent.FalconRouter.tierOf(m).rank >= com.ctodrs.saqrcode.agent.FalconRouter.Tier.MID.rank

    @Test fun failoverChainPutsTheUserFallbackFirstThenOtherProviders() {
        val chosen = RouteTarget("anthropic", "claude-sonnet-4-5")
        val req = RouteRequest(ModelTier.STRONG, chosen)
        val avail = listOf(anthropic, openai, deepseek)
        val all = SmartRouter.candidates(avail, prices, emptyMap())
        val chain = SmartRouter.failoverChain(chosen, req, all, FallbackTarget("deepseek", "deepseek-chat"), avail)
        assertEquals(RouteTarget("deepseek", "deepseek-chat"), chain.first())
        assertEquals(SmartRouter.MAX_FAILOVER, chain.size)
        assertTrue(chain.none { it == chosen })
        assertTrue(chain.drop(1).all { it.provider != "anthropic" }) // an outage takes the whole provider down
        // a fallback on a provider without a key is never used
        val noKey = SmartRouter.failoverChain(chosen, req, SmartRouter.candidates(listOf(anthropic, openai), prices, emptyMap()),
            FallbackTarget("deepseek", "deepseek-chat"), listOf(anthropic, openai))
        assertTrue(noKey.none { it.provider == "deepseek" })
        assertTrue(SmartRouter.logLine("reviewer", SmartRouter.route(req, avail, prices)).startsWith("reviewer → anthropic · claude-sonnet-4-5 ("))
    }

    /** Acceptance scenario 12: the provider fails → the run switches to the alternative and finishes. */
    @Test fun acceptance12ProviderFailureSwitchesToTheAlternative() = runBlocking {
        val chain = listOf(RouteTarget("openai", "gpt-4.1"), RouteTarget("anthropic", "claude-sonnet-4-5"))
        val calls = ArrayList<String>()
        val switches = ArrayList<String>()
        // a fake client per provider: OpenAI is down (503), Anthropic answers
        val fake: suspend (RouteTarget) -> Turn = { t ->
            calls += t.provider
            if (t.provider == "openai") throw LlmException("HTTP 503: upstream overloaded")
            Turn("done on ${t.model}", emptyList(), "end_turn", Usage(100, 20))
        }
        val (turn, used) = Failover.run(chain, onSwitch = { from, to, e -> switches += "${from.provider}->${to.provider}:${e.message}" }, call = fake)
        assertEquals("done on claude-sonnet-4-5", turn.text)
        assertEquals(chain[1], used)
        assertEquals(listOf("openai", "anthropic"), calls)
        assertEquals(listOf("openai->anthropic:HTTP 503: upstream overloaded"), switches)
        // a bad key (401) is not a provider outage: it surfaces at once, no switching
        calls.clear()
        try {
            Failover.run<Turn>(chain) { t -> calls += t.provider; throw LlmException("HTTP 401: invalid key") }
            fail("401 must not fail over")
        } catch (e: LlmException) { assertTrue(e.message!!.startsWith("HTTP 401")) }
        assertEquals(listOf("openai"), calls)
        // every provider down: the last transient error surfaces
        try {
            Failover.run<Turn>(chain) { throw LlmException("HTTP 502: bad gateway") }
            fail("exhausted chain must throw")
        } catch (e: LlmException) { assertTrue(e.message!!.contains("502")) }
        Unit
    }

    @Test fun spendCapDecisionsAndCostEstimate() {
        assertEquals(SpendCap.Hit.NONE, SpendCap.check(4.0, 20.0, 0.0, 0.0)) // caps off
        assertEquals(SpendCap.Hit.DAILY, SpendCap.check(5.0, 20.0, 5.0, 100.0))
        assertEquals(SpendCap.Hit.MONTHLY, SpendCap.check(1.0, 100.01, 5.0, 100.0))
        assertEquals(SpendCap.Hit.NONE, SpendCap.check(null, null, 5.0, 100.0)) // nothing priced → cannot apply
        assertEquals("daily_cap", SpendCap.noticeText(SpendCap.Hit.DAILY))
        // claude-haiku-4-5: 1 $/M in, 5 $/M out. 2 steps from a 10k context: (10k + 11.5k) in, 1.4k out
        val two = CostEstimate.steps("claude-haiku-4-5", 2, 10_000, prices)!!
        assertEquals((21_500 * 1.0 + 1_400 * 5.0) / 1e6, two, 1e-12)
        val r = CostEstimate.range(listOf("claude-haiku-4-5" to 4, "claude-sonnet-4-5" to 1), 10_000, prices)!!
        assertTrue(r.low < r.high)
        assertEquals(CostEstimate.steps("claude-haiku-4-5", 2, 10_000, prices)!! + CostEstimate.steps("claude-sonnet-4-5", 1, 10_000, prices)!!, r.low, 1e-12)
        assertNull(CostEstimate.range(listOf("my-local-llm" to 3), 10_000, prices)) // unpriced → no fake number
    }
}

/** v0.17 settings: every risky feature is off by default, so an upgrade behaves like v0.16. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V017SettingsTest {
    private fun settings(): SettingsStore {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("saqr_settings", Context.MODE_PRIVATE).edit().clear().commit()
        return SettingsStore(app, SecureStore(app))
    }

    @Test fun defaultsPreserveBehaviourAndClamp() {
        val s = settings()
        assertFalse(s.memoryRelevance); assertFalse(s.sessionSummary); assertFalse(s.globalMemory); assertFalse(s.smartRouter)
        assertTrue(s.memoryLessons)
        assertEquals(8, s.memoryTopK)
        assertEquals(0.0, s.dailySpendCap, 0.0); assertEquals(0.0, s.monthlySpendCap, 0.0)
        s.memoryTopK = 99; assertEquals(MemoryRecall.MAX_K, s.memoryTopK)
        s.memoryTopK = 0; assertEquals(MemoryRecall.MIN_K, s.memoryTopK)
        s.dailySpendCap = 2.5; assertEquals(2.5, s.dailySpendCap, 1e-9)
        s.dailySpendCap = -1.0; assertEquals(0.0, s.dailySpendCap, 0.0)
    }

    @Test fun routerOnlySeesProvidersWithAKeyOrALocalEndpoint() {
        val s = settings()
        s.activeProvider = "anthropic"
        val ids = s.routableProviders().map { it.id }
        // the active provider is the user's own choice; everything else needs a saved key
        assertEquals(listOf("anthropic"), ids)
        assertFalse("openai" in ids || "deepseek" in ids || "openrouter" in ids)
        // a keyless custom endpoint counts only once it has a real URL and a model
        assertFalse("custom" in ids)
        s.saveProvider("custom", "http://127.0.0.1:8080/v1", "qwen2.5-coder", null)
        val withLocal = s.routableProviders()
        assertTrue(withLocal.any { it.id == "custom" && it.models == listOf("qwen2.5-coder") })
        s.saveProvider("custom", "https://", "", null)
    }
}
