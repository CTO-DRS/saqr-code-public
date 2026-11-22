package com.ctodrs.saqrcode.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctodrs.saqrcode.ui.theme.JetBrainsMono
import com.ctodrs.saqrcode.ui.theme.Saqr

/** Builds a 24×24 Lucide-style stroked icon (2px, round caps/joins). */
internal fun stroked(name: String, autoMirror: Boolean, vararg paths: String): ImageVector {
    val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f, autoMirror = autoMirror)
    paths.forEach { d ->
        b.addPath(
            pathData = addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

/** Thin wrapper: icons default to 20dp and the current secondary text colour. */
@Composable
fun SIcon(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = Saqr.colors.text2, contentDescription: String? = null) {
    Icon(icon, contentDescription, modifier.size(size), tint = tint)
}

// ---------------------------------------------------------------- file types

data class FileType(val label: String, val color: Color)

object FileTypes {
    private val map: Map<String, FileType> = buildMap {
        fun put(exts: String, label: String, c: Long) = exts.split(' ').forEach { put(it, FileType(label, Color(c))) }
        put("kt kts", "KT", 0xFFA97BFF)
        put("java", "JV", 0xFFF0705A)
        put("py", "PY", 0xFF4B9CE2)
        put("js mjs cjs", "JS", 0xFFE8C547)
        put("jsx", "JX", 0xFF61DAFB)
        put("ts", "TS", 0xFF3D8EDB)
        put("tsx", "TX", 0xFF3D8EDB)
        put("html htm", "<>", 0xFFEF7B4D)
        put("css scss", "#", 0xFF5B8DEF)
        put("json", "{}", 0xFFD9B44A)
        put("md markdown", "M↓", 0xFF8FA3B8)
        put("txt", "TX", 0xFF8E959E)
        put("sh bash zsh", "$", 0xFF6CC56C)
        put("xml svg", "<>", 0xFFE0904A)
        put("gradle", "GR", 0xFF4DB6AC)
        put("dart", "DT", 0xFF40C4FF)
        put("go", "GO", 0xFF4FC3D9)
        put("rs", "RS", 0xFFE0805A)
        put("c h", "C", 0xFF7C9CD6)
        put("cpp cc hpp", "C+", 0xFF6A8FE0)
        put("swift", "SW", 0xFFF2774B)
        put("yml yaml toml", "YM", 0xFFCB7FD8)
        put("png jpg jpeg gif webp", "IMG", 0xFF57C7A0)
        put("lock", "LK", 0xFF8E959E)
    }
    val default = FileType("··", Color(0xFF8E959E))
    fun of(name: String): FileType {
        val lower = name.lowercase()
        if (lower == "dockerfile") return FileType("DK", Color(0xFF4A9EEB))
        if (lower == "agents.md") return FileType("AI", Color(0xFFE9B44C))
        if (lower.startsWith(".git")) return FileType("GT", Color(0xFFF05033))
        return map[lower.substringAfterLast('.', "")] ?: default
    }
}

/**
 * A colored file-type glyph: a document with a folded corner, tinted in the language colour,
 * carrying a tiny monospace label. Original design for Saqr Code.
 */
@Composable
fun FileTypeIcon(name: String, size: Dp = 20.dp) {
    val t = FileTypes.of(name)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width; val h = this.size.height
            val left = w * 0.12f; val right = w * 0.88f; val top = h * 0.04f; val bottom = h * 0.96f
            val fold = w * 0.26f
            val r = w * 0.12f
            val body = Path().apply {
                moveTo(left + r, top)
                lineTo(right - fold, top)
                lineTo(right, top + fold)
                lineTo(right, bottom - r)
                quadraticTo(right, bottom, right - r, bottom)
                lineTo(left + r, bottom)
                quadraticTo(left, bottom, left, bottom - r)
                lineTo(left, top + r)
                quadraticTo(left, top, left + r, top)
                close()
            }
            drawPath(body, t.color.copy(alpha = 0.16f))
            drawPath(body, t.color.copy(alpha = 0.85f), style = Stroke(width = w * 0.065f, join = StrokeJoin.Round))
            val corner = Path().apply {
                moveTo(right - fold, top); lineTo(right - fold, top + fold - r * 0.4f)
                quadraticTo(right - fold, top + fold, right - fold + r * 0.4f, top + fold); lineTo(right, top + fold)
            }
            drawPath(corner, t.color.copy(alpha = 0.85f), style = Stroke(width = w * 0.065f, join = StrokeJoin.Round, cap = StrokeCap.Round))
        }
        Text(
            t.label, color = t.color, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
            fontSize = (size.value * (if (t.label.length >= 3) 0.26f else 0.32f)).sp, lineHeight = (size.value * 0.34f).sp,
            textAlign = TextAlign.Center, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Folder glyph in the accent colour. */
@Composable
fun FolderIcon(open: Boolean, size: Dp = 20.dp) {
    val c = Saqr.colors.accentText
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val r = w * 0.1f
        val tab = Path().apply {
            moveTo(w * 0.08f, h * 0.26f)
            quadraticTo(w * 0.08f, h * 0.16f, w * 0.18f, h * 0.16f)
            lineTo(w * 0.38f, h * 0.16f); lineTo(w * 0.48f, h * 0.27f); lineTo(w * 0.82f, h * 0.27f)
            quadraticTo(w * 0.92f, h * 0.27f, w * 0.92f, h * 0.37f)
            lineTo(w * 0.92f, h * 0.74f)
            quadraticTo(w * 0.92f, h * 0.84f, w * 0.82f, h * 0.84f)
            lineTo(w * 0.18f, h * 0.84f)
            quadraticTo(w * 0.08f, h * 0.84f, w * 0.08f, h * 0.74f)
            close()
        }
        drawPath(tab, c.copy(alpha = if (open) 0.22f else 0.16f))
        drawPath(tab, c, style = Stroke(width = w * 0.075f, join = StrokeJoin.Round))
        if (open) drawRoundRect(c.copy(alpha = 0.55f), Offset(w * 0.08f, h * 0.42f), Size(w * 0.84f, h * 0.06f), CornerRadius(r))
    }
}

// ---------------------------------------------------------------- providers

/** Monogram badge for an AI provider (no third-party logos are bundled). */
@Composable
fun ProviderBadge(providerId: String, size: Dp = 32.dp) {
    val (label, color) = when (providerId) {
        "anthropic" -> "A\\" to Color(0xFFD97757)
        "openai" -> "OA" to Color(0xFF10A37F)
        "gemini" -> "G" to Color(0xFF4F8DF5)
        "openrouter" -> "OR" to Color(0xFF8B7CF6)
        "deepseek" -> "DS" to Color(0xFF4D6BFE)
        else -> "</>" to Color(0xFF8E959E)
    }
    Box(
        Modifier.size(size).background(color.copy(alpha = 0.16f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold,
            fontSize = (size.value * if (label.length >= 3) 0.3f else 0.36f).sp, maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
    }
}
