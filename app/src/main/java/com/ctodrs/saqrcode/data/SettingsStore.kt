package com.ctodrs.saqrcode.data

import android.content.Context
import com.ctodrs.saqrcode.agent.ModelPrice
import com.ctodrs.saqrcode.agent.PriceTable
import com.ctodrs.saqrcode.agent.ProviderKind
import com.ctodrs.saqrcode.agent.Providers
import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.agent.SlashCommands
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

    /** "dark" (default) | "light" | "system" */
    var themeMode: String
        get() = prefs.getString("theme_mode", "dark") ?: "dark"
        set(v) { prefs.edit().putString("theme_mode", v).apply(); bump() }

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

    /** v0.8: MCP servers (name + URL); their auth headers live in [SecureStore]. */
    var mcpServers: List<com.ctodrs.saqrcode.agent.Mcp.Server>
        get() = com.ctodrs.saqrcode.agent.Mcp.decodeConfig(prefs.getString("mcp_servers", null))
        set(v) { prefs.edit().putString("mcp_servers", com.ctodrs.saqrcode.agent.Mcp.encodeConfig(v)).apply(); bump() }

    /** v0.8: auth header value for an MCP server ("Bearer …" or any header text), encrypted. */
    fun mcpAuth(serverName: String): String? = secure.get("mcp_auth_" + com.ctodrs.saqrcode.agent.Mcp.slugify(serverName))

    fun setMcpAuth(serverName: String, value: String?) {
        secure.put("mcp_auth_" + com.ctodrs.saqrcode.agent.Mcp.slugify(serverName), value)
    }

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

    /** Terminal font size in sp. */
    var terminalFontSize: Int
        get() = prefs.getInt("term_font", 12)
        set(v) { prefs.edit().putInt("term_font", v.coerceIn(8, 22)).apply(); bump() }

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
