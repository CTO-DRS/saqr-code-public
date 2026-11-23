package com.ctodrs.saqrcode

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.FileChange
import com.ctodrs.saqrcode.agent.PlanItem
import com.ctodrs.saqrcode.agent.Providers
import com.ctodrs.saqrcode.agent.Tools
import com.ctodrs.saqrcode.data.ProviderConfig
import com.ctodrs.saqrcode.ui.FilesPaneContent
import com.ctodrs.saqrcode.ui.HomeContent
import com.ctodrs.saqrcode.ui.IdeFrame
import com.ctodrs.saqrcode.ui.IdeTopBar
import com.ctodrs.saqrcode.ui.OnboardingScreen
import com.ctodrs.saqrcode.ui.OpenFile
import com.ctodrs.saqrcode.ui.Pane
import com.ctodrs.saqrcode.ui.PaneTitle
import com.ctodrs.saqrcode.ui.ProjectCard
import com.ctodrs.saqrcode.ui.SettingsContent
import com.ctodrs.saqrcode.ui.SettingsUi
import com.ctodrs.saqrcode.ui.TerminalContent
import com.ctodrs.saqrcode.ui.TermTabUi
import com.ctodrs.saqrcode.ui.buildTree
import com.ctodrs.saqrcode.ui.chat.ChatContent
import com.ctodrs.saqrcode.ui.chat.ChatUiState
import com.ctodrs.saqrcode.ui.chat.PlanPillContent
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

/**
 * Renders the main screens on the JVM (Robolectric native graphics) to PNGs in dist/screens.
 * Opt-in: SAQR_SCREENS=1 ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "ar-rSA-ldrtl-w411dp-h891dp-night-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File(System.getProperty("saqr.screens.dir") ?: "build/screens")

    @Before fun gate() { assumeTrue(System.getProperty("saqr.screens") == "1"); outDir.mkdirs() }

    private fun shot(name: String, theme: String = "dark", content: @Composable () -> Unit) {
        rule.setContent { SaqrTheme(theme) { content() } }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(700)
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ------------------------------------------------------------ sample data

    private val tafqitBefore = (1..30).joinToString("\n") { "    line $it" }
    private fun edited(n: Int) = tafqitBefore.lines().mapIndexed { i, l -> if (i < n) "$l // fixed" else l }.joinToString("\n")
    private fun created(n: Int) = (1..n).joinToString("\n") { "line $it" }

    private fun tool(name: String, summary: String, ms: Long, ok: Boolean?, result: String = "[exit 0]\nok") =
        ChatItem.Tool(name, summary).apply {
            if (ok != null) { this.ok = ok; this.result = if (ok) result else "[exit 1]\nFAILED: 2 tests"; durationMs = ms }
        }

    private fun applied(path: String, before: String?, after: String?, ms: Long) =
        ChatItem.Approval(listOf(FileChange(path, before, after))).apply { decision = true; durationMs = ms }

    private fun assistant(t: String) = ChatItem.Assistant().apply { text = t; streaming = false }

    private fun chatItems(): List<ChatItem> = listOf(
        ChatItem.User("سوّ لي مكتبة تفقيط للأرقام بالعربي مع اختبارات، وشغّلها"),
        assistant("تمام! سأبني **مكتبة التفقيط** أولاً ثم أكتب اختبارات بالعينات المرجعية وأشغّلها في الطرفية."),
        tool(Tools.READ, "AGENTS.md", 400, true),
        applied("src/Tafqit.java", null, created(122), 13_000),
        applied("test/TafqitTest.java", null, created(86), 9_000),
        assistant("أجمّع وأشغّل الاختبارات فورًا للتحقق من الاصطلاح قبل المتابعة:"),
        tool(Tools.RUN, "cd /workspace && javac -d out src/*.java test/*.java && java -cp out TafqitTest", 9_000, false),
        assistant("الخلل: «و» العطف تسبق العشرات المركبة (`ثلاثة وعشرون` لا `ثلاثة عشرون`) — أصلحه:"),
        applied("src/Tafqit.java", tafqitBefore, edited(5), 9_000),
        tool(Tools.RUN, "cd /workspace && java -cp out TafqitTest", 7_000, true, "[exit 0]\n43/43 passed"),
        assistant("✅ **43/43** أخضر. الآن `EmojiSense`:"),
        applied("src/EmojiSense.java", null, created(122), 13_000),
        tool(Tools.RUN, "cd /workspace && java -cp out EmojiSenseTest", 0, null),
        ChatItem.Notice("تم الإيقاف لحماية رصيدك: كانت المهمة قد استهلكت نحو 272 خطوة. أرسل رسالة للمتابعة.", kind = ChatItem.Notice.Kind.WARNING),
    )

    private val plan = listOf(
        PlanItem("مكتبة التفقيط", "done"), PlanItem("اختبارات العينات", "in_progress"),
        PlanItem("EmojiSense", "pending"), PlanItem("README", "pending"), PlanItem("إيداع ودفع", "pending"),
    )

    @Composable
    private fun ChatScreenShot(items: List<ChatItem>, running: Boolean, input: String = "") {
        val list = remember { mutableStateListOf<ChatItem>().apply { addAll(items) } }
        IdeFrame(topBar = {
            IdeTopBar(Pane.AGENT, {}, {}, center = { PlanPillContent(plan, running, {}, "tafqit") })
        }) { top ->
            ChatContent(
                ChatUiState(list, running, step = 22, maxSteps = 40, contextTokens = 55_400, contextWindow = 200_000,
                    providerId = "anthropic", model = "claude-opus-4-1", models = listOf("claude-opus-4-1"), ready = true),
                input, {}, {}, {}, { _, _ -> }, {}, {}, topInset = top, autoScroll = false,
            )
        }
    }

    // ------------------------------------------------------------ screens

    @Test fun chat() = shot("chat") { ChatScreenShot(chatItems(), running = true) }

    @Test fun chatLight() = shot("chat-light", theme = "light") { ChatScreenShot(chatItems(), running = true) }

    @Test fun chatApproval() = shot("chat-approval") {
        ChatScreenShot(listOf(
            ChatItem.User("Add a dark mode toggle to the landing page"),
            assistant("I'll add a toggle button, the CSS variables for both themes, and persist the choice in `localStorage`:\n\n```js\nconst root = document.documentElement;\nbtn.onclick = () => root.classList.toggle('dark');\n```"),
            ChatItem.Approval(listOf(
                FileChange("index.html", tafqitBefore, edited(3)),
                FileChange("style.css", tafqitBefore, edited(8)),
                FileChange("app.js", null, created(24)),
            )),
        ), running = true)
    }

    @Test fun chatEmpty() = shot("chat-empty") { ChatScreenShot(emptyList(), running = false) }

    @Test fun home() = shot("home") {
        val now = 1_763_800_000_000L
        HomeContent(
            projects = listOf(
                ProjectCard("tafqit", now - 4 * 60_000, 12, "Tafqit.java"),
                ProjectCard("مقهى-سراج", now - 3 * 3_600_000, 5, "index.html"),
                ProjectCard("weather-bot", now - 2 * 86_400_000, 8, "main.py"),
            ),
            needsProvider = false, onOpen = {}, onSettings = {}, onCreate = {}, onDelete = {}, now = now,
        )
    }

    @Test fun homeFirstRun() = shot("home-empty") {
        HomeContent(emptyList(), needsProvider = true, onOpen = {}, onSettings = {}, onCreate = {}, onDelete = {})
    }

    private fun sampleProject(): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "saqr-shot-" + System.nanoTime()).apply { mkdirs() }
        File(dir, "src").mkdirs(); File(dir, "test").mkdirs()
        File(dir, "src/Tafqit.kt").writeText(TAFQIT)
        File(dir, "src/EmojiSense.kt").writeText("object EmojiSense\n")
        File(dir, "test/TafqitTest.kt").writeText("class TafqitTest\n")
        File(dir, "index.html").writeText("<!doctype html>\n")
        File(dir, "style.css").writeText("body{}\n")
        File(dir, "main.py").writeText("print('hi')\n")
        File(dir, "README.md").writeText("# tafqit\n")
        File(dir, "AGENTS.md").writeText("# rules\n")
        File(dir, "package.json").writeText("{}\n")
        return dir
    }

    @Test fun editor() = shot("editor") {
        val dir = sampleProject()
        val tabs = listOf(OpenFile("src/Tafqit.kt", TAFQIT), OpenFile("index.html", "<!doctype html>\n").apply { value = androidx.compose.ui.text.input.TextFieldValue("<!doctype html>\n<html>") })
        IdeFrame(topBar = { IdeTopBar(Pane.FILES, {}, {}, center = { PaneTitle(Pane.FILES, "tafqit") }) }) { top ->
            Box(Modifier.padding(top = top)) {
                FilesPaneContent(buildTree(dir, emptyList()), remember { mutableStateListOf() }, tabs, 0, false, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }

    @Test fun files() = shot("files") {
        val dir = sampleProject()
        val expanded = remember { mutableStateListOf("src") }
        IdeFrame(topBar = { IdeTopBar(Pane.FILES, {}, {}, center = { PaneTitle(Pane.FILES, "tafqit") }) }) { top ->
            Box(Modifier.padding(top = top)) {
                FilesPaneContent(buildTree(dir, expanded), expanded, listOf(OpenFile("src/Tafqit.kt", TAFQIT)), 0, true, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
    }

    @Test fun terminal() = shot("terminal") {
        IdeFrame(topBar = { IdeTopBar(Pane.TERMINAL, {}, {}, center = { PaneTitle(Pane.TERMINAL, "tafqit") }) }) { top ->
            Box(Modifier.padding(top = top)) {
                // TODO: feed TERMINAL_OUT through a real TerminalEmulator; for now renders the empty-screen chrome.
                TerminalContent(
                    emulator = null, version = 0, tabs = listOf(TermTabUi(1, "alpine 1", false)), active = 0,
                    linuxReady = true, installing = false, error = false, progress = "", finished = false, fontSize = 13,
                    onInstall = {}, onSize = { _, _ -> }, onWrite = {}, onPaste = {}, onCopyAll = {},
                    onRestart = {}, onSelectTab = {}, onNewTab = {}, onCloseTab = {}, onFont = {},
                )
            }
        }
    }

    private fun settingsUi() = SettingsUi(
        themeMode = "dark", langTag = "ar",
        providers = Providers.all.map { def ->
            def to ProviderConfig(def.id, def.defaultBaseUrl, def.defaultModel, hasKey = def.id in setOf("anthropic", "openrouter"))
        },
        activeProvider = "anthropic", approveAll = false, githubUser = "cto-drs", githubTokenSet = true, versionName = "0.2.0",
        onTheme = {}, onLang = {}, onActivate = {}, onSaveProvider = { _, _, _, _ -> }, fetchModels = { _, _, _, _ -> Result.success(emptyList()) },
        onApproveAll = {},
    )

    private fun askItems(): List<ChatItem> {
        val req = com.ctodrs.saqrcode.agent.AskUserTool.parse(org.json.JSONObject("""
            {"title":"A few choices before I start","questions":[
              {"id":"platform","prompt":"Where should it run?","kind":"single","allow_other":true,"options":[
                {"id":"web","label":"Web","description":"Runs in any browser, easiest to share","recommended":true},
                {"id":"android","label":"Android","description":"Native app with Jetpack Compose"}]},
              {"id":"extras","prompt":"Extras","kind":"multi","options":[{"id":"auth","label":"Login"},{"id":"dark","label":"Dark mode","recommended":true}]},
              {"id":"tests","prompt":"Write tests?","kind":"confirm","default":true}]}"""))!!
        return listOf(
            ChatItem.User("Build me a habit tracker"),
            assistant("Before I start, a couple of decisions shape the whole project:"),
            ChatItem.AskUser(req, ""),
        )
    }

    @Test fun chatAskUser() = shot("chat-ask-user") { ChatScreenShot(askItems(), running = true) }

    @Test fun chatAskUserLight() = shot("chat-ask-user-light", theme = "light") { ChatScreenShot(askItems(), running = true) }

    @Composable
    private fun ChatV04(input: String) {
        val list = remember {
            mutableStateListOf<ChatItem>(
                ChatItem.User("أضف وضعًا داكنًا لصفحة الهبوط").apply { checkpointId = "1" },
                assistant("تم: أضفت زر التبديل ومتغيرات CSS للوضعين."),
                applied("style.css", tafqitBefore, edited(4), 4_000),
                ChatItem.Notice("أضف وضعًا داكنًا لصفحة الهبوط", kind = ChatItem.Notice.Kind.RESTORED, count = 2),
            )
        }
        IdeFrame(topBar = {
            IdeTopBar(Pane.AGENT, {}, {}, overflow = { com.ctodrs.saqrcode.ui.components.PillIcon(com.ctodrs.saqrcode.ui.icons.Lucide.EllipsisVertical, null, {}) },
                center = { PlanPillContent(emptyList(), false, {}, "landing") })
        }) { top ->
            ChatContent(
                ChatUiState(list, false, step = 6, maxSteps = 40, contextTokens = 18_400, contextWindow = 200_000,
                    providerId = "anthropic", model = "claude-sonnet-4-5", models = listOf("claude-sonnet-4-5"), ready = true,
                    customCommands = listOf(com.ctodrs.saqrcode.agent.SlashCommand("deploy", "Deploy {file} to staging")),
                    sessionTokens = 48_200, sessionCost = 0.31, usageEstimated = false, budget = 10.0, monthSpent = 8.6),
                input, {}, {}, {}, { _, _ -> }, {}, {}, topInset = top, autoScroll = false,
            )
        }
    }

    @Test fun chatSlash() = shot("chat-slash") { ChatV04("/") }

    @Test fun chatSlashLight() = shot("chat-slash-light", theme = "light") { ChatV04("/ا") }

    @Test fun settings() = shot("settings") { SettingsContent(settingsUi(), {}) }

    /** Tall window so the v0.4 sections (slash commands, budget, prices) are in frame. */
    @Config(qualifiers = "+h2600dp")
    @Test fun settingsFull() = shot("settings-full") {
        SettingsContent(SettingsUi(
            themeMode = "dark", langTag = "ar", providers = emptyList(), activeProvider = "anthropic", approveAll = false,
            githubUser = "", githubTokenSet = false, versionName = "0.4.0", onTheme = {}, onLang = {}, onActivate = {},
            onSaveProvider = { _, _, _, _ -> }, fetchModels = { _, _, _, _ -> Result.success(emptyList()) }, onApproveAll = {},
            customCommands = listOf(com.ctodrs.saqrcode.agent.SlashCommand("deploy", "Build {file} and deploy it to staging. {args}")),
            monthlyBudget = 10.0,
        ), {})
    }

    @Test fun settingsProvider() = shot("settings-provider") { SettingsContent(settingsUi(), {}, initiallyExpanded = "anthropic") }

    @Test fun onboarding() = shot("onboarding") { OnboardingScreen(onDone = {}) }

    @Test fun onboarding2() = shot("onboarding-2") { OnboardingScreen(onDone = {}, initialPage = 1) }

    companion object {
        val TAFQIT = """package tafqit

/** Converts numbers to Arabic words (تفقيط). */
object Tafqit {
    private val ones = listOf("", "واحد", "اثنان", "ثلاثة", "أربعة")
    private val tens = listOf("", "عشرة", "عشرون", "ثلاثون")

    fun spell(n: Long): String {
        if (n == 0L) return "صفر"
        val parts = ArrayList<String>()
        var rest = n
        // thousands, then hundreds
        if (rest >= 1000) {
            parts += group(rest / 1000) + " ألف"
            rest %= 1000
        }
        if (rest > 0) parts += group(rest)
        return parts.joinToString(" و")
    }

    private fun group(n: Long): String = when {
        n < 10 -> ones[n.toInt()]
        n < 100 -> "${'$'}{ones[(n % 10).toInt()]} و${'$'}{tens[(n / 10).toInt()]}"
        else -> "مئة"
    }
}
"""
        val TERMINAL_OUT = """Alpine Linux 3.24.4 (proot) — /workspace
$ apk add openjdk17
(1/42) Installing java-common (0.5-r0)
(2/42) Installing openjdk17-jre-headless (17.0.13_p11-r0)
OK: 212 MiB in 61 packages
$ javac -d out src/*.java test/*.java
$ java -cp out TafqitTest
Running 43 tests…
  ✓ spell(0) = صفر
  ✓ spell(23) = ثلاثة وعشرون
  ✓ spell(1999) = ألف وتسعمئة وتسعة وتسعون
43/43 passed in 0.41s
$ ls -la
drwxr-xr-x    4 root  root   4096 Nov 22 15:41 .
-rw-r--r--    1 root  root    212 Nov 22 15:12 AGENTS.md
drwxr-xr-x    2 root  root   4096 Nov 22 15:40 src
drwxr-xr-x    2 root  root   4096 Nov 22 15:40 test"""
    }
}
