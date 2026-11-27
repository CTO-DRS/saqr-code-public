package com.ctodrs.saqrcode

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ctodrs.saqrcode.ui.theme.DarkSaqr
import com.ctodrs.saqrcode.ui.theme.ImagePalette
import com.ctodrs.saqrcode.ui.theme.LightSaqr
import com.ctodrs.saqrcode.ui.theme.NightSaqr
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.19: built-in themes keep WCAG AA contrast for reading text, links and button labels. */
class V0122ThemeContrastTest {
    private fun ratio(a: Color, b: Color) = ImagePalette.contrast(a.toArgb(), b.toArgb())
    private val themes = listOf("dark" to DarkSaqr, "light" to LightSaqr, "night" to NightSaqr)

    @Test fun `primary and secondary text meet 4_5 on every surface`() {
        val bad = ArrayList<String>()
        for ((n, t) in themes) for ((sn, s) in listOf("bg" to t.bg, "s1" to t.surface1, "s2" to t.surface2, "s3" to t.surface3))
            for ((tn, fg) in listOf("text1" to t.text1, "text2" to t.text2)) {
                val r = ratio(fg, s); if (r < 4.5f) bad += "$n $tn on $sn = $r"
            }
        assertTrue(bad.toString(), bad.isEmpty())
    }

    @Test fun `accent text and button labels meet 4_5`() {
        val bad = ArrayList<String>()
        for ((n, t) in themes) {
            ratio(t.accentText, t.bg).let { if (it < 4.5f) bad += "$n accentText/bg = $it" }
            ratio(t.accentText, t.surface1).let { if (it < 4.5f) bad += "$n accentText/s1 = $it" }
            ratio(t.onAccent, t.accent).let { if (it < 4.5f) bad += "$n onAccent/accent = $it" }
        }
        assertTrue(bad.toString(), bad.isEmpty())
    }

    @Test fun `status colors and tertiary text meet 3 on the background`() {
        val bad = ArrayList<String>()
        for ((n, t) in themes) for ((cn, c) in listOf("text3" to t.text3, "danger" to t.danger, "success" to t.success, "warning" to t.warning, "info" to t.info)) {
            val r = ratio(c, t.bg); if (r < 3f) bad += "$n $cn/bg = $r"
        }
        assertTrue(bad.toString(), bad.isEmpty())
    }
}
