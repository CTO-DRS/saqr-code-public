package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.McpStdio
import com.ctodrs.saqrcode.agent.Mcp
import com.ctodrs.saqrcode.billing.Entitlement
import com.ctodrs.saqrcode.billing.Entitlements
import com.ctodrs.saqrcode.billing.PlanTier
import com.ctodrs.saqrcode.billing.Plans
import com.ctodrs.saqrcode.payments.LicenseCodec
import com.ctodrs.saqrcode.payments.Moyasar
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** v0.11 · plans/entitlements, Moyasar invoices, offline license tokens, stdio MCP config. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V011FeaturesTest {

    private val key = LicenseCodec.decodeKey("7f637a5e79e49160fa5e7c278a40782cd55479684292a24af06dd634ed5d0b6b".repeat(1))!!
    private val now = 1_900_000_000_000L // fixed epoch (2030) — must stay ahead of the wall clock

    // ------------------------------------------------------------ plans & limits

    @Test
    fun `plan catalog has unique ids and SAR prices in halalas`() {
        assertEquals(3, Plans.CATALOG.size)
        assertEquals(Plans.CATALOG.map { it.id }.toSet().size, Plans.CATALOG.size)
        assertEquals(1500, Plans.PRO_MONTHLY.priceHalalas)   // SAR 15
        assertEquals(500, Plans.STUDENT_MONTHLY.priceHalalas) // SAR 5
        assertEquals(14900, Plans.LIFETIME.priceHalalas)      // SAR 149
        assertTrue(Plans.PRO_MONTHLY.monthly && Plans.STUDENT_MONTHLY.monthly)
        assertFalse(Plans.LIFETIME.monthly)
    }

    @Test
    fun `amount text formats two decimals`() {
        assertEquals("15.00", Plans.amountText(Plans.PRO_MONTHLY))
        assertEquals("5.00", Plans.amountText(Plans.STUDENT_MONTHLY))
        assertEquals("149.00", Plans.amountText(Plans.LIFETIME))
    }

    @Test
    fun `free limits are bounded and pro limits are lifted`() {
        val free = Plans.limitsFor(PlanTier.FREE)
        assertEquals(3, free.maxProjects)
        assertEquals(1, free.maxMcpServers)
        assertEquals(1, free.maxGitHosts)
        assertFalse(free.teamAgents)
        assertFalse(free.signedRelease)

        for (tier in listOf(PlanTier.PRO, PlanTier.STUDENT, PlanTier.LIFETIME)) {
            val lim = Plans.limitsFor(tier)
            assertTrue(lim.unlimitedProjects)
            assertTrue(lim.teamAgents)
            assertTrue(lim.signedRelease)
        }
    }

    @Test
    fun `play product ids map back to plans`() {
        assertEquals(Plans.PRO_MONTHLY, Plans.byPlayProduct("saqr_pro_monthly"))
        assertEquals(Plans.STUDENT_MONTHLY, Plans.byPlayProduct("saqr_student_monthly"))
        assertEquals(Plans.LIFETIME, Plans.byPlayProduct("saqr_lifetime"))
        assertNull(Plans.byPlayProduct("unknown.sku"))
    }

    // ------------------------------------------------------------ entitlements

    private fun ent(tier: PlanTier = PlanTier.PRO, exp: Long? = now + 1000, source: String = "license") =
        Entitlement(tier, source, exp, "order-1", "a@b.c")

    @Test
    fun `entitlement states resolve active grace and expired`() {
        assertEquals(Entitlements.State.ACTIVE, Entitlements.stateOf(ent(), now))
        assertEquals(Entitlements.State.GRACE, Entitlements.stateOf(ent(), now + 1000 + 2 * DAY))
        assertEquals(Entitlements.State.EXPIRED, Entitlements.stateOf(ent(), now + 1000 + 4 * DAY))
        assertEquals(Entitlements.State.ACTIVE, Entitlements.stateOf(ent(exp = null), now))
        assertEquals(Entitlements.State.FREE, Entitlements.stateOf(null, now))
    }

    @Test
    fun `effective tier keeps paid tier in grace and drops to free after expiry`() {
        assertEquals(PlanTier.PRO, Entitlements.effectiveTier(ent(), now + 1000 + 2 * DAY))
        assertEquals(PlanTier.FREE, Entitlements.effectiveTier(ent(), now + 1000 + 4 * DAY))
    }

    @Test
    fun `renewMonthly extends from expiry while active else from now`() {
        val future = now + 10 * DAY
        val fromActive = Entitlements.renewMonthly(ent(exp = future), now)
        assertEquals(future + Entitlements.MONTH_MS, fromActive)
        val past = now - 10 * DAY
        val fromExpired = Entitlements.renewMonthly(ent(exp = past), now)
        assertEquals(now + Entitlements.MONTH_MS, fromExpired)
        assertEquals(now + Entitlements.MONTH_MS, Entitlements.renewMonthly(null, now))
    }

    @Test
    fun `merge keeps the higher tier and the longer expiry`() {
        val pro = ent(PlanTier.PRO, exp = now + 10 * DAY)
        val student = ent(PlanTier.STUDENT, exp = now + 30 * DAY)
        assertEquals(pro, Entitlements.merge(student, pro))
        assertEquals(pro, Entitlements.merge(pro, student))
        val longer = ent(PlanTier.PRO, exp = now + 40 * DAY)
        assertEquals(longer, Entitlements.merge(pro, longer))
        val lifetime = ent(PlanTier.LIFETIME, exp = null)
        assertEquals(lifetime, Entitlements.merge(pro, lifetime))
    }

    @Test
    fun `entitlement json roundtrips`() {
        val e = ent(PlanTier.STUDENT, exp = now + 5000)
        assertEquals(e, Entitlements.decode(Entitlements.encode(e)))
        val life = ent(PlanTier.LIFETIME, exp = null)
        assertEquals(life, Entitlements.decode(Entitlements.encode(life)))
        assertNull(Entitlements.decode(null))
        assertNull(Entitlements.decode("not json"))
        assertNull(Entitlements.decode("{\"tier\":\"banana\"}"))
    }

    // ------------------------------------------------------------ Moyasar

    @Test
    fun `moyasar amounts convert between SAR and halalas`() {
        assertEquals(1500, Moyasar.amountHalalas(15.0))
        assertEquals(501, Moyasar.amountHalalas(5.01))
        assertEquals(150, Moyasar.amountHalalas(1.5))
        assertEquals("SAR 15.00", Moyasar.formatSar(1500))
        assertEquals("SAR 149.50", Moyasar.formatSar(14950))
    }

    @Test
    fun `moyasar auth header is basic with key and empty password`() {
        val h = Moyasar.authHeader("sk_test_x")
        assertTrue(h.startsWith("Basic "))
        val decoded = String(java.util.Base64.getDecoder().decode(h.removePrefix("Basic ")))
        assertEquals("sk_test_x:", decoded)
    }

    @Test
    fun `create invoice body carries required fields and metadata`() {
        val body = Moyasar.createInvoiceBody(
            Plans.PRO_MONTHLY, "Saqr Code pro_monthly",
            successUrl = "https://example.com/success",
            metadata = mapOf("saqr_plan" to "pro_monthly", "saqr_email" to "a@b.c"),
        )
        assertEquals(1500, body.getInt("amount"))
        assertEquals("SAR", body.getString("currency"))
        assertEquals("Saqr Code pro_monthly", body.getString("description"))
        assertEquals("https://example.com/success", body.getString("success_url"))
        val md = body.getJSONObject("metadata")
        assertEquals("pro_monthly", md.getString("saqr_plan"))
        assertEquals("a@b.c", md.getString("saqr_email"))
    }

    @Test
    fun `invoice status mapping covers moyasar values and keeps unknown as initiated`() {
        assertEquals(Moyasar.InvoiceStatus.PAID, Moyasar.statusOf("paid"))
        assertEquals(Moyasar.InvoiceStatus.EXPIRED, Moyasar.statusOf("Expired"))
        assertEquals(Moyasar.InvoiceStatus.FAILED, Moyasar.statusOf("failed"))
        assertEquals(Moyasar.InvoiceStatus.CANCELED, Moyasar.statusOf("canceled"))
        assertEquals(Moyasar.InvoiceStatus.INITIATED, Moyasar.statusOf("initiated"))
        assertEquals(Moyasar.InvoiceStatus.INITIATED, Moyasar.statusOf("weird-value"))
        assertEquals(Moyasar.InvoiceStatus.INITIATED, Moyasar.statusOf(null))
    }

    @Test
    fun `invoice parsing reads id status url and amount`() {
        val json = JSONObject()
            .put("id", "inv_123")
            .put("status", "paid")
            .put("url", "https://pay.moyasar.com/inv_123")
            .put("amount", 1500)
            .put("currency", "SAR")
            .put("description", "Saqr Code pro_monthly")
        val inv = Moyasar.parseInvoice(json)
        assertEquals("inv_123", inv.id)
        assertEquals(Moyasar.InvoiceStatus.PAID, inv.status)
        assertEquals("https://pay.moyasar.com/inv_123", inv.url)
        assertEquals(1500, inv.amountHalalas)
    }

    @Test
    fun `moyasar error body extracts a readable message`() {
        val err = Moyasar.errorOf("{\"message\":\"Validation failed\",\"errors\":{\"amount\":[\"must be >= 100\"]}}")
        assertTrue(err!!.contains("Validation failed"))
        assertNull(Moyasar.errorOf(null))
        assertNull(Moyasar.errorOf("{\"other\":1}"))
    }

    // ------------------------------------------------------------ license tokens

    @Test
    fun `license token roundtrips with tier email and order`() {
        val exp = now + 10 * DAY
        val token = LicenseCodec.sign(LicenseCodec.TIER_PRO, "user@example.com", "inv_9", exp, now, key)
        assertTrue(token.startsWith("SQ1."))
        assertEquals(3, token.split('.').size)
        val v = LicenseCodec.verify(token, key, now).getOrThrow()
        assertEquals(PlanTier.PRO, v.tier)
        assertEquals("user@example.com", v.email)
        assertEquals("inv_9", v.orderId)
        assertEquals(exp, v.expiresAt)
    }

    @Test
    fun `lifetime tokens carry no expiry and never lapse`() {
        val token = LicenseCodec.sign(LicenseCodec.TIER_LIFETIME, null, "inv_life", null, now, key)
        val v = LicenseCodec.verify(token, key, now + 50L * DAY).getOrThrow()
        assertEquals(PlanTier.LIFETIME, v.tier)
        assertNull(v.expiresAt)
        assertNull(v.email)
    }

    @Test
    fun `tampered tokens and wrong keys are rejected`() {
        val token = LicenseCodec.sign(LicenseCodec.TIER_PRO, null, "o", now + DAY, now, key)
        val parts = token.split('.').toMutableList()
        // flip a byte of the payload: student instead of pro
        val forged = LicenseCodec.sign(LicenseCodec.TIER_LIFETIME, null, "o", null, now, key).split('.')
        assertTrue(LicenseCodec.verify("${parts[0]}.${forged[1]}.${parts[2]}", key, now).isFailure)
        val other = LicenseCodec.decodeKey(LicenseCodec.encodeKey(LicenseCodec.generateKey()))!!
        assertTrue(LicenseCodec.verify(token, other, now).isFailure)
        assertTrue(LicenseCodec.verify("SQ1.no-no.no", key, now).isFailure)
        assertTrue(LicenseCodec.verify("garbage", key, now).isFailure)
    }

    @Test
    fun `expired tokens are refused with the expired reason`() {
        val token = LicenseCodec.sign(LicenseCodec.TIER_PRO, null, "o", now - 1000, now, key)
        val result = LicenseCodec.verify(token, key, now)
        assertTrue(result.isFailure)
        assertEquals("expired", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `key encode decode roundtrips and python tool tokens verify`() {
        val k2 = LicenseCodec.decodeKey(LicenseCodec.encodeKey(key))!!
        assertTrue(key.contentEquals(k2))
        // token minted by scripts/license_tool.py (same payload layout, compact separators)
        val payload = JSONObject()
            .put("v", 1).put("tier", "student").put("email", JSONObject.NULL)
            .put("order", "inv_py").put("exp", now + DAY).put("iat", now)
        val pyToken = "SQ1." +
            java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.toString().toByteArray(Charsets.UTF_8)) + "." + tokenSigOf(payload)
        val v = LicenseCodec.verify(pyToken, key, now).getOrThrow()
        assertEquals(PlanTier.STUDENT, v.tier)
        assertEquals("inv_py", v.orderId)
    }

    /** HMAC helper mirroring the Python tool's signing (HMAC-SHA256 over compact JSON). */
    private fun tokenSigOf(payload: JSONObject): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        val sig = mac.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sig)
    }

    // ------------------------------------------------------------ settings integration

    @Test
    fun `settings apply license and play purchases and expose limits`() {
        val app = RuntimeEnvironment.getApplication() as android.app.Application
        val settings = com.ctodrs.saqrcode.data.SettingsStore(app, com.ctodrs.saqrcode.data.SecureStore(app))

        assertEquals(PlanTier.FREE, settings.planTier())
        assertFalse(settings.limits().teamAgents)

        settings.applyLicense(LicenseCodec.Verified(PlanTier.STUDENT, now + DAY, null, "inv_x"))
        assertEquals(PlanTier.STUDENT, settings.planTier())
        assertTrue(settings.limits().teamAgents)
        assertTrue(settings.limits().unlimitedProjects)

        settings.applyPlayPurchase(Plans.LIFETIME, now, "GPA.order-2")
        assertEquals(PlanTier.LIFETIME, settings.planTier())
        assertNull(settings.entitlement()!!.expiresAt)
        assertEquals("GPA.order-2", settings.entitlement()!!.orderId)
        assertEquals(Entitlements.SOURCE_PLAY, settings.entitlement()!!.source)

        // a monthly play purchase on top keeps the higher tier
        settings.applyPlayPurchase(Plans.PRO_MONTHLY, now, "GPA.order-3")
        assertEquals(PlanTier.LIFETIME, settings.planTier())

        settings.setEntitlement(null)
        assertEquals(PlanTier.FREE, settings.planTier())
    }

    @Test
    fun `paywall activation rejects bad tokens and accepts good ones`() {
        val app = RuntimeEnvironment.getApplication() as android.app.Application
        val settings = com.ctodrs.saqrcode.data.SettingsStore(app, com.ctodrs.saqrcode.data.SecureStore(app))
        val token = LicenseCodec.sign(LicenseCodec.TIER_PRO, null, "inv_act", now + DAY, now, key)
        assertNull(com.ctodrs.saqrcode.billing.Paywall.activate(settings, token, now))
        assertEquals(PlanTier.PRO, settings.planTier())
        // any malformed token must fail without throwing or granting a tier
        assertEquals(PlanTier.PRO, run {
            com.ctodrs.saqrcode.billing.Paywall.activate(settings, "SQ1.bogus.token", now)
            settings.planTier()
        })
        assertTrue(com.ctodrs.saqrcode.billing.Paywall.activate(settings, "SQ1.bogus.token", now) != null)
    }

    // ------------------------------------------------------------ stdio MCP

    @Test
    fun `stdio server config roundtrips and validates commands`() {
        val servers = listOf(
            McpStdio.StdioServer("Files", "npx -y @modelcontextprotocol/server-filesystem /workspace", "HOME=/root\nKEY=VAL"),
            McpStdio.StdioServer("Git", "/usr/bin/git-mcp-server"),
        )
        assertEquals(servers, McpStdio.decodeConfig(McpStdio.encodeConfig(servers)))
        assertNull(McpStdio.validateCommand("node server.js"))
        assertEquals("command is empty", McpStdio.validateCommand("  "))
        assertEquals("command must be a single line", McpStdio.validateCommand("a\nb"))
        assertNull(McpStdio.validateCommand("echo hi && node x.js"))
    }

    @Test
    fun `stdio env parsing takes K=V pairs per line or comma`() {
        assertEquals(listOf("A=1", "B=x y"), McpStdio.parseEnv("A=1\nB=x y"))
        assertEquals(listOf("A=1", "B=2"), McpStdio.parseEnv("A=1,B=2"))
        assertEquals(emptyList<String>(), McpStdio.parseEnv("novalue\n=junk\n\n"))
    }

    @Test
    fun `stdio json-rpc builders and framing match the protocol`() {
        val init = McpStdio.initializeRequest(1)
        assertEquals("2.0", init.getString("jsonrpc"))
        assertEquals(1, init.getInt("id"))
        assertEquals(Mcp.PROTOCOL_VERSION, init.getJSONObject("params").getString("protocolVersion"))
        assertTrue(init.getJSONObject("params").getJSONObject("clientInfo").getString("name").isNotBlank())

        val call = McpStdio.toolsCallRequest(7, "read_file", JSONObject().put("path", "/workspace/a.txt"))
        assertEquals(7, call.getInt("id"))
        assertEquals("tools/call", call.getString("method"))
        assertEquals("read_file", call.getJSONObject("params").getString("name"))

        // framing: one JSON object per line; banners are dropped, notifications pass
        assertNull(McpStdio.parseLine("[INFO] server starting"))
        assertNull(McpStdio.parseLine("not json"))
        val resp = McpStdio.parseLine("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"content\":[]}}")!!
        assertTrue(McpStdio.respondsTo(resp, 7))
        assertFalse(McpStdio.respondsTo(resp, 8))
        val notif = McpStdio.parseLine("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}")!!
        assertNull(McpStdio.idOf(notif))
    }

    @Test
    fun `stdio tool names share the mcp_ namespace with http servers`() {
        assertEquals("mcp_files_read_file", Mcp.toolName("Files", "read_file"))
        val tools = Mcp.parseToolsList(
            McpStdio.StdioServer("Files", "x").slug,
            JSONObject().put("tools", org.json.JSONArray()
                .put(JSONObject().put("name", "read_file").put("description", "reads a file")
                    .put("inputSchema", JSONObject().put("type", "object")))),
        )
        assertEquals(1, tools.size)
        assertEquals("mcp_files_read_file", tools[0].name)
    }

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000
    }
}
