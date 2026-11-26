package com.ctodrs.saqrcode

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.ui.DeveloperBannerCarousel
import com.ctodrs.saqrcode.ui.DeveloperBanners
import com.ctodrs.saqrcode.ui.DeveloperCard
import com.ctodrs.saqrcode.ui.DeveloperShare
import com.ctodrs.saqrcode.ui.UpdateCheck
import com.ctodrs.saqrcode.ui.theme.SaqrTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-night-xxhdpi")
class V0108DeveloperExtrasTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val asset get() = File("src/main/assets/developer_banners.json").readText()

    @Test fun `shipped banner file parses with valid links`() {
        val b = DeveloperBanners.parse(asset)
        assertTrue(b.size >= 2)
        assertTrue(b.all { it.titleAr.isNotBlank() && it.titleEn.isNotBlank() && (it.url == null || it.url!!.startsWith("https://")) })
    }

    @Test fun `bad banner json never crashes and bad items are skipped`() {
        assertTrue(DeveloperBanners.parse("not json").isEmpty())
        val b = DeveloperBanners.parse("""[{"id":"x"},{"title":{"en":"Hi"},"url":"http://insecure","colors":["zz"]}]""")
        assertEquals(1, b.size); assertEquals(null, b[0].url); assertEquals("Hi", b[0].title(true))
    }

    @Test fun `update check compares the newest release with the installed version`() {
        val json = """[{"tag_name":"v0.1.7-debug","html_url":"https://github.com/x/y/releases/tag/v0.1.7-debug","draft":false},{"tag_name":"v0.1.6-debug"}]"""
        assertEquals(UpdateCheck.State.UpToDate, UpdateCheck.parse(json, "0.1.7"))
        assertEquals(UpdateCheck.State.Available("0.1.7", "https://github.com/x/y/releases/tag/v0.1.7-debug"), UpdateCheck.parse(json, "0.1.6"))
        assertEquals(UpdateCheck.State.Failed, UpdateCheck.parse("[]", "0.1.6"))
        assertEquals(UpdateCheck.State.Failed, UpdateCheck.parse("oops", "0.1.6"))
    }

    @Test fun `all accounts text lists every account`() {
        val t = DeveloperShare.allAccountsText("H", listOf("A" to "https://a", "B" to "https://b"))
        assertEquals("H\n\nA: https://a\nB: https://b", t)
    }

    @Test fun `carousel shows banners and opens the link`() {
        val opened = mutableListOf<String>()
        val b = DeveloperBanners.parse(asset)
        rule.setContent { SaqrTheme("dark") { DeveloperBannerCarousel(b, autoAdvance = false) { opened += it } } }
        rule.onNodeWithText(b[0].titleAr).assertExists()
        rule.onNodeWithContentDescription(b[0].titleAr + ". " + b[0].bodyAr).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf(b[0].url), opened)
    }

    @Test fun `check for updates button reports an available version`() {
        val json = """[{"tag_name":"v0.1.9-debug","html_url":"https://github.com/x/y/releases/tag/v0.1.9-debug"}]"""
        rule.setContent { SaqrTheme("dark") { DeveloperCard(versionName = "0.1.6", fetchReleases = { json }) } }
        rule.onNodeWithContentDescription("تحقق من التحديثات").assertHeightIsAtLeast(48.dp).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithTextCount("الإصدار 0.1.9 متاح") > 0 }
        rule.onNodeWithText("الإصدار 0.1.9 متاح").assertExists()
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextCount(t: String) =
    onAllNodes(androidx.compose.ui.test.hasText(t)).fetchSemanticsNodes().size
