package com.ctodrs.saqrcode

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ctodrs.saqrcode.git.GitRemote
import com.ctodrs.saqrcode.git.GitStatus
import com.ctodrs.saqrcode.git.MergeRecord
import com.ctodrs.saqrcode.git.PrInfo
import com.ctodrs.saqrcode.ui.GitActions
import com.ctodrs.saqrcode.ui.GitScreenContent
import com.ctodrs.saqrcode.ui.GitTab
import com.ctodrs.saqrcode.ui.GitUiState

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

/** v0.1.11 visual proof (opt-in like ScreenshotTest): the PR tab with the undo banner, and the merge dialog, AR (RTL) and EN. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ScreenshotTestGitPr {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(System.getProperty("saqr.screens.dir") ?: "build/screens")
    @Before fun gate() { assumeTrue(System.getProperty("saqr.screens") == "1"); outDir.mkdirs() }

    private fun state(ar: Boolean) = GitUiState(
        status = GitStatus(isRepo = true, branch = "main", headShort = "181b576", upstream = "origin/main", hasCommits = true),
        remotes = listOf(GitRemote("origin", "https://github.com/CTO-DRS/saqr-code.git")),
        prs = listOf(
            PrInfo(12, if (ar) "وضع الخطة قبل التنفيذ" else "Plan mode before execution", "feature/plan-mode", "main",
                "rasheed", false, "https://github.com/CTO-DRS/saqr-code/pull/12", "2026-11-27T10:00:00Z"),
            PrInfo(11, if (ar) "إصلاح تعطل المزامنة" else "Fix sync crash on logout", "fix/sync-crash", "main",
                "octocat", true, "https://github.com/CTO-DRS/saqr-code/pull/11", "2026-11-27T09:00:00Z"),
        ),
        pendingMerge = MergeRecord("CTO-DRS", "saqr-code", 10, "main", "aaa", "bbb", "squash", 1L),
        pendingMergeReady = true,
    )

    private fun save(name: String) {
        rule.waitForIdle(); rule.mainClock.advanceTimeBy(700)
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun listShot(name: String, ar: Boolean) {
        rule.setContent {
            SaqrTheme("dark") {
                Box(Modifier.fillMaxSize().background(Saqr.colors.bg)) { GitScreenContent(state(ar), GitTab.PULLS, {}, GitActions()) }
            }
        }
        save(name)
    }

    private fun mergeShot(name: String, ar: Boolean) {
        rule.setContent {
            SaqrTheme("dark") {
                Box(Modifier.fillMaxSize().background(Saqr.colors.bg).padding(24.dp)) {
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.material3.Text(
                            androidx.compose.ui.res.stringResource(R.string.git_pr_merge_title, "12"),
                            style = Saqr.type.title2, color = Saqr.colors.text1)
                        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                        com.ctodrs.saqrcode.ui.PrMergeContent("squash", {})
                    }
                }
            }
        }
        save(name)
    }

    @Config(qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-night-xxhdpi")
    @Test fun prsAr() = listShot("gitpr-ar", true)

    @Config(qualifiers = "en-rUS-ldltr-w411dp-h891dp-night-xxhdpi")
    @Test fun prsEn() = listShot("gitpr-en", false)

    @Config(qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-night-xxhdpi")
    @Test fun mergeAr() = mergeShot("gitpr-merge-ar", true)

    @Config(qualifiers = "en-rUS-ldltr-w411dp-h891dp-night-xxhdpi")
    @Test fun mergeEn() = mergeShot("gitpr-merge-en", false)
}
