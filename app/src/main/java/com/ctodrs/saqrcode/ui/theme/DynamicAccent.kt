package com.ctodrs.saqrcode.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * v0.12.1 «ريش مكمّلة» · الديناميكي · Material You bridge (Android 12+).
 *
 * The Saqr identity (graphite backgrounds, neutral text tiers, state colors) stays fixed; only
 * the accent *family* is re-tinted from the system wallpaper seed. This keeps contrast and the
 * ≤10% gold discipline intact while honoring the user's Material You palette:
 *
 * - accent       → the system seed
 * - accentText   → a step lighter on dark themes / darker on light ones (readable on bg)
 * - accentSoft   → the same tinted-background alpha the built-in themes use (0x1F / 0x24)
 * - onAccent     → ColorFx.onColor: near-black on light seeds, warm-white on dark ones
 *
 * Pure functions over [SaqrColors] so the derivation is unit-testable; the system seed comes
 * from `dynamicDark/LightColorScheme(...).primary` in MainActivity (API 31+, gold fallback).
 */
object DynamicAccent {

    /** Returns [base] with its accent family re-tinted from [accent] (opaque ARGB). */
    fun withAccent(base: SaqrColors, accent: Int): SaqrColors = base.copy(
        accent = Color(accent),
        accentText = Color(if (base.isDark) ColorFx.lighten(accent, 0.12f) else ColorFx.darken(accent, 0.42f)),
        accentSoft = Color(ColorFx.alpha(accent, if (base.isDark) 0x1F else 0x24)),
        onAccent = Color(ColorFx.onColor(accent)),
    )
}
