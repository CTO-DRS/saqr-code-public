package com.ctodrs.saqrcode

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ctodrs.saqrcode.agent.PriceTable
import com.ctodrs.saqrcode.agent.RunStats
import com.ctodrs.saqrcode.agent.Usage
import com.ctodrs.saqrcode.ui.chat.UsageSheetContent
import com.ctodrs.saqrcode.ui.chat.UsageSummary
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.SaqrTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** v0.1.15 visual proof (opt-in): the usage sheet with the live "This task" row and the daily budget bar, AR (RTL) and EN. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ScreenshotTestUsage {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("saqr.screens.dir") ?: "build/screens")
    @Before fun gate() { assumeTrue(System.getProperty("saqr.screens") == "1"); outDir.mkdirs() }

    private val prices = PriceTable.merge(emptyList())

    private fun summary() = UsageSummary(
        session = mapOf("gpt-4o-mini" to Usage(42100, 8300)),
        today = mapOf("gpt-4o-mini" to Usage(120000, 26000), "claude-sonnet-4-5" to Usage(30500, 9200)),
        month = mapOf("gpt-4o-mini" to Usage(2_400_000, 610_000)),
        prices = prices,
        budget = 10.0,
        dailyBudget = 2.0,
    )

    private fun runStats() = RunStats(activeMs = 74_000, steps = 9, files = 4, commands = 3, tokens = 12_300, estimatedTokens = false, cost = 0.0042)

    private fun save(name: String) {
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(700)
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(name: String) {
        rule.setContent {
            SaqrTheme("dark") {
                Box(Modifier.fillMaxSize().background(Saqr.colors.bg).padding(24.dp)) {
                    UsageSheetContent(summary(), runStats = { runStats() }, running = false)
                }
            }
        }
        save(name)
    }

    @Config(qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-night-xxhdpi")
    @Test fun usageAr() = shot("usage-daily-ar")

    @Config(qualifiers = "en-rUS-ldltr-w411dp-h891dp-night-xxhdpi")
    @Test fun usageEn() = shot("usage-daily-en")
}
