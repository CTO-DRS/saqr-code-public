package com.ctodrs.saqrcode.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/*
 * v0.13.5 «وضع صحراوي»: the offline-first policy as pure logic — which mode is stored,
 * which features are cloud-bound and refuse clearly when the desert is active, and which
 * stay local (files, terminal, memory, symbol index, local git, stdio MCP, and the
 * on-device build from a cached environment). Android wiring (ConnectivityManager +
 * settings) lives in SaqrApp / SettingsScreen; every cloud path consults the gates here.
 *
 * القاعدة الحاكمة: لا شيء ينهار ولا يعلق — كل مسار سحابي يُجاب برسالة واضحة واحدة،
 * وكل ما هو محلي يستمر كأن لم يكن شيء.
 */

object DesertMode {
    /** Settings value stored in prefs; [OFF] is the default and [from] never throws. */
    enum class Mode(val key: String) {
        /** Never ration: cloud features run whenever the device reaches them. */
        OFF(""),
        /** Active automatically while the device has no usable internet. */
        AUTO("auto"),
        /** Forced by the user: ration data / battery / heat even while online. */
        ALWAYS("always");

        companion object {
            fun from(key: String?): Mode = entries.firstOrNull { it.key == key } ?: OFF
        }
    }

    /** The cloud-bound feature classes a refusal can name. */
    enum class Feature { CHAT, WEB_TOOLS, MCP_REMOTE, GIT_REMOTE, SDK_DOWNLOAD, CLOUD_WORKSPACE, LOCAL }

    /** [AUTO] follows the network; [ALWAYS] is unconditional; [OFF] never rations. */
    fun active(mode: Mode, online: Boolean): Boolean =
        mode == Mode.ALWAYS || (mode == Mode.AUTO && !online)

    /**
     * The feature a tool call belongs to; null = purely local and always allowed.
     * Stdio MCP tools are deliberately absent — they run in the terminal env on the
     * device itself, so they keep working offline like every local tool.
     */
    fun cloudTool(name: String): Feature? = when (name) {
        Tools.WEB_SEARCH, Tools.WEB_FETCH -> Feature.WEB_TOOLS
        Tools.GIT_PULL, Tools.GIT_PUSH -> Feature.GIT_REMOTE
        Tools.CLOUD_RUN, Tools.CLOUD_TEST, Tools.CLOUD_SYNC, Tools.CLOUD_DESKTOP -> Feature.CLOUD_WORKSPACE
        else -> null
    }

    /** The model-readable refusal (a tool result): one line, says what still works. */
    fun toolMessage(feature: Feature): String = when (feature) {
        Feature.WEB_TOOLS ->
            "Error: desert mode is active (no network): web_search/web_fetch are offline. " +
                "Continue from the project's local files, project_search and run_command instead."
        Feature.MCP_REMOTE ->
            "Error: desert mode is active (no network): remote MCP servers are offline. " +
                "Local stdio MCP servers and the built-in tools still work."
        Feature.GIT_REMOTE ->
            "Error: desert mode is active (no network): git push/pull are offline. " +
                "Local commits still work — commit now and push when the connection returns."
        Feature.CHAT ->
            "Error: desert mode is active (no network): the model provider is unreachable."
        Feature.SDK_DOWNLOAD ->
            "Error: desert mode is active (no network): this step needs a download that is not cached yet."
        Feature.CLOUD_WORKSPACE ->
            "Error: desert mode is active (no network): the cloud workspace is offline. Use run_command and run_tests on the phone instead."
        Feature.LOCAL -> ""
    }

    /** The verdict of one gate check: allowed, or the exact refusal to surface. */
    data class Verdict(val allowed: Boolean, val message: String = "")

    /** The single gate every cloud path consults; [Feature.LOCAL] never blocks. */
    fun gate(feature: Feature, desert: Boolean): Verdict =
        if (!desert || feature == Feature.LOCAL) Verdict(true)
        else Verdict(false, toolMessage(feature))

    /** A build bootstrap download runs only online — or when everything is already cached. */
    fun downloadAllowed(desert: Boolean, cached: Boolean): Boolean = !desert || cached
}

/** Live desert state wired by the app (settings + ConnectivityManager); readable anywhere. */
object DesertState {
    var mode by mutableStateOf(DesertMode.Mode.OFF)
    /** True while the device has a usable (validated) internet connection. */
    var online by mutableStateOf(true)
    val active: Boolean get() = DesertMode.active(mode, online)
}
