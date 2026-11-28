package com.ctodrs.saqrcode.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

/** Token colours. Dark = "Falcon Night", Light = "Falcon Day" (Saqr gold / teal family). */
data class CodePalette(
    val keyword: Color, val string: Color, val comment: Color, val number: Color, val tag: Color, val type: Color,
    val annotation: Color = tag, val operator: Color = comment, val attr: Color = type,
    val heading: Color = keyword, val link: Color = number, val code: Color = string,
    val match: Color = Color(0x403FC7B8), val currentMatch: Color = Color(0x993FC7B8), val currentLine: Color = Color(0x0FFFFFFF),
) {
    companion object {
        val Dark = CodePalette(
            keyword = Color(0xFFF0B85A), string = Color(0xFF9BD08F), comment = Color(0xFF6B7380),
            number = Color(0xFF7CC4F2), tag = Color(0xFFF08A73), type = Color(0xFF6CCBB6),
            annotation = Color(0xFFD7A0E6), operator = Color(0xFFB7BDC6), attr = Color(0xFFE8CF8A),
            heading = Color(0xFF6FDDD0), link = Color(0xFF7CC4F2), code = Color(0xFF9BD08F),
            match = Color(0x403FC7B8), currentMatch = Color(0x993FC7B8), currentLine = Color(0x0FFFFFFF),
        )
        val Light = CodePalette(
            keyword = Color(0xFF9A5B00), string = Color(0xFF2F7D32), comment = Color(0xFF8A8F97),
            number = Color(0xFF1F6FB2), tag = Color(0xFFB23A24), type = Color(0xFF137A68),
            annotation = Color(0xFF8A3FA0), operator = Color(0xFF5A606A), attr = Color(0xFF8A6A00),
            heading = Color(0xFF9A5B00), link = Color(0xFF1F6FB2), code = Color(0xFF2F7D32),
            match = Color(0x40C48A12), currentMatch = Color(0x8CC48A12), currentLine = Color(0x0A000000),
        )
    }

    fun style(t: TokenType): SpanStyle = when (t) {
        TokenType.KEYWORD -> SpanStyle(color = keyword)
        TokenType.STRING -> SpanStyle(color = string)
        TokenType.NUMBER -> SpanStyle(color = number)
        TokenType.COMMENT -> SpanStyle(color = comment, fontStyle = FontStyle.Italic)
        TokenType.ANNOTATION -> SpanStyle(color = annotation)
        TokenType.TYPE -> SpanStyle(color = type)
        TokenType.OPERATOR -> SpanStyle(color = operator)
        TokenType.TAG -> SpanStyle(color = tag)
        TokenType.ATTR -> SpanStyle(color = attr)
        TokenType.HEADING -> SpanStyle(color = heading, fontWeight = FontWeight.Bold)
        TokenType.LINK -> SpanStyle(color = link, textDecoration = TextDecoration.Underline)
        TokenType.CODE -> SpanStyle(color = code)
        TokenType.EMPHASIS -> SpanStyle(color = keyword, fontStyle = FontStyle.Italic)
    }
}

/**
 * Re-lexes only the lines whose text or incoming state changed since the last call; the rest of the cached
 * per-line tokens are reused. Typing on line N of a 5k-line file re-lexes ~1 line (more only when a multi-line
 * construct such as an opened block comment changes the state of the following lines).
 */
class IncrementalHighlighter(val lang: String) {
    private var lines: List<String> = emptyList()
    private var startStates = IntArray(0)
    private var results: Array<LineTokens?> = arrayOfNulls(0)
    private var lastText: String? = null

    /** Lines lexed by the most recent [update] (for tests / diagnostics). */
    var lastRelexed = 0
        private set

    fun update(text: String): List<LineTokens> {
        if (text === lastText || text == lastText) { lastRelexed = 0; return results.map { it!! } }
        val newLines = text.split('\n')
        val newStates = IntArray(newLines.size)
        val newRes = arrayOfNulls<LineTokens>(newLines.size)
        // align unchanged tail as well as head so inserting a line does not re-lex everything below it
        var head = 0
        while (head < newLines.size && head < lines.size && newLines[head] == lines[head]) head++
        var tail = 0
        while (tail < newLines.size - head && tail < lines.size - head &&
            newLines[newLines.size - 1 - tail] == lines[lines.size - 1 - tail]) tail++
        val shift = lines.size - newLines.size
        var state = Tokenizer.NORMAL
        var relexed = 0
        for (i in newLines.indices) {
            newStates[i] = state
            val oldIdx = when {
                i < head -> i
                i >= newLines.size - tail -> i + shift
                else -> -1
            }
            val cached = if (oldIdx in lines.indices && startStates[oldIdx] == state) results[oldIdx] else null
            val r = cached ?: Tokenizer.lexLine(newLines[i], lang, state).also { relexed++ }
            newRes[i] = r
            state = r.endState
        }
        lines = newLines; startStates = newStates; results = newRes; lastText = text; lastRelexed = relexed
        return newRes.map { it!! }
    }
}

object SyntaxHighlighter {

    fun languageOf(fileName: String): String {
        val lower = fileName.lowercase()
        if (lower.endsWith(".gradle.kts")) return "kt"
        val ext = lower.substringAfterLast('.', "")
        return when (ext) {
            "kt", "kts", "kotlin", "gradle", "swift", "groovy" -> "kt"
            "java" -> "java"
            "js", "jsx", "mjs", "cjs", "javascript" -> "js"
            "ts", "tsx", "mts", "cts", "typescript" -> "ts"
            "py", "pyw", "python" -> "py"
            "html", "htm", "vue", "svelte" -> "html"
            "xml", "svg", "plist", "xsd", "xsl" -> "xml"
            "css", "scss", "less" -> "css"
            "json", "jsonc", "json5" -> "json"
            "md", "markdown", "mdx" -> "md"
            "sh", "bash", "zsh", "shell", "console", "yaml", "yml", "toml", "ini", "properties", "env", "conf" -> "sh"
            "dart" -> "dart"
            "go" -> "go"
            "rs" -> "rs"
            "c", "h", "cpp", "hpp", "cc", "cxx", "m", "mm", "cs" -> "c"
            else -> "plain"
        }
    }

    /** Spans to overlay (e.g. find matches): [start, end) in whole-text offsets. */
    class Overlay(val start: Int, val end: Int, val style: SpanStyle)

    fun annotate(text: String, perLine: List<LineTokens>, p: CodePalette, overlays: List<Overlay> = emptyList()): AnnotatedString {
        val b = AnnotatedString.Builder(text)
        val styles = HashMap<TokenType, SpanStyle>()
        var offset = 0
        var li = 0
        var pos = 0
        // walk line starts without allocating substrings
        while (li < perLine.size) {
            for (t in perLine[li].tokens) {
                val s = offset + t.start; val e = offset + t.end
                if (e <= text.length) b.addStyle(styles.getOrPut(t.type) { p.style(t.type) }, s, e)
            }
            val nl = text.indexOf('\n', pos)
            if (nl < 0) break
            pos = nl + 1; offset = pos; li++
        }
        for (o in overlays) if (o.end <= text.length && o.end > o.start) b.addStyle(o.style, o.start, o.end)
        return b.toAnnotatedString()
    }

    /** One-shot highlight (chat code blocks). */
    fun highlight(text: String, lang: String, p: CodePalette = CodePalette.Dark): AnnotatedString {
        if (text.length > 200_000 || lang == "plain") return AnnotatedString(text)
        return annotate(text, IncrementalHighlighter(lang).update(text), p)
    }
}

/** Editor transformation: cached incremental highlighting plus find-match overlays. Identity offset mapping. */
class HighlightTransformation(
    private val highlighter: IncrementalHighlighter,
    private val palette: CodePalette,
    private val matches: List<IntRange> = emptyList(),
    private val current: Int = -1,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val t = text.text
        if (highlighter.lang == "plain" && matches.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val lines = if (highlighter.lang == "plain" || t.length > 1_000_000) emptyList() else highlighter.update(t)
        val overlays = matches.mapIndexedNotNull { i, r ->
            if (r.last + 1 > t.length) null
            else SyntaxHighlighter.Overlay(r.first, r.last + 1, SpanStyle(background = if (i == current) palette.currentMatch else palette.match))
        }
        return TransformedText(SyntaxHighlighter.annotate(t, lines, palette, overlays), OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is HighlightTransformation && other.highlighter === highlighter &&
        other.palette == palette && other.matches == matches && other.current == current
    override fun hashCode() = System.identityHashCode(highlighter) * 31 + matches.hashCode() * 7 + current
}
