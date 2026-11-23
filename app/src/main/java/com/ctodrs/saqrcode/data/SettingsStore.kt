package com.ctodrs.saqrcode.data

import android.content.Context
import com.ctodrs.saqrcode.agent.ProviderKind
import com.ctodrs.saqrcode.agent.Providers
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

    /** Terminal font size in sp. */
    var terminalFontSize: Int
        get() = prefs.getInt("term_font", 12)
        set(v) { prefs.edit().putInt("term_font", v.coerceIn(8, 22)).apply(); bump() }

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
