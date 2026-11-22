package com.ctodrs.saqrcode.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Motion
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

// ---------------------------------------------------------------- interaction

/** Gentle press-down scale used by every tappable surface. */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.96f else 1f, tween(Motion.fast, easing = Motion.standard), label = "press")
    this.scale(s).clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), enabled = enabled, onClick = onClick)
}

/** Moving highlight used while content is streaming / loading. */
fun Modifier.shimmer(active: Boolean = true): Modifier = composed {
    if (!active) return@composed this
    val c = Saqr.colors
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "x")
    this.graphicsLayer(alpha = 0.99f).drawWithContent {
        drawContent()
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, c.text1.copy(alpha = if (c.isDark) 0.55f else 0.7f), Color.Transparent),
                start = Offset(w * x - w * 0.4f, 0f), end = Offset(w * x + w * 0.4f, size.height),
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop,
        )
    }
}

// ---------------------------------------------------------------- surfaces

@Composable
fun SaqrCard(
    modifier: Modifier = Modifier,
    radius: Dp = Radius.lg,
    color: Color = Saqr.colors.surface1,
    border: Color = Saqr.colors.border,
    padding: PaddingValues = PaddingValues(Sp.lg),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .clip(shape).background(color).border(1.dp, border, shape).padding(padding),
    ) { content() }
}

/** Circular glass button used in floating bars. */
@Composable
fun GlassIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier,
                    active: Boolean = false, size: Dp = 44.dp) {
    val c = Saqr.colors
    Box(
        modifier.size(size).pressable(onClick = onClick).clip(CircleShape)
            .background(if (active) c.accentSoft else c.glass)
            .border(1.dp, if (active) c.accent.copy(alpha = 0.45f) else c.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) { SIcon(icon, size = 20.dp, tint = if (active) c.accentText else c.text1, contentDescription = contentDescription) }
}

/** Pill container with glass background for grouping. */
@Composable
fun GlassPill(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val c = Saqr.colors
    Row(
        modifier.height(44.dp)
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .clip(CircleShape).background(c.glass).border(1.dp, c.border, CircleShape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun PillIcon(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, active: Boolean = false) {
    val c = Saqr.colors
    Box(
        Modifier.size(36.dp).pressable(onClick = onClick).clip(CircleShape).background(if (active) c.accentSoft else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) { SIcon(icon, size = 20.dp, tint = if (active) c.accentText else c.text1, contentDescription = contentDescription) }
}

// ---------------------------------------------------------------- buttons

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier.heightIn(min = 48.dp).pressable(enabled, onClick).clip(shape)
            .background(
                if (enabled) Brush.verticalGradient(listOf(c.accent.copy(alpha = 1f), lerpColor(c.accent, Color(0xFFC98F2A), 0.55f)))
                else SolidColor(c.surface3),
            )
            .padding(horizontal = Sp.xl, vertical = Sp.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { SIcon(icon, size = 18.dp, tint = if (enabled) c.onAccent else c.text3); Spacer(Modifier.width(Sp.sm)) }
        Text(text, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = if (enabled) c.onAccent else c.text3, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null,
                    enabled: Boolean = true, tint: Color = Saqr.colors.text1) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier.heightIn(min = 44.dp).pressable(enabled, onClick).clip(shape).background(c.surface2)
            .border(1.dp, c.border, shape).padding(horizontal = Sp.lg, vertical = Sp.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        val t = if (enabled) tint else c.text3
        if (icon != null) { SIcon(icon, size = 17.dp, tint = t); Spacer(Modifier.width(Sp.sm)) }
        Text(text, style = Saqr.type.label, color = t, maxLines = 1)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null,
                tint: Color = Saqr.colors.accentText, enabled: Boolean = true) {
    Row(
        modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(Radius.sm)).pressable(enabled, onClick).padding(horizontal = Sp.md, vertical = Sp.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val t = if (enabled) tint else Saqr.colors.text3
        if (icon != null) { SIcon(icon, size = 17.dp, tint = t); Spacer(Modifier.width(6.dp)) }
        Text(text, style = Saqr.type.label, color = t, maxLines = 1)
    }
}

// ---------------------------------------------------------------- small pieces

@Composable
fun DiffChip(added: Int?, removed: Int?) {
    val c = Saqr.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (added != null && added > 0) Chip("+$added", c.diffAdd, c.diffAddBg)
        if (removed != null && removed > 0) Chip("−$removed", c.diffDel, c.diffDelBg)
    }
}

@Composable
fun Chip(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text, style = Saqr.type.monoMicro.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr), color = fg, maxLines = 1)
    }
}

/** Arabic-Indic → Latin digits, so numbers read the same everywhere in the UI (as in most Gulf tech products). */
fun String.latinDigits(): String = buildString(length) {
    for (ch in this@latinDigits) append(if (ch in '\u0660'..'\u0669') '0' + (ch - '\u0660') else if (ch in '\u06F0'..'\u06F9') '0' + (ch - '\u06F0') else ch)
}

/** A tag chip with optional leading dot. */
@Composable
fun Tag(text: String, color: Color = Saqr.colors.text2, dot: Boolean = false, modifier: Modifier = Modifier) {
    val c = Saqr.colors
    Row(
        modifier.clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) { Box(Modifier.size(6.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = Saqr.type.micro, color = color, maxLines = 1)
    }
}

enum class Status { RUNNING, OK, ERROR, PENDING }

/** ✓ green / ! red / spinner — drawn, so it stays crisp at any density. */
@Composable
fun StatusIcon(status: Status, size: Dp = 20.dp) {
    val c = Saqr.colors
    when (status) {
        Status.RUNNING -> Spinner(size - 2.dp, c.accent)
        Status.PENDING -> Canvas(Modifier.size(size)) {
            drawCircle(c.accent, radius = this.size.minDimension / 2f - 1.dp.toPx(), style = Stroke(1.6.dp.toPx()))
            drawCircle(c.accent, radius = 2.dp.toPx())
        }
        Status.OK -> Canvas(Modifier.size(size)) {
            drawCircle(c.success)
            val w = this.size.width
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.29f, w * 0.52f); lineTo(w * 0.44f, w * 0.66f); lineTo(w * 0.72f, w * 0.37f)
            }
            drawPath(p, if (c.isDark) Color(0xFF0B1A12) else Color.White,
                style = Stroke(w * 0.12f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        Status.ERROR -> Canvas(Modifier.size(size)) {
            drawCircle(c.danger)
            val w = this.size.width
            val ink = if (c.isDark) Color(0xFF240B0C) else Color.White
            drawLine(ink, Offset(w / 2, w * 0.27f), Offset(w / 2, w * 0.56f), w * 0.12f, StrokeCap.Round)
            drawCircle(ink, w * 0.07f, Offset(w / 2, w * 0.72f))
        }
    }
}

@Composable
fun Spinner(size: Dp = 18.dp, color: Color = Saqr.colors.accent, stroke: Dp = 2.dp) {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
    Canvas(Modifier.size(size)) {
        drawCircle(color.copy(alpha = 0.18f), style = Stroke(stroke.toPx()))
        drawArc(color, a, 100f, false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
    }
}

/** Thin progress line with rounded ends. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color = Saqr.colors.accent, track: Color = Saqr.colors.surface3) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(Motion.slow, easing = Motion.emphasized), label = "p")
    Canvas(modifier.height(3.dp)) {
        val y = size.height / 2
        drawLine(track, Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
        if (f > 0f) {
            val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
            val start = if (rtl) size.width else 0f
            val end = if (rtl) size.width * (1 - f) else size.width * f
            drawLine(color, Offset(start, y), Offset(end, y), size.height, StrokeCap.Round)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = Sp.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = Saqr.colors.text2, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun SaqrSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Saqr.colors
    Switch(
        checked, onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent, checkedTrackColor = c.accent, checkedBorderColor = c.accent,
            uncheckedThumbColor = c.text3, uncheckedTrackColor = c.surface3, uncheckedBorderColor = c.borderStrong,
        ),
    )
}

/** Styled single/multi-line field matching the design system (no M3 outline). */
@Composable
fun SaqrField(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: String? = null, placeholder: String? = null, singleLine: Boolean = true,
    mono: Boolean = false, visual: VisualTransformation = VisualTransformation.None,
    keyboard: KeyboardOptions = KeyboardOptions.Default, trailing: (@Composable () -> Unit)? = null,
    supporting: String? = null, isError: Boolean = false,
) {
    val c = Saqr.colors
    val style: TextStyle = (if (mono) Saqr.type.mono else Saqr.type.body).copy(color = c.text1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) Text(label, style = Saqr.type.caption.copy(fontWeight = FontWeight.Medium), color = c.text2)
        val shape = RoundedCornerShape(Radius.sm)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(shape).background(c.surface3.copy(alpha = if (c.isDark) 0.6f else 0.7f))
                .border(1.dp, if (isError) c.danger else c.border, shape).padding(horizontal = Sp.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value, onChange, Modifier.weight(1f).padding(vertical = Sp.md), textStyle = style, singleLine = singleLine,
                visualTransformation = visual, keyboardOptions = keyboard, cursorBrush = SolidColor(c.accent),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder != null)
                            Text(placeholder, style = (if (placeholder.any { it in '\u0600'..'\u06FF' }) Saqr.type.bodySmall else style).copy(color = c.text3),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
        if (supporting != null) Text(supporting, style = Saqr.type.caption, color = if (isError) c.danger else c.text3)
    }
}

/** Segmented control (e.g. theme / language). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Saqr.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface3.copy(alpha = 0.6f))
            .border(1.dp, c.border, RoundedCornerShape(Radius.sm)).padding(3.dp),
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp).clip(RoundedCornerShape(Radius.sm - 3.dp))
                    .background(if (sel) c.surface1 else Color.Transparent)
                    .then(if (sel) Modifier.border(1.dp, c.borderStrong, RoundedCornerShape(Radius.sm - 3.dp)) else Modifier)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, style = Saqr.type.label, color = if (sel) c.text1 else c.text2, maxLines = 1) }
        }
    }
}

/** Settings/list row with leading icon tile. */
@Composable
fun ListRow(
    title: String, subtitle: String? = null, icon: ImageVector? = null, leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, iconTint: Color = Saqr.colors.accentText,
) {
    val c = Saqr.colors
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Sp.lg, vertical = Sp.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> leading()
            icon != null -> Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(iconTint.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                SIcon(icon, size = 18.dp, tint = iconTint)
            }
        }
        if (leading != null || icon != null) Spacer(Modifier.width(Sp.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = Saqr.type.title3.copy(fontSize = Saqr.type.body.fontSize), color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Saqr.type.caption, color = c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) { Spacer(Modifier.width(Sp.sm)); trailing() }
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 62.dp).height(1.dp).background(Saqr.colors.border))
}

// ---------------------------------------------------------------- brand

/** The falcon mark on its graphite tile with a soft gold glow. */
@Composable
fun FalconMark(size: Dp = 40.dp, glow: Boolean = false) {
    val c = Saqr.colors
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (glow) Canvas(Modifier.size(size * 2.2f)) {
            drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.22f), Color.Transparent)))
        }
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.28f))
                .background(Brush.linearGradient(listOf(Color(0xFF1C2027), Color(0xFF0C0E11))))
                .border(1.dp, Color(0x33E9B44C), RoundedCornerShape(size * 0.28f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(size * 1.62f))
        }
    }
}

/** Empty / zero state: mark, title, body, optional action. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector? = null,
               action: (@Composable () -> Unit)? = null) {
    val c = Saqr.colors
    Column(modifier.widthIn(max = 420.dp).padding(Sp.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon == null) FalconMark(64.dp, glow = true)
        else Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(c.surface2).border(1.dp, c.border, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            SIcon(icon, size = 28.dp, tint = c.accentText)
        }
        Spacer(Modifier.height(Sp.xl))
        Text(title, style = Saqr.type.title2, color = c.text1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(Sp.sm))
        Text(body, style = Saqr.type.bodySmall, color = c.text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(Sp.xl)); action() }
    }
}

@Composable
fun CloseIcon(onClick: () -> Unit) = PillIcon(Lucide.X, null, onClick)

fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
