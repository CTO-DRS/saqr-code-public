package com.ctodrs.saqrcode.editor

/** Kinds of token the editor colours. */
enum class TokenType { KEYWORD, STRING, NUMBER, COMMENT, ANNOTATION, TYPE, OPERATOR, TAG, ATTR, HEADING, LINK, CODE, EMPHASIS }

/** A token inside one line: [start, end) are offsets into that line. */
data class Token(val start: Int, val end: Int, val type: TokenType)

/** Result of lexing a single line: its tokens plus the lexer state carried into the next line. */
class LineTokens(val tokens: List<Token>, val endState: Int)

/**
 * Small hand-written, line-incremental lexer. Each line is lexed from the state the previous line ended in,
 * so multi-line constructs (block comments, triple-quoted strings, template literals, HTML comments, markdown fences)
 * work while an edit only re-lexes the lines whose input changed (see [IncrementalHighlighter]).
 *
 * State layout: low byte = mode, bit 8 = "inside a CSS declaration block".
 */
object Tokenizer {
    const val NORMAL = 0
    const val BLOCK_COMMENT = 1
    const val TRIPLE_DQ = 2
    const val TRIPLE_SQ = 3
    const val TEMPLATE = 4
    const val HTML_COMMENT = 5
    const val MD_FENCE = 6
    const val HTML_TAG = 7
    private const val CSS_BLOCK = 0x100

    private fun words(s: String) = s.trim().split(Regex("\\s+")).toHashSet()

    private val keywords: Map<String, Set<String>> = mapOf(
        "kt" to words("package import class interface object fun val var if else when for while do return break continue try catch finally throw is as in out null true false this super data sealed enum annotation inner value override private public internal protected open abstract final suspend companion lateinit const init by typealias where get set field it inline noinline crossinline reified operator infix tailrec vararg external expect actual"),
        "java" to words("package import class interface enum extends implements public private protected static final abstract native synchronized transient volatile strictfp void if else switch case default for while do return break continue try catch finally throw throws new null true false this super instanceof assert int long boolean char double float byte short var record sealed permits yield"),
        "js" to words("import export from default function const let var if else switch case for while do return break continue try catch finally throw new delete null undefined true false this super class extends static get set async await typeof instanceof void of in yield debugger with NaN Infinity"),
        "ts" to words("import export from default function const let var if else switch case for while do return break continue try catch finally throw new delete null undefined true false this super class extends implements interface type enum namespace module declare abstract readonly public private protected static get set async await typeof instanceof keyof infer is as satisfies void of in yield any unknown never string number boolean bigint symbol object"),
        "py" to words("import from as def class return if elif else for while break continue try except finally raise with lambda None True False and or not in is pass yield global nonlocal async await del assert match case self cls print"),
        "sh" to words("if then else elif fi for while until do done case esac function return in select time export local readonly declare unset shift source alias echo printf cd exit set test read eval exec trap"),
        "json" to words("true false null"),
        "css" to words("important inherit initial unset none auto"),
        "dart" to words("import export library part class extends implements with mixin void final const var if else for while do switch case default return true false null new this super async await static late required dynamic is as in"),
        "go" to words("package import func var const type struct interface map chan if else for range return go defer switch case default break continue nil true false select fallthrough goto"),
        "rs" to words("fn let mut pub struct enum impl trait use mod if else match for while loop return true false self Self crate super as where async await move ref static const unsafe dyn type extern in break continue"),
        "c" to words("int char void long short unsigned signed struct union enum typedef static const extern volatile register auto inline if else for while do return break continue switch case default goto sizeof include define ifdef ifndef endif pragma class public private protected virtual template typename namespace using new delete this true false nullptr bool"),
    )
    private val cLike = setOf("kt", "java", "js", "ts", "json", "dart", "go", "rs", "c", "css")
    private val annotationLangs = setOf("kt", "java", "ts", "js", "py", "dart")
    private val opChars = "+-*/%=<>!&|^~?:".toSet()

    fun lexLine(line: String, lang: String, state: Int): LineTokens = when (lang) {
        "html", "xml" -> lexMarkup(line, state)
        "md" -> lexMarkdown(line, state)
        "css" -> lexCss(line, state)
        "plain" -> LineTokens(emptyList(), NORMAL)
        else -> lexCode(line, lang, state)
    }

    /** Convenience for tests and one-shot use: lex a whole text, return tokens per line. */
    fun lexAll(text: String, lang: String): List<List<Token>> {
        var st = NORMAL
        return text.split('\n').map { l -> lexLine(l, lang, st).also { st = it.endState }.tokens }
    }

    // ------------------------------------------------------------ code (C-like, python, shell)

    private fun isIdStart(c: Char) = c.isLetter() || c == '_' || c == '$'
    private fun isId(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    private fun lexCode(s: String, lang: String, state0: Int): LineTokens {
        val out = ArrayList<Token>()
        val kw = keywords[lang] ?: keywords["js"]!!
        val hash = lang == "py" || lang == "sh"
        val slashComments = lang in cLike || lang !in setOf("py", "sh")
        var mode = state0 and 0xFF
        var i = 0
        val n = s.length
        fun add(a: Int, b: Int, t: TokenType) { if (b > a) out += Token(a, b, t) }

        while (i < n) {
            when (mode) {
                BLOCK_COMMENT -> {
                    val e = s.indexOf("*/", i)
                    if (e < 0) { add(i, n, TokenType.COMMENT); i = n } else { add(i, e + 2, TokenType.COMMENT); i = e + 2; mode = NORMAL }
                    continue
                }
                TRIPLE_DQ, TRIPLE_SQ -> {
                    val q = if (mode == TRIPLE_DQ) "\"\"\"" else "'''"
                    val e = s.indexOf(q, i)
                    if (e < 0) { add(i, n, TokenType.STRING); i = n } else {
                        var end = e + 3
                        while (end < n && s[end] == q[0]) end++ // """" closing with extra quotes (kotlin)
                        add(i, end, TokenType.STRING); i = end; mode = NORMAL
                    }
                    continue
                }
                TEMPLATE -> {
                    var j = i
                    while (j < n && s[j] != '`') { if (s[j] == '\\') j++; j++ }
                    if (j >= n) { add(i, n, TokenType.STRING); i = n } else { add(i, j + 1, TokenType.STRING); i = j + 1; mode = NORMAL }
                    continue
                }
            }
            val c = s[i]
            when {
                c == ' ' || c == '\t' -> i++
                slashComments && c == '/' && i + 1 < n && s[i + 1] == '/' -> { add(i, n, TokenType.COMMENT); i = n }
                slashComments && c == '/' && i + 1 < n && s[i + 1] == '*' -> {
                    val e = s.indexOf("*/", i + 2)
                    if (e < 0) { add(i, n, TokenType.COMMENT); i = n; mode = BLOCK_COMMENT } else { add(i, e + 2, TokenType.COMMENT); i = e + 2 }
                }
                hash && c == '#' && (lang == "py" || i == 0 || s[i - 1] == ' ' || s[i - 1] == '\t' || s[i - 1] == ';') -> { add(i, n, TokenType.COMMENT); i = n }
                (lang == "kt" || lang == "py" || lang == "dart") && s.startsWith("\"\"\"", i) -> {
                    val e = s.indexOf("\"\"\"", i + 3)
                    if (e < 0) { add(i, n, TokenType.STRING); i = n; mode = TRIPLE_DQ } else { add(i, e + 3, TokenType.STRING); i = e + 3 }
                }
                (lang == "py" || lang == "dart") && s.startsWith("'''", i) -> {
                    val e = s.indexOf("'''", i + 3)
                    if (e < 0) { add(i, n, TokenType.STRING); i = n; mode = TRIPLE_SQ } else { add(i, e + 3, TokenType.STRING); i = e + 3 }
                }
                c == '`' && (lang == "js" || lang == "ts" || lang == "sh") -> {
                    var j = i + 1
                    while (j < n && s[j] != '`') { if (s[j] == '\\') j++; j++ }
                    if (j >= n) { add(i, n, TokenType.STRING); i = n; if (lang != "sh") mode = TEMPLATE } else { add(i, j + 1, TokenType.STRING); i = j + 1 }
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < n && s[j] != c) { if (s[j] == '\\' && !(lang == "sh" && c == '\'')) j++; j++ }
                    val end = minOf(j + 1, n)
                    var type = TokenType.STRING
                    if (lang == "json") { // object keys
                        var k = end
                        while (k < n && (s[k] == ' ' || s[k] == '\t')) k++
                        if (k < n && s[k] == ':') type = TokenType.ATTR
                    }
                    add(i, end, type); i = end
                }
                c.isDigit() || (c == '.' && i + 1 < n && s[i + 1].isDigit() && (i == 0 || !isId(s[i - 1]))) -> {
                    if (i > 0 && isId(s[i - 1])) { i++; continue }
                    val e = scanNumber(s, i); add(i, e, TokenType.NUMBER); i = e
                }
                c == '@' && lang in annotationLangs && i + 1 < n && isIdStart(s[i + 1]) -> {
                    var j = i + 1
                    while (j < n && (isId(s[j]) || s[j] == '.' || s[j] == ':')) j++
                    add(i, j, TokenType.ANNOTATION); i = j
                }
                lang == "sh" && c == '$' && i + 1 < n && (isIdStart(s[i + 1]) || s[i + 1] == '{' || s[i + 1].isDigit() || s[i + 1] in "@#?*!") -> {
                    var j = i + 1
                    if (s[j] == '{') { val e = s.indexOf('}', j); j = if (e < 0) n else e + 1 }
                    else if (!isIdStart(s[j])) j++
                    else while (j < n && (s[j].isLetterOrDigit() || s[j] == '_')) j++
                    add(i, j, TokenType.TYPE); i = j
                }
                isIdStart(c) -> {
                    var j = i + 1
                    val idChar: (Char) -> Boolean = if (lang == "sh") { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '-' } else ::isId
                    while (j < n && idChar(s[j])) j++
                    val w = s.substring(i, j)
                    // python string prefixes: f"..", r'..', b"..", rb"..."
                    if (lang == "py" && j < n && (s[j] == '"' || s[j] == '\'') && w.lowercase() in setOf("f", "r", "b", "u", "rb", "br", "fr", "rf")) {
                        val q = s[j]
                        val triple = s.startsWith("$q$q$q", j)
                        if (triple) {
                            val e = s.indexOf("$q$q$q", j + 3)
                            if (e < 0) { add(i, n, TokenType.STRING); i = n; mode = if (q == '"') TRIPLE_DQ else TRIPLE_SQ } else { add(i, e + 3, TokenType.STRING); i = e + 3 }
                        } else {
                            var k = j + 1
                            while (k < n && s[k] != q) { if (s[k] == '\\') k++; k++ }
                            val end = minOf(k + 1, n); add(i, end, TokenType.STRING); i = end
                        }
                        continue
                    }
                    when {
                        w in kw -> add(i, j, TokenType.KEYWORD)
                        lang != "sh" && lang != "json" && w[0].isUpperCase() -> add(i, j, TokenType.TYPE)
                    }
                    i = j
                }
                c in opChars -> {
                    var j = i + 1
                    while (j < n && s[j] in opChars && !(s[j] == '/' && j + 1 < n && (s[j + 1] == '/' || s[j + 1] == '*'))) j++
                    add(i, j, TokenType.OPERATOR); i = j
                }
                else -> i++
            }
        }
        return LineTokens(out, mode)
    }

    private fun scanNumber(s: String, start: Int): Int {
        val n = s.length
        var j = start
        if (s[j] == '0' && j + 1 < n && s[j + 1] in "xXbBoO") {
            j += 2
            while (j < n && (s[j].isLetterOrDigit() || s[j] == '_')) j++
            return j
        }
        while (j < n && (s[j].isDigit() || s[j] == '_')) j++
        if (j < n && s[j] == '.' && j + 1 < n && s[j + 1].isDigit()) { j++; while (j < n && (s[j].isDigit() || s[j] == '_')) j++ }
        if (j < n && (s[j] == 'e' || s[j] == 'E')) {
            var k = j + 1
            if (k < n && (s[k] == '+' || s[k] == '-')) k++
            if (k < n && s[k].isDigit()) { j = k; while (j < n && s[j].isDigit()) j++ }
        }
        while (j < n && s[j] in "fFlLuUdDn") j++
        return j
    }

    // ------------------------------------------------------------ HTML / XML

    private fun lexMarkup(s: String, state0: Int): LineTokens {
        val out = ArrayList<Token>()
        var mode = state0 and 0xFF
        var i = 0
        val n = s.length
        fun add(a: Int, b: Int, t: TokenType) { if (b > a) out += Token(a, b, t) }
        while (i < n) {
            if (mode == HTML_COMMENT) {
                val e = s.indexOf("-->", i)
                if (e < 0) { add(i, n, TokenType.COMMENT); i = n } else { add(i, e + 3, TokenType.COMMENT); i = e + 3; mode = NORMAL }
                continue
            }
            val c = s[i]
            if (mode == HTML_TAG) {
                when {
                    c == ' ' || c == '\t' -> i++
                    c == '>' -> { add(i, i + 1, TokenType.TAG); i++; mode = NORMAL }
                    (c == '/' || c == '?') && i + 1 < n && s[i + 1] == '>' -> { add(i, i + 2, TokenType.TAG); i += 2; mode = NORMAL }
                    c == '"' || c == '\'' -> {
                        val e = s.indexOf(c, i + 1)
                        val end = if (e < 0) n else e + 1
                        add(i, end, TokenType.STRING); i = end
                    }
                    c == '=' -> { add(i, i + 1, TokenType.OPERATOR); i++ }
                    c.isLetter() || c == '_' || c == ':' || c == '@' -> {
                        var j = i + 1
                        while (j < n && (s[j].isLetterOrDigit() || s[j] in "-_:.@")) j++
                        add(i, j, TokenType.ATTR); i = j
                    }
                    else -> i++
                }
                continue
            }
            when {
                s.startsWith("<!--", i) -> {
                    val e = s.indexOf("-->", i + 4)
                    if (e < 0) { add(i, n, TokenType.COMMENT); i = n; mode = HTML_COMMENT } else { add(i, e + 3, TokenType.COMMENT); i = e + 3 }
                }
                s.startsWith("<![CDATA[", i) -> {
                    val e = s.indexOf("]]>", i)
                    val end = if (e < 0) n else e + 3
                    add(i, end, TokenType.STRING); i = end
                }
                c == '<' && i + 1 < n && (s[i + 1].isLetter() || s[i + 1] in "/!?") -> {
                    var j = i + 1
                    if (s[j] in "/!?") j++
                    while (j < n && (s[j].isLetterOrDigit() || s[j] in "-_:.")) j++
                    add(i, j, TokenType.TAG); i = j; mode = HTML_TAG
                }
                c == '&' -> {
                    val e = s.indexOf(';', i)
                    if (e in (i + 2)..(i + 10) && s.substring(i + 1, e).all { it.isLetterOrDigit() || it == '#' }) { add(i, e + 1, TokenType.NUMBER); i = e + 1 } else i++
                }
                else -> i++
            }
        }
        return LineTokens(out, mode)
    }

    // ------------------------------------------------------------ CSS

    private fun lexCss(s: String, state0: Int): LineTokens {
        val out = ArrayList<Token>()
        var mode = state0 and 0xFF
        var inBlock = state0 and CSS_BLOCK != 0
        var i = 0
        val n = s.length
        fun add(a: Int, b: Int, t: TokenType) { if (b > a) out += Token(a, b, t) }
        while (i < n) {
            if (mode == BLOCK_COMMENT) {
                val e = s.indexOf("*/", i)
                if (e < 0) { add(i, n, TokenType.COMMENT); i = n } else { add(i, e + 2, TokenType.COMMENT); i = e + 2; mode = NORMAL }
                continue
            }
            val c = s[i]
            when {
                c == '/' && i + 1 < n && s[i + 1] == '*' -> {
                    val e = s.indexOf("*/", i + 2)
                    if (e < 0) { add(i, n, TokenType.COMMENT); i = n; mode = BLOCK_COMMENT } else { add(i, e + 2, TokenType.COMMENT); i = e + 2 }
                }
                c == '{' -> { inBlock = true; i++ }
                c == '}' -> { inBlock = false; i++ }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < n && s[j] != c) { if (s[j] == '\\') j++; j++ }
                    val end = minOf(j + 1, n); add(i, end, TokenType.STRING); i = end
                }
                c == '@' -> {
                    var j = i + 1
                    while (j < n && (s[j].isLetterOrDigit() || s[j] == '-')) j++
                    add(i, j, TokenType.KEYWORD); i = j
                }
                c == '!' && s.startsWith("!important", i) -> { add(i, i + 10, TokenType.KEYWORD); i += 10 }
                inBlock && c == '#' -> {
                    var j = i + 1
                    while (j < n && s[j].isLetterOrDigit()) j++
                    add(i, j, TokenType.NUMBER); i = j
                }
                (c.isDigit() || (c == '.' && i + 1 < n && s[i + 1].isDigit())) && (i == 0 || !(s[i - 1].isLetterOrDigit() || s[i - 1] == '-' || s[i - 1] == '_')) -> {
                    var j = i
                    while (j < n && (s[j].isDigit() || s[j] == '.')) j++
                    while (j < n && (s[j].isLetter() || s[j] == '%')) j++
                    add(i, j, TokenType.NUMBER); i = j
                }
                !inBlock && (c == '.' || c == '#') && i + 1 < n && (s[i + 1].isLetter() || s[i + 1] == '_' || s[i + 1] == '-') -> {
                    var j = i + 1
                    while (j < n && (s[j].isLetterOrDigit() || s[j] in "-_")) j++
                    add(i, j, TokenType.TYPE); i = j
                }
                c.isLetter() || c == '-' || c == '_' -> {
                    var j = i + 1
                    while (j < n && (s[j].isLetterOrDigit() || s[j] in "-_")) j++
                    if (inBlock) {
                        var k = j
                        while (k < n && s[k] == ' ') k++
                        if (k < n && s[k] == ':') add(i, j, TokenType.ATTR)
                        else if (s.substring(i, j) in keywords["css"]!!) add(i, j, TokenType.KEYWORD)
                    } else if (i == 0 || s[i - 1] != ':') add(i, j, TokenType.TAG)
                    else add(i, j, TokenType.KEYWORD) // pseudo-class
                    i = j
                }
                else -> i++
            }
        }
        return LineTokens(out, mode or (if (inBlock) CSS_BLOCK else 0))
    }

    // ------------------------------------------------------------ Markdown

    private val mdLink = Regex("""!?\[[^\]\n]*]\([^)\n]*\)|<https?://[^>\s]+>""")
    private val mdCode = Regex("""`[^`\n]+`""")
    private val mdEmph = Regex("""\*\*[^*\n]+\*\*|__[^_\n]+__|(?<![*\w])\*[^*\s][^*\n]*\*|(?<![_\w])_[^_\s][^_\n]*_""")
    private val mdList = Regex("""^\s*(?:[-*+]|\d+[.)])\s""")

    private fun lexMarkdown(s: String, state0: Int): LineTokens {
        val mode = state0 and 0xFF
        val trimmed = s.trimStart()
        val fence = trimmed.startsWith("```") || trimmed.startsWith("~~~")
        if (mode == MD_FENCE) return LineTokens(if (s.isEmpty()) emptyList() else listOf(Token(0, s.length, TokenType.CODE)), if (fence) NORMAL else MD_FENCE)
        if (fence) return LineTokens(listOf(Token(0, s.length, TokenType.CODE)), MD_FENCE)
        if (s.isEmpty()) return LineTokens(emptyList(), NORMAL)
        if (trimmed.startsWith("#")) {
            val hashes = trimmed.takeWhile { it == '#' }.length
            if (hashes in 1..6 && (trimmed.length == hashes || trimmed[hashes] == ' ')) return LineTokens(listOf(Token(0, s.length, TokenType.HEADING)), NORMAL)
        }
        if (trimmed.startsWith(">")) return LineTokens(listOf(Token(0, s.length, TokenType.COMMENT)), NORMAL)
        if (s.isNotBlank() && s.trim().let { t -> t.length >= 3 && (t.all { it == '-' } || t.all { it == '*' } || t.all { it == '=' }) })
            return LineTokens(listOf(Token(0, s.length, TokenType.OPERATOR)), NORMAL)
        val out = ArrayList<Token>()
        mdList.find(s)?.let { out += Token(it.range.first, it.range.last + 1, TokenType.KEYWORD) }
        val taken = BooleanArray(s.length)
        fun claim(r: IntRange, t: TokenType) {
            if (r.any { taken[it] }) return
            r.forEach { taken[it] = true }; out += Token(r.first, r.last + 1, t)
        }
        mdCode.findAll(s).forEach { claim(it.range, TokenType.CODE) }
        mdLink.findAll(s).forEach { claim(it.range, TokenType.LINK) }
        mdEmph.findAll(s).forEach { claim(it.range, TokenType.EMPHASIS) }
        out.sortBy { it.start }
        return LineTokens(out, NORMAL)
    }
}
