package com.ctodrs.saqrcode.ui.theme

/**
 * v0.12.1 «ريش مكمّلة» · الثيم من صورة · Image → theme.
 *
 * Builds a full [PackColors] from an image's pixels, fully offline: the pixels are quantized
 * into a small RGB histogram, the most frequent buckets define the background/text neutrals,
 * and the most saturated *distinct* bucket becomes the accent. Deterministic (stable sorting,
 * no randomness) so the same image always yields the same theme.
 *
 * Pure Kotlin (IntArrays + [ColorFx]) so it is unit-testable on the JVM; the Bitmap
 * down-sampling lives in SettingsScreen (`getPixels` on a scaled bitmap).
 */
object ImagePalette {

    /** Buckets kept as accent candidates after counting (a small, stable shortlist). */
    private const val TOP_BUCKETS = 24
    /** Minimum saturation (0..1) for a bucket to qualify as an accent. */
    private const val MIN_SAT = 0.30f
    /** Minimum luminance distance from the background for a *distinct* accent. */
    private const val MIN_LUM_DIST = 0.12f
    /** WCAG contrast required between text1 and the background; below it we snap to the theme default. */
    private const val MIN_TEXT_CONTRAST = 4.5f
    /** Saqr gold — the accent fallback when the image carries no distinct saturated hue. */
    private val GOLD = 0xFFE9B44C.toInt()

    /**
     * The theme pack derived from the pixel array, or null for empty/translucent-only images.
     * [dark] picks which side of the image becomes the background.
     */
    fun extract(pixels: IntArray, dark: Boolean): PackColors? {
        val counts = HashMap<Int, Int>() // 4-bit-per-channel key → pixel count
        var opaque = 0
        for (p in pixels) {
            if ((p ushr 24) < 200) continue
            opaque++
            // 12-bit key: R's top nibble at 8..11, G's at 4..7, B's at 0..3.
            val key = (((p shr 16) and 0xF0) shl 4) or ((p shr 8) and 0xF0) or ((p and 0xF0) shr 4)
            counts[key] = (counts[key] ?: 0) + 1
        }
        if (opaque < 16) return null

        // Most frequent buckets first (stable: ties break by key so the result is deterministic).
        val top = counts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .take(TOP_BUCKETS)
            .map { argb(it.key) to it.value }
            .ifEmpty { return null }

        val byLum = top.sortedBy { ColorFx.luminance(it.first) }
        val bg = if (dark) byLum.first().first else byLum.last().first
        val lumBg = relLum(bg)

        // Accent: the most saturated candidate that is clearly distinct from the background.
        val saturated = top.map { it.first }
            .filter { saturation(it) >= MIN_SAT && relLum(it) in 0.06f..0.94f && it != bg }
        val distinct = saturated.firstOrNull { kotlin.math.abs(relLum(it) - lumBg) >= MIN_LUM_DIST }
        val accent = distinct
            ?: saturated.firstOrNull { it != bg }
            ?: GOLD

        // Neutrals: the far side of the image, with a readable-texture guarantee.
        val rawText = if (dark) byLum.last().first else byLum.first().first
        val text1 = if (contrast(rawText, bg) >= MIN_TEXT_CONTRAST) rawText
            else if (dark) 0xFFEDEEF0.toInt() else 0xFF16181B.toInt()

        val s1 = if (dark) ColorFx.lighten(bg, 0.06f) else ColorFx.darken(bg, 0.05f)
        val s2 = if (dark) ColorFx.lighten(bg, 0.12f) else ColorFx.darken(bg, 0.10f)
        val t2 = ColorFx.mix(text1, bg, 0.45f)
        return PackColors(bg, s1, s2, text1, t2, accent)
    }

    /** True when the image is, on average, dark — used to pick the background side automatically. */
    fun prefersDark(pixels: IntArray): Boolean {
        var sum = 0f
        var n = 0
        val step = maxOf(1, pixels.size / 8192)
        for (i in pixels.indices step step) {
            sum += ColorFx.luminance(pixels[i] or 0xFF000000.toInt())
            n++
        }
        return n == 0 || sum / n < 0.5f
    }

    /** Convenience: a full named pack from an image, with its own stable id. */
    fun packFromImage(pixels: IntArray, name: String, nameEn: String, mood: String): ThemePack? {
        val dark = prefersDark(pixels)
        val c = extract(pixels, dark) ?: return null
        val id = "img-" + (c.accent and 0xFFFFFF).toString(16) + "-" + (c.bg and 0xFFFFFF).toString(16)
        return ThemePack(id, name.take(40), nameEn.take(40), mood.take(120), dark, c)
    }

    /** Re-centers a 4-bit key into full-range ARGB (x4 → *17 covers 0..255). */
    private fun argb(key: Int): Int {
        val r = ((key shr 8) and 0xF) * 17
        val g = ((key shr 4) and 0xF) * 17
        val b = (key and 0xF) * 17
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Max-channel spread, the cheap saturation proxy used for accent picking. */
    private fun saturation(argb: Int): Float {
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        if (mx == 0) return 0f
        return (mx - mn) / mx.toFloat()
    }

    /** WCAG relative luminance (gamma-corrected; [ColorFx.luminance] stays sRGB for mix math). */
    private fun relLum(argb: Int): Float {
        fun lin(c: Int): Float {
            val v = c / 255f
            return if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055) / 1.055).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * lin((argb shr 16) and 0xFF) + 0.7152f * lin((argb shr 8) and 0xFF) + 0.0722f * lin(argb and 0xFF)
    }

    /** WCAG contrast ratio (1..21) between two opaque colors. */
    fun contrast(a: Int, b: Int): Float {
        val la = relLum(a); val lb = relLum(b)
        val hi = maxOf(la, lb); val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }
}
