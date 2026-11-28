package com.ctodrs.saqrcode.agent

enum class ProviderKind { OPENAI_COMPAT, ANTHROPIC }

/**
 * Real, official APIs only. Each user brings their own key. No web-session scraping.
 * Gemini is reached through Google's official OpenAI-compatible endpoint.
 */
data class ProviderDef(
    val id: String,
    val displayName: String,
    val kind: ProviderKind,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val keyOptional: Boolean = false,
    val baseUrlEditable: Boolean = false,
    val keyUrl: String = "",
    /** Context window in tokens, used for the context meter. */
    val contextWindow: Int = 128_000,
)

object Providers {
    val all = listOf(
        ProviderDef(
            "anthropic", "Anthropic (Claude)", ProviderKind.ANTHROPIC,
            "https://api.anthropic.com", "claude-sonnet-4-5",
            listOf("claude-sonnet-4-5", "claude-opus-4-1", "claude-haiku-4-5"),
            keyUrl = "https://console.anthropic.com/settings/keys", contextWindow = 200_000,
        ),
        ProviderDef(
            "openai", "OpenAI", ProviderKind.OPENAI_COMPAT,
            "https://api.openai.com/v1", "gpt-4.1-mini",
            listOf("gpt-4.1-mini", "gpt-4.1", "gpt-4o", "o4-mini"),
            keyUrl = "https://platform.openai.com/api-keys", contextWindow = 1_000_000,
        ),
        ProviderDef(
            "gemini", "Google Gemini", ProviderKind.OPENAI_COMPAT,
            "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash",
            listOf("gemini-2.5-flash", "gemini-2.5-pro"),
            keyUrl = "https://aistudio.google.com/apikey", contextWindow = 1_000_000,
        ),
        ProviderDef(
            "openrouter", "OpenRouter", ProviderKind.OPENAI_COMPAT,
            "https://openrouter.ai/api/v1", "openai/gpt-4o-mini",
            listOf("openai/gpt-4o-mini", "anthropic/claude-sonnet-4.5", "deepseek/deepseek-chat", "google/gemini-2.5-flash", "qwen/qwen3-coder"),
            keyUrl = "https://openrouter.ai/keys",
        ),
        ProviderDef(
            "deepseek", "DeepSeek API", ProviderKind.OPENAI_COMPAT,
            "https://api.deepseek.com/v1", "deepseek-chat",
            listOf("deepseek-chat", "deepseek-reasoner"),
            keyUrl = "https://platform.deepseek.com/api_keys",
        ),
        ProviderDef(
            "saqr-free", "Saqr Free AI", ProviderKind.OPENAI_COMPAT,
            "https://saqr-cloud.nexus-link.workers.dev/ai/v1", "auto",
            emptyList(), baseUrlEditable = true,
        ),
        ProviderDef(
            "custom", "Custom (OpenAI-compatible)", ProviderKind.OPENAI_COMPAT,
            "https://", "", emptyList(), keyOptional = true, baseUrlEditable = true,
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}
