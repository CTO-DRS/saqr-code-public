package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ActiveRuns
import com.ctodrs.saqrcode.agent.AgentRuns
import com.ctodrs.saqrcode.agent.AskAnswer
import com.ctodrs.saqrcode.agent.AskUserTool
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.FileChange
import com.ctodrs.saqrcode.agent.HistoryRebuilder
import com.ctodrs.saqrcode.agent.ImagePart
import com.ctodrs.saqrcode.agent.ImagePruner
import com.ctodrs.saqrcode.agent.Msg
import com.ctodrs.saqrcode.agent.NotifText
import com.ctodrs.saqrcode.agent.PlanItem
import com.ctodrs.saqrcode.agent.RunActivity
import com.ctodrs.saqrcode.agent.RunEffect
import com.ctodrs.saqrcode.agent.RunInfo
import com.ctodrs.saqrcode.agent.RunOutcome
import com.ctodrs.saqrcode.agent.RunWait
import com.ctodrs.saqrcode.agent.SessionCodec
import com.ctodrs.saqrcode.agent.SessionData
import com.ctodrs.saqrcode.agent.SessionMeta
import com.ctodrs.saqrcode.agent.SessionRepair
import com.ctodrs.saqrcode.agent.SessionSearch
import com.ctodrs.saqrcode.agent.SessionStore
import com.ctodrs.saqrcode.agent.SessionTitles
import com.ctodrs.saqrcode.agent.SpeechText
import com.ctodrs.saqrcode.agent.ToolCall
import com.ctodrs.saqrcode.agent.Tools
import com.ctodrs.saqrcode.agent.Usage
import com.ctodrs.saqrcode.agent.VoiceLocale
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class V06FeaturesTest {

    // ------------------------------------------------------------------ fixtures

    private fun tmp(): File = Files.createTempDirectory("saqr-v06").toFile()

    /** A tiny valid PNG-ish payload (bytes only matter for the round trip). */
    private fun image(seed: Int) = ImagePart(Base64.getEncoder().encodeToString(ByteArray(64) { (it * seed).toByte() }), "image/png")

    private val askArgs = """{"title":"قبل أن أبدأ","questions":[{"id":"stack","prompt":"أي منصة؟","kind":"single",
        "options":[{"id":"web","label":"ويب","recommended":true},{"id":"android","label":"أندرويد"}]}]}"""

    /** A realistic session: image message, a turn with tools, an applied change, a git push, an answered and a pending question. */
    private fun sample(img: ImagePart): List<ChatItem> {
        val user1 = ChatItem.User("/fix", listOf(img), prompt = "Fix the failing test in app.js").apply { checkpointId = "cp-1" }
        val a1 = ChatItem.Assistant().apply {
            text = "أقرأ الملف أولاً"; streaming = false
            calls = listOf(ToolCall("c1", Tools.READ, """{"path":"app.js"}"""), ToolCall("c2", Tools.RUN, """{"command":"npm test"}"""))
            results["c1"] = "1: const x = 1"; results["c2"] = "[exit 1]\nFAIL"
        }
        val t1 = ChatItem.Tool(Tools.READ, "app.js", "c1").apply { result = "1: const x = 1"; ok = true; durationMs = 120 }
        val t2 = ChatItem.Tool(Tools.RUN, "npm test", "c2").apply { result = "[exit 1]\nFAIL"; ok = false; durationMs = 3400 }
        val a2 = ChatItem.Assistant().apply {
            text = ""; streaming = false // tool-only turn (hidden in the UI)
            calls = listOf(ToolCall("c3", Tools.EDIT, """{"path":"app.js","old_string":"1","new_string":"2"}"""))
            results["c3"] = "OK: app.js saved (1 lines)."
        }
        val ap = ChatItem.Approval(listOf(FileChange("app.js", "const x = 1", "const x = 2"))).apply { decision = true; durationMs = 0 }
        val push = ChatItem.Approval(emptyList(), action = "git push origin main", actionKind = ChatItem.Approval.ActionKind.PUSH).apply { decision = false; durationMs = 900 }
        val req = AskUserTool.parse(JSONObject(askArgs))!!
        val a3 = ChatItem.Assistant().apply {
            text = "سؤالان"; streaming = false
            calls = listOf(ToolCall("c4", Tools.ASK, askArgs), ToolCall("c5", Tools.ASK, askArgs))
            results["c4"] = AskUserTool.serialize(req, mapOf("stack" to AskAnswer(listOf("android"))))
        }
        val answered = ChatItem.AskUser(req, askArgs, callId = "c4").apply {
            answers = mapOf("stack" to AskAnswer(listOf("android"))); status = ChatItem.AskUser.Status.ANSWERED; durationMs = 5000
        }
        val pending = ChatItem.AskUser(req, askArgs, callId = "c5")
        val notice = ChatItem.Notice("restored", kind = ChatItem.Notice.Kind.RESTORED, count = 2)
        return listOf(user1, a1, t1, t2, a2, ap, push, a3, answered, pending, notice)
    }

    private fun meta(id: String = "s1", title: String = "Fix test", updated: Long = 1000L) =
        SessionMeta(id, title, true, 500L, updated, 3, "")

    // ------------------------------------------------------------------ serialization round trip

    @Test fun sessionRoundTripKeepsEverything() {
        val dir = tmp()
        val store = SessionStore(dir)
        val img = image(3)
        val items = sample(img)
        val data = SessionData(meta(), items, listOf(PlanItem("Read", "done"), PlanItem("Fix", "in_progress")), listOf("app.js"),
            mapOf("gpt-4o" to Usage(1200, 300), "claude" to Usage(10, 5, estimated = true)), active = true)
        store.save(SessionCodec.encode(data, store.images))

        // the image is a file, not inline base64
        val json = File(dir, "s1.json").readText()
        assertFalse(json.contains(img.base64))
        assertEquals(1, File(dir, "img").listFiles()!!.size)

        val back = SessionStore(dir).load("s1")!!
        assertTrue(back.active)
        assertEquals(listOf("app.js"), back.touched)
        assertEquals(2, back.plan.size); assertEquals("in_progress", back.plan[1].status)
        assertEquals(Usage(1200, 300), back.usage["gpt-4o"]); assertTrue(back.usage["claude"]!!.estimated)
        assertEquals(items.size, back.items.size)

        val u = back.items[0] as ChatItem.User
        assertEquals("/fix", u.text); assertEquals("Fix the failing test in app.js", u.prompt); assertEquals("cp-1", u.checkpointId)
        assertEquals(img.base64, u.images.single().base64); assertEquals("image/png", u.images.single().mediaType)
        assertEquals(items[0].createdAt, u.createdAt)

        val a1 = back.items[1] as ChatItem.Assistant
        assertFalse(a1.streaming); assertEquals(2, a1.calls.size); assertEquals("[exit 1]\nFAIL", a1.results["c2"])
        val t2 = back.items[3] as ChatItem.Tool
        assertEquals("npm test", t2.summary); assertEquals("c2", t2.callId); assertFalse(t2.ok); assertEquals(3400, t2.durationMs)
        val ap = back.items[5] as ChatItem.Approval
        assertEquals(true, ap.decision); assertEquals("const x = 2", ap.changes.single().after)
        val push = back.items[6] as ChatItem.Approval
        assertEquals("git push origin main", push.action); assertEquals(false, push.decision)
        val answered = back.items[8] as ChatItem.AskUser
        assertEquals(ChatItem.AskUser.Status.ANSWERED, answered.status); assertEquals(listOf("android"), answered.answers["stack"]!!.selected)
        assertFalse(answered.restored)
        val pending = back.items[9] as ChatItem.AskUser
        assertEquals(ChatItem.AskUser.Status.PENDING, pending.status); assertTrue(pending.restored); assertEquals("c5", pending.callId)
        val n = back.items[10] as ChatItem.Notice
        assertEquals(ChatItem.Notice.Kind.RESTORED, n.kind); assertEquals(2, n.count)
    }

    @Test fun deletedFileChangeAndNullsSurvive() {
        val store = SessionStore(tmp())
        val items = listOf(ChatItem.Approval(listOf(FileChange("old.txt", "bye", null), FileChange("new.txt", null, "hi"))).apply { decision = true })
        store.save(SessionCodec.encode(SessionData(meta(), items), store.images))
        val ch = (store.load("s1")!!.items.single() as ChatItem.Approval).changes
        assertNull(ch[0].after); assertTrue(ch[0].isDelete)
        assertNull(ch[1].before); assertTrue(ch[1].isNew)
    }

    // ------------------------------------------------------------------ LLM history rebuild

    @Test fun rebuildPairsEveryCallWithItsResult() {
        val img = image(5)
        val msgs = HistoryRebuilder.rebuild(sample(img))
        // user, a1, r1, r2, a2, r3, a3, r4, r5
        assertEquals(9, msgs.size)
        val u = msgs[0] as Msg.User
        assertEquals("Fix the failing test in app.js", u.text) // the model's prompt, not the "/fix" bubble
        assertEquals(1, u.images.size)
        assertEquals(listOf("c1", "c2"), (msgs[1] as Msg.Assistant).toolCalls.map { it.id })
        assertEquals("1: const x = 1", (msgs[2] as Msg.ToolResult).content)
        assertEquals("", (msgs[4] as Msg.Assistant).text) // tool-only turn kept for the model
        assertEquals("c3", (msgs[5] as Msg.ToolResult).callId)
        assertTrue((msgs[7] as Msg.ToolResult).content.contains("\"android\""))
        // the pending question has no result yet: the model sees an "interrupted" result, never a dangling call
        assertEquals(HistoryRebuilder.INTERRUPTED, (msgs[8] as Msg.ToolResult).content)
        // every tool call is followed by a result for it
        val calls = msgs.filterIsInstance<Msg.Assistant>().flatMap { it.toolCalls }.map { it.id }
        assertEquals(calls, msgs.filterIsInstance<Msg.ToolResult>().map { it.callId })
    }

    @Test fun rebuildKeepsLastTwoImagePruning() {
        val items = (1..4).flatMap { i ->
            listOf(ChatItem.User("shot $i", listOf(image(i))), ChatItem.Assistant().apply { text = "ok $i"; streaming = false })
        }
        val pruned = ImagePruner.prune(HistoryRebuilder.rebuild(items))
        val users = pruned.filterIsInstance<Msg.User>()
        assertEquals(listOf(0, 0, 1, 1), users.map { it.images.size })
        assertTrue(users[0].text.endsWith(ImagePruner.OMITTED))
    }

    @Test fun ownerFindsTheAssistantThatAsked() {
        val items = sample(image(2))
        assertEquals("سؤالان", HistoryRebuilder.owner(items, "c5")!!.text)
        assertNull(HistoryRebuilder.owner(items, "nope"))
        assertNull(HistoryRebuilder.owner(items, null))
    }

    @Test fun answeringRestoredAskResumesWithToolResult() {
        // what AgentSession.answer does for a restored card: the answer becomes the call's result
        val items = sample(image(1))
        val pending = SessionRepair.pendingAsk(items)!!
        val owner = HistoryRebuilder.owner(items, pending.callId)!!
        owner.results[pending.callId!!] = AskUserTool.skipped()
        val last = HistoryRebuilder.rebuild(items).last() as Msg.ToolResult
        assertEquals("c5", last.callId)
        assertTrue(last.content.contains("\"skipped\":true"))
    }

    // ------------------------------------------------------------------ resume after the process was killed

    @Test fun resumeAfterKillFromPersistedSession() {
        val dir = tmp()
        val store = SessionStore(dir)
        // the process died while `npm test` ran and an approval was open
        val user = ChatItem.User("شغّل الاختبارات")
        val a = ChatItem.Assistant().apply {
            text = "أشغّلها"; streaming = false
            calls = listOf(ToolCall("r1", Tools.RUN, """{"command":"npm test"}"""), ToolCall("r2", Tools.GIT_PUSH, "{}"))
        }
        val running = ChatItem.Tool(Tools.RUN, "npm test", "r1")
        val approval = ChatItem.Approval(emptyList(), "git push origin main")
        store.save(SessionCodec.encode(SessionData(meta("k1"), listOf(user, a, running, approval), active = true), store.images))
        assertEquals("k1", store.last())

        // restart: load + repair
        val restored = SessionStore(dir).load(store.last()!!)!!
        assertTrue(restored.active)
        SessionRepair.repair(restored.items, keepAskPending = true)
        val tool = restored.items[2] as ChatItem.Tool
        assertEquals("(interrupted)", tool.result); assertFalse(tool.ok)
        assertEquals(false, (restored.items[3] as ChatItem.Approval).decision)
        assertNull(SessionRepair.pendingAsk(restored.items)) // nothing to answer: the turn can be resumed

        val history = HistoryRebuilder.rebuild(restored.items)
        assertEquals(4, history.size)
        assertTrue(history.drop(2).all { it is Msg.ToolResult && it.content == HistoryRebuilder.INTERRUPTED })
    }

    @Test fun repairKeepsOrCancelsPendingQuestions() {
        val items = sample(image(4))
        assertTrue(SessionRepair.repair(items, keepAskPending = true) || true)
        assertNotNull(SessionRepair.pendingAsk(items))
        SessionRepair.repair(items, keepAskPending = false)
        assertNull(SessionRepair.pendingAsk(items))
    }

    @Test fun activeRunsFileForBootAndRestart() {
        val f = File(tmp(), "agent/active.json")
        val runs = ActiveRuns(f)
        assertTrue(runs.read().isEmpty())
        runs.add("tafqit"); runs.add("مقهى")
        assertEquals(setOf("tafqit", "مقهى"), ActiveRuns(f).read())
        runs.remove("tafqit")
        assertEquals(setOf("مقهى"), runs.read())
        runs.write(emptySet())
        assertFalse(f.exists())
        f.parentFile!!.mkdirs(); f.writeText("{garbage")
        assertTrue(runs.read().isEmpty())
    }

    // ------------------------------------------------------------------ session list ops

    private fun saveN(store: SessionStore, id: String, title: String, text: String, updated: Long) {
        val items = listOf(ChatItem.User(text), ChatItem.Assistant().apply { this.text = "reply to $text"; streaming = false })
        store.save(SessionCodec.encode(SessionData(SessionMeta(id, title, true, updated, updated, 2, ""), items), store.images))
    }

    @Test fun listRenameDeleteSearch() {
        val dir = tmp()
        val store = SessionStore(dir)
        saveN(store, "a", "مكتبة التفقيط", "سوّ لي مكتبة تفقيط", 100)
        saveN(store, "b", "Landing page", "Add a dark mode toggle", 200)
        saveN(store, "c", "Kotlin app", "Build an IME keyboard", 300)
        assertEquals(listOf("c", "b", "a"), store.list().map { it.id })
        assertEquals("c", store.last())

        store.rename("b", "  صفحة   الهبوط ")
        assertEquals("صفحة الهبوط", store.list().first { it.id == "b" }.title)
        assertFalse(store.list().first { it.id == "b" }.autoTitle)
        assertEquals("صفحة الهبوط", SessionStore(dir).load("b")!!.meta.title) // persisted in the file too

        // search: title, message text, Arabic without diacritics / alef forms, every word must match
        assertEquals(listOf("a"), store.search("التفقيط").map { it.id })
        assertEquals(listOf("b"), store.search("DARK mode").map { it.id })
        assertEquals(listOf("a"), store.search("سو لي").map { it.id })
        assertEquals(listOf("c"), store.search("keyboard ime").map { it.id })
        assertTrue(store.search("nothing here").isEmpty())
        assertEquals(3, store.search("  ").size)

        store.delete("c")
        assertEquals(listOf("b", "a"), store.list().map { it.id })
        assertFalse(File(dir, "c.json").exists())
        assertEquals("b", SessionStore(dir).last())

        // a lost index is rebuilt from the session files
        File(dir, "index.json").delete()
        assertEquals(setOf("a", "b"), SessionStore(dir).list().map { it.id }.toSet())
    }

    @Test fun deletingSessionRemovesItsUnusedImages() {
        val dir = tmp()
        val store = SessionStore(dir)
        val shared = image(7); val own = image(9)
        store.save(SessionCodec.encode(SessionData(meta("x"), listOf(ChatItem.User("a", listOf(shared, own)))), store.images))
        store.save(SessionCodec.encode(SessionData(meta("y", updated = 2000), listOf(ChatItem.User("b", listOf(shared)))), store.images))
        assertEquals(2, File(dir, "img").listFiles()!!.size)
        store.delete("x")
        assertEquals(1, File(dir, "img").listFiles()!!.size)
        assertEquals(shared.base64, store.load("y")!!.items.filterIsInstance<ChatItem.User>().single().images.single().base64)
    }

    @Test fun capAt200SessionsDropsOldest() {
        val store = SessionStore(tmp())
        for (i in 1..SessionStore.MAX_SESSIONS + 3) saveN(store, "s$i", "t$i", "m$i", i.toLong())
        val ids = store.list().map { it.id }
        assertEquals(SessionStore.MAX_SESSIONS, ids.size)
        assertFalse("s1" in ids); assertFalse("s3" in ids); assertTrue("s4" in ids)
        assertEquals(SessionStore.MAX_SESSIONS, store.dir.listFiles()!!.count { it.name.endsWith(".json") && it.name != "index.json" })
    }

    @Test fun autoTitle() {
        assertEquals("سوّ لي مكتبة تفقيط", SessionTitles.auto("سوّ لي **مكتبة** تفقيط"))
        assertEquals("Fix the bug in app.js", SessionTitles.auto("Fix the bug in `app.js`\n```js\nlet x\n```"))
        val long = SessionTitles.auto("Build a complete Android keyboard with Arabic layout, emoji row and voice typing support")
        assertTrue(long.length <= SessionTitles.MAX + 1); assertTrue(long.endsWith("…")); assertFalse(long.contains("  "))
        assertEquals("", SessionTitles.auto("   "))
    }

    @Test fun searchNormalization() {
        assertTrue(SessionSearch.matches("أضِفْ زرّ الدفع", "اضف زر"))
        assertTrue(SessionSearch.matches("مكتبة التفقيط", "مكتبه"))
        assertFalse(SessionSearch.matches("hello", "world"))
    }

    // ------------------------------------------------------------------ speech text + locale

    @Test fun speechStripsCodeAndMarkdown() {
        val md = """
            ## النتيجة
            تم إصلاح **الخطأ** في `app.js` — انظر [التوثيق](https://example.com/docs).

            ```kotlin
            fun main() = println("hi")
            ```
            - البند الأول
            1. شغّل `npm test`
            > ملاحظة مهمة
            | a | b |
            |---|---|
            رابط: https://x.y/z
        """.trimIndent()
        val t = SpeechText.clean(md)
        assertFalse(t.contains("println")); assertFalse(t.contains("```")); assertFalse(t.contains("**"))
        assertFalse(t.contains("https://")); assertFalse(t.contains("##")); assertFalse(t.contains("---"))
        assertTrue(t.contains("النتيجة")); assertTrue(t.contains("app.js")); assertTrue(t.contains("التوثيق"))
        assertTrue(t.contains("البند الأول")); assertTrue(t.contains("شغّل npm test")); assertTrue(t.contains("ملاحظة مهمة"))
        assertFalse(t.lines().any { it.startsWith("-") || it.startsWith("1.") || it.startsWith(">") })
        assertEquals("ar", SpeechText.language(t))
        assertEquals("en", SpeechText.language(SpeechText.clean("Fixed the **bug** in `snake_case_name`.")))
        assertTrue(SpeechText.clean("Fixed in `snake_case_name`").contains("snake_case_name"))
        assertEquals("", SpeechText.clean("```\nonly code\n```"))
    }

    @Test fun speechChunks() {
        val text = (1..400).joinToString(" ") { "جملة رقم $it." }
        val parts = SpeechText.chunks(text, 500)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= 500 })
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
    }

    @Test fun voiceLocaleSelection() {
        assertEquals(VoiceLocale.AR, VoiceLocale.pick("ar"))
        assertEquals(VoiceLocale.AR, VoiceLocale.pick("ar-SA"))
        assertEquals(VoiceLocale.EN, VoiceLocale.pick("en-GB"))
        assertEquals(VoiceLocale.EN, VoiceLocale.pick("fr"))
        assertEquals(VoiceLocale.AR, VoiceLocale.pick("", systemLang = "ar"))   // "system" app language
        assertEquals(VoiceLocale.EN, VoiceLocale.pick("ar", override = "en-US")) // long-press override wins
        assertEquals(VoiceLocale.AR, VoiceLocale.pick("en", override = "ar"))
        assertEquals(VoiceLocale.EN, VoiceLocale.toggle(VoiceLocale.AR))
        assertEquals(VoiceLocale.AR, VoiceLocale.toggle(VoiceLocale.EN))
        assertEquals("سوّ صفحة", VoiceLocale.merge("", " سوّ صفحة "))
        assertEquals("Add a button", VoiceLocale.merge("Add", "a button"))
        assertEquals("Add a", VoiceLocale.merge("Add a", ""))
    }

    // ------------------------------------------------------------------ background service state machine

    @Test fun serviceStateMachine() {
        val m = AgentRuns()
        var fx = m.start("p1", 1000)
        assertTrue(RunEffect.StartService in fx); assertTrue(RunEffect.AcquireLocks in fx)
        assertTrue(fx.contains(RunEffect.PersistActive(setOf("p1"))))
        assertTrue(m.serviceNeeded && m.locksNeeded)

        fx = m.activity("p1", 1, RunActivity.THINKING)
        assertTrue(RunEffect.RenewLocks in fx) // new step re-arms the wake lock timeout
        fx = m.activity("p1", 1, RunActivity(RunActivity.Kind.WRITE, "app.kt"))
        assertFalse(RunEffect.RenewLocks in fx)
        assertEquals(RunActivity.Kind.WRITE, m.get("p1")!!.activity.kind)

        // pause for an answer: locks released, service stays, attention posted
        val w = RunWait(RunWait.Kind.PUSH, "git push origin main")
        fx = m.waiting("p1", w)
        assertTrue(RunEffect.ReleaseLocks in fx); assertTrue(RunEffect.PostAttention("p1", w) in fx)
        assertTrue(m.serviceNeeded); assertFalse(m.locksNeeded)

        // a second project starts while p1 waits
        fx = m.start("p2", 2000)
        assertFalse(RunEffect.StartService in fx); assertTrue(RunEffect.AcquireLocks in fx)
        assertEquals("p2", m.headline()!!.project)

        fx = m.resumed("p1")
        assertTrue(RunEffect.ClearAttention("p1") in fx); assertFalse(RunEffect.AcquireLocks in fx) // already held for p2
        assertTrue(m.resumed("p1").isEmpty()) // resuming twice is a no-op

        fx = m.finish("p2", RunOutcome(RunOutcome.Kind.DONE, "done"))
        assertFalse(RunEffect.StopService in fx); assertTrue(RunEffect.UpdateOngoing in fx)
        assertTrue(fx.contains(RunEffect.PostFinished("p2", RunOutcome(RunOutcome.Kind.DONE, "done"))))

        fx = m.finish("p1", RunOutcome(RunOutcome.Kind.STOPPED))
        assertTrue(RunEffect.StopService in fx); assertTrue(RunEffect.ReleaseLocks in fx)
        assertFalse(fx.any { it is RunEffect.PostFinished }) // the user pressed Stop: no "finished" notification
        assertTrue(fx.contains(RunEffect.PersistActive(emptySet())))
        assertFalse(m.serviceNeeded)
        assertTrue(m.finish("p1", RunOutcome(RunOutcome.Kind.DONE)).isEmpty())
    }

    @Test fun waitingWhileOnlyRunReleasesThenReacquires() {
        val m = AgentRuns()
        m.start("p", 0)
        assertTrue(RunEffect.ReleaseLocks in m.waiting("p", RunWait(RunWait.Kind.ASK, "?")))
        // the loop moves on (answer given): activity after a wait re-acquires and clears the attention notification
        val fx = m.activity("p", 2, RunActivity.THINKING)
        assertTrue(RunEffect.AcquireLocks in fx); assertTrue(RunEffect.ClearAttention("p") in fx)
    }

    @Test fun askInlineButtonsOnlyForSmallSingleChoice() {
        val two = RunWait.forAsk(AskUserTool.parse(JSONObject(askArgs))!!)
        assertEquals("stack", two.questionId); assertEquals(listOf("web" to "ويب", "android" to "أندرويد"), two.options)
        val four = RunWait.forAsk(AskUserTool.parse(JSONObject("""{"questions":[{"id":"q","prompt":"p","kind":"single",
            "options":["a","b","c","d"]}]}"""))!!)
        assertNull(four.questionId); assertTrue(four.options.isEmpty())
        val twoQs = RunWait.forAsk(AskUserTool.parse(JSONObject("""{"questions":[{"id":"q1","prompt":"p1","kind":"confirm"},{"id":"q2","prompt":"p2","kind":"text"}]}"""))!!)
        assertNull(twoQs.questionId); assertEquals("p1", twoQs.detail)
    }

    // ------------------------------------------------------------------ notification texts

    @Test fun notificationTextsArabicAndEnglish() {
        val r = RunInfo("tafqit", 0, step = 4, activity = RunActivity.forTool(Tools.WRITE, "src/app.kt"))
        assertEquals("Step 4 · Writing app.kt…", NotifText.ongoing(r, "en"))
        assertEquals("الخطوة 4 · يكتب app.kt…", NotifText.ongoing(r, "ar"))
        assertEquals("Thinking…", NotifText.ongoing(RunInfo("p", 0), "en"))
        assertEquals("Running: npm test", NotifText.activity(RunActivity.forTool(Tools.RUN, "npm test"), "en-US"))
        assertEquals("يشغّل: npm test", NotifText.activity(RunActivity.forTool(Tools.RUN, "npm test"), "ar-SA"))
        assertEquals("Waiting for you", NotifText.ongoing(r.copy(wait = RunWait(RunWait.Kind.ASK, "?")), "en"))
        assertEquals("بانتظار ردّك", NotifText.ongoing(r.copy(wait = RunWait(RunWait.Kind.ASK, "?")), "ar"))
        assertEquals("tafqit (+2)", NotifText.ongoingTitle(r, 2, "en"))

        assertEquals("Saqr needs your answer", NotifText.attentionTitle("en"))
        assertEquals("صقر يحتاج ردّك", NotifText.attentionTitle("ar"))
        assertEquals("tafqit · Approve git push", NotifText.attentionBody("tafqit", RunWait(RunWait.Kind.PUSH), "en"))
        assertEquals("tafqit · الموافقة على git commit", NotifText.attentionBody("tafqit", RunWait(RunWait.Kind.COMMIT), "ar"))
        assertEquals("tafqit · أي منصة؟", NotifText.attentionBody("tafqit", RunWait(RunWait.Kind.ASK, "أي منصة؟"), "ar"))

        assertEquals("Task finished", NotifText.finishedTitle(RunOutcome(RunOutcome.Kind.DONE), "en"))
        assertEquals("تعثّرت المهمة", NotifText.finishedTitle(RunOutcome(RunOutcome.Kind.ERROR), "ar"))
        assertEquals("p · Tap to view", NotifText.finishedBody("p", RunOutcome(RunOutcome.Kind.DONE), "en"))
        val long = NotifText.finishedBody("p", RunOutcome(RunOutcome.Kind.DONE, "x".repeat(400)), "en")
        assertTrue(long.endsWith("…")); assertTrue(long.length <= 165)
        assertEquals("43/43 passed. Run it with npm test", NotifText.summary("✅ **43/43** passed.\n\n```\nlog\n```\nRun it with `npm test`").removePrefix("✅ "))

        assertEquals("0:07", NotifText.elapsed(7_000)); assertEquals("12:05", NotifText.elapsed(725_000)); assertEquals("1:00:00", NotifText.elapsed(3_600_000))
        assertEquals("Resume task?", NotifText.resumeTitle("en")); assertEquals("استئناف المهمة؟", NotifText.resumeTitle("ar"))
        assertTrue(NotifText.resumeBody(listOf("a", "b"), "ar").endsWith("a، b"))
    }
}
