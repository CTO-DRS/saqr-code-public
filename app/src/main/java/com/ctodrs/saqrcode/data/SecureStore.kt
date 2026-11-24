package com.ctodrs.saqrcode.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secrets (API keys, GitHub token) live in EncryptedSharedPreferences whose master key
 * is held by the Android Keystore (AES-256-GCM). Never logged, never backed up.
 * If the keystore is unavailable (corrupted keyset, broken OEM keystore, unit tests on the
 * JVM), the store resets once and finally falls back to plain prefs — degraded but alive.
 */
class SecureStore(context: Context) : KeyValue {
    private val prefs: SharedPreferences = runCatching { create(context) }
        .recoverCatching {
            // Corrupted keyset (e.g. after restore). Reset rather than crash.
            Log.w("SecureStore", "Resetting encrypted prefs: ${it.javaClass.simpleName}")
            context.deleteSharedPreferences(FILE)
            create(context)
        }
        .recoverCatching {
            // v0.11: no keystore at all — keep the app working (secrets at rest unencrypted).
            Log.w("SecureStore", "Falling back to plain prefs: ${it.javaClass.simpleName}")
            context.getSharedPreferences("${FILE}_plain", Context.MODE_PRIVATE)
        }.getOrThrow()

    private fun create(context: Context): SharedPreferences {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun get(name: String): String? = prefs.getString(name, null)?.takeIf { it.isNotBlank() }
    override fun put(name: String, value: String?) {
        prefs.edit().apply { if (value.isNullOrBlank()) remove(name) else putString(name, value.trim()) }.apply()
    }
    fun has(name: String) = get(name) != null

    companion object { private const val FILE = "saqr_secrets" }
}
