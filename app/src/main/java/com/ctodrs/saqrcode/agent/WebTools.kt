package com.ctodrs.saqrcode.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * v0.7 · أدوات الويب · Web tools for the agent: `web_search` (DuckDuckGo HTML, no key needed)
 * and `web_fetch` (readable text of a page). A guard blocks private/loopback/link-local
 * addresses — the phone is a personal device, and the agent must not reach the LAN or
 * cloud metadata endpoints. Pure parsing/guard logic is unit-tested in V07FeaturesTest.
 */
object WebGuard {

    private val BLOCKED_HOSTS = setOf(
        "localhost", "metadata", "metadata.google.internal", "metadata.goog",
        "ip6-localhost", "ip6-loopback", "local",
    )

    data class Verdict(val allowed: Boolean, val reason: String = "")

    /** Scheme/host-level check (pure). [reason] explains the block; empty verdict reason when allowed. */
    fun check(url: String): Verdict {
        val u = url.trim()
        if (u.isEmpty()) return Verdict(false, "empty URL")
        val uri = runCatching { java.net.URI(u) }.getOrNull() ?: return Verdict(false, "unparseable URL")
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return Verdict(false, "only http/https URLs are allowed")
        if (!uri.userInfo.isNullOrEmpty()) return Verdict(false, "URLs with embedded credentials are not allowed")
        val host = uri.host?.lowercase() ?: return Verdict(false, "URL has no host")
        if (host in BLOCKED_HOSTS || host.endsWith(".local") || host.endsWith(".localhost")) {
            return Verdict(false, "private/internal hosts are blocked")
        }
        if (host.startsWith("[") || host.contains(':')) { // IPv6 literal
            val h = host.trim('[', ']')
            if (h == "::1" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80") || h.startsWith("::1")) {
                return Verdict(false, "private/internal addresses are blocked")
            }
            return Verdict(true)
        }
        val v4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})${'$'}""").find(host)
        if (v4 != null) {
            val a = v4.groupValues[1].toIntOrNull() ?: return Verdict(false, "bad address")
            val b = v4.groupValues[2].toIntOrNull() ?: 0
            val blocked = when (a) {
                0, 10, 127 -> true
                169 -> b == 254
                172 -> b in 16..31
                192 -> b == 168 || (b == 0 && v4.groupValues[3] == "0") // 192.168.* and 192.0.0.*
                198 -> b == 18 || b == 19
                100 -> b in 64..127
                else -> false
            }
            if (blocked) return Verdict(false, "private/internal addresses are blocked")
        }
        return Verdict(true)
    }

    /** DNS-level check: every resolved address must be public (anti-rebinding best effort). */
    fun checkResolved(host: String): Verdict = runCatching {
        InetAddress.getAllByName(host).forEach { addr ->
            if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress || addr.isAnyLocalAddress) {
                return Verdict(false, "host resolves to a private address")
            }
        }
        Verdict(true)
    }.getOrElse { Verdict(false, "cannot resolve host: ${it.message}") }
}

/** Parser for DuckDuckGo's HTML endpoint (no JS, no key). */
object DdgParser {

    data class Result(val title: String, val url: String, val snippet: String)

    private val TITLE_RE = Regex("""class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val SNIPPET_RE = Regex("""class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

    fun parse(html: String, max: Int = 6): List<Result> {
        val titles = TITLE_RE.findAll(html).toList()
        val snippets = SNIPPET_RE.findAll(html).map { stripTags(it.groupValues[1]) }.toList()
        val out = ArrayList<Result>(max)
        for ((i, m) in titles.withIndex()) {
            if (out.size >= max) break
            val title = stripTags(m.groupValues[2]).trim()
            val url = unwrap(m.groupValues[1])
            if (title.isEmpty() || url.isEmpty()) continue
            out += Result(title, url, snippets.getOrNull(i).orEmpty())
        }
        return out
    }

    /** DDG wraps results in `//duckduckgo.com/l/?uddg=<encoded>&rut=…`; unwrap to the real URL. */
    fun unwrap(href: String): String {
        var u = href.trim()
        if (u.startsWith("//")) u = "https:" + u
        val marker = "uddg="
        val idx = u.indexOf(marker)
        if (idx >= 0) {
            val rest = u.substring(idx + marker.length)
            val end = rest.indexOf('&')
            val enc = if (end >= 0) rest.substring(0, end) else rest
            runCatching { return URLDecoder.decode(enc, "UTF-8") }
        }
        return u
    }

    private val ENTITIES = Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z]+);""")

    /** Removes tags and decodes the common HTML entities. */
    fun stripTags(s: String): String {
        var t = s.replace(Regex("""<[^>]*>"""), " ")
        t = ENTITIES.replace(t) { m ->
            val e = m.groupValues[1]
            when (e.lowercase()) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                else -> if (e.startsWith("#x") || e.startsWith("#X")) {
                    e.drop(2).toLongOrNull(16)?.toChar()?.toString() ?: ""
                } else if (e.startsWith("#")) {
                    e.drop(1).toLongOrNull()?.toChar()?.toString() ?: ""
                } else ""
            }
        }
        return t.replace(Regex("""\s+"""), " ").trim()
    }
}

object WebTools {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private const val UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36 SaqrCode/0.7"

    /** `web_search` tool body: DuckDuckGo HTML search → formatted numbered results. */
    suspend fun search(query: String, max: Int = 6): String = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext "Error: empty search query."
        val url = "https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(q, "UTF-8")
        val html = try {
            get(url)
        } catch (e: Exception) {
            return@withContext "Error: web search failed (${e.message ?: e.javaClass.simpleName})."
        }
        val results = DdgParser.parse(html, max.coerceIn(1, 10))
        if (results.isEmpty()) "No results for \"$q\". Try different keywords."
        else buildString {
            appendLine("Web results for \"$q\":")
            results.forEachIndexed { i, r ->
                appendLine("${i + 1}. ${r.title}")
                appendLine("   ${r.url}")
                if (r.snippet.isNotEmpty()) appendLine("   ${r.snippet.take(240)}")
            }
            append("Use web_fetch on a URL to read the full page.")
        }
    }

    /** `web_fetch` tool body: GET a page, guard private nets, return readable text. */
    suspend fun fetch(url: String, maxChars: Int = 8_000): String = withContext(Dispatchers.IO) {
        val u = url.trim()
        val g = WebGuard.check(u)
        if (!g.allowed) return@withContext "Blocked by safety policy: ${g.reason}."
        val uri = runCatching { java.net.URI(u) }.getOrElse { return@withContext "Error: unparseable URL." }
        val hostGuard = uri.host?.let { WebGuard.checkResolved(it) } ?: return@withContext "Error: URL has no host."
        if (!hostGuard.allowed) return@withContext "Blocked by safety policy: ${hostGuard.reason}."
        val body = try {
            get(u)
        } catch (e: Exception) {
            return@withContext "Error: fetch failed (${e.message ?: e.javaClass.simpleName})."
        }
        val text = htmlToText(body)
        if (text.isBlank()) "(empty or non-HTML page, ${body.length} bytes raw)"
        else {
            val cap = maxChars.coerceIn(200, 20_000)
            (if (text.length > cap) text.take(cap) + "\n…[truncated — fetch with a larger max_chars or read the source file]" else text)
        }
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "en,ar;q=0.8").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val ct = resp.header("Content-Type") ?: "text/html"
            val body = resp.body?.string() ?: ""
            if (ct.contains("json", ignoreCase = true)) {
                // pretty-print small JSON so the model can read API responses
                return runCatching { JSONObject(body).toString(2) }.getOrElse { body }
            }
            return body
        }
    }

    /** HTML → readable text: drops script/style/comments, converts block ends to newlines. */
    fun htmlToText(html: String): String {
        var t = html
            .replace(Regex("""(?is)<script\b.*?</script>"""), " ")
            .replace(Regex("""(?is)<style\b.*?</style>"""), " ")
            .replace(Regex("""(?is)<noscript\b.*?</noscript>"""), " ")
            .replace(Regex("""(?is)<!--.*?-->"""), " ")
            .replace(Regex("""(?i)<(br|/p|/div|/li|/h[1-6]|/tr|/section|/article|/header|/footer)\s*/?>"""), "\n")
        t = DdgParser.stripTags(t)
        t = t.replace(Regex("""[ \t]+"""), " ").replace(Regex("""\n\s*\n\s*\n+"""), "\n\n")
        return t.trim()
    }
}
