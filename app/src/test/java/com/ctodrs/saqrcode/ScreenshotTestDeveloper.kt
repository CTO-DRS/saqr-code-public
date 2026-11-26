package com.ctodrs.saqrcode

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.ui.DeveloperCard
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

/** Opt-in visual proof of the developer card (dark/light, AR/EN). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ScreenshotTestDeveloper {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("saqr.screens.dir") ?: "build/screens")
    @Before fun gate() { assumeTrue(System.getProperty("saqr.screens") == "1"); outDir.mkdirs() }

    private fun shot(name: String, theme: String) {
        rule.setContent {
            SaqrTheme(theme) {
                Column(Modifier.fillMaxSize().background(Saqr.colors.surface1).padding(top = 200.dp, start = 16.dp, end = 16.dp)) {
                    DeveloperCard(versionName = "0.1.5")
                }
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(500)
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sheetShot(name: String) {
        rule.setContent {
            SaqrTheme("dark") {
                Column(Modifier.fillMaxSize().background(Saqr.colors.surface1).padding(top = 360.dp)) {
                    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xF20B0C24), androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).padding(top = 20.dp)) {
                        com.ctodrs.saqrcode.ui.DeveloperLinkSheetContent(com.ctodrs.saqrcode.ui.DeveloperLinks.all[1], {}, {})
                    }
                }
            }
        }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(300)
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Config(qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-night-xxhdpi") @Test fun sheetAr() = sheetShot("dev-sheet-ar")
    @Config(qualifiers = "en-rUS-ldltr-w411dp-h891dp-night-xxhdpi") @Test fun sheetEn() = sheetShot("dev-sheet-en")
    @Config(qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-night-xxhdpi") @Test fun ar() = shot("dev-ar", "dark")
    @Config(qualifiers = "en-rUS-ldltr-w411dp-h891dp-night-xxhdpi") @Test fun en() = shot("dev-en", "dark")
    @Config(qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-notnight-xxhdpi") @Test fun arLight() = shot("dev-ar-light", "light")
}
