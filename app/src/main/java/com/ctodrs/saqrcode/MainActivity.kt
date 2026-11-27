package com.ctodrs.saqrcode

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ctodrs.saqrcode.ui.AccountsScreen
import com.ctodrs.saqrcode.ui.HomeScreen
import com.ctodrs.saqrcode.ui.IdeScreen
import com.ctodrs.saqrcode.ui.OnboardingScreen
import com.ctodrs.saqrcode.ui.SettingsScreen
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.SaqrTheme

class MainActivity : AppCompatActivity() {
    /** A project to open (and maybe resume) from a notification: "name" or "name\u0000resume". */
    private val openRequest = mutableStateOf<String?>(null)

    private fun handle(intent: Intent?) {
        val p = intent?.getStringExtra(EXTRA_PROJECT) ?: return
        openRequest.value = p + if (intent.action == ACTION_RESUME) "\u0000resume" else ""
        intent.removeExtra(EXTRA_PROJECT)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onPause() {
        super.onPause()
        com.ctodrs.saqrcode.ui.Workspace.saveAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as SaqrApp).settings
        if (savedInstanceState == null) handle(intent)
        setContent {
            val version by settings.version.collectAsState()
            version
            // v0.12: the active Theme Studio pack (parsed once per settings change).
            val customPack = androidx.compose.runtime.remember(version) {
                runCatching { com.ctodrs.saqrcode.ui.theme.ThemePack.fromJson(settings.customThemeJson) }.getOrNull()
            }
            // v0.12.1: the Material You seed (API 31+), resolved once per settings/system change.
            val sysDark = isSystemInDarkTheme()
            val dynAccent = if (settings.themeMode == "dynamic") androidx.compose.runtime.remember(version, sysDark) {
                runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        val scheme = if (sysDark) androidx.compose.material3.dynamicDarkColorScheme(this@MainActivity)
                        else androidx.compose.material3.dynamicLightColorScheme(this@MainActivity)
                        scheme.primary.toArgb()
                    } else null
                }.getOrNull()
            } else null
            SaqrTheme(themeMode = settings.themeMode, customPack = customPack, dynamicAccent = dynAccent) {
                val c = Saqr.colors
                LaunchedEffect(c.isDark) {
                    val style = if (c.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, c.text1.toArgb())
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                Box(Modifier.fillMaxSize().background(c.bg)) {
                    // "onboarding" | "home" | "settings" | "accounts" | "project:<name>"
                    var screen by rememberSaveable { mutableStateOf(if (settings.onboarded) "home" else "onboarding") }
                    var returnTo by rememberSaveable { mutableStateOf("home") }
                    var accountsReturn by rememberSaveable { mutableStateOf("settings") }
                    val req = openRequest.value
                    LaunchedEffect(req) {
                        if (req != null) {
                            val name = req.substringBefore('\u0000')
                            val ws = com.ctodrs.saqrcode.ui.Workspace.get(name)
                            if (ws != null) {
                                if (!settings.onboarded) settings.onboarded = true
                                ws.tab = com.ctodrs.saqrcode.ui.Pane.AGENT
                                screen = "project:$name"
                                if (req.endsWith("\u0000resume")) {
                                    com.ctodrs.saqrcode.service.Notifs.cancelResumeOffer(this@MainActivity)
                                    ws.agent.resumeInterrupted()
                                }
                            }
                            openRequest.value = null
                        }
                    }
                    BackHandler(enabled = screen != "home" && screen != "onboarding") {
                        screen = when (screen) { "settings" -> returnTo; "accounts" -> accountsReturn; "paywall" -> returnTo; else -> "home" }
                    }
                    // v0.11: one Play Billing client for the whole app; the paywall drives it.
                    val play = androidx.compose.runtime.remember { com.ctodrs.saqrcode.billing.PlayBilling(this@MainActivity, settings) }
                    androidx.compose.runtime.DisposableEffect(Unit) {
                        play.connect()
                        onDispose { play.close() }
                    }
                    AnimatedContent(screen, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "nav") { s ->
                        when {
                            s == "onboarding" -> com.ctodrs.saqrcode.ui.Capped { OnboardingScreen(onDone = { openSettings ->
                                settings.onboarded = true
                                returnTo = "home"
                                screen = if (openSettings && !settings.activeReady()) "settings" else "home"
                            }) }
                            s == "settings" -> com.ctodrs.saqrcode.ui.Capped { SettingsScreen(onBack = { screen = returnTo }, onAccounts = { accountsReturn = "settings"; screen = "accounts" }, onPaywall = { returnTo = "settings"; screen = "paywall" }) }
                            s == "accounts" -> com.ctodrs.saqrcode.ui.Capped { AccountsScreen(onBack = { screen = accountsReturn }) }
                            s == "paywall" -> com.ctodrs.saqrcode.ui.Capped { com.ctodrs.saqrcode.billing.PaywallScreen(onBack = { screen = returnTo }, settings = settings, play = play) }
                            s.startsWith("project:") -> IdeScreen(
                                projectName = s.removePrefix("project:"),
                                onBack = { screen = "home" },
                                onSettings = { returnTo = s; screen = "settings" },
                                onAccounts = { accountsReturn = s; screen = "accounts" },
                                onPaywall = { returnTo = s; screen = "paywall" },
                            )
                            else -> com.ctodrs.saqrcode.ui.Capped { HomeScreen(
                                onOpen = { screen = "project:$it" },
                                onSettings = { returnTo = "home"; screen = "settings" },
                                onPaywall = { returnTo = "home"; screen = "paywall" },
                            ) }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_PROJECT = "com.ctodrs.saqrcode.extra.PROJECT"
        const val ACTION_OPEN = "com.ctodrs.saqrcode.action.OPEN_PROJECT"
        const val ACTION_RESUME = "com.ctodrs.saqrcode.action.RESUME_PROJECT"
    }
}
