package com.ctodrs.saqrcode.data

/**
 * v0.7 · قوالب جديدة · New project templates: a Kotlin + Jetpack Compose app (Arabic/RTL-ready)
 * and an Arabic IME keyboard (InputMethodService). Kept as plain text so [ProjectRepository]
 * can materialise them; the on-device builder (ondevice.OnDeviceBuild) can compile the
 * Compose template straight into an APK on the phone.
 */
object TemplateFiles {

    /** A valid lowercase package segment from a project name. */
    fun pkg(name: String): String {
        val p = name.lowercase().replace(Regex("""[^a-z0-9]"""), "").take(20)
        return if (p.isEmpty() || p.first().isDigit()) "app$p" else p
    }

    private fun fill(s: String, name: String): String =
        s.replace("@@PKG@@", pkg(name)).replace("@@NAME@@", name.replace("\"", ""))

    /** v0.1.49: like [fill] for XML and HTML files: `& < > "` in the name are escaped; [strings] also escapes `'` for strings.xml. */
    private fun fillXml(s: String, name: String, strings: Boolean = false): String {
        var n = name.replace("\"", "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        if (strings) n = n.replace("'", "\\'")
        return s.replace("@@PKG@@", pkg(name)).replace("@@NAME@@", n)
    }

    // ------------------------------------------------------------ compose

    fun compose(name: String): Map<String, String> {
        val m = mapOf(
            "README.md" to "# $name\n\nA Kotlin + Jetpack Compose starter (Arabic-first, RTL-ready).\n\n" +
                "- Build it right on this phone: open the project → **Build** tab → Prepare → Build APK.\n" +
                "- Or on a computer: open the folder in Android Studio (no wrapper; use `gradle wrapper` first on the CLI).\n",
            "AGENTS.md" to Template.AGENTS_MD +
                "- This is a Compose app: keep UI in Kotlin, put user-facing text in strings.xml (values + values-ar).\n",
            "settings.gradle.kts" to fill(COMPOSE_SETTINGS, name),
            "build.gradle.kts" to fill(COMPOSE_ROOT, name),
            "gradle.properties" to fill(COMPOSE_GRADLE_PROPS, name),
            "app/build.gradle.kts" to fill(COMPOSE_APP, name),
            "app/src/main/AndroidManifest.xml" to fill(COMPOSE_MANIFEST, name),
            "app/src/main/java/com/saqr/" + pkg(name) + "/MainActivity.kt" to fill(COMPOSE_MAIN, name),
            "app/src/main/res/values/strings.xml" to fillXml(COMPOSE_STRINGS_EN, name, strings = true),
            "app/src/main/res/values-ar/strings.xml" to fillXml(COMPOSE_STRINGS_AR, name, strings = true),
            "app/src/main/res/values/themes.xml" to fill(COMPOSE_THEMES, name),
        )
        return m
    }

    private const val COMPOSE_SETTINGS = """pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "@@NAME@@"
include(":app")
"""

    private const val COMPOSE_ROOT = """plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
"""

    private const val COMPOSE_GRADLE_PROPS = """org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
"""

    private const val COMPOSE_APP = """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.saqr.@@PKG@@"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.saqr.@@PKG@@"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("en", "ar")
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
"""

    private const val COMPOSE_MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""

    private const val COMPOSE_MAIN = """package com.saqr.@@PKG@@

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { MainScreen() }
        }
    }
}

@Composable
fun MainScreen() {
    var count by remember { mutableIntStateOf(0) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(24.dp),
        ) {
            Text(stringResource(R.string.app_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.press_count, count), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { count++ }) { Text(stringResource(R.string.press_here)) }
        }
    }
}
"""

    private const val COMPOSE_STRINGS_EN = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">@@NAME@@</string>
    <string name="app_title">Hello from @@NAME@@</string>
    <string name="press_here">Press me</string>
    <string name="press_count">Taps: %1${'$'}d</string>
</resources>
"""

    private const val COMPOSE_STRINGS_AR = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">@@NAME@@</string>
    <string name="app_title">مرحباً من @@NAME@@ 🦅</string>
    <string name="press_here">اضغط هنا</string>
    <string name="press_count">عدد الضغطات: %1${'$'}d</string>
</resources>
"""

    private const val COMPOSE_THEMES = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">#0e1116</item>
    </style>
</resources>
"""

    // ------------------------------------------------------------ lite web (v0.8: builds in minutes)

    /**
     * A WebView-only app with ZERO dependencies (no AndroidX, no Compose) — the whole build is
     * one Kotlin file, so even a low-end phone compiles it in minutes. The page itself is a
     * styled, RTL-ready assets/index.html the agent can edit freely.
     */
    fun liteWeb(name: String): Map<String, String> {
        return mapOf(
            "README.md" to "# $name\n\nA WebView app with zero dependencies — builds on this phone in minutes (Build tab → Prepare → Build APK).\n\n" +
                "- The page lives at `app/src/main/assets/index.html`; edit it directly or ask the agent.\n",
            "AGENTS.md" to Template.AGENTS_MD +
                "- This is a zero-dependency WebView app: the UI is `app/src/main/assets/index.html` (HTML/CSS/JS, Arabic-ready). Keep MainActivity.kt minimal.\n",
            "settings.gradle.kts" to fill(LITE_SETTINGS, name),
            "build.gradle.kts" to fill(LITE_ROOT, name),
            "gradle.properties" to fill(LITE_GRADLE_PROPS, name),
            "app/build.gradle.kts" to fill(LITE_APP, name),
            "app/src/main/AndroidManifest.xml" to fillXml(LITE_MANIFEST, name),
            "app/src/main/java/com/saqr/" + pkg(name) + "/MainActivity.kt" to fill(LITE_MAIN, name),
            "app/src/main/assets/index.html" to fillXml(LITE_PAGE, name),
        )
    }

    private const val LITE_SETTINGS = """pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "@@NAME@@"
include(":app")
"""

    private const val LITE_ROOT = """plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
"""

    private const val LITE_GRADLE_PROPS = """org.gradle.jvmargs=-Xmx900m -Dfile.encoding=UTF-8
android.useAndroidX=false
"""

    private const val LITE_APP = """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.saqr.@@PKG@@"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.saqr.@@PKG@@"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("en", "ar")
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // zero dependencies by design: this is what makes the build fast on phones
}
"""

    private const val LITE_MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="@@NAME@@"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""

    private const val LITE_MAIN = """package com.saqr.@@PKG@@

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.defaultTextEncodingName = "utf-8"
        web.webViewClient = WebViewClient()
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }
}
"""

    private const val LITE_PAGE = """<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>@@NAME@@</title>
<style>
  :root { color-scheme: dark; }
  body { margin: 0; font-family: system-ui, sans-serif; background: #0e1116; color: #e8eaed;
         min-height: 100vh; display: flex; align-items: center; justify-content: center; }
  .card { text-align: center; padding: 32px 24px; }
  h1 { font-size: 28px; margin: 0 0 8px; }
  p  { color: #9aa0a6; margin: 0 0 24px; }
  button { font-size: 16px; padding: 12px 28px; border-radius: 12px; border: 0;
           background: #4f8cff; color: white; }
  .count { margin-top: 16px; font-size: 14px; color: #9aa0a6; }
</style>
</head>
<body>
  <div class="card">
    <h1>@@NAME@@ 🦅</h1>
    <p>عدّل هذه الصفحة من app/src/main/assets/index.html</p>
    <button onclick="tap()">اضغط هنا</button>
    <div class="count" id="c">عدد الضغطات: 0</div>
  </div>
<script>
  let n = 0;
  function tap() { n++; document.getElementById('c').textContent = 'عدد الضغطات: ' + n; }
</script>
</body>
</html>
"""

    // ------------------------------------------------------------ IME keyboard

    fun ime(name: String): Map<String, String> {
        val m = mapOf(
            "README.md" to "# $name\n\nAn Arabic IME keyboard (InputMethodService + Arabic layout).\n\n" +
                "Build it on this phone from the **Build** tab, then enable it in Android settings → System → Languages & input → On-screen keyboards.\n",
            "AGENTS.md" to Template.AGENTS_MD +
                "- This is an Arabic keyboard app: keep the layout XML and the service simple; commits use English.\n",
            "settings.gradle.kts" to fill(COMPOSE_SETTINGS, name),
            "build.gradle.kts" to fill(COMPOSE_ROOT, name),
            "gradle.properties" to fill(COMPOSE_GRADLE_PROPS, name),
            "app/build.gradle.kts" to fill(IME_APP, name),
            "app/src/main/AndroidManifest.xml" to fill(IME_MANIFEST, name),
            "app/src/main/java/com/saqr/" + pkg(name) + "/ArabicImeService.kt" to fill(IME_SERVICE, name),
            "app/src/main/res/xml/method.xml" to fill(IME_METHOD, name),
            "app/src/main/res/xml/arabic_keyboard.xml" to fill(IME_KEYBOARD, name),
            "app/src/main/res/values/strings.xml" to fillXml(IME_STRINGS_EN, name, strings = true),
            "app/src/main/res/values-ar/strings.xml" to fillXml(IME_STRINGS_AR, name, strings = true),
        )
        return m
    }

    private const val IME_APP = """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.saqr.@@PKG@@"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.saqr.@@PKG@@"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("en", "ar")
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
"""

    private const val IME_MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <service
            android:name=".ArabicImeService"
            android:exported="true"
            android:label="@string/ime_label"
            android:permission="android.permission.BIND_INPUT_METHOD">
            <intent-filter>
                <action android:name="android.view.InputMethod" />
            </intent-filter>
            <meta-data
                android:name="android.view.im"
                android:resource="@xml/method" />
        </service>
    </application>
</manifest>
"""

    private const val IME_SERVICE = """package com.saqr.@@PKG@@

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View

/** A simple Arabic keyboard: three letter rows, space, delete, done. */
class ArabicImeService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private var kv: KeyboardView? = null

    override fun onCreateInputView(): View {
        val keyboard = Keyboard(this, R.xml.arabic_keyboard)
        val view = KeyboardView(this, keyboard)
        view.setOnKeyboardActionListener(this)
        view.isPreviewEnabled = false
        kv = view
        return view
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                val before = ic.getTextBeforeCursor(2, 0) ?: ""
                // delete a full surrogate pair (emoji) when present
                val n = if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
                ic.deleteSurroundingText(n, 0)
            }
            Keyboard.KEYCODE_DONE -> ic.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
            32 -> ic.commitText(" ", 1)
            else -> {
                val ch = Character.toChars(primaryCode)
                ic.commitText(String(ch), 1)
            }
        }
    }

    override fun onText(text: CharSequence?) {
        if (!text.isNullOrEmpty()) currentInputConnection?.commitText(text, 1)
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeUp() {}
    override fun swipeDown() {}
}
"""

    private const val IME_METHOD = """<?xml version="1.0" encoding="utf-8"?>
<input-method xmlns:android="http://schemas.android.com/apk/res/android">
    <subtype
        android:imeSubtypeLocale="ar"
        android:imeSubtypeMode="keyboard"
        android:label="@string/ime_label" />
</input-method>
"""

    private const val IME_KEYBOARD = """<?xml version="1.0" encoding="utf-8"?>
<Keyboard xmlns:android="http://schemas.android.com/apk/res/android"
    android:keyWidth="10%p"
    android:keyHeight="8.5%p"
    android:horizontalGap="0px"
    android:verticalGap="1px">
    <Row>
        <Key android:codes="1590" android:keyLabel="ض"/>
        <Key android:codes="1589" android:keyLabel="ص"/>
        <Key android:codes="1579" android:keyLabel="ث"/>
        <Key android:codes="1602" android:keyLabel="ق"/>
        <Key android:codes="1601" android:keyLabel="ف"/>
        <Key android:codes="1594" android:keyLabel="غ"/>
        <Key android:codes="1593" android:keyLabel="ع"/>
        <Key android:codes="1607" android:keyLabel="ه"/>
        <Key android:codes="1582" android:keyLabel="خ"/>
        <Key android:codes="1581" android:keyLabel="ح"/>
        <Key android:codes="1580" android:keyLabel="ج"/>
        <Key android:codes="1583" android:keyLabel="د"/>
    </Row>
    <Row>
        <Key android:codes="1588" android:keyLabel="ش"/>
        <Key android:codes="1587" android:keyLabel="س"/>
        <Key android:codes="1610" android:keyLabel="ي"/>
        <Key android:codes="1576" android:keyLabel="ب"/>
        <Key android:codes="1604" android:keyLabel="ل"/>
        <Key android:codes="1575" android:keyLabel="ا"/>
        <Key android:codes="1578" android:keyLabel="ت"/>
        <Key android:codes="1606" android:keyLabel="ن"/>
        <Key android:codes="1605" android:keyLabel="م"/>
        <Key android:codes="1603" android:keyLabel="ك"/>
        <Key android:codes="1591" android:keyLabel="ط"/>
    </Row>
    <Row>
        <Key android:codes="1574" android:keyLabel="ئ"/>
        <Key android:codes="1569" android:keyLabel="ء"/>
        <Key android:codes="1572" android:keyLabel="ؤ"/>
        <Key android:codes="1585" android:keyLabel="ر"/>
        <Key android:codes="1604" android:keyLabel="لا" android:keyOutputText="لا"/>
        <Key android:codes="1609" android:keyLabel="ى"/>
        <Key android:codes="1577" android:keyLabel="ة"/>
        <Key android:codes="1608" android:keyLabel="و"/>
        <Key android:codes="1586" android:keyLabel="ز"/>
        <Key android:codes="1592" android:keyLabel="ظ"/>
    </Row>
    <Row>
        <Key android:codes="-5" android:keyLabel="⌫" android:keyWidth="20%p" android:isRepeatable="true"/>
        <Key android:codes="32" android:keyLabel="␣" android:keyWidth="60%p"/>
        <Key android:codes="-4" android:keyLabel="↵" android:keyWidth="20%p"/>
    </Row>
</Keyboard>
"""

    private const val IME_STRINGS_EN = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">@@NAME@@</string>
    <string name="ime_label">Arabic keyboard (@@NAME@@)</string>
</resources>
"""

    private const val IME_STRINGS_AR = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">@@NAME@@</string>
    <string name="ime_label">لوحة مفاتيح عربية (@@NAME@@)</string>
</resources>
"""
}
