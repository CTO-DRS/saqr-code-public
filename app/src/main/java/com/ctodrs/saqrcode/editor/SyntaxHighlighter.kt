package com.ctodrs.saqrcode.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Token colours. Dark = "Falcon Night", Light = "Falcon Day". */
data class CodePalette(
    val keyword: Color, val string: Color, val comment: Color, val number: Color, val tag: Color, val type: Color,
) {
    companion object {
        val Dark = CodePalette(
            keyword = Color(0xFFF0B85A), string = Color(0xFF9BD08F), comment = Color(0xFF6B7380),
            number = Color(0xFF7CC4F2), tag = Color(0xFFF08A73), type = Color(0xFF6CCBB6),
        )
        val Light = CodePalette(
            keyword = Color(0xFF9A5B00), string = Color(0xFF2F7D32), comment = Color(0xFF8A8F97),
            number = Color(0xFF1F6FB2), tag = Color(0xFFB23A24), type = Color(0xFF137A68),
        )
    }
}

/** Lightweight regex highlighter. */
object SyntaxHighlighter {

    private val kw = mapOf(
        "kt" to "package import class interface object fun val var if else when for while do return break continue try catch finally throw is as in null true false this super data sealed enum override private public internal protected open abstract suspend companion lateinit const init by typealias",
        "java" to "package import class interface extends implements public private protected static final void if else switch case for while do return break continue try catch finally throw throws new null true false this super int long boolean char double float byte short var record",
        "js" to "import export from default function const let var if else switch case for while do return break continue try catch finally throw new null undefined true false this class extends async await typeof instanceof of in yield",
        "py" to "import from as def class return if elif else for while break continue try except finally raise with lambda None True False and or not in is pass yield global nonlocal async await self",
        "sh" to "if then else elif fi for while do done case esac function return in export local echo cd",
        "dart" to "import class extends implements with void final const var if else for while return true false null new this super async await static late required",
        "go" to "package import func var const type struct interface map chan if else for range return go defer switch case break continue nil true false",
        "rs" to "fn let mut pub struct enum impl trait use mod if else match for while loop return true false self Self crate super as where async await",
        "c" to "int char void long short unsigned signed struct union enum typedef static const if else for while do return break continue switch case default sizeof include define",
    ).mapValues { it.value.split(' ').toSet() }

    private val alias = mapOf(
        "kts" to "kt", "ts" to "js", "tsx" to "js", "jsx" to "js", "mjs" to "js", "cjs" to "js", "json" to "js",
        "bash" to "sh", "zsh" to "sh", "cpp" to "c", "h" to "c", "hpp" to "c", "cc" to "c", "gradle" to "kt", "swift" to "kt",
    )

    fun languageOf(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            ext in setOf("html", "htm", "xml", "svg", "vue") -> "html"
            ext in setOf("css", "scss") -> "css"
            ext in setOf("md", "txt", "", "text", "plain") -> "plain"
            ext in setOf("python") -> "py"
            ext in setOf("kotlin") -> "kt"
            ext in setOf("javascript", "typescript") -> "js"
            ext in setOf("shell", "console") -> "sh"
            else -> alias[ext] ?: ext
        }
    }

    private val tokenRe = Regex(
        """(?<comment>//[^\n]*|/\*[\s\S]*?\*/|#[^\n]*)|(?<str>"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`)|(?<num>\b\d+(?:\.\d+)?[fFLu]?\b)|(?<word>\b[A-Za-z_][A-Za-z0-9_]*\b)"""
    )
    private val htmlRe = Regex("""(?<comment><!--[\s\S]*?-->)|(?<str>"[^"\n]*"|'[^'\n]*')|(?<tag></?[A-Za-z][A-Za-z0-9-]*|/?>)""")

    fun highlight(text: String, lang: String, p: CodePalette = CodePalette.Dark): AnnotatedString {
        val keywordColor = p.keyword; val stringColor = p.string; val commentColor = p.comment
        val numberColor = p.number; val tagColor = p.tag; val typeColor = p.type
        if (text.length > 80_000 || lang == "plain") return AnnotatedString(text)
        val b = AnnotatedString.Builder(text)
        if (lang == "html" || lang == "css") {
            htmlRe.findAll(text).forEach { m ->
                val g = m.groups
                val style = when {
                    g["comment"] != null -> SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)
                    g["str"] != null -> SpanStyle(color = stringColor)
                    else -> SpanStyle(color = tagColor)
                }
                b.addStyle(style, m.range.first, m.range.last + 1)
            }
            return b.toAnnotatedString()
        }
        val words = kw[lang] ?: kw["js"]!!
        val hashComments = lang in setOf("py", "sh", "rb", "yaml", "yml", "toml")
        tokenRe.findAll(text).forEach { m ->
            val g = m.groups
            val style = when {
                g["comment"] != null -> {
                    val v = m.value
                    if (v.startsWith("#") && !hashComments) null else SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)
                }
                g["str"] != null -> SpanStyle(color = stringColor)
                g["num"] != null -> SpanStyle(color = numberColor)
                g["word"] != null -> when {
                    m.value in words -> SpanStyle(color = keywordColor)
                    m.value[0].isUpperCase() -> SpanStyle(color = typeColor)
                    else -> null
                }
                else -> null
            }
            if (style != null) b.addStyle(style, m.range.first, m.range.last + 1)
        }
        return b.toAnnotatedString()
    }
}

class HighlightTransformation(private val lang: String, private val palette: CodePalette = CodePalette.Dark) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(SyntaxHighlighter.highlight(text.text, lang, palette), OffsetMapping.Identity)
}
