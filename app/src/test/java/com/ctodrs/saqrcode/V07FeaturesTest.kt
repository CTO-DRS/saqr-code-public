package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ContextCompactor
import com.ctodrs.saqrcode.agent.DdgParser
import com.ctodrs.saqrcode.agent.FileChange
import com.ctodrs.saqrcode.agent.LoopDetector
import com.ctodrs.saqrcode.agent.Msg
import com.ctodrs.saqrcode.agent.ProjectChecks
import com.ctodrs.saqrcode.agent.Reviewer
import com.ctodrs.saqrcode.agent.ToolCall
import com.ctodrs.saqrcode.agent.WebGuard
import com.ctodrs.saqrcode.data.Template
import com.ctodrs.saqrcode.data.TemplateFiles
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v0.7 · team review, compaction, loop guard, web tools, checks, templates. */
class V07FeaturesTest {

    // ------------------------------------------------------------ loop detector

    @Test
    fun `fingerprint is stable across JSON key order`() {
        val a = listOf(ToolCall("1", "read_file", """{"path":"app.kt","start_line":1}"""))
        val b = listOf(ToolCall("1", "read_file", """{"start_line":1,"path":"app.kt"}"""))
        assertEquals(LoopDetector.fingerprint(a), LoopDetector.fingerprint(b))
    }

    @Test
    fun `different args or tool give different fingerprints`() {
        val a = listOf(ToolCall("1", "read_file", """{"path":"a.kt"}"""))
        val b = listOf(ToolCall("1", "read_file", """{"path":"b.kt"}"""))
        assertTrue(LoopDetector.fingerprint(a) != LoopDetector.fingerprint(b))
        val c = listOf(ToolCall("1", "write_file", """{"path":"a.kt"}"""))
        assertTrue(LoopDetector.fingerprint(a) != LoopDetector.fingerprint(c))
    }

    @Test
    fun `triple repeat detected only on the third identical step`() {
        val f = LoopDetector.fingerprint(listOf(ToolCall("1", "list_files", "{}")))
        val g = LoopDetector.fingerprint(listOf(ToolCall("1", "read_file", """{"path":"x"}""")))
        assertFalse(LoopDetector.tripleRepeat(listOf(f, f)))
        assertFalse(LoopDetector.tripleRepeat(listOf(f, f, g)))
        assertTrue(LoopDetector.tripleRepeat(listOf(g, f, f, f)))
        assertTrue(LoopDetector.tripleRepeat(listOf(f, f, f)))
        assertFalse(LoopDetector.tripleRepeat(listOf(f, g, f, g, f, g)))
        assertFalse(LoopDetector.tripleRepeat(listOf("", "", "")))
    }

    // ------------------------------------------------------------ compaction

    private fun history(n: Int): List<Msg> = (0 until n).map {
        if (it % 3 == 0) Msg.User("u$it") else Msg.Assistant("a$it", emptyList())
    }

    @Test
    fun `needsCompact honours threshold and size`() {
        assertFalse(ContextCompactor.needsCompact(1_000L, 30))
        assertTrue(ContextCompactor.needsCompact(ContextCompactor.THRESHOLD_CHARS + 1L, 40))
        assertFalse(ContextCompactor.needsCompact(ContextCompactor.THRESHOLD_CHARS + 1L, 10))
    }

    @Test
    fun `cutIndex lands on a user message and keeps the tail`() {
        val h = history(30)
        val cut = ContextCompactor.cutIndex(h, keepTail = 10, minSummarised = 4)!!
        assertTrue(cut in 4..20)
        assertTrue(h[cut] is Msg.User)
        assertTrue(h.size - cut >= 10)
        assertNull(ContextCompactor.cutIndex(history(8), 10, 4))
    }

    @Test
    fun `apply produces one note plus the tail`() {
        val h = history(30)
        val cut = ContextCompactor.cutIndex(h)!!
        val out = ContextCompactor.apply(h, cut, "Brief.")
        assertEquals(h.size - cut + 1, out.size)
        assertTrue(out.first().let { it is Msg.User && it.text.startsWith(ContextCompactor.MARKER) })
    }

    // ------------------------------------------------------------ reviewer

    @Test
    fun `reviewer parses plain and fenced JSON`() {
        val ok = Reviewer.parse("""{"verdict":"ok","summary":"تمام"}""")
        assertNotNull(ok); assertTrue(ok!!.ok); assertEquals("تمام", ok.summary)
        val fenced = Reviewer.parse("```json\n{\"verdict\":\"issues\",\"summary\":\"bug\",\"issues\":[{\"file\":\"a.kt\",\"line\":\"12\",\"comment\":\"off by one\"}]}\n```")
        assertNotNull(fenced); assertFalse(fenced!!.ok); assertEquals(1, fenced.issues.size)
        assertEquals("a.kt", fenced.issues[0].file)
        assertNull(Reviewer.parse("no json here"))
        // prose around the JSON is tolerated
        val prose = Reviewer.parse("Verdict: {\"verdict\":\"ok\",\"summary\":\"fine\"} hope this helps")
        assertNotNull(prose); assertTrue(prose!!.ok)
    }

    @Test
    fun `issues verdict without issues still blocks until cleared`() {
        val r = Reviewer.parse("""{"verdict":"issues","summary":"s"}""")
        assertNotNull(r); assertFalse(r!!.ok) // the reviewer said issues; the coder gets the summary
        // the feedback message stays useful even with no concrete issues listed
        assertTrue(Reviewer.feedbackMessage(r).startsWith("[Reviewer] s"))
    }

    @Test
    fun `diffOf renders adds dels and deletions`() {
        val changes = listOf(
            FileChange("a.kt", "line1\nline2", "line1\nline2 changed\nline3"),
            FileChange("gone.kt", "x", null),
            FileChange("new.kt", null, "hello"),
        )
        val d = Reviewer.diffOf(changes)
        assertTrue(d.contains("--- a.kt (modified)"))
        assertTrue(d.contains("+ line2 changed"))
        assertTrue(d.contains("--- gone.kt (deleted)"))
        assertTrue(d.contains("--- new.kt (new file)"))
        assertTrue(d.contains("+ hello"))
    }

    @Test
    fun `feedback message lists issues`() {
        val r = Reviewer.Report(false, "found bugs", listOf(Reviewer.Issue("a.kt", "3", "null deref")))
        val m = Reviewer.feedbackMessage(r)
        assertTrue(m.startsWith("[Reviewer]"))
        assertTrue(m.contains("a.kt:3"))
        assertTrue(m.contains("null deref"))
    }

    // ------------------------------------------------------------ web guard + DDG parser

    @Test
    fun `web guard blocks private and allows public`() {
        for (bad in listOf(
            "http://localhost/x", "https://127.0.0.1/", "http://169.254.169.254/latest/meta-data",
            "http://192.168.1.1/", "http://10.0.0.1/", "http://172.16.0.1/", "http://172.31.255.1/",
            "http://100.64.0.1/", "ftp://example.com/", "file:///etc/passwd",
            "https://user:pass@example.com/", "http://foo.local/", "http://metadata.google.internal/",
        )) assertFalse("should block: $bad", WebGuard.check(bad).allowed)
        for (good in listOf("https://developer.android.com/reference", "http://example.com:8080/a?q=1"))
            assertTrue("should allow: $good", WebGuard.check(good).allowed)
    }

    @Test
    fun `ddg parser extracts results and unwraps redirects`() {
        val html = """
        <div class="result">
        <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fkotlinlang.org%2Fdocs%2Fhome.html&amp;rut=abc">Kotlin <b>docs</b></a>
        <a class="result__snippet" href="#">The official &amp; friendly language docs &#8212; great</a>
        <a rel="nofollow" class="result__a" href="https://example.com/direct">Direct link</a>
        <a class="result__snippet" href="#">Second snippet</a>
        </div>
        """.trimIndent()
        val rs = DdgParser.parse(html, 5)
        assertEquals(2, rs.size)
        assertEquals("https://kotlinlang.org/docs/home.html", rs[0].url)
        assertEquals("Kotlin docs", rs[0].title)
        assertTrue(rs[0].snippet.contains("& friendly"))
        assertTrue(rs[0].snippet.contains("docs — great"))
        assertEquals("https://example.com/direct", rs[1].url)
        assertEquals("ok x", DdgParser.stripTags("<p>ok</p> <span>x</span>"))
    }

    @Test
    fun `html to text drops scripts and keeps headings`() {
        val html = "<html><head><style>p{}</style></head><body><script>var x=1;</script><h1>مرحبا</h1><p>text</p></body></html>"
        val t = com.ctodrs.saqrcode.agent.WebTools.htmlToText(html)
        assertFalse(t.contains("var x"))
        assertFalse(t.contains("p{}"))
        assertTrue(t.contains("مرحبا"))
        assertTrue(t.contains("text"))
    }

    // ------------------------------------------------------------ project checks

    @Test
    fun `detect tests from package json and pytest layout`() {
        val tmp = createTempDir()
        try {
            File(tmp, "package.json").writeText("""{"scripts":{"test":"vitest run"}}""")
            val npm = ProjectChecks.detectTests(tmp)
            assertTrue(npm.any { it.label == "npm test" && it.command.contains("npm test") })

            File(tmp, "package.json").delete()
            File(tmp, "tests").mkdirs()
            File(tmp, "tests/test_app.py").writeText("def test_x():\n    assert True\n")
            val py = ProjectChecks.detectTests(tmp)
            assertTrue(py.any { it.label == "pytest" })
        } finally { tmp.deleteRecursively() }
    }

    @Test
    fun `lint detects broken json and unbalanced html`() {
        val tmp = createTempDir()
        try {
            File(tmp, "broken.json").writeText("{\"a\": 1") // truncated: org.json tolerates trailing commas
            File(tmp, "page.html").writeText("<html><body><div><p>x</body></html>")
            val json = ProjectChecks.jsonIssues(tmp)
            assertEquals(1, json.size); assertTrue(json[0].file == "broken.json")
            val html = ProjectChecks.htmlIssues(tmp)
            assertTrue(html.any { it.file == "page.html" && it.message.contains("div") })
        } finally { tmp.deleteRecursively() }
    }

    // ------------------------------------------------------------ templates

    @Test
    fun `compose template has buildable files`() {
        val files = Template.COMPOSE.files("Notes")
        assertTrue(files.containsKey("app/build.gradle.kts"))
        assertTrue(files.containsKey("app/src/main/AndroidManifest.xml"))
        val main = files.entries.first { it.key.endsWith("MainActivity.kt") }.value
        assertTrue(main.contains("package com.saqr.notes"))
        assertTrue(main.contains("MaterialTheme"))
        assertTrue(files["app/build.gradle.kts"]!!.contains("compose = true"))
        assertTrue(files["app/src/main/res/values-ar/strings.xml"]!!.contains("مرحباً"))
    }

    @Test
    fun `ime template declares an input method service`() {
        val files = Template.IME.files("Kbd")
        val manifest = files["app/src/main/AndroidManifest.xml"]!!
        assertTrue(manifest.contains("android.permission.BIND_INPUT_METHOD"))
        assertTrue(manifest.contains(".ArabicImeService"))
        val svc = files.entries.first { it.key.endsWith("ArabicImeService.kt") }.value
        assertTrue(svc.contains("InputMethodService"))
        assertTrue(files["app/src/main/res/xml/arabic_keyboard.xml"]!!.contains("keyLabel=\"ض\""))
        assertEquals("kbd", TemplateFiles.pkg("Kbd"))
    }

    @Test
    fun `pkg sanitises package segments`() {
        assertEquals("app123", TemplateFiles.pkg("123"))
        assertEquals("myapp", TemplateFiles.pkg("My App!"))
        assertEquals("app", TemplateFiles.pkg("###"))
    }
}
