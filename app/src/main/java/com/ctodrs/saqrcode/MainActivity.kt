package com.ctodrs.saqrcode

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
import com.ctodrs.saqrcode.ui.HomeScreen
import com.ctodrs.saqrcode.ui.IdeScreen
import com.ctodrs.saqrcode.ui.OnboardingScreen
import com.ctodrs.saqrcode.ui.SettingsScreen
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.SaqrTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as SaqrApp).settings
        setContent {
            val version by settings.version.collectAsState()
            version
            SaqrTheme(themeMode = settings.themeMode) {
                val c = Saqr.colors
                LaunchedEffect(c.isDark) {
                    val style = if (c.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, c.text1.toArgb())
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                Box(Modifier.fillMaxSize().background(c.bg)) {
                    // "onboarding" | "home" | "settings" | "project:<name>"
                    var screen by rememberSaveable { mutableStateOf(if (settings.onboarded) "home" else "onboarding") }
                    var returnTo by rememberSaveable { mutableStateOf("home") }
                    BackHandler(enabled = screen != "home" && screen != "onboarding") { screen = if (screen == "settings") returnTo else "home" }
                    AnimatedContent(screen, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "nav") { s ->
                        when {
                            s == "onboarding" -> OnboardingScreen(onDone = { openSettings ->
                                settings.onboarded = true
                                returnTo = "home"
                                screen = if (openSettings && !settings.activeReady()) "settings" else "home"
                            })
                            s == "settings" -> SettingsScreen(onBack = { screen = returnTo })
                            s.startsWith("project:") -> IdeScreen(
                                projectName = s.removePrefix("project:"),
                                onBack = { screen = "home" },
                                onSettings = { returnTo = s; screen = "settings" },
                            )
                            else -> HomeScreen(
                                onOpen = { screen = "project:$it" },
                                onSettings = { returnTo = "home"; screen = "settings" },
                            )
                        }
                    }
                }
            }
        }
    }
}
