package com.ctodrs.saqrcode.cloud

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * v0.1.67 · Zero-setup Free AI. A fresh install asks the Saqr proxy for its own token
 * (it only unlocks the free AI route, never the cloud machine) and uses it as the key for
 * the built-in "saqr-free" provider. The user never pastes anything.
 */
object FreeAi {
    const val PROVIDER_ID = "saqr-free"

    /** A random, stable id for this install (16+ url-safe characters). */
    fun newInstallId(): String = java.util.UUID.randomUUID().toString().replace("-", "")

    /** Pure parse of the proxy's /register reply; null when it is not a usable token. */
    fun parseToken(body: String): String? = try {
        JSONObject(body).optString("token").takeIf { it.startsWith("i1.") && it.length > 20 }
    } catch (e: Exception) { null }

    /** Ask the proxy for a token for [installId]. Null on any failure (offline, proxy down); the caller retries later. */
    suspend fun register(baseUrl: String, installId: String): String? = withContext(Dispatchers.IO) {
        try {
            val c = URL(baseUrl.trimEnd('/') + "/register").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.outputStream.use { it.write(JSONObject().put("install", installId).toString().toByteArray()) }
            if (c.responseCode !in 200..299) return@withContext null
            parseToken(c.inputStream.bufferedReader().readText())
        } catch (e: Exception) { null }
    }
}
