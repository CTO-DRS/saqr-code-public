package com.ctodrs.saqrcode.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * v0.12 · Derives a complete [SaqrColors] palette from a six-color [ThemePack].
 *
 * The pack owns the identity (background, surfaces, text tiers, accent); everything else is
 * derived: borders and surfaces blend toward the text/background anchors, the accent gets its
 * text/soft/on variants, and the semantic states (success, danger, warning, info, diff colors,
 * terminal text) are inherited from the matching dark or light baseline so they stay readable.
 */
fun ThemePack.toSaqrColors(): SaqrColors {
    val p = colors
    val base = if (dark) DarkSaqr else LightSaqr
    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()

    val surface3 = ColorFx.mix(p.surface2, p.text1, 0.06f)
    val text3 = ColorFx.mix(p.text2, p.bg, 0.45f)
    val border = ColorFx.mix(p.bg, p.text1, 0.14f)
    val borderStrong = ColorFx.mix(p.bg, p.text1, 0.24f)
    val accentText = if (dark) ColorFx.lighten(p.accent, 0.10f) else ColorFx.darken(p.accent, 0.42f)
    val codeBg = ColorFx.mix(p.bg, if (dark) black else white, 0.5f)
    val codeHeader = ColorFx.mix(codeBg, p.surface2, 0.6f)
    val termBg = ColorFx.mix(codeBg, black, 0.5f)
    val glass = ColorFx.alpha(ColorFx.mix(p.surface2, p.bg, 0.4f), 0xE6)

    return SaqrColors(
        isDark = dark,
        bg = Color(p.bg),
        surface1 = Color(p.surface1),
        surface2 = Color(p.surface2),
        surface3 = Color(surface3),
        border = Color(border),
        borderStrong = Color(borderStrong),
        text1 = Color(p.text1),
        text2 = Color(p.text2),
        text3 = Color(text3),
        accent = Color(p.accent),
        accentText = Color(accentText),
        accentSoft = Color(ColorFx.alpha(p.accent, 0x1F)),
        onAccent = Color(ColorFx.onColor(p.accent)),
        success = base.success,
        danger = base.danger,
        warning = base.warning,
        warningBg = base.warningBg,
        warningBorder = base.warningBorder,
        info = base.info,
        diffAdd = base.diffAdd,
        diffAddBg = base.diffAddBg,
        diffDel = base.diffDel,
        diffDelBg = base.diffDelBg,
        codeBg = Color(codeBg),
        codeHeader = Color(codeHeader),
        termBg = Color(termBg),
        termText = base.termText,
        glass = Color(glass),
        scrim = Color(p.bg),
    )
}
