package com.ctodrs.saqrcode

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.Tools
import com.ctodrs.saqrcode.ui.IdeFrame
import com.ctodrs.saqrcode.ui.IdeTopBar
import com.ctodrs.saqrcode.ui.Pane
import com.ctodrs.saqrcode.ui.chat.ChatContent
import com.ctodrs.saqrcode.ui.chat.ChatUiState
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

/** Majlis visual proof (opt-in like ScreenshotTest): chat with sources row, action bar, file card and a bold link, AR (RTL) and EN. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ScreenshotTestMajlis {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("saqr.screens.dir") ?: "build/screens")
    @Before fun gate() { assumeTrue(System.getProperty("saqr.screens") == "1"); outDir.mkdirs() }

    private fun items(ar: Boolean): List<ChatItem> {
        fun tool(n: String, s: String) = ChatItem.Tool(n, s).apply { result = "ok"; ok = true; durationMs = 300 }
        return listOf(
            ChatItem.User(if (ar) "أين الحزمة" else "Where is the package?"),
            tool(Tools.READ, "AGENTS.md"), tool(Tools.RUN, "zip -r android_app_package.zip app"), tool(Tools.WEB_FETCH, "https://developer.android.com"),
            ChatItem.Assistant().apply {
                streaming = false
                text = if (ar) "تم إنشاء الحزمة بنجاح! يمكنك الآن تحميل **[android_app_package.zip](sandbox:/workspace/android_app_package.zip)** الذي يحتوي على جميع ملفات التطبيق."
                else "The package is ready. Download **[android_app_package.zip](sandbox:/workspace/android_app_package.zip)** with all the app files."
            },
        )
    }

    private fun shot(name: String, ar: Boolean) {
        val dir = File(outDir, "proj").apply { mkdirs() }
        File(dir, "android_app_package.zip").writeBytes(ByteArray(48_300))
        rule.setContent { SaqrTheme("dark") { Screen(ar, dir) } }
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(700)
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Screen(ar: Boolean, dir: File) {
        val list = remember { mutableStateListOf<ChatItem>().apply { addAll(items(ar)) } }
        IdeFrame(topBar = { IdeTopBar(Pane.AGENT, {}, {}, center = {}) }) { top ->
            ChatContent(
                ChatUiState(list, false, step = 3, maxSteps = 40, contextTokens = 4_700, contextWindow = 128_000,
                    providerId = "anthropic", model = "claude-opus-4-1", models = listOf("claude-opus-4-1"), ready = true),
                "", {}, {}, {}, { _, _ -> }, {}, {}, topInset = top, autoScroll = false, onSpeak = {}, workspaceDir = dir,
            )
        }
    }

    @Config(qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-night-xxhdpi")
    @Test fun majlisAr() = shot("majlis-ar", true)

    @Config(qualifiers = "en-rUS-ldltr-w411dp-h891dp-night-xxhdpi")
    @Test fun majlisEn() = shot("majlis-en", false)
}
