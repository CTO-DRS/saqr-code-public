package com.ctodrs.saqrcode.ondevice

/**
 * v0.9 · بناء إصدار موقّع · Signed-release build on the device.
 *
 * The user imports a keystore into the project (for example `keystore/release.jks` through the
 * Files tab) and types its passwords in the Build tab. Passwords live only in the Compose state —
 * they are never written to prefs, the log, or the project. Signing happens inside Gradle itself:
 * a generated Groovy init script injects a `signingConfigs` block into every Android application
 * module and points `buildTypes.release` at it, so AGP produces v1+v2+v3 signatures exactly like
 * a desktop release build (no extra downloads, no apksigner step). The values travel through
 * environment variables, not the command line, so they never show up in `ps` output.
 *
 * Pure parts ([SigningConfig.complete], [SigningInit.script]) are unit-tested in V09FeaturesTest.
 */
data class SigningConfig(
    /** Keystore path relative to the project root, e.g. `keystore/release.jks`. */
    val keystorePath: String,
    val storePassword: String,
    val alias: String,
    val keyPassword: String,
) {
    /** True when every field is filled and the path looks local (no URL, no traversal outside). */
    fun complete(): Boolean = keystorePath.isNotBlank() && storePassword.isNotEmpty() &&
        alias.isNotBlank() && keyPassword.isNotEmpty() && isSafePath(keystorePath)

    companion object {
        /** Rejects empty/absolute/escaping paths: the keystore must live inside the project. */
        fun isSafePath(p: String): Boolean {
            val t = p.trim().replace('\\', '/')
            if (t.isEmpty() || t.startsWith("/") || t.contains(":")) return false
            val parts = t.split('/').filter { it.isNotEmpty() }
            if (parts.any { it == ".." }) return false
            return parts.size in 1..6
        }
    }
}

/** Generates the Groovy init script Gradle runs with `--init-script` for the signed release. */
object SigningInit {
    const val ENV_KS = "SAQR_KS"
    const val ENV_KS_PASS = "SAQR_KS_PASS"
    const val ENV_ALIAS = "SAQR_KS_ALIAS"
    const val ENV_KEY_PASS = "SAQR_KS_KEY_PASS"

    /** In-proot path the init script is bound at. */
    const val SCRIPT_PATH = "/opt/saqr-init.gradle"

    fun script(): String = """
        // Saqr Code on-device signing (generated file — do not edit)
        allprojects {
            afterEvaluate { proj ->
                if (proj.plugins.hasPlugin('com.android.application')) {
                    proj.android {
                        signingConfigs {
                            saqrRelease {
                                storeFile file(System.getenv('$ENV_KS') ?: '/workspace/keystore/release.jks')
                                storePassword System.getenv('$ENV_KS_PASS') ?: ''
                                keyAlias System.getenv('$ENV_ALIAS') ?: ''
                                keyPassword System.getenv('$ENV_KEY_PASS') ?: ''
                            }
                        }
                        buildTypes {
                            release {
                                // R8 on a phone is out of reach for most devices; keep the release build light.
                                minifyEnabled false
                                shrinkResources false
                                signingConfig signingConfigs.saqrRelease
                            }
                        }
                    }
                }
            }
        }
    """.trimIndent() + "\n"
}
