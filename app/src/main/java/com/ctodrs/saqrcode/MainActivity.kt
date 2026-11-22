package com.ctodrs.saqrcode

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ctodrs.saqrcode.ui.HomeScreen
import com.ctodrs.saqrcode.ui.IdeScreen
import com.ctodrs.saqrcode.ui.SaqrTheme
import com.ctodrs.saqrcode.ui.SettingsScreen

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaqrTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // "home" | "settings" | "project:<name>"
                    var screen by rememberSaveable { mutableStateOf("home") }
                    var returnTo by rememberSaveable { mutableStateOf("home") }
                    BackHandler(enabled = screen != "home") { screen = if (screen == "settings") returnTo else "home" }
                    when {
                        screen == "settings" -> SettingsScreen(onBack = { screen = returnTo })
                        screen.startsWith("project:") -> IdeScreen(
                            projectName = screen.removePrefix("project:"),
                            onBack = { screen = "home" },
                            onSettings = { returnTo = screen; screen = "settings" },
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
