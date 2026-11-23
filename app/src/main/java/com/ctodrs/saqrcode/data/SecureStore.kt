package com.ctodrs.saqrcode.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secrets (API keys, GitHub token) live in EncryptedSharedPreferences whose master key
 * is held by the Android Keystore (AES-256-GCM). Never logged, never backed up.
 */
class SecureStore(context: Context) : KeyValue {
    private val prefs: SharedPreferences = try {
        create(context)
    } catch (e: Exception) {
        // Corrupted keyset (e.g. after restore). Reset rather than crash.
        Log.w("SecureStore", "Resetting encrypted prefs: ${e.javaClass.simpleName}")
        context.deleteSharedPreferences(FILE)
        create(context)
    }

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
