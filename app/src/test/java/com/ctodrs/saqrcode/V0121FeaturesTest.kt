package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.ondevice.FieldKit
import com.ctodrs.saqrcode.ui.theme.ColorFx
import com.ctodrs.saqrcode.ui.theme.DarkSaqr
import com.ctodrs.saqrcode.ui.theme.DynamicAccent
import com.ctodrs.saqrcode.ui.theme.HapticKind
import com.ctodrs.saqrcode.ui.theme.HapticTokens
import com.ctodrs.saqrcode.ui.theme.ImagePalette
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v0.12.1 «ريش مكمّلة» · image→theme, Material You overlay, semantic haptics, field kit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class V0121FeaturesTest {

    // ------------------------------------------------------------ ImagePalette

    /** 60% near-black, 30% red, 10% warm-white. */
    private fun darkImage(): IntArray = IntArray(1200) { i ->
        when {
            i < 720 -> 0xFF050505.toInt()
            i < 1080 -> 0xFFE02020.toInt()
            else -> 0xFFF5F5F5.toInt()
        }
    }

    /** 60% warm-white, 30% blue, 10% near-black. */
    private fun lightImage(): IntArray = IntArray(1200) { i ->
        when {
            i < 720 -> 0xFFFCFAF5.toInt()
            i < 1080 -> 0xFF2050E0.toInt()
            else -> 0xFF0A0A0A.toInt()
        }
    }

    @Test
    fun `image palette picks a dark background, saturated accent and readable text`() {
        val pc = ImagePalette.extract(darkImage(), dark = true)
        assertNotNull(pc)
        // Background: the dominant dark side of the image.
        assertTrue("bg should be near-black, got ${ColorFx.hex(pc!!.bg)}", ColorFx.luminance(pc.bg) < 0.1f)
        // Accent: the red bucket (r dominates g/b).
        assertTrue("accent should be red, got ${ColorFx.hex(pc.accent)}",
            ((pc.accent shr 16) and 0xFF) > ((pc.accent shr 8) and 0xFF) + 60 &&
            ((pc.accent shr 16) and 0xFF) > (pc.accent and 0xFF) + 60)
        // Text stays WCAG-readable on the background.
        assertTrue("text contrast must be >= 4.5", ImagePalette.contrast(pc.text1, pc.bg) >= 4.5f)
        assertTrue(ImagePalette.prefersDark(darkImage()))
    }

    @Test
    fun `image palette handles light images mirrored`() {
        val pc = ImagePalette.extract(lightImage(), dark = false)
        assertNotNull(pc)
        assertTrue("bg should be light, got ${ColorFx.hex(pc!!.bg)}", ColorFx.luminance(pc.bg) > 0.85f)
        assertTrue("accent should be blue, got ${ColorFx.hex(pc.accent)}",
            ((pc.accent and 0xFF)) > ((pc.accent shr 16) and 0xFF) + 60 &&
            (pc.accent and 0xFF) > ((pc.accent shr 8) and 0xFF) + 60)
        assertTrue(ImagePalette.contrast(pc.text1, pc.bg) >= 4.5f)
        // prefersDark flips with the image.
        assertTrue(!ImagePalette.prefersDark(lightImage()))
    }

    @Test
    fun `image palette rejects empty or translucent-only pixels`() {
        assertNull(ImagePalette.extract(IntArray(0), dark = true))
        assertNull(ImagePalette.extract(IntArray(100) { 0x80E02020.toInt() }, dark = true))
    }

    @Test
    fun `packFromImage yields a valid, importable theme pack`() {
        val p = ImagePalette.packFromImage(darkImage(), "صورة", "From image", "test")!!
        assertTrue(p.dark)
        assertTrue(p.id.startsWith("img-"))
        val back = com.ctodrs.saqrcode.ui.theme.ThemePack.fromJson(p.toJson())
        assertEquals(p.colors.accent, back!!.colors.accent)
        assertEquals(p.dark, back.dark)
    }

    // ------------------------------------------------------------ DynamicAccent

    @Test
    fun `dynamic accent keeps Saqr neutrals and re-tints only the accent family`() {
        val dyn = DynamicAccent.withAccent(DarkSaqr, 0xFF43B0D5.toInt())
        // Identity surfaces untouched.
        assertEquals(DarkSaqr.bg, dyn.bg)
        assertEquals(DarkSaqr.surface1, dyn.surface1)
        assertEquals(DarkSaqr.text1, dyn.text1)
        assertEquals(DarkSaqr.success, dyn.success)
        // Accent family re-tinted.
        assertEquals(0xFF43B0D5.toInt(), dyn.accent.toArgb())
        assertTrue(dyn.accentText != dyn.accent)
        assertEquals(0x1F43B0D5.toInt(), dyn.accentSoft.toArgb())
        // onAccent is readable against the (light) blue accent.
        assertEquals(ColorFx.onColor(0xFF43B0D5.toInt()), dyn.onAccent.toArgb())
    }

    @Test
    fun `dynamic accent on light themes darkens the text tier`() {
        val base = com.ctodrs.saqrcode.ui.theme.LightSaqr
        val dyn = DynamicAccent.withAccent(base, 0xFFE9B44C.toInt())
        assertEquals(0x24E9B44C.toInt(), dyn.accentSoft.toArgb())
        assertTrue(ColorFx.luminance(dyn.accentText.toArgb()) < ColorFx.luminance(0xFFE9B44C.toInt()))
    }

    // ------------------------------------------------------------ HapticTokens

    @Test
    fun `haptic tokens map to the right constants with API fallbacks`() {
        assertEquals(HapticTokens.CLOCK_TICK, HapticTokens.constant(HapticKind.TICK, 28))
        assertEquals(HapticTokens.VIRTUAL_KEY, HapticTokens.constant(HapticKind.POP, 26))
        assertEquals(HapticTokens.LONG_PRESS, HapticTokens.constant(HapticKind.HEAVY, 35))
        // CONFIRM/REJECT exist on 30+, graceful fallback before that.
        assertEquals(HapticTokens.CONFIRM, HapticTokens.constant(HapticKind.CONFIRM, 31))
        assertEquals(HapticTokens.KEYBOARD_TAP, HapticTokens.constant(HapticKind.CONFIRM, 28))
        assertEquals(HapticTokens.REJECT, HapticTokens.constant(HapticKind.REJECT, 30))
        assertEquals(HapticTokens.LONG_PRESS, HapticTokens.constant(HapticKind.REJECT, 29))
    }

    // ------------------------------------------------------------ FieldKit

    private val rec = FieldKit.Record(
        "build-debug", "gradle", 754_000, "Pixel 8", "Tensor", 8192, 87, 6, 1_700_000_000_000,
    )

    @Test
    fun `socFamily recognises the major vendors`() {
        assertEquals("Snapdragon", FieldKit.socFamily("qcom", "SM8650", "lahaina"))
        assertEquals("Snapdragon", FieldKit.socFamily(null, "snapdragon 7 gen 1", null))
        assertEquals("MediaTek", FieldKit.socFamily("mt6789", null, null))
        assertEquals("MediaTek", FieldKit.socFamily(null, "Dimensity 9200", "TECNO"))
        assertEquals("Exynos", FieldKit.socFamily("exynos2100", null, "universal2100"))
        assertEquals("Tensor", FieldKit.socFamily("tensor", "gs201", null))
        assertEquals("Unknown", FieldKit.socFamily("", null, ""))
        assertEquals("Other", FieldKit.socFamily("xyz-board", null, null))
    }

    @Test
    fun `formatMs renders every magnitude`() {
        assertEquals("800ms", FieldKit.formatMs(800))
        assertEquals("45s", FieldKit.formatMs(45_000))
        assertEquals("12m 34s", FieldKit.formatMs(754_000))
        assertEquals("1h 05m", FieldKit.formatMs(3_900_000))
        assertEquals("?", FieldKit.formatMs(-1))
    }

    @Test
    fun `markdown table is README-ready`() {
        val md = FieldKit.markdown(listOf(rec))
        assertTrue(md.startsWith("| Device | SoC | RAM | Step | Time | APK | Run |"))
        assertTrue(md.contains("| Pixel 8 | Tensor |"))
        assertTrue(md.contains("| gradle | 12m 34s | 6 MB | build-debug |"))
    }

    @Test
    fun `csv escapes quotes and keeps numeric columns`() {
        val csv = FieldKit.csv(listOf(rec.copy(device = "Galaxy \"S24\", 128GB")))
        assertTrue(csv.startsWith("device,soc,ram_mb,run,step,time_ms,apk_mb,battery_pct,at"))
        assertTrue(csv.contains("\"Galaxy \"\"S24\"\", 128GB\""))
        assertTrue(csv.contains(",754000,6,87,1700000000000"))
    }

    @Test
    fun `encode decode round trips records and drops junk`() {
        val back = FieldKit.decode(FieldKit.encode(listOf(rec)))
        assertEquals(1, back.size)
        assertEquals(rec, back[0])
        assertEquals(emptyList<FieldKit.Record>(), FieldKit.decode(null))
        assertEquals(emptyList<FieldKit.Record>(), FieldKit.decode("not json"))
        val partial = org.json.JSONArray()
            .put(org.json.JSONObject().put("run", "bootstrap").put("step", "rootfs"))
            .put(org.json.JSONObject().put("run", "").put("step", "jdk17"))
            .toString()
        assertEquals(1, FieldKit.decode(partial).size)
    }
}
