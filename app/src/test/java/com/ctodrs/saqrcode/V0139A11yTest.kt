package com.ctodrs.saqrcode

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave E accessibility pass on the newer screens: source-level checks, as in V0123IconA11yScanTest. */
class V0139A11yTest {
    private fun src(name: String): String {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        return root.walkTopDown().first { it.name == name }.readText()
    }

    @Test fun `status and error messages in settings are live regions`() {
        val s = src("SettingsScreen.kt")
        assertTrue(s.contains("LiveRegionMode.Polite"))
        for (marker in listOf("chatMsg?.let { Text(", "R.string.sync_pushed, it)", "R.string.settings_search_none")) {
            val i = s.indexOf(marker)
            assertTrue("missing marker $marker", i >= 0)
            val window = s.substring(i, minOf(s.length, i + 260))
            assertTrue("no polite() near $marker", window.contains("polite()"))
        }
    }

    @Test fun `segmented options announce as radio buttons with a selected state and a 48dp target`() {
        val c = src("Components.kt")
        assertTrue(c.contains("Role.RadioButton"))
        assertTrue(c.contains("this.selected = sel"))
        assertTrue(!c.contains("heightIn(min = 36.dp).clip(RoundedCornerShape(Radius.sm - 3.dp))"))
    }

    @Test fun `text fields keep a 48dp minimum height`() {
        assertTrue(src("Components.kt").contains("heightIn(min = 48.dp).clip(shape)"))
    }
}
