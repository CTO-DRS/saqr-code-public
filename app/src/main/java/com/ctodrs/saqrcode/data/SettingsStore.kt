package com.ctodrs.saqrcode.data

import android.content.Context
import com.ctodrs.saqrcode.agent.ModelPrice
import com.ctodrs.saqrcode.agent.PriceTable
import com.ctodrs.saqrcode.agent.ProviderKind
import com.ctodrs.saqrcode.agent.Providers
import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.agent.SlashCommands
import com.ctodrs.saqrcode.billing.Entitlement
import com.ctodrs.saqrcode.billing.Entitlements
import com.ctodrs.saqrcode.billing.Plan
import com.ctodrs.saqrcode.billing.PlanLimits
import com.ctodrs.saqrcode.billing.PlanTier
import com.ctodrs.saqrcode.billing.Plans
import com.ctodrs.saqrcode.payments.LicenseCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ProviderConfig(
    val id: String,
    val baseUrl: String,
    val model: String,
    val hasKey: Boolean,
)

/** Non-secret settings in plain prefs; secrets delegated to [SecureStore]. */
class SettingsStore(context: Context, private val secure: SecureStore) {
    private val prefs = context.getSharedPreferences("saqr_settings", Context.MODE_PRIVATE)

    private val _version = MutableStateFlow(0)
    /** Bumps whenever settings change so Compose can recompose. */
    val version: StateFlow<Int> = _version
    private fun bump() { _version.value++ }

    var activeProvider: String
        get() = prefs.getString("active_provider", Providers.all.first().id)!!
        set(v) { prefs.edit().putString("active_provider", v).apply(); bump() }

    /** "dark" (default) | "light" | "system" | "oled" | "custom" (Theme Studio pack) */
    var themeMode: String
        get() = prefs.getString("theme_mode", "dark") ?: "dark"
        set(v) { prefs.edit().putString("theme_mode", v).apply(); bump() }

    /** v0.12: the applied Theme Studio pack JSON (active while themeMode == "custom"). */
    var customThemeJson: String
        get() = prefs.getString("custom_theme", "") ?: ""
        set(v) { prefs.edit().putString("custom_theme", v).apply(); bump() }

    /** v0.12: user-saved Theme Studio packs (JSON array of [com.ctodrs.saqrcode.ui.theme.ThemePack]). */
    var savedThemesJson: String
        get() = prefs.getString("saved_themes", "") ?: ""
        set(v) { prefs.edit().putString("saved_themes", v).apply(); bump() }

    /** First-run welcome has been seen. */
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) { prefs.edit().putBoolean("onboarded", v).apply(); bump() }

    fun setModel(id: String, model: String) { prefs.edit().putString("model_$id", model.trim()).apply(); bump() }

    var approveAll: Boolean
        get() = prefs.getBoolean("approve_all", false)
        set(v) { prefs.edit().putBoolean("approve_all", v).apply(); bump() }

    var githubUser: String
        get() = prefs.getString("github_user", "") ?: ""
        set(v) { prefs.edit().putString("github_user", v.trim()).apply(); bump() }

    /** Git hosting accounts (GitHub + other HTTPS hosts); tokens encrypted by [SecureStore]. */
    val accounts = AccountStore(secure).also { it.migrateLegacy(prefs.getString("github_user", null)) }

    /** Kept for the v0.2 Git pane code paths: the GitHub token, if linked. */
    val githubToken: String? get() = accounts.githubToken()

    fun accountsChanged() = bump()

    /** Commit identity; defaults to the linked GitHub account. */
    var gitName: String
        get() = prefs.getString("git_name", null)?.takeIf { it.isNotBlank() } ?: accounts.github?.displayName ?: ""
        set(v) { prefs.edit().putString("git_name", v.trim()).apply(); bump() }

    var gitEmail: String
        get() = prefs.getString("git_email", null)?.takeIf { it.isNotBlank() } ?: accounts.github?.noreplyEmail ?: ""
        set(v) { prefs.edit().putString("git_email", v.trim()).apply(); bump() }

    /** Client ID of the user's own GitHub OAuth App (Device Flow). Public value, no secret needed. */
    var githubClientId: String
        get() = prefs.getString("gh_client_id", "") ?: ""
        set(v) { prefs.edit().putString("gh_client_id", v.trim()).apply(); bump() }

    /** Agent mode: "single" (one agent) or "team" (planner → coder → reviewer). */
    var agentMode: String
        get() = prefs.getString("agent_mode", "single") ?: "single"
        set(v) { prefs.edit().putString("agent_mode", v).apply(); bump() }

    /** v0.7 team mode: the reviewer model id. Blank = the same model as the coder. */
    var reviewModel: String
        get() = prefs.getString("review_model", "") ?: ""
        set(v) { prefs.edit().putString("review_model", v.trim()).apply(); bump() }

    /** v0.8 team mode: run lint/tests after each batch and feed the output to the reviewer. */
    var reviewAutoTests: Boolean
        get() = prefs.getBoolean("review_auto_tests", true)
        set(v) { prefs.edit().putBoolean("review_auto_tests", v).apply(); bump() }

    /** v0.8 team mode: the planner model id. Blank = the same model as the coder. */
    var plannerModel: String
        get() = prefs.getString("planner_model", "") ?: ""
        set(v) { prefs.edit().putString("planner_model", v.trim()).apply(); bump() }

    /**
     * v0.13.2 Falcon Router: route planner / review / compaction to the cheapest capable
     * model of the active provider (only when the price table proves it saves money).
     * Explicit per-stage picks still win over the router; the coder is never routed.
     */
    var falconRouter: Boolean
        get() = prefs.getBoolean("falcon_router", true)
        set(v) { prefs.edit().putBoolean("falcon_router", v).apply(); bump() }

    // ------------------------------------------------ v0.13.3 Saqr Coach

    /** v0.13.3: epoch ms of the first command the agent received (0 = none yet). */
    var coachFirstMessageAt: Long
        get() = prefs.getLong("coach_first_msg", 0L)
        set(v) { prefs.edit().putLong("coach_first_msg", v).apply(); bump() }

    /** v0.13.3: the coach card was hidden by the user (re-openable from settings). */
    var coachDismissed: Boolean
        get() = prefs.getBoolean("coach_dismissed", false)
        set(v) { prefs.edit().putBoolean("coach_dismissed", v).apply(); bump() }

    /** v0.13.3: step → epoch ms first observed done (codec in [com.ctodrs.saqrcode.agent.CoachProgressCodec]). */
    var coachProgressJson: String
        get() = prefs.getString("coach_progress", "") ?: ""
        set(v) { prefs.edit().putString("coach_progress", v).apply(); bump() }

    fun coachProgress(): Map<String, Long> =
        com.ctodrs.saqrcode.agent.CoachProgressCodec.decode(coachProgressJson)

    /** v0.8: MCP servers (name + URL); their auth headers live in [SecureStore]. */
    var mcpServers: List<com.ctodrs.saqrcode.agent.Mcp.Server>
        get() = com.ctodrs.saqrcode.agent.Mcp.decodeConfig(prefs.getString("mcp_servers", null))
        set(v) { prefs.edit().putString("mcp_servers", com.ctodrs.saqrcode.agent.Mcp.encodeConfig(v)).apply(); bump() }

    /** v0.8: auth header value for an MCP server ("Bearer …" or any header text), encrypted. */
    fun mcpAuth(serverName: String): String? = secure.get("mcp_auth_" + com.ctodrs.saqrcode.agent.Mcp.slugify(serverName))

    fun setMcpAuth(serverName: String, value: String?) {
        secure.put("mcp_auth_" + com.ctodrs.saqrcode.agent.Mcp.slugify(serverName), value)
    }

    // ------------------------------------------------ v0.14.1 MCP OAuth (PKCE)

    /** v0.14.1: per-server OAuth config (client id, scopes, endpoints) keyed by slug — non-secret. */
    var mcpOAuthJson: String
        get() = prefs.getString("mcp_oauth_configs", "") ?: ""
        set(v) { prefs.edit().putString("mcp_oauth_configs", v).apply(); bump() }

    fun mcpOAuthConfig(serverName: String): com.ctodrs.saqrcode.agent.McpOAuth.Config? =
        com.ctodrs.saqrcode.agent.McpOAuth.decodeConfigs(mcpOAuthJson)[com.ctodrs.saqrcode.agent.Mcp.slugify(serverName)]

    fun setMcpOAuthConfig(serverName: String, cfg: com.ctodrs.saqrcode.agent.McpOAuth.Config?) {
        val slug = com.ctodrs.saqrcode.agent.Mcp.slugify(serverName)
        val cur = com.ctodrs.saqrcode.agent.McpOAuth.decodeConfigs(mcpOAuthJson)
        mcpOAuthJson = com.ctodrs.saqrcode.agent.McpOAuth.encodeConfigs(if (cfg == null) cur - slug else cur + (slug to cfg))
    }

    /** v0.14.1: the OAuth token set of a server — lives encrypted in [SecureStore]. */
    fun mcpTokens(serverName: String): com.ctodrs.saqrcode.agent.McpOAuth.Tokens? =
        com.ctodrs.saqrcode.agent.McpOAuth.decodeTokens(secure.get("mcp_oauth_" + com.ctodrs.saqrcode.agent.Mcp.slugify(serverName)))

    fun setMcpTokens(serverName: String, tokens: com.ctodrs.saqrcode.agent.McpOAuth.Tokens?) {
        secure.put("mcp_oauth_" + com.ctodrs.saqrcode.agent.Mcp.slugify(serverName), tokens?.let { com.ctodrs.saqrcode.agent.McpOAuth.encodeTokens(it) })
    }

    /** v0.14.1: in-flight PKCE flows persisted by state so the callback activity finds them. */
    var mcpOAuthPendingJson: String
        get() = prefs.getString("mcp_oauth_pending", "") ?: ""
        set(v) { prefs.edit().putString("mcp_oauth_pending", v).apply(); bump() }

    fun setMcpOAuthPending(pending: com.ctodrs.saqrcode.agent.McpOAuth.Pending) {
        mcpOAuthPendingJson = com.ctodrs.saqrcode.agent.McpOAuth.encodePending(
            com.ctodrs.saqrcode.agent.McpOAuth.decodePending(mcpOAuthPendingJson) + (pending.state to pending))
    }

    fun mcpOAuthPending(state: String): com.ctodrs.saqrcode.agent.McpOAuth.Pending? =
        com.ctodrs.saqrcode.agent.McpOAuth.decodePending(mcpOAuthPendingJson)[state]

    fun clearMcpOAuthPending(state: String) {
        mcpOAuthPendingJson = com.ctodrs.saqrcode.agent.McpOAuth.encodePending(
            com.ctodrs.saqrcode.agent.McpOAuth.decodePending(mcpOAuthPendingJson) - state)
    }

    /**
     * v0.14.1: the Authorization header for a server — live OAuth tokens first, the
     * manual auth header second (also the fallback while tokens are expired).
     */
    fun mcpAuthHeader(serverName: String): String? {
        if (mcpOAuthConfig(serverName) != null) {
            mcpTokens(serverName)?.let { t ->
                if (!com.ctodrs.saqrcode.agent.McpOAuth.needsRefresh(t, System.currentTimeMillis())) {
                    return com.ctodrs.saqrcode.agent.McpOAuth.bearerOf(t)
                }
            }
        }
        return mcpAuth(serverName)
    }

    /** v0.10: MCP tools hidden from the agent, keyed by server slug → raw tool names. */
    var mcpDisabledTools: Map<String, Set<String>>
        get() = com.ctodrs.saqrcode.agent.Mcp.decodeDisabled(prefs.getString("mcp_disabled", null))
        set(v) { prefs.edit().putString("mcp_disabled", com.ctodrs.saqrcode.agent.Mcp.encodeDisabled(v)).apply(); bump() }

    fun setMcpToolDisabled(serverName: String, rawTool: String, disabled: Boolean) {
        val slug = com.ctodrs.saqrcode.agent.Mcp.slugify(serverName)
        val cur = mcpDisabledTools
        val forServer = cur[slug].orEmpty().toMutableSet()
        if (disabled) forServer.add(rawTool) else forServer.remove(rawTool)
        mcpDisabledTools = if (forServer.isEmpty()) cur - slug else cur + (slug to forServer)
    }

    fun mcpToolDisabled(serverName: String, rawTool: String): Boolean =
        mcpDisabledTools[com.ctodrs.saqrcode.agent.Mcp.slugify(serverName)]?.contains(rawTool) == true

    /** v0.10: after a successful build, delete old APKs in dist/ and the build intermediates. */
    var keepLastApk: Boolean
        get() = prefs.getBoolean("keep_last_apk", false)
        set(v) { prefs.edit().putBoolean("keep_last_apk", v).apply(); bump() }

    /** v0.8: direct aapt2 download URL (mirror). Blank/auto = the built-in lookup; a preset id or a direct URL in v0.9. */
    var aapt2Mirror: String
        get() = prefs.getString("aapt2_mirror", "") ?: ""
        set(v) { prefs.edit().putString("aapt2_mirror", v.trim()).apply(); bump() }

    /** v0.9 signed release: keystore path inside the project (non-secret). Passwords are not stored. */
    var releaseKeystorePath: String
        get() = prefs.getString("release_ks_path", "keystore/release.jks") ?: "keystore/release.jks"
        set(v) { prefs.edit().putString("release_ks_path", v.trim()).apply(); bump() }

    /** v0.9 signed release: the key alias inside the keystore (non-secret). */
    var releaseKeyAlias: String
        get() = prefs.getString("release_ks_alias", "") ?: ""
        set(v) { prefs.edit().putString("release_ks_alias", v.trim()).apply(); bump() }

    /** v0.7: summarise the oldest context when it grows past the threshold (keeps the meter honest on small models). */
    var autoCompact: Boolean
        get() = prefs.getBoolean("auto_compact", true)
        set(v) { prefs.edit().putBoolean("auto_compact", v).apply(); bump() }

    /** v0.6: keep agent turns running in a foreground service when the app is in the background. */
    var keepInBackground: Boolean
        get() = prefs.getBoolean("keep_background", true)
        set(v) { prefs.edit().putBoolean("keep_background", v).apply(); bump() }

    /** The one-time battery-optimization explanation was shown. */
    var batteryTipShown: Boolean
        get() = prefs.getBoolean("battery_tip_shown", false)
        set(v) { prefs.edit().putBoolean("battery_tip_shown", v).apply() }

    /** POST_NOTIFICATIONS was requested once (Android 13+). */
    var notificationsAsked: Boolean
        get() = prefs.getBoolean("notif_asked", false)
        set(v) { prefs.edit().putBoolean("notif_asked", v).apply() }

    /** v0.6 voice: send the message as soon as dictation ends. */
    var voiceAutoSend: Boolean
        get() = prefs.getBoolean("voice_auto_send", false)
        set(v) { prefs.edit().putBoolean("voice_auto_send", v).apply(); bump() }

    /** Dictation language: "" follows the app language, else "ar-SA" / "en-US" (long-press on the mic toggles). */
    var voiceLang: String
        get() = prefs.getString("voice_lang", "") ?: ""
        set(v) { prefs.edit().putString("voice_lang", v).apply(); bump() }

    /** v0.13.4 hands-free: Dictation.Mode.key — "" off, "on" continuous typing, "wake" «يا صقر» commands. */
    var dictation: String
        get() = prefs.getString("dictation_mode", "") ?: ""
        set(v) { prefs.edit().putString("dictation_mode", v).apply(); bump() }

    /** v0.13.5 desert mode: DesertMode.Mode.key — "" off, "auto" follow the network, "always" forced. */
    var desert: String
        get() = prefs.getString("desert_mode", "") ?: ""
        set(v) { prefs.edit().putString("desert_mode", v).apply(); bump() }

    /** v0.14 skill packs: the installed .saqrpack bundles (template + commands + rules + tools). */
    var packsJson: String
        get() = prefs.getString("saqr_packs", null) ?: ""
        set(v) { prefs.edit().putString("saqr_packs", v).apply(); bump() }

    /** Terminal font size in sp. */
    var terminalFontSize: Int
        get() = prefs.getInt("term_font", 12)
        set(v) { prefs.edit().putInt("term_font", v.coerceIn(8, 22)).apply(); bump() }

    /** v0.12: editor font size in sp. */
    var editorFontSize: Int
        get() = prefs.getInt("editor_font", 13)
        set(v) { prefs.edit().putInt("editor_font", v.coerceIn(12, 20)).apply(); bump() }

    /** v0.12: wrap long lines in the code editor instead of horizontal scrolling. */
    var editorWrap: Boolean
        get() = prefs.getBoolean("editor_wrap", false)
        set(v) { prefs.edit().putBoolean("editor_wrap", v).apply(); bump() }

    /** User-defined slash commands (name + prompt template). */
    var customCommands: List<SlashCommand>
        get() = SlashCommands.decode(prefs.getString("slash_custom", null))
        set(v) { prefs.edit().putString("slash_custom", SlashCommands.encode(v)).apply(); bump() }

    /** The user's price rows (overrides / additions to [PriceTable.seeds]). */
    var userPrices: List<ModelPrice>
        get() = PriceTable.decode(prefs.getString("prices", null))
        set(v) { prefs.edit().putString("prices", PriceTable.encode(v)).apply(); bump() }

    /** Effective price table: seeds merged with the user's rows. */
    fun prices(): List<ModelPrice> = PriceTable.merge(userPrices)

    fun setPrice(row: ModelPrice) {
        userPrices = userPrices.filter { PriceTable.key(it.model) != PriceTable.key(row.model) } + row
    }

    /** Optional monthly budget in USD; null = no budget. */
    var monthlyBudget: Double?
        get() = prefs.getString("monthly_budget_usd", null)?.toDoubleOrNull()?.takeIf { it > 0.0 }
        set(v) { prefs.edit().putString("monthly_budget_usd", v?.takeIf { it > 0.0 }?.toString()).apply(); bump() }

    /** Per-model vision overrides ("on" / "off"); models without one use [com.ctodrs.saqrcode.agent.Vision.heuristic]. */
    var visionOverrides: Map<String, String>
        get() = com.ctodrs.saqrcode.agent.Vision.decode(prefs.getString("vision_overrides", null))
        set(v) { prefs.edit().putString("vision_overrides", com.ctodrs.saqrcode.agent.Vision.encode(v)).apply(); bump() }

    /** [mode] is Vision.AUTO / ON / OFF. */
    fun setVisionMode(model: String, mode: String) {
        val k = com.ctodrs.saqrcode.agent.Vision.normalizeKey(model)
        visionOverrides = if (mode == com.ctodrs.saqrcode.agent.Vision.AUTO) visionOverrides - k else visionOverrides + (k to mode)
    }

    fun visionMode(model: String): String = visionOverrides[com.ctodrs.saqrcode.agent.Vision.normalizeKey(model)] ?: com.ctodrs.saqrcode.agent.Vision.AUTO

    fun supportsVision(model: String) = com.ctodrs.saqrcode.agent.Vision.supports(model, visionOverrides)

    // ------------------------------------------------ v0.11 billing & licensing

    /** v0.11: the persisted entitlement JSON (plan tier, source, expiry). Empty = free tier. */
    var entitlementJson: String
        get() = prefs.getString("entitlement", "") ?: ""
        set(v) { prefs.edit().putString("entitlement", v).apply(); bump() }

    fun entitlement(): Entitlement? = Entitlements.decode(entitlementJson)

    fun setEntitlement(e: Entitlement?) {
        entitlementJson = e?.let { Entitlements.encode(it) } ?: ""
    }

    /** The tier in force right now (grace keeps the paid tier, expiry falls back to FREE). */
    fun planTier(): PlanTier = Entitlements.effectiveTier(entitlement(), System.currentTimeMillis())

    /** Project/MCP/host limits and feature flags for the current tier. */
    fun limits(): PlanLimits = Plans.limitsFor(planTier())

    /** v0.11: a Play purchase is its own proof — acknowledged and re-verified at every start. */
    fun applyPlayPurchase(plan: Plan, purchaseTime: Long, orderId: String) {
        val cur = entitlement()
        val exp = if (plan.monthly) Entitlements.renewMonthly(cur, purchaseTime) else null
        setEntitlement(Entitlements.merge(cur, Entitlement(plan.tier, Entitlements.SOURCE_PLAY, exp, orderId)))
    }

    /** v0.11: a verified license token grants (or extends) the plan it names. */
    fun applyLicense(verified: LicenseCodec.Verified) {
        val cur = entitlement()
        val inc = Entitlement(
            tier = verified.tier,
            source = Entitlements.SOURCE_LICENSE,
            expiresAt = verified.expiresAt,
            orderId = verified.orderId.ifBlank { "license" },
            email = verified.email,
        )
        setEntitlement(Entitlements.merge(cur, inc))
    }

    /** v0.11: the merchant's public Moyasar payment page (safe to show to users; no secrets). */
    var moyasarPayLink: String
        get() = prefs.getString("moyasar_pay_link", "") ?: ""
        set(v) { prefs.edit().putString("moyasar_pay_link", v.trim()).apply(); bump() }

    /** v0.11: Moyasar secret key — merchant tools only, lives in [SecureStore] on one device. */
    fun moyasarSecret(): String? = secure.get("moyasar_secret")

    fun setMoyasarSecret(value: String?) {
        secure.put("moyasar_secret", value?.trim()?.takeIf { it.isNotBlank() })
    }

    /** v0.11: MCP servers speaking stdio, run inside the terminal env (proot or /system/bin/sh). */
    var mcpStdioServers: List<com.ctodrs.saqrcode.agent.McpStdio.StdioServer>
        get() = com.ctodrs.saqrcode.agent.McpStdio.decodeConfig(prefs.getString("mcp_stdio_servers", null))
        set(v) { prefs.edit().putString("mcp_stdio_servers", com.ctodrs.saqrcode.agent.McpStdio.encodeConfig(v)).apply(); bump() }

    fun config(id: String): ProviderConfig {
        val def = Providers.byId(id)
        return ProviderConfig(
            id = id,
            baseUrl = prefs.getString("base_$id", null)?.takeIf { it.isNotBlank() } ?: def.defaultBaseUrl,
            model = prefs.getString("model_$id", null)?.takeIf { it.isNotBlank() } ?: def.defaultModel,
            hasKey = secure.has("key_$id"),
        )
    }

    fun apiKey(id: String): String? = secure.get("key_$id")

    fun saveProvider(id: String, baseUrl: String, model: String, apiKey: String?) {
        prefs.edit().putString("base_$id", baseUrl.trim()).putString("model_$id", model.trim()).apply()
        if (apiKey != null) secure.put("key_$id", apiKey)
        bump()
    }

    fun clearKey(id: String) { secure.put("key_$id", null); bump() }

    fun activeReady(): Boolean {
        val def = Providers.byId(activeProvider)
        return def.kind == ProviderKind.OPENAI_COMPAT && def.keyOptional || apiKey(activeProvider) != null
    }
}
