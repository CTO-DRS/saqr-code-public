package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.AskAnswer
import com.ctodrs.saqrcode.agent.AskKind
import com.ctodrs.saqrcode.agent.AskUserTool
import com.ctodrs.saqrcode.agent.PendingAskStore
import com.ctodrs.saqrcode.agent.QuickReplies
import com.ctodrs.saqrcode.agent.Tools
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AskUserTest {
    private val sample = JSONObject("""
        {"title":"Before I start","submit_label":"Go",
         "questions":[
          {"id":"platform","prompt":"Where should it run?","kind":"single","allow_other":true,
           "options":[{"id":"web","label":"Web","description":"Runs in any browser","recommended":true},
                      {"id":"android","label":"Android"},{"id":"ios","label":"iOS"}]},
          {"id":"features","prompt":"Extras?","kind":"multi","default":["auth"],
           "options":[{"id":"auth","label":"Login"},{"id":"pay","label":"Payments"}]},
          {"id":"name","prompt":"App name?","kind":"text"},
          {"id":"tests","prompt":"Write tests?","kind":"confirm","default":true}
         ]}""")

    // ------------------------------------------------------------ schema / parsing

    @Test fun toolIsExposedWithSchema() {
        val spec = Tools.specs.single { it.name == Tools.ASK }
        assertTrue(spec.parameters.getJSONArray("required").toString().contains("questions"))
        val kinds = spec.parameters.getJSONObject("properties").getJSONObject("questions").getJSONObject("items")
            .getJSONObject("properties").getJSONObject("kind").getJSONArray("enum").toString()
        assertEquals("""["single","multi","text","confirm"]""", kinds)
    }

    @Test fun parsesValidRequest() {
        val r = AskUserTool.parse(sample)!!
        assertEquals("Before I start", r.title)
        assertEquals("Go", r.submitLabel)
        assertEquals(listOf(AskKind.SINGLE, AskKind.MULTI, AskKind.TEXT, AskKind.CONFIRM), r.questions.map { it.kind })
        val p = r.questions[0]
        assertEquals(3, p.options.size)
        assertTrue(p.options[0].recommended)
        assertEquals("Runs in any browser", p.options[0].description)
        assertTrue(p.allowOther)
        assertEquals(listOf("auth"), r.questions[1].default)
        assertEquals(listOf(AskUserTool.YES), r.questions[3].default)
    }

    @Test fun missingFieldsAreFilledOrRejected() {
        assertNull(AskUserTool.parse(null))
        assertNull(AskUserTool.parse(JSONObject("{}")))
        assertNull(AskUserTool.parse(JSONObject("""{"questions":[]}""")))
        assertNull(AskUserTool.parse(JSONObject("""{"questions":[{"id":"x"}]}""")))   // no prompt, no options
        val r = AskUserTool.parse(JSONObject("""{"questions":[
            {"prompt":"Pick one","kind":"single","options":[{"label":"Dark mode"},{"id":"light"},{}]},
            {"prompt":"Second","kind":"text"}]}"""))!!
        assertNull(r.title)
        assertEquals("q1", r.questions[0].id)
        assertEquals("q2", r.questions[1].id)
        assertEquals(listOf("dark_mode", "light"), r.questions[0].options.map { it.id })
        assertEquals("light", r.questions[0].options[1].label)   // label falls back to id
    }

    @Test fun unknownKindFallsBackToText() {
        val r = AskUserTool.parse(JSONObject("""{"questions":[
            {"id":"a","prompt":"Colour?","kind":"slider"},
            {"id":"b","prompt":"Pick","kind":"single"},
            {"id":"c","prompt":"Opts","options":["One","Two"]}]}"""))!!
        assertEquals(AskKind.TEXT, r.questions[0].kind)
        assertEquals(AskKind.TEXT, r.questions[1].kind)           // single without options
        assertEquals(AskKind.SINGLE, r.questions[2].kind)         // no kind + options => single
        assertEquals(listOf("One", "Two"), r.questions[2].options.map { it.label })
    }

    @Test fun duplicateIdsAndBadDefaultsAreSanitised() {
        val r = AskUserTool.parse(JSONObject("""{"questions":[
            {"id":"x","prompt":"A","kind":"single","default":"nope","options":[{"id":"o","label":"O"},{"id":"o","label":"P"}]},
            {"id":"x","prompt":"B","kind":"single","default":"P label","options":[{"id":"p","label":"P label"}]}]}"""))!!
        assertEquals(2, r.questions.map { it.id }.toSet().size)
        assertEquals(2, r.questions[0].options.map { it.id }.toSet().size)
        assertTrue(r.questions[0].default.isEmpty())
        assertEquals(listOf("p"), r.questions[1].default)          // default given as label
    }

    // ------------------------------------------------------------ validation

    @Test fun initialAnswersUseDefaultsThenRecommended() {
        val r = AskUserTool.parse(sample)!!
        val a = AskUserTool.initialAnswers(r)
        assertEquals(listOf("web"), a["platform"]!!.selected)     // recommended
        assertEquals(listOf("auth"), a["features"]!!.selected)    // default
        assertEquals(listOf("yes"), a["tests"]!!.selected)
        assertFalse(AskUserTool.canSubmit(r, a))                  // text still empty
        assertTrue(AskUserTool.canSubmit(r, a + ("name" to AskAnswer(text = "Falcon"))))
    }

    @Test fun submitEnabledLogic() {
        val r = AskUserTool.parse(sample)!!
        val q = r.questions[0]
        var a = AskAnswer()
        assertFalse(AskUserTool.isAnswered(q, a))
        a = AskUserTool.toggle(q, a, "android")
        assertTrue(AskUserTool.isAnswered(q, a))
        a = AskUserTool.toggleOther(q, a)                         // "Other…" chosen but empty
        assertTrue(a.selected.isEmpty())
        assertFalse(AskUserTool.isAnswered(q, a))
        a = a.copy(other = "  ")
        assertFalse(AskUserTool.isAnswered(q, a))
        a = a.copy(other = "Desktop")
        assertTrue(AskUserTool.isAnswered(q, a))
        a = AskUserTool.toggle(q, a, "ios")                       // regular option clears Other
        assertNull(a.other); assertEquals(listOf("ios"), a.selected)

        val m = r.questions[1]
        var b = AskAnswer(selected = listOf("auth"))
        b = AskUserTool.toggle(m, b, "auth")
        assertFalse(AskUserTool.isAnswered(m, b))                 // nothing ticked
        b = AskUserTool.toggle(m, AskUserTool.toggle(m, b, "pay"), "auth")
        assertEquals(listOf("pay", "auth"), b.selected)

        val conf = r.questions[3]
        assertFalse(AskUserTool.isAnswered(conf, AskAnswer()))
        assertTrue(AskUserTool.isAnswered(conf, AskAnswer(selected = listOf("no"))))
    }

    @Test fun optionalQuestionsDoNotBlockSubmit() {
        val r = AskUserTool.parse(JSONObject("""{"questions":[{"id":"n","prompt":"Notes","kind":"text","required":false}]}"""))!!
        assertTrue(AskUserTool.canSubmit(r, AskUserTool.initialAnswers(r)))
    }

    // ------------------------------------------------------------ serialization

    @Test fun serializesAnswersWithLabels() {
        val r = AskUserTool.parse(sample)!!
        val ans = mapOf(
            "platform" to AskAnswer(other = "Desktop"),
            "features" to AskAnswer(selected = listOf("pay", "auth"), other = "Push"),
            "name" to AskAnswer(text = "  صقر  "),
            "tests" to AskAnswer(selected = listOf("no")),
        )
        val o = JSONObject(AskUserTool.serialize(r, ans)).getJSONObject("answers")
        assertEquals("Desktop", o.getJSONObject("platform").getString("label"))
        assertTrue(o.getJSONObject("platform").getBoolean("other"))
        val f = o.getJSONArray("features")
        assertEquals(3, f.length())
        assertEquals("auth", f.getJSONObject(0).getString("id"))   // option order, not tap order
        assertEquals("Login", f.getJSONObject(0).getString("label"))
        assertEquals("Push", f.getJSONObject(2).getString("label"))
        assertEquals("صقر", o.getString("name"))
        assertFalse(o.getBoolean("tests"))

        val single = JSONObject(AskUserTool.serialize(r, mapOf("platform" to AskAnswer(selected = listOf("web"))))).getJSONObject("answers")
        assertEquals("Web", single.getJSONObject("platform").getString("label"))
        assertTrue(single.isNull("name"))
        assertTrue(JSONObject(AskUserTool.skipped()).getBoolean("skipped"))
    }

    @Test fun displayAndRestartMessage() {
        val r = AskUserTool.parse(sample)!!
        val ans = AskUserTool.initialAnswers(r) + ("name" to AskAnswer(text = "Falcon"))
        assertEquals("Web", AskUserTool.display(r.questions[0], ans["platform"]))
        assertEquals("نعم", AskUserTool.display(r.questions[3], ans["tests"], yes = "نعم", no = "لا"))
        val msg = AskUserTool.asUserMessage(r, ans)
        assertTrue(msg.contains("- Where should it run?: Web"))
        assertTrue(msg.contains("\"answers\""))
        assertTrue(AskUserTool.asUserMessage(r, null).contains("Skipped"))
    }

    @Test fun pendingStoreRoundTrip() {
        val dir = Files.createTempDirectory("ask").toFile()
        val store = PendingAskStore(File(dir, "pending/p.json"))
        assertNull(store.load())
        store.save(sample.toString())
        assertNotNull(store.load())
        assertEquals(4, store.load()!!.questions.size)
        store.clear()
        assertNull(store.load())
        dir.deleteRecursively()
    }

    // ------------------------------------------------------------ quick replies

    @Test fun quickRepliesEnglishLines() {
        val t = "Which platform should I target?\n\n1. **Web** — easiest to share\n2. Android\n3. iOS (needs a Mac)"
        assertEquals(listOf("Web", "Android", "iOS (needs a Mac)"), QuickReplies.detect(t))
    }

    @Test fun quickRepliesInlineAndClosingLine() {
        assertEquals(listOf("Web", "Android", "Desktop"), QuickReplies.detect("Where should it run? 1. Web 2. Android 3. Desktop"))
        val t = "I can go two ways:\n1) Rewrite in Compose\n2) Keep XML views\nWhich do you prefer?"
        assertEquals(listOf("Rewrite in Compose", "Keep XML views"), QuickReplies.detect(t))
    }

    @Test fun quickRepliesArabic() {
        val t = "وش تفضّل للواجهة؟\n١. تصميم داكن\n٢. تصميم فاتح\n٣. الاثنين مع زر تبديل"
        assertEquals(listOf("تصميم داكن", "تصميم فاتح", "الاثنين مع زر تبديل"), QuickReplies.detect(t))
        val w = "أي قاعدة بيانات تريد؟\n1. SQLite: خفيفة\n2. Room\n3. Firebase"
        assertEquals(listOf("SQLite", "Room", "Firebase"), QuickReplies.detect(w))
    }

    @Test fun quickRepliesIgnoreNonQuestions() {
        assertTrue(QuickReplies.detect("Done. What changed:\n1. Added tests\n2. Fixed the bug").isEmpty())   // no question
        assertTrue(QuickReplies.detect("Pick one?\n1. Only one").isEmpty())
        assertTrue(QuickReplies.detect("Options?\n1. A\n3. B").isEmpty())                                   // not sequential
        assertTrue(QuickReplies.detect("Which?\n1. A\n2. B\n\nThen I'll explain the whole migration plan in detail with every step and caveat you need to consider before going further.").isEmpty())
        assertTrue(QuickReplies.detect("").isEmpty())
    }
}
