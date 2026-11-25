package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.Mcp
import com.ctodrs.saqrcode.agent.McpClient
import com.ctodrs.saqrcode.agent.McpOAuth
import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * v0.14.1 «أجنحة مفتوحة · MCP عبر OAuth»: the PKCE primitives (verifier/challenge/state,
 * with the RFC 7636 Appendix B vector), well-known discovery URLs, metadata parsing, the
 * authorize URL, redirect parsing, token bodies and responses, the token/config/pending
 * codecs, encrypted per-server storage, the auth-header preference order, and the MCP
 * client's 401 handling over the wire (MockWebServer — no real sockets beyond localhost).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V0141FeaturesTest {

    private fun settings() = SettingsStore(
        RuntimeEnvironment.getApplication(), SecureStore(RuntimeEnvironment.getApplication()))

    // ------------------------------------------------- PKCE primitives

    @Test
    fun `code challenge follows the rfc 7636 appendix b test vector`() {
        // The RFC's own vector: S256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk").
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            McpOAuth.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `code verifier is 64 unreserved chars and never repeats`() {
        val unreserved = Regex("[A-Za-z0-9-._~]+")
        val a = McpOAuth.codeVerifier()
        val b = McpOAuth.codeVerifier()
        assertEquals(64, a.length)
        assertTrue(unreserved.matches(a))
        assertTrue(unreserved.matches(b))
        assertTrue(a != b)
        // The challenge of the generated verifier is base64url with no padding or + /.
        val ch = McpOAuth.codeChallenge(a)
        assertTrue(ch.none { it == '+' || it == '=' })
        assertFalse(ch.contains('/'))
    }

    @Test
    fun `state is url safe and unique`() {
        val s1 = McpOAuth.newState()
        val s2 = McpOAuth.newState()
        assertTrue(s1.length >= 20)
        assertTrue(s1.none { it == '+' || it == '=' || it == '/' })
        assertTrue(s1 != s2)
    }

    // ------------------------------------------------- discovery URLs

    @Test
    fun `well known urls keep path and port`() {
        assertEquals(
            "https://mcp.example.com/.well-known/oauth-protected-resource/mcp",
            McpOAuth.resourceMetadataUrl("https://mcp.example.com/mcp"),
        )
        assertEquals(
            "https://mcp.example.com/.well-known/oauth-protected-resource",
            McpOAuth.resourceMetadataUrl("https://mcp.example.com"),
        )
        assertEquals(
            "https://auth.example.com/.well-known/oauth-authorization-server/tenant1",
            McpOAuth.authServerMetadataUrl("https://auth.example.com/tenant1"),
        )
        assertEquals(
            "https://mcp.example.com:8443/.well-known/oauth-authorization-server",
            McpOAuth.authServerRootMetadataUrl("https://mcp.example.com:8443/mcp"),
        )
    }

    // ------------------------------------------------- metadata parsing

    @Test
    fun `protected resource metadata picks the first issuer`() {
        val two = JSONObject("""{"authorization_servers":["https://a.example.com","https://b.example.com"]}""")
        assertEquals("https://a.example.com", McpOAuth.parseProtectedResource(two))
        val blankFirst = JSONObject("""{"authorization_servers":["  ","https://b.example.com"]}""")
        assertEquals("https://b.example.com", McpOAuth.parseProtectedResource(blankFirst))
        assertNull(McpOAuth.parseProtectedResource(JSONObject("""{"other":1}""")))
        assertNull(McpOAuth.parseProtectedResource(JSONObject("""{"authorization_servers":[]}""")))
    }

    @Test
    fun `server metadata requires https endpoints and reads scopes`() {
        val ok = McpOAuth.parseServerMetadata(JSONObject(
            """{"authorization_endpoint":"https://a.example.com/authorize",
                "token_endpoint":"https://a.example.com/token",
                "scopes_supported":["mcp:read","mcp:write"]}"""))
        assertNotNull(ok)
        assertEquals("https://a.example.com/authorize", ok!!.authorizationEndpoint)
        assertEquals(listOf("mcp:read", "mcp:write"), ok.scopes)
        // Plain http is refused — tokens never travel unencrypted.
        assertNull(McpOAuth.parseServerMetadata(JSONObject(
            """{"authorization_endpoint":"http://a.example.com/authorize",
                "token_endpoint":"https://a.example.com/token"}""")))
        // Missing endpoints are refused.
        assertNull(McpOAuth.parseServerMetadata(JSONObject("""{"token_endpoint":"https://a/t"}""")))
        // Scopes are optional.
        val bare = McpOAuth.parseServerMetadata(JSONObject(
            """{"authorization_endpoint":"https://a/authorize","token_endpoint":"https://a/token"}"""))
        assertEquals(emptyList<String>(), bare!!.scopes)
    }

    // ------------------------------------------------- authorize URL

    @Test
    fun `authorize url carries every pkce parameter`() {
        val url = McpOAuth.authorizeUrl(
            "https://a.example.com/authorize", "my client", "saqr://oauth/callback",
            "ch_1", "st_1", listOf("mcp:read", "mcp:write"), "https://mcp.example.com/mcp")
        assertTrue(url.startsWith("https://a.example.com/authorize?"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=my%20client"))
        assertTrue(url.contains("redirect_uri=saqr%3A%2F%2Foauth%2Fcallback"))
        assertTrue(url.contains("code_challenge=ch_1"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("state=st_1"))
        assertTrue(url.contains("scope=mcp%3Aread%20mcp%3Awrite"))
        assertTrue(url.contains("resource=https%3A%2F%2Fmcp.example.com%2Fmcp"))
        // Spaces are always %20, never the form-style +.
        assertFalse(url.contains("+"))
        // An endpoint that already has a query gets & not a second ?.
        val url2 = McpOAuth.authorizeUrl("https://a/authorize?aud=x", "c", "r", "ch", "st", emptyList(), null)
        assertTrue(url2.contains("authorize?aud=x&response_type=code"))
        assertFalse(url2.contains("resource="))
        assertFalse(url2.contains("scope="))
    }

    // ------------------------------------------------- redirect parsing

    @Test
    fun `redirect parsing covers code error and garbage`() {
        val ok = McpOAuth.parseRedirect("saqr://oauth/callback?code=ab%20c&state=st1")
        assertEquals(McpOAuth.Redirect("ab c", "st1", null), ok)
        val denied = McpOAuth.parseRedirect("saqr://oauth/callback?error=access_denied&state=st1")
        assertEquals(McpOAuth.Redirect(null, "st1", "access_denied"), denied)
        // No query, or a redirect without a code, is unreadable.
        assertNull(McpOAuth.parseRedirect("saqr://oauth/callback"))
        assertNull(McpOAuth.parseRedirect("saqr://oauth/callback?state=x"))
    }

    // ------------------------------------------------- token bodies and responses

    @Test
    fun `token request body fields are readable via form field`() {
        val body = McpOAuth.tokenRequestBody("the code", "the verifier", "cid", "saqr://oauth/callback", "https://mcp.example.com/mcp")
        assertEquals("authorization_code", McpOAuth.formField(body, "grant_type"))
        assertEquals("the code", McpOAuth.formField(body, "code"))
        assertEquals("the verifier", McpOAuth.formField(body, "code_verifier"))
        assertEquals("cid", McpOAuth.formField(body, "client_id"))
        assertEquals("saqr://oauth/callback", McpOAuth.formField(body, "redirect_uri"))
        assertEquals("https://mcp.example.com/mcp", McpOAuth.formField(body, "resource"))
        // No resource parameter when none is given.
        val bare = McpOAuth.tokenRequestBody("c", "v", "cid", "r", null)
        assertNull(McpOAuth.formField(bare, "resource"))
    }

    @Test
    fun `refresh request body carries the grant`() {
        val body = McpOAuth.refreshRequestBody("rt 1", "cid")
        assertEquals("refresh_token", McpOAuth.formField(body, "grant_type"))
        assertEquals("rt 1", McpOAuth.formField(body, "refresh_token"))
        assertEquals("cid", McpOAuth.formField(body, "client_id"))
    }

    @Test
    fun `token response parsing honours expires in and refresh fallback`() {
        val now = 1_000_000L
        val full = McpOAuth.parseTokenResponse(JSONObject(
            """{"access_token":"at","refresh_token":"rt","expires_in":7200,"scope":"mcp:read"}"""), now)
        assertEquals(McpOAuth.Tokens("at", "rt", now + 7_200_000L, "mcp:read"), full)
        // Missing expires_in → the one-hour default.
        val def = McpOAuth.parseTokenResponse(JSONObject("""{"access_token":"at"}"""), now)
        assertEquals(now + 3_600_000L, def!!.expiresAt)
        // Absurdly short expires_in is clamped to 60 s.
        val clamped = McpOAuth.parseTokenResponse(JSONObject("""{"access_token":"at","expires_in":10}"""), now)
        assertEquals(now + 60_000L, clamped!!.expiresAt)
        // No rotating refresh_token → the previous set's token is kept.
        val kept = McpOAuth.parseTokenResponse(JSONObject("""{"access_token":"at2"}"""), now,
            McpOAuth.Tokens("at", "old-rt", now, ""))
        assertEquals("old-rt", kept!!.refreshToken)
        // No access_token → nothing to parse.
        assertNull(McpOAuth.parseTokenResponse(JSONObject("""{"error":"invalid_grant"}"""), now))
    }

    @Test
    fun `needs refresh applies the sixty second skew`() {
        val now = 5_000_000L
        val tokens = { expiresIn: Long -> McpOAuth.Tokens("at", null, now + expiresIn, "") }
        assertFalse(McpOAuth.needsRefresh(tokens(61_000L), now))   // just outside the skew
        assertTrue(McpOAuth.needsRefresh(tokens(60_000L), now))    // exactly at the skew
        assertTrue(McpOAuth.needsRefresh(tokens(0L), now))         // expired
        assertEquals("Bearer tok", McpOAuth.bearerOf(McpOAuth.Tokens("tok", null, 0, "")))
    }

    // ------------------------------------------------- codecs

    @Test
    fun `tokens codec round trip and garbage`() {
        val t = McpOAuth.Tokens("at", "rt", 123L, "scope")
        assertEquals(t, McpOAuth.decodeTokens(McpOAuth.encodeTokens(t)))
        // A token set without a refresh token round trips too.
        val bare = McpOAuth.Tokens("at", null, 5L, "")
        assertEquals(bare, McpOAuth.decodeTokens(McpOAuth.encodeTokens(bare)))
        assertNull(McpOAuth.decodeTokens(null))
        assertNull(McpOAuth.decodeTokens("not json"))
        assertNull(McpOAuth.decodeTokens("""{"refresh_token":"only"}"""))
    }

    @Test
    fun `configs codec round trip junk and blank ids`() {
        val map = mapOf(
            "server-a" to McpOAuth.Config("cid-a", listOf("mcp:read"), "https://a/authorize", "https://a/token", "https://mcp.a"),
            "server-b" to McpOAuth.Config("cid-b"),
        )
        assertEquals(map, McpOAuth.decodeConfigs(McpOAuth.encodeConfigs(map)))
        assertEquals(emptyMap<String, McpOAuth.Config>(), McpOAuth.decodeConfigs(null))
        assertEquals(emptyMap<String, McpOAuth.Config>(), McpOAuth.decodeConfigs("junk"))
        // A config without a client id is dropped on decode.
        val onlyB = McpOAuth.decodeConfigs("""{"a":{"client_id":"   "},"b":{"client_id":"ok"}}""")
        assertEquals(mapOf("b" to McpOAuth.Config("ok")), onlyB)
    }

    @Test
    fun `pending codec round trip skips incomplete`() {
        val p1 = McpOAuth.Pending("Server A", "st1", "ver1", "cid1", "saqr://oauth/callback", "https://a/token", "https://mcp.a")
        val p2 = McpOAuth.Pending("Server B", "st2", "ver2", "cid2", "saqr://oauth/callback", "https://b/token", null)
        val back = McpOAuth.decodePending(McpOAuth.encodePending(mapOf("st1" to p1, "st2" to p2)))
        assertEquals(2, back.size)
        assertEquals(p1, back["st1"])
        assertEquals(p2, back["st2"])
        // Incomplete entries (blank server / verifier / client id / token url) are dropped.
        assertEquals(emptyMap<String, McpOAuth.Pending>(),
            McpOAuth.decodePending("""{"st1":{"server":"","verifier":"v","client_id":"c","token_url":"t"}}"""))
        assertEquals(emptyMap<String, McpOAuth.Pending>(),
            McpOAuth.decodePending("""{"st1":{"server":"s","verifier":"","client_id":"c","token_url":"t"}}"""))
        // A redirect_uri-less pending falls back to the app's scheme.
        val def = McpOAuth.decodePending("""{"st1":{"server":"s","verifier":"v","client_id":"c","token_url":"t"}}""")
        assertEquals(McpOAuth.REDIRECT_URI, def["st1"]!!.redirectUri)
        assertEquals(emptyMap<String, McpOAuth.Pending>(), McpOAuth.decodePending(null))
    }

    // ------------------------------------------------- SettingsStore storage

    @Test
    fun `settings store keeps oauth config tokens and pending by slug`() {
        val s = settings()
        // Config keyed by the slugified server name — «X Server» and «x server» are one server.
        s.setMcpOAuthConfig("X Server", McpOAuth.Config("cid", listOf("mcp:read")))
        assertEquals("cid", s.mcpOAuthConfig("x server")!!.clientId)
        assertEquals("cid", s.mcpOAuthConfig("X Server")!!.clientId)
        s.setMcpOAuthConfig("X Server", null)
        assertNull(s.mcpOAuthConfig("X Server"))
        // Tokens live in the encrypted store and clear to null.
        val t = McpOAuth.Tokens("at", "rt", 99L, "scope")
        s.setMcpTokens("X Server", t)
        assertEquals(t, s.mcpTokens("X Server"))
        s.setMcpTokens("X Server", null)
        assertNull(s.mcpTokens("X Server"))
        // Pending flows persist by state and clear.
        val p = McpOAuth.Pending("X Server", "st9", "ver9", "cid", McpOAuth.REDIRECT_URI, "https://a/token", null)
        s.setMcpOAuthPending(p)
        assertEquals(p, s.mcpOAuthPending("st9"))
        s.clearMcpOAuthPending("st9")
        assertNull(s.mcpOAuthPending("st9"))
    }

    @Test
    fun `auth header prefers live oauth bearer then falls back`() {
        val s = settings()
        s.setMcpAuth("S2", "Basic manual")
        // No OAuth config → the manual header passes through.
        assertEquals("Basic manual", s.mcpAuthHeader("S2"))
        // OAuth config + a valid token → the Bearer header wins.
        s.setMcpOAuthConfig("S2", McpOAuth.Config("cid"))
        val now = System.currentTimeMillis()
        s.setMcpTokens("S2", McpOAuth.Tokens("live", "rt", now + 600_000L, ""))
        assertEquals("Bearer live", s.mcpAuthHeader("S2"))
        // An expired token falls back to the manual header until refresh succeeds.
        s.setMcpTokens("S2", McpOAuth.Tokens("stale", null, now - 1L, ""))
        assertEquals("Basic manual", s.mcpAuthHeader("S2"))
    }

    // ------------------------------------------------- MCP client over the wire

    @Test
    fun `mcp client flags 401 as unauthorized`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401))
        server.start()
        try {
            val cl = McpClient(Mcp.Server("T", server.url("/").toString().trimEnd('/'), "Bearer stale"), OkHttpClient())
            assertNull(cl.listTools())
            assertTrue(cl.unauthorized)
            assertTrue(cl.endpointError!!.contains("401"))
        } finally { server.shutdown() }
    }

    @Test
    fun `mcp client sends the bearer token on every request`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(
            """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26","capabilities":{},
                "serverInfo":{"name":"t","version":"1"}}}"""))
        server.enqueue(MockResponse().setResponseCode(200)) // the initialized notification
        server.enqueue(MockResponse().setBody(
            """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo","description":"Echoes"}]}}"""))
        server.start()
        try {
            val cl = McpClient(Mcp.Server("T", server.url("/").toString().trimEnd('/'), "Bearer abc123"), OkHttpClient())
            val tools = cl.listTools()
            assertFalse(cl.unauthorized)
            assertEquals(1, tools?.size)
            // All three requests (initialize, notification, tools/list) carry the header.
            assertEquals("Bearer abc123", server.takeRequest().getHeader("Authorization"))
            assertEquals("Bearer abc123", server.takeRequest().getHeader("Authorization"))
            assertEquals("Bearer abc123", server.takeRequest().getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test
    fun `other http errors keep the old message and never flag unauthorized`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        server.start()
        try {
            val cl = McpClient(Mcp.Server("T", server.url("/").toString().trimEnd('/'), null), OkHttpClient())
            assertNull(cl.listTools())
            assertFalse(cl.unauthorized)
            assertTrue(cl.endpointError!!.contains("503"))
        } finally { server.shutdown() }
    }
}
