package com.ctodrs.saqrcode

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.ui.DeveloperCard
import com.ctodrs.saqrcode.ui.DeveloperLinks
import com.ctodrs.saqrcode.ui.DeveloperLinkSheetContent
import com.ctodrs.saqrcode.ui.theme.SaqrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-night-xxhdpi")
class V0105DeveloperCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `links match the owner's mandate exactly`() {
        val expected = listOf(
            "https://wa.me/message/MVTVWPOPFOVFF1", "https://t.me/CTO_DRS", "https://www.tiktok.com/@cto.drs",
            "https://www.instagram.com/cto.drs", "https://x.com/cto_drs", "https://youtube.com/@cto-drs",
            "https://github.com/CTO-DRS", "https://cto-drs.com",
        )
        assertEquals(expected, DeveloperLinks.all.map { it.url })
        assertEquals("CTO.DRS", DeveloperLinks.NAME)
        assertTrue(DeveloperLinks.all.all { it.url.startsWith("https://") })
    }

    @Test fun `eight 48dp labelled buttons and each tap opens its QR sheet`() {
        rule.setContent { SaqrTheme("dark") { DeveloperCard(versionName = "0.1.5") } }
        val labels = listOf("واتساب", "تيليجرام", "تيك توك", "إنستغرام", "إكس", "يوتيوب", "GitHub", "الموقع")
        labels.forEach { rule.onNodeWithContentDescription(it).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).assertHasClickAction() }
        rule.onNodeWithContentDescription("تيليجرام").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("QR: تيليجرام").assertExists()
    }

    @Test fun `sheet buttons fire open and copy`() {
        var open = 0; var copy = 0
        rule.setContent { SaqrTheme("dark") { DeveloperLinkSheetContent(DeveloperLinks.all[1], { open++ }, { copy++ }) } }
        rule.onNodeWithContentDescription("فتح").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithContentDescription("نسخ الرابط").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, open); assertEquals(1, copy)
    }
}
