package com.ctodrs.saqrcode.ui

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Banner carousel content. Edit `assets/developer_banners.json` to announce a new app or an offer:
 * each item has id, title/body as {"ar","en"}, an optional url and two gradient colours.
 */
object DeveloperBanners {
    data class Banner(val id: String, val titleAr: String, val titleEn: String, val bodyAr: String, val bodyEn: String, val url: String?, val colorA: Long, val colorB: Long) {
        fun title(ar: Boolean) = if (ar) titleAr else titleEn
        fun body(ar: Boolean) = if (ar) bodyAr else bodyEn
    }

    private fun hex(s: String, fallback: Long): Long = runCatching { 0xFF000000 or s.removePrefix("#").toLong(16) }.getOrDefault(fallback)

    /** Bad or missing items are skipped, never crash. */
    fun parse(json: String): List<Banner> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val t = o.optJSONObject("title"); val b = o.optJSONObject("body")
            val ar = t?.optString("ar").orEmpty(); val en = t?.optString("en").orEmpty()
            if (ar.isBlank() && en.isBlank()) return@mapNotNull null
            val c = o.optJSONArray("colors")
            Banner(
                o.optString("id", "b$i"), ar.ifBlank { en }, en.ifBlank { ar },
                b?.optString("ar").orEmpty().ifBlank { b?.optString("en").orEmpty() }, b?.optString("en").orEmpty().ifBlank { b?.optString("ar").orEmpty() },
                o.optString("url").takeIf { it.startsWith("https://") },
                hex(c?.optString(0).orEmpty(), 0xFF4F46E5), hex(c?.optString(1).orEmpty(), 0xFF7C3AED),
            )
        }
    }.getOrDefault(emptyList())

    fun load(ctx: Context): List<Banner> =
        runCatching { ctx.assets.open("developer_banners.json").bufferedReader().use { parse(it.readText()) } }.getOrDefault(emptyList())
}

/** "Check for updates" against the public releases of the project. */
object UpdateCheck {
    const val RELEASES_PAGE = "https://github.com/CTO-DRS/saqr-code-public/releases"
    const val API = "https://api.github.com/repos/CTO-DRS/saqr-code-public/releases?per_page=5"

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val version: String, val url: String) : State
        data object Failed : State
    }

    /** The API lists newest first. Versions were reset to 0.1.x, so compare by equality with the installed one, not by number. */
    fun parse(json: String, current: String): State = runCatching {
        val a = JSONArray(json)
        val o = (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { !it.optBoolean("draft") } ?: return State.Failed
        val v = o.getString("tag_name").removePrefix("v").removeSuffix("-debug")
        if (v == current) State.UpToDate else State.Available(v, o.optString("html_url").takeIf { it.startsWith("https://") } ?: RELEASES_PAGE)
    }.getOrDefault(State.Failed)

    /** Blocking; call off the main thread. */
    fun fetch(): String {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("User-Agent", "SaqrCode")
        return try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }
}
