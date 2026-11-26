package com.ctodrs.saqrcode.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.editor.CodePalette
import com.ctodrs.saqrcode.editor.SyntaxHighlighter
import com.ctodrs.saqrcode.ui.components.pressable
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.JetBrainsMono
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.delay

/** Minimal Markdown block model. */
sealed class MdBlock {
    data class Para(val text: String) : MdBlock()
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Bullet(val text: String, val ordinal: String?, val indent: Int) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Code(val lang: String, val code: String, val closed: Boolean) : MdBlock()
    data object Rule : MdBlock()
}

object MarkdownParser {
    private val ordered = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
    private val bullet = Regex("""^(\s*)[-*•+]\s+(.*)$""")
    private val heading = Regex("""^(#{1,4})\s+(.*)$""")

    fun parse(src: String): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        val lines = src.replace("\r\n", "\n").split('\n')
        val para = StringBuilder()
        fun flush() { if (para.isNotBlank()) out += MdBlock.Para(para.toString().trim()); para.setLength(0) }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trimStart()
            when {
                t.startsWith("```") -> {
                    flush()
                    val lang = t.removePrefix("```").trim()
                    val code = StringBuilder(); var closed = false
                    i++
                    while (i < lines.size) {
                        if (lines[i].trimStart().startsWith("```")) { closed = true; break }
                        if (code.isNotEmpty()) code.append('\n'); code.append(lines[i]); i++
                    }
                    out += MdBlock.Code(lang, code.toString(), closed)
                }
                t.isEmpty() -> flush()
                heading.matches(t) -> { flush(); val m = heading.find(t)!!; out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2]) }
                t == "---" || t == "***" || t == "___" -> { flush(); out += MdBlock.Rule }
                t.startsWith(">") -> { flush(); out += MdBlock.Quote(t.removePrefix(">").trim()) }
                ordered.matches(line) -> { flush(); val m = ordered.find(line)!!; out += MdBlock.Bullet(m.groupValues[3], m.groupValues[2], m.groupValues[1].length / 2) }
                bullet.matches(line) -> { flush(); val m = bullet.find(line)!!; out += MdBlock.Bullet(m.groupValues[2], null, m.groupValues[1].length / 2) }
                else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line) }
            }
            i++
        }
        flush()
        return out
    }

    private val inline = Regex("""(`[^`\n]+`)|(\*\*[^*\n]+\*\*)|(__[^_\n]+__)|(~~[^~\n]+~~)|(\[[^\]\n]+]\([^)\s]+\))|(\*[^*\n]+\*)|((?<![\w])_[^_\n]+_(?![\w]))""")

    fun inline(text: String, codeBg: Color, codeFg: Color, link: Color): AnnotatedString = buildAnnotatedString {
        appendInline(text, codeBg, codeFg, link)
    }

    /** Recursive so `**[label](url)**` (bold around a link) is a bold link, not raw bracket text that the bidi algorithm scrambles in Arabic. */
    private fun AnnotatedString.Builder.appendInline(text: String, codeBg: Color, codeFg: Color, link: Color) {
        var last = 0
        for (m in inline.findAll(text)) {
            append(text.substring(last, m.range.first))
            val v = m.value
            when {
                v.startsWith("`") -> withStyle(SpanStyle(
                    fontFamily = if (v.any { it in '\u0600'..'\u06FF' }) com.ctodrs.saqrcode.ui.theme.PlexArabic else JetBrainsMono,
                    fontSize = 13.sp, background = codeBg, color = codeFg,
                )) {
                    append("\u2009"); append(v.trim('`')); append("\u2009")
                }
                v.startsWith("**") || v.startsWith("__") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { appendInline(v.substring(2, v.length - 2), codeBg, codeFg, link) }
                v.startsWith("~~") -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInline(v.substring(2, v.length - 2), codeBg, codeFg, link) }
                v.startsWith("[") -> {
                    val label = v.substringAfter('[').substringBefore("](")
                    val url = v.substringAfter("](").dropLast(1)
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline)))) { append(label) }
                }
                else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(v.substring(1, v.length - 1), codeBg, codeFg, link) }
            }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}

/** Renders assistant Markdown with per-paragraph text direction (Arabic RTL, English LTR). */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val c = Saqr.colors
    val type = Saqr.type
    val blocks = remember(text) { MarkdownParser.parse(text) }
    val codeBg = c.surface3
    val base = type.body.copy(color = c.text1, textDirection = TextDirection.Content)
    fun ann(s: String) = MarkdownParser.inline(s, codeBg, c.accentText, c.info)
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
            blocks.forEach { b ->
                when (b) {
                    is MdBlock.Para -> Text(ann(b.text), style = base)
                    is MdBlock.Heading -> Text(
                        ann(b.text),
                        style = (if (b.level <= 2) type.title2 else type.title3).copy(color = c.text1, textDirection = TextDirection.Content),
                        modifier = Modifier.padding(top = Sp.xs),
                    )
                    is MdBlock.Bullet -> Row(Modifier.padding(start = (b.indent * 16).dp)) {
                        Box(Modifier.width(22.dp).padding(top = 1.dp)) {
                            if (b.ordinal != null) Text("${b.ordinal}.", style = base.copy(color = c.accentText, fontFamily = JetBrainsMono, fontSize = 13.sp))
                            else Box(Modifier.padding(top = 10.dp, start = 6.dp).size(5.dp).clip(RoundedCornerShape(3.dp)).background(c.accentText))
                        }
                        Text(ann(b.text), style = base, modifier = Modifier.weight(1f))
                    }
                    is MdBlock.Quote -> Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.accent.copy(alpha = 0.6f)))
                        Spacer(Modifier.width(Sp.md))
                        Text(ann(b.text), style = base.copy(color = c.text2))
                    }
                    is MdBlock.Code -> CodeBlock(b.code, b.lang)
                    MdBlock.Rule -> Box(Modifier.fillMaxWidth().padding(vertical = Sp.sm).height(1.dp).background(c.border))
                }
            }
        }
    }
}


/** Code block: language label + copy, LTR, syntax highlighted, horizontally scrollable. */
@Composable
fun CodeBlock(code: String, lang: String, maxHeight: Int = 380) {
    val c = Saqr.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }
    val palette = if (c.isDark) CodePalette.Dark else CodePalette.Light
    val language = remember(lang) { SyntaxHighlighter.languageOf("x." + lang.ifBlank { "txt" }) }
    val highlighted = remember(code, language, palette) { SyntaxHighlighter.highlight(code, language, palette) }
    val shape = RoundedCornerShape(Radius.md)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxWidth().clip(shape).background(c.codeBg).border(1.dp, c.border, shape)) {
            Row(
                Modifier.fillMaxWidth().background(c.codeHeader).padding(start = Sp.md, end = Sp.xs, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(lang.ifBlank { "code" }.lowercase(), style = Saqr.type.monoMicro, color = c.text3, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).pressable { clipboard.setText(AnnotatedString(code)); copied = true }
                        .padding(horizontal = Sp.sm, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(copied, label = "copy") { done ->
                        SIcon(if (done) Lucide.Check else Lucide.Copy, size = 14.dp, tint = if (done) c.success else c.text2)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (copied) R.string.copied else R.string.copy), style = Saqr.type.micro, color = c.text2)
                }
            }
            Box(Modifier.heightIn(max = maxHeight.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                Text(highlighted, style = Saqr.type.mono.copy(color = c.text1, fontSize = 12.5.sp, lineHeight = 19.sp),
                    modifier = Modifier.padding(horizontal = Sp.md, vertical = Sp.md), softWrap = false)
            }
        }
    }
}

/** Monospace output box (terminal-ish) used inside expanded tool rows. */
@Composable
fun OutputBox(text: String, command: String? = null, maxHeight: Int = 280) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.sm)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth().clip(shape).background(c.termBg).border(1.dp, c.border, shape)) {
            SelectionContainer {
                Column(Modifier.heightIn(max = maxHeight.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(Sp.md)) {
                    if (command != null) Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.accent)) { append("❯ ") }
                            withStyle(SpanStyle(color = Color(0xFFEDEEF0))) { append(command) }
                        }, style = Saqr.type.monoSmall, softWrap = false,
                    )
                    Text(text, style = Saqr.type.monoSmall.copy(color = c.termText.copy(alpha = 0.86f)), softWrap = false)
                }
            }
        }
    }
}
