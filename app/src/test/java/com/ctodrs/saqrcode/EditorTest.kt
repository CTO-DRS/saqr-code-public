package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.editor.EditHistory
import com.ctodrs.saqrcode.editor.EditorState
import com.ctodrs.saqrcode.editor.FindReplace
import com.ctodrs.saqrcode.editor.IncrementalHighlighter
import com.ctodrs.saqrcode.editor.SyntaxHighlighter
import com.ctodrs.saqrcode.editor.Token
import com.ctodrs.saqrcode.editor.TokenType
import com.ctodrs.saqrcode.editor.TokenType.ANNOTATION
import com.ctodrs.saqrcode.editor.TokenType.ATTR
import com.ctodrs.saqrcode.editor.TokenType.CODE
import com.ctodrs.saqrcode.editor.TokenType.COMMENT
import com.ctodrs.saqrcode.editor.TokenType.HEADING
import com.ctodrs.saqrcode.editor.TokenType.KEYWORD
import com.ctodrs.saqrcode.editor.TokenType.LINK
import com.ctodrs.saqrcode.editor.TokenType.NUMBER
import com.ctodrs.saqrcode.editor.TokenType.OPERATOR
import com.ctodrs.saqrcode.editor.TokenType.STRING
import com.ctodrs.saqrcode.editor.TokenType.TAG
import com.ctodrs.saqrcode.editor.TokenType.TYPE
import com.ctodrs.saqrcode.editor.Tokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTest {

    /** Tokens of one line as (text, type) pairs, for readable assertions. */
    private fun toks(line: String, lang: String, state: Int = Tokenizer.NORMAL): List<Pair<String, TokenType>> =
        Tokenizer.lexLine(line, lang, state).tokens.map { line.substring(it.start, it.end) to it.type }

    private fun all(text: String, lang: String): List<List<Pair<String, TokenType>>> {
        val ls = text.split('\n')
        return Tokenizer.lexAll(text, lang).mapIndexed { i, t -> t.map { tok: Token -> ls[i].substring(tok.start, tok.end) to tok.type } }
    }

    // ---------------------------------------------------------------- tokenizer

    @Test fun kotlin() {
        val t = toks("""@Composable fun foo(x: Int = 0x1F): String = "a${'$'}{x}b" // done""", "kt")
        assertTrue(("@Composable" to ANNOTATION) in t)
        assertTrue(("fun" to KEYWORD) in t)
        assertTrue(("Int" to TYPE) in t)
        assertTrue(("0x1F" to NUMBER) in t)
        assertTrue(("String" to TYPE) in t)
        assertTrue(("\"a\${x}b\"" to STRING) in t)
        assertTrue(("// done" to COMMENT) in t)
        assertTrue(("=" to OPERATOR) in t)
        assertFalse(t.any { it.first == "foo" })
    }

    @Test fun kotlinTripleQuotedAndBlockCommentSpanLines() {
        val r = all("val s = \"\"\"line1\nline2 // not comment\nend\"\"\" + 1\n/* a\n b */ val x = 2.5f", "kt")
        assertEquals("\"\"\"line1" to STRING, r[0].last())
        assertEquals(listOf("line2 // not comment" to STRING), r[1])
        assertEquals("end\"\"\"" to STRING, r[2][0])
        assertTrue(("1" to NUMBER) in r[2])
        assertEquals(listOf("/* a" to COMMENT), r[3])
        assertEquals(" b */" to COMMENT, r[4][0])
        assertTrue(("2.5f" to NUMBER) in r[4])
        assertTrue(("val" to KEYWORD) in r[4])
    }

    @Test fun java() {
        val t = toks("public static final long N = 10L; @Override void run() { new ArrayList<>(); }", "java")
        listOf("public", "static", "final", "long", "void", "new").forEach { assertTrue(it, (it to KEYWORD) in t) }
        assertTrue(("10L" to NUMBER) in t)
        assertTrue(("@Override" to ANNOTATION) in t)
        assertTrue(("ArrayList" to TYPE) in t)
    }

    @Test fun pythonStringsCommentsDecorators() {
        val r = all("@app.route('/x')\ndef f(a=1e3):\n    s = f\"hi {a}\"  # note\n    '''doc\n    more'''\n    return None", "py")
        assertEquals("@app.route" to ANNOTATION, r[0][0])
        assertTrue(("'/x'" to STRING) in r[0])
        assertTrue(("def" to KEYWORD) in r[1])
        assertTrue(("1e3" to NUMBER) in r[1])
        assertTrue(("f\"hi {a}\"" to STRING) in r[2])
        assertTrue(("# note" to COMMENT) in r[2])
        assertEquals(listOf("'''doc" to STRING), r[3])
        assertEquals(listOf("    more'''" to STRING), r[4])
        assertTrue(("None" to KEYWORD) in r[5])
    }

    @Test fun jsAndTsTemplatesAcrossLines() {
        val r = all("const a = `x\n${'$'}{y}` // c\nlet n = 3;", "js")
        assertTrue(("const" to KEYWORD) in r[0])
        assertEquals("`x" to STRING, r[0].last())
        assertEquals("\${y}`" to STRING, r[1][0])
        assertTrue(("// c" to COMMENT) in r[1])
        assertTrue(("3" to NUMBER) in r[2])
        val ts = toks("interface P { readonly n: number } @Input() x", "ts")
        assertTrue(("interface" to KEYWORD) in ts && ("readonly" to KEYWORD) in ts && ("number" to KEYWORD) in ts)
        assertTrue(("P" to TYPE) in ts)
        assertTrue(("@Input" to ANNOTATION) in ts)
    }

    @Test fun html() {
        val r = all("<!-- c\n--> <div class=\"a\" id='b'>&amp; text</div>", "html")
        assertEquals(listOf("<!-- c" to COMMENT), r[0])
        assertEquals("-->" to COMMENT, r[1][0])
        assertTrue(("<div" to TAG) in r[1])
        assertTrue(("class" to ATTR) in r[1] && ("id" to ATTR) in r[1])
        assertTrue(("\"a\"" to STRING) in r[1] && ("'b'" to STRING) in r[1])
        assertTrue(("&amp;" to NUMBER) in r[1])
        assertTrue(("</div" to TAG) in r[1])
        assertFalse(r[1].any { it.first == "text" })
        // attributes on a following line keep tag mode
        val m = all("<view\n  android:id=\"@+id/x\" />", "xml")
        assertTrue(("android:id" to ATTR) in m[1])
        assertTrue(("/>" to TAG) in m[1])
    }

    @Test fun css() {
        val r = all(".btn:hover, a {\n  color: #fff; margin: 4px !important;\n}\n@media (x) {}", "css")
        assertTrue((".btn" to TYPE) in r[0])
        assertTrue(("a" to TAG) in r[0])
        assertTrue(("color" to ATTR) in r[1] && ("margin" to ATTR) in r[1])
        assertTrue(("#fff" to NUMBER) in r[1] && ("4px" to NUMBER) in r[1])
        assertTrue(("!important" to KEYWORD) in r[1])
        assertTrue(("@media" to KEYWORD) in r[3])
    }

    @Test fun json() {
        val t = toks("""{"name": "saqr", "n": -1.5e2, "ok": true, "x": null}""", "json")
        assertTrue(("\"name\"" to ATTR) in t && ("\"saqr\"" to STRING) in t)
        assertTrue(("1.5e2" to NUMBER) in t)
        assertTrue(("true" to KEYWORD) in t && ("null" to KEYWORD) in t)
    }

    @Test fun markdown() {
        val r = all("# Title\nSee [docs](http://x) and `code` **bold**\n```kotlin\nval x = 1 # not heading\n```\n- item", "md")
        assertEquals(listOf("# Title" to HEADING), r[0])
        assertTrue(("[docs](http://x)" to LINK) in r[1])
        assertTrue(("`code`" to CODE) in r[1])
        assertTrue(("**bold**" to TokenType.EMPHASIS) in r[1])
        assertEquals(listOf("```kotlin" to CODE), r[2])
        assertEquals(listOf("val x = 1 # not heading" to CODE), r[3])
        assertEquals(listOf("```" to CODE), r[4])
        assertEquals("- " to KEYWORD, r[5][0])
    }

    @Test fun shell() {
        val t = toks("""if [ -n "${'$'}HOME" ]; then echo ${'$'}{PATH} # c""", "sh")
        assertTrue(("if" to KEYWORD) in t && ("then" to KEYWORD) in t && ("echo" to KEYWORD) in t)
        assertTrue(("\"\$HOME\"" to STRING) in t)
        assertTrue(("\${PATH}" to TYPE) in t)
        assertTrue(("# c" to COMMENT) in t)
        // a # inside a word is not a comment
        assertFalse(toks("echo a#b", "sh").any { it.second == COMMENT })
    }

    @Test fun languageDetection() {
        assertEquals("kt", SyntaxHighlighter.languageOf("app/build.gradle.kts"))
        assertEquals("kt", SyntaxHighlighter.languageOf("Main.kt"))
        assertEquals("ts", SyntaxHighlighter.languageOf("a.tsx"))
        assertEquals("xml", SyntaxHighlighter.languageOf("AndroidManifest.xml"))
        assertEquals("md", SyntaxHighlighter.languageOf("README.md"))
        assertEquals("json", SyntaxHighlighter.languageOf("package.json"))
        assertEquals("plain", SyntaxHighlighter.languageOf("notes.txt"))
    }

    @Test fun incrementalRelexesOnlyChangedLines() {
        val h = IncrementalHighlighter("kt")
        val lines = (1..5000).map { "val v$it = \"s$it\" // c" }
        val text = lines.joinToString("\n")
        h.update(text)
        assertEquals(5000, h.lastRelexed)
        val edited = text.replace("val v2500 =", "val v2500x =")
        val res = h.update(edited)
        assertEquals(1, h.lastRelexed)
        assertEquals(5000, res.size)
        // inserting a line re-lexes only that line
        val inserted = edited.replace("val v10 =", "fun z() {}\nval v10 =")
        h.update(inserted); assertEquals(1, h.lastRelexed) // shifted lines below are reused
        // opening a block comment changes state of following lines
        val opened = inserted.replace("val v4999 =", "/* val v4999 =")
        h.update(opened); assertTrue(h.lastRelexed >= 2)
        val last = h.update(opened).last()
        assertEquals(COMMENT, last.tokens.single().type)
        // whole-file highlight of 5k lines stays fast
        val t0 = System.nanoTime()
        repeat(20) { i -> h.update(opened.replace("val v100 =", "val v100$i =")) }
        assertTrue("incremental update too slow", (System.nanoTime() - t0) / 20 < 50_000_000)
    }

    // ---------------------------------------------------------------- undo / redo

    @Test fun undoCoalescesTyping() {
        val h = EditHistory()
        var s = EditorState("", 0)
        var t = 1000L
        for (ch in "hello") { val n = EditorState(s.text + ch, s.selStart + 1); h.record(s, n, t); s = n; t += 100 }
        assertEquals(1, h.undoSize)
        // newline starts a new group, and so does a pause
        val nl = EditorState(s.text + "\n", s.selStart + 1); h.record(s, nl, t); s = nl; t += 100
        val w = EditorState(s.text + "w", s.selStart + 1); h.record(s, w, t + 5000); s = w
        assertEquals(3, h.undoSize)
        s = h.undo(s)!!; assertEquals("hello\n", s.text)
        s = h.undo(s)!!; assertEquals("hello", s.text)
        s = h.undo(s)!!; assertEquals("", s.text)
        assertNull(h.undo(s))
        s = h.redo(s)!!; assertEquals("hello", s.text)
        s = h.redo(s)!!; assertEquals("hello\n", s.text)
        s = h.redo(s)!!; assertEquals("hello\nw", s.text)
        assertFalse(h.canRedo)
    }

    @Test fun undoBackspaceGroupAndRedoCleared() {
        val h = EditHistory()
        var s = EditorState("abcdef", 6)
        var t = 0L
        repeat(3) { val n = EditorState(s.text.dropLast(1), s.selStart - 1); h.record(s, n, t); s = n; t += 50 }
        assertEquals("abc", s.text); assertEquals(1, h.undoSize)
        s = h.undo(s)!!; assertEquals("abcdef", s.text)
        assertTrue(h.canRedo)
        val paste = EditorState(s.text + "XYZ", 9); h.record(s, paste, t + 10); s = paste
        assertFalse(h.canRedo)
        // selection-only change records nothing
        h.record(s, s.copy(selStart = 0, selEnd = 3), t + 20)
        assertEquals(1, h.undoSize)
        // caret jump breaks coalescing
        val a = EditorState("aXbcdefXYZ", 2); h.record(EditorState("abcdefXYZ", 1), a, t + 30)
        val b = EditorState("aXbcdefXYZQ", 11); h.record(a, b, t + 40)
        assertEquals(3, h.undoSize)
    }

    @Test fun historyIsBounded() {
        val h = EditHistory(maxSteps = 10)
        var s = EditorState("")
        repeat(50) { val n = EditorState(s.text + "\n"); h.record(s, n); s = n }
        assertEquals(10, h.undoSize)
    }

    // ---------------------------------------------------------------- find / replace

    @Test fun findPlainCaseAndRegex() {
        val text = "Foo foo FOO\nbar foo"
        assertEquals(4, FindReplace.find(text, "foo", FindReplace.Options()).matches.size)
        assertEquals(listOf(4..6, 16..18), FindReplace.find(text, "foo", FindReplace.Options(caseSensitive = true)).matches)
        // regex chars are literal unless regex mode is on
        assertEquals(0, FindReplace.find(text, "f.o", FindReplace.Options()).matches.size)
        assertEquals(4, FindReplace.find(text, "f.o", FindReplace.Options(regex = true)).matches.size)
        assertEquals(listOf(0..2, 12..14), FindReplace.find(text, "^\\w+", FindReplace.Options(regex = true)).matches)
        val bad = FindReplace.find(text, "(", FindReplace.Options(regex = true))
        assertNotNull(bad.error); assertTrue(bad.matches.isEmpty())
        assertTrue(FindReplace.find(text, "^", FindReplace.Options(regex = true)).matches.isEmpty())
        assertTrue(FindReplace.find(text, "", FindReplace.Options()).matches.isEmpty())
    }

    @Test fun nextPrevWrap() {
        val m = listOf(2..3, 10..11, 20..21)
        assertEquals(1, FindReplace.nextIndex(m, 5))
        assertEquals(0, FindReplace.nextIndex(m, 25))
        assertEquals(1, FindReplace.prevIndex(m, 15))
        assertEquals(2, FindReplace.prevIndex(m, 0))
        assertEquals(-1, FindReplace.nextIndex(emptyList(), 0))
    }

    @Test fun replaceOneAndAll() {
        val text = "a1 b22 c333 \$x"
        val o = FindReplace.Options(regex = true)
        val m = FindReplace.find(text, "(\\w)(\\d+)", o).matches
        val (t1, caret) = FindReplace.replaceOne(text, m[1], "(\\w)(\\d+)", "$2$1", o)
        assertEquals("a1 22b c333 \$x", t1); assertEquals(6, caret)
        assertEquals("1a 22b 333c \$x" to 3, FindReplace.replaceAll(text, "(\\w)(\\d+)", "$2$1", o))
        // literal mode: $ in query and replacement are literal
        assertEquals("a1 b22 c333 \$1y" to 1, FindReplace.replaceAll(text, "\$x", "\$1y", FindReplace.Options()))
        assertEquals("X1 b22 c333 \$x" to 1, FindReplace.replaceAll(text, "A", "X", FindReplace.Options(caseSensitive = false)).let { (s, n) -> s to n })
        assertEquals(text to 0, FindReplace.replaceAll(text, "A", "X", FindReplace.Options(caseSensitive = true)))
        // bad group reference leaves text untouched
        assertEquals(text to 0, FindReplace.replaceAll(text, "\\d", "$9", o))
        // ar text
        assertEquals("مرحبا صقر" to 1, FindReplace.replaceAll("مرحبا عالم", "عالم", "صقر", FindReplace.Options()))
    }
}
