import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// v0.11 offline licensing: HMAC key for license tokens. Set `licenseKey=<64 hex chars>` in
// keystore.properties (private repo only). The fallback is a PUBLIC dev key — anyone can mint
// tokens against it; real distributions must ship the private key. See NOTICE.md.
val devLicenseKey = "0000000000000000000000000000000000000000000000000000000000000064"
val licenseKeyHex = keystoreProps.getProperty("licenseKey") ?: devLicenseKey

android {
    namespace = "com.ctodrs.saqrcode"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.ctodrs.saqrcode"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "0.14.4"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en", "ar")
        buildConfigField("String", "SAQR_LICENSE_KEY", "\"$licenseKeyHex\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.getProperty("storeFile") != null) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystoreProps.getProperty("storeFile") != null)
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }

    packaging {
        // proot & its loader must be real files on disk (nativeLibraryDir) so they can be exec'd.
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "plugin.properties",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF", "about.html"
            )
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = System.getenv("SAQR_TEST_HEAP") ?: "3g"
                // Robolectric fetches android-all jars at runtime; Maven Central rate-limits shared IPs, so use Google's mirror.
                it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2")
                // Screenshot rendering is opt-in: SAQR_SCREENS=1 ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
                it.systemProperty("saqr.screens", System.getenv("SAQR_SCREENS") ?: "0")
                it.systemProperty("saqr.screens.dir", rootProject.file("dist/screens").absolutePath)
                // v0.12: without SAQR_SCREENS=1 the screenshot class never runs at all — its compose
                // activity rule launches before the @Before gate could assume-skip it, which made the
                // whole suite fail on machines without Robolectric native graphics.
                if (System.getenv("SAQR_SCREENS") != "1") {
                    it.exclude("**/ScreenshotTest*")
                }
            }
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.13.3.202401111512-r")
    implementation("org.slf4j:slf4j-nop:1.7.36")
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.1")
    // v0.11 Google Play Billing (PBL 8.x — v7 is no longer accepted by Play).
    // 8.0.0: last 8.x line compatible with this project's Kotlin 2.0 metadata reader.
    implementation("com.android.billingclient:billing-ktx:8.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
