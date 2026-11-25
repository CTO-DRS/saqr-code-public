package com.ctodrs.saqrcode.agent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * v0.14.1 · MCP عبر OAuth · MCP over OAuth (PKCE).
 *
 * The MCP authorization spec (2025-03-26) secures protected MCP servers with OAuth 2.1:
 * the client discovers the authorization server's metadata (RFC 8414 / MCP protected-resource
 * metadata), runs an authorization-code flow with PKCE (RFC 7636, S256 only) inside the system
 * browser, exchanges the code at the token endpoint and refreshes tokens when they age out.
 *
 * Everything protocol-level here is pure and unit-tested in V0141FeaturesTest; the network
 * half is quarantined in [McpOAuthHttp] (a thin OkHttp wrapper) so tests never touch sockets.
 * Tokens live in the encrypted SecureStore keyed `mcp_oauth_<slug>`; the per-server config
 * (client id, scopes, endpoints) is non-secret and stays in plain prefs.
 */
object McpOAuth {

    /** The redirect lands back on the app through this custom scheme (declared in the manifest). */
    const val REDIRECT_URI = "saqr://oauth/callback"
    const val CHALLENGE_METHOD = "S256"

    /** RFC 7636 unreserved set: the verifier may only contain these. */
    private const val VERIFIER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private const val VERIFIER_LEN = 64

    private val random = SecureRandom()

    // ---------------------------------------------------------------- PKCE (pure)

    /** A fresh `code_verifier`: 64 unreserved characters (RFC 7636 allows 43–128). */
    fun codeVerifier(): String {
        val bytes = ByteArray(VERIFIER_LEN)
        random.nextBytes(bytes)
        return buildString(VERIFIER_LEN) {
            for (b in bytes) append(VERIFIER_CHARS[(b.toInt() and 0xFF) % VERIFIER_CHARS.length])
        }
    }

    /** `code_challenge` = BASE64URL-UNPADDED(SHA-256(ASCII(verifier))) — the S256 method. */
    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    /** Anti-forgery `state` for one authorization flow; must survive the browser round-trip. */
    fun newState(): String {
        val bytes = ByteArray(24)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    // ---------------------------------------------------------------- discovery URLs (pure)

    /** MCP protected-resource metadata: `/.well-known/oauth-protected-resource[/path]` at the server host. */
    fun resourceMetadataUrl(serverUrl: String): String = wellKnownUrl(serverUrl, "oauth-protected-resource")

    /** RFC 8414 authorization-server metadata: the well-known segment is inserted before the issuer path. */
    fun authServerMetadataUrl(issuerUrl: String): String = wellKnownUrl(issuerUrl, "oauth-authorization-server")

    /** Fallback for servers whose issuer is the bare host: the well-known document sits at the root. */
    fun authServerRootMetadataUrl(serverUrl: String): String {
        val u = URL(serverUrl)
        return baseUrlOf(u) + "/.well-known/oauth-authorization-server"
    }

    private fun baseUrlOf(u: URL): String =
        u.protocol + "://" + u.host + if (u.port > 0) ":" + u.port else ""

    private fun wellKnownUrl(url: String, doc: String): String {
        val u = URL(url)
        val path = u.path?.trim('/') ?: ""
        val base = baseUrlOf(u) + "/.well-known/" + doc
        return if (path.isEmpty()) base else "$base/$path"
    }

    // ---------------------------------------------------------------- metadata parsing (pure)

    /** One authorization server as discovered from its metadata document. */
    data class AuthServer(val authorizationEndpoint: String, val tokenEndpoint: String, val scopes: List<String>)

    /** `{"authorization_servers": ["https://auth.example.com", …]}` → the first issuer, or null. */
    fun parseProtectedResource(json: JSONObject): String? {
        val arr = json.optJSONArray("authorization_servers") ?: return null
        for (i in 0 until arr.length()) {
            val s = arr.optString(i).trim()
            if (s.isNotEmpty()) return s
        }
        return null
    }

    /**
     * RFC 8414 §2 (server metadata) → [AuthServer]; both endpoints are mandatory,
     * must be absolute https, and `scopes_supported` is optional.
     */
    fun parseServerMetadata(json: JSONObject): AuthServer? {
        val auth = json.optString("authorization_endpoint").trim()
        val token = json.optString("token_endpoint").trim()
        if (auth.isEmpty() || token.isEmpty()) return null
        if (!auth.startsWith("https://") || !token.startsWith("https://")) return null
        val scopes = json.optJSONArray("scopes_supported")?.let { arr ->
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().ifEmpty { null } }
        } ?: emptyList()
        return AuthServer(auth, token, scopes)
    }

    // ---------------------------------------------------------------- authorize URL (pure)

    /** Builds the browser URL of one PKCE authorization request. */
    fun authorizeUrl(
        authorizationEndpoint: String,
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        state: String,
        scopes: List<String>,
        resource: String? = null,
    ): String = buildString {
        append(authorizationEndpoint)
        append(if (authorizationEndpoint.contains('?')) '&' else '?')
        append("response_type=code")
        append("&client_id=").append(enc(clientId))
        append("&redirect_uri=").append(enc(redirectUri))
        append("&code_challenge=").append(enc(codeChallenge))
        append("&code_challenge_method=").append(CHALLENGE_METHOD)
        append("&state=").append(enc(state))
        if (scopes.isNotEmpty()) append("&scope=").append(enc(scopes.joinToString(" ")))
        resource?.takeIf { it.isNotBlank() }?.let { append("&resource=").append(enc(it)) }
    }

    // ---------------------------------------------------------------- redirect (pure)

    /** What the browser handed back: a code + state on success, an `error` on denial. */
    data class Redirect(val code: String?, val state: String?, val error: String?)

    /** Parses `saqr://oauth/callback?code=…&state=…` (or `?error=access_denied&state=…`). */
    fun parseRedirect(uri: String): Redirect? {
        val q = uri.substringAfter('?', "").takeIf { it.isNotEmpty() } ?: return null
        var code: String? = null
        var state: String? = null
        var error: String? = null
        for (pair in q.split('&')) {
            val i = pair.indexOf('=')
            if (i <= 0) continue
            val k = URLDecoder.decode(pair.substring(0, i), "UTF-8")
            val v = URLDecoder.decode(pair.substring(i + 1), "UTF-8")
            when (k) {
                "code" -> code = v
                "state" -> state = v
                "error" -> error = v
            }
        }
        if (error != null) return Redirect(null, state, error)
        if (code.isNullOrBlank()) return null
        return Redirect(code, state, null)
    }

    // ---------------------------------------------------------------- token requests (pure)

    /** `application/x-www-form-urlencoded` body of the authorization-code exchange. */
    fun tokenRequestBody(code: String, verifier: String, clientId: String, redirectUri: String, resource: String? = null): String =
        buildString {
            append("grant_type=authorization_code")
            append("&code=").append(enc(code))
            append("&code_verifier=").append(enc(verifier))
            append("&client_id=").append(enc(clientId))
            append("&redirect_uri=").append(enc(redirectUri))
            resource?.takeIf { it.isNotBlank() }?.let { append("&resource=").append(enc(it)) }
        }

    /** Body of the refresh-token grant (RFC 6749 §6). */
    fun refreshRequestBody(refreshToken: String, clientId: String): String =
        "grant_type=refresh_token&refresh_token=" + enc(refreshToken) + "&client_id=" + enc(clientId)

    /** Extracts the form field of one `k=v&…` body (for tests and debugging). */
    fun formField(body: String, key: String): String? =
        body.split('&').firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.let { URLDecoder.decode(it, "UTF-8") }

    // ---------------------------------------------------------------- tokens (pure)

    /** One granted token set; [expiresAt] is epoch ms (expires_in, 1h default). */
    data class Tokens(val accessToken: String, val refreshToken: String?, val expiresAt: Long, val scope: String)

    /**
     * Parses a token-endpoint response. `refresh_token` falls back to the previous set's
     * token when the server does not rotate it (RFC 6749 §6 allows omission).
     */
    fun parseTokenResponse(json: JSONObject, now: Long, previous: Tokens? = null): Tokens? {
        val at = json.optString("access_token").trim()
        if (at.isEmpty()) return null
        val expiresIn = if (json.has("expires_in")) json.optLong("expires_in", 3600L).coerceAtLeast(60L) else 3600L
        val rt = json.optString("refresh_token").trim().ifEmpty { null } ?: previous?.refreshToken
        return Tokens(at, rt, now + expiresIn * 1000L, json.optString("scope").trim())
    }

    /** The `Authorization` header value the MCP client sends. */
    fun bearerOf(tokens: Tokens): String = "Bearer " + tokens.accessToken

    /** True when the token is expired or inside the 60s refresh skew — refresh before use. */
    fun needsRefresh(tokens: Tokens, now: Long, skewMs: Long = 60_000L): Boolean =
        tokens.expiresAt - skewMs <= now

    // ---------------------------------------------------------------- storage codecs (pure)

    /** Per-server non-secret config: client id, scopes, and optional endpoint overrides. */
    data class Config(
        val clientId: String,
        val scopes: List<String> = emptyList(),
        val authUrl: String? = null,
        val tokenUrl: String? = null,
        val resource: String? = null,
    )

    fun encodeTokens(t: Tokens): String = JSONObject()
        .put("access_token", t.accessToken)
        .put("refresh_token", t.refreshToken ?: "")
        .put("expires_at", t.expiresAt)
        .put("scope", t.scope)
        .toString()

    fun decodeTokens(json: String?): Tokens? = runCatching {
        val o = JSONObject(json ?: return null)
        val at = o.optString("access_token").trim()
        if (at.isEmpty()) return null
        Tokens(at, o.optString("refresh_token").ifEmpty { null }, o.optLong("expires_at", 0L), o.optString("scope"))
    }.getOrNull()

    fun encodeConfigs(map: Map<String, Config>): String = JSONObject().apply {
        map.forEach { (slug, cfg) ->
            put(slug, JSONObject()
                .put("client_id", cfg.clientId)
                .put("scopes", JSONArray(cfg.scopes))
                .put("auth_url", cfg.authUrl ?: "")
                .put("token_url", cfg.tokenUrl ?: "")
                .put("resource", cfg.resource ?: ""))
        }
    }.toString()

    fun decodeConfigs(json: String?): Map<String, Config> = runCatching {
        val o = JSONObject(json ?: return emptyMap())
        val out = HashMap<String, Config>()
        o.keys().forEach { slug ->
            val c = o.optJSONObject(slug) ?: return@forEach
            val id = c.optString("client_id").trim()
            if (id.isEmpty()) return@forEach
            val scopes = c.optJSONArray("scopes")?.let { arr ->
                (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().ifEmpty { null } }
            } ?: emptyList()
            out[slug] = Config(
                id, scopes,
                c.optString("auth_url").ifEmpty { null },
                c.optString("token_url").ifEmpty { null },
                c.optString("resource").ifEmpty { null },
            )
        }
        out
    }.getOrDefault(emptyMap())

    /**
     * One in-flight PKCE flow, persisted by `state` so the callback activity can find it
     * after the browser round-trip. The verifier is a short-lived single-use secret that is
     * worthless without the browser's authorization session.
     */
    data class Pending(
        val serverName: String,
        val state: String,
        val verifier: String,
        val clientId: String,
        val redirectUri: String,
        val tokenUrl: String,
        val resource: String? = null,
    )

    fun encodePending(map: Map<String, Pending>): String = JSONObject().apply {
        map.forEach { (state, p) ->
            put(state, JSONObject()
                .put("server", p.serverName)
                .put("verifier", p.verifier)
                .put("client_id", p.clientId)
                .put("redirect_uri", p.redirectUri)
                .put("token_url", p.tokenUrl)
                .put("resource", p.resource ?: ""))
        }
    }.toString()

    fun decodePending(json: String?): Map<String, Pending> = runCatching {
        val o = JSONObject(json ?: return emptyMap())
        val out = HashMap<String, Pending>()
        o.keys().forEach { state ->
            val p = o.optJSONObject(state) ?: return@forEach
            val server = p.optString("server").trim()
            val verifier = p.optString("verifier").trim()
            val clientId = p.optString("client_id").trim()
            val tokenUrl = p.optString("token_url").trim()
            if (server.isEmpty() || verifier.isEmpty() || clientId.isEmpty() || tokenUrl.isEmpty()) return@forEach
            out[state] = Pending(
                server, state, verifier, clientId,
                p.optString("redirect_uri").ifEmpty { REDIRECT_URI },
                tokenUrl,
                p.optString("resource").ifEmpty { null },
            )
        }
        out
    }.getOrDefault(emptyMap())

    // ---------------------------------------------------------------- encoding helper

    /** Percent-encodes a query/form value; `+` never appears (spaces are always `%20`). */
    fun enc(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20").replace("%7E", "~")
}

/**
 * v0.14.1 · the network half of MCP OAuth — thin OkHttp wrappers kept far from the pure
 * protocol code so unit tests never open a socket. OAuth error responses arrive as JSON
 * bodies on 4xx too, so [postToken] returns the parsed body regardless of the status code.
 */
object McpOAuthHttp {

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val FORM = "application/x-www-form-urlencoded".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** GETs a JSON metadata document (`Accept: application/json`); null on any failure. */
    fun getJson(url: String): JSONObject? = runCatching {
        val req = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "SaqrCode/0.14")
            .get()
        client.newCall(req.build()).execute().use { resp ->
            if (!resp.isSuccessful) return@runCatching null
            JSONObject(resp.body!!.string())
        }
    }.getOrNull()

    /** POSTs a form body to a token endpoint and parses the JSON body (success or OAuth error). */
    fun postToken(url: String, formBody: String): JSONObject? = runCatching {
        val req = Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "SaqrCode/0.14")
            .post(formBody.toRequestBody(FORM))
        client.newCall(req.build()).execute().use { resp ->
            JSONObject(resp.body!!.string())
        }
    }.getOrNull()

    /**
     * Discovery: MCP protected-resource metadata names the issuer, then RFC 8414 metadata
     * names the endpoints; a direct well-known lookup on the server URL is the fallback.
     */
    fun discoverEndpoints(serverUrl: String): McpOAuth.AuthServer? {
        val issuer = McpOAuthHttp.getJson(McpOAuth.resourceMetadataUrl(serverUrl))
            ?.let { McpOAuth.parseProtectedResource(it) }
        val candidates = buildList {
            issuer?.let { add(McpOAuth.authServerMetadataUrl(it)) }
            add(McpOAuth.authServerMetadataUrl(serverUrl))
            add(McpOAuth.authServerRootMetadataUrl(serverUrl))
        }
        for (url in candidates) {
            val md = getJson(url) ?: continue
            val server = McpOAuth.parseServerMetadata(md) ?: continue
            return server
        }
        return null
    }
}

/**
 * v0.14.1 · runtime glue between the OAuth store and the agent turn: refreshes an
 * expiring token (best effort) right before the MCP client connects. Skipped entirely
 * in desert mode — [AgentSession.refreshMcpTools] never reaches this for remote servers.
 */
object McpOAuthRuntime {

    /**
     * Refreshes the tokens of [serverName] when [McpOAuth.needsRefresh] fires and a refresh
     * token exists. Returns true when usable tokens are in place (valid or refreshed).
     */
    fun maybeRefresh(settings: com.ctodrs.saqrcode.data.SettingsStore, serverName: String, now: Long = System.currentTimeMillis()): Boolean {
        val cfg = settings.mcpOAuthConfig(serverName) ?: return false
        val tokens = settings.mcpTokens(serverName) ?: return false
        if (!McpOAuth.needsRefresh(tokens, now)) return true
        val rt = tokens.refreshToken ?: return false
        val endpoint = cfg.tokenUrl ?: return false
        val json = McpOAuthHttp.postToken(endpoint, McpOAuth.refreshRequestBody(rt, cfg.clientId)) ?: return false
        val fresh = McpOAuth.parseTokenResponse(json, now, tokens) ?: return false
        settings.setMcpTokens(serverName, fresh)
        return true
    }
}
