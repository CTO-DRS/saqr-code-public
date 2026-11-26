package com.ctodrs.saqrcode.data

import com.ctodrs.saqrcode.git.CloudBuildKit
import com.ctodrs.saqrcode.git.GhException
import com.ctodrs.saqrcode.git.GhRepo
import com.ctodrs.saqrcode.git.GitHubApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * v0.14.4 «أجنحة مفتوحة · المزامنة عبر المستودع» · Repository-based sync.
 *
 * The user's settings, themes, custom commands and skill packs — captured as one snapshot,
 * **encrypted on the device** (AES-GCM with a key derived from a passphrase via PBKDF2) and
 * pushed to a **private repository the user owns** (their token, their repo), or shared as
 * a file. A second device pulls the same copy, decrypts it with the same passphrase and is
 * set up in under two minutes. No central server, and secrets never leave the device: the
 * snapshot whitelist is explicit, so API keys, tokens and auth headers are excluded by
 * construction.
 *
 * Three layers, same discipline as Gallery/CloudBuild: the pure protocol ([SyncKit] —
 * snapshot codec, the vault, the repo-slug and path builders) is fully unit-tested; the
 * network half ([SyncClient], thin like GalleryClient) only ever puts the token in the
 * Authorization header; the UI section owns the honest states (what is missing, what a
 * failure means, an explicit «apply» before anything is overwritten).
 */

/** A syncable, non-secret setting: read from and write into [SettingsStore] explicitly. */
data class SyncSetting(
    val key: String,
    val get: (SettingsStore) -> String?,
    val set: (SettingsStore, String) -> Unit,
)

data class SyncPayload(
    val version: Int,
    val app: String,
    val createdAt: Long,
    val device: String,
    val settings: Map<String, String>,
    /** mode / active pack json / saved packs json. */
    val themes: Map<String, String>,
    /** Raw SlashCommands JSON (decoded by the existing strict codecs on apply). */
    val commandsJson: String,
    /** Raw SaqrPackCodec packs JSON. */
    val packsJson: String,
)

data class SyncApplied(
    val settings: Int,
    val themes: Int,
    val commands: Int,
    val packs: Int,
)

class SyncException(val reason: String) : Exception(reason)

object SyncKit {

    const val VERSION = 1
    const val MAGIC = "SAQRSYNC1"
    const val DEFAULT_REPO = "saqr-sync"
    const val FILE_NAME = "saqr-sync.enc"

    /** PBKDF2-HMAC-SHA256: 16-byte salt, 210k iterations (a deliberate mobile trade-off), 256-bit key. */
    const val KDF_ITERATIONS = 210_000
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val MIN_PASSPHRASE = 8

    /** The sync file lives on the default branch of the user's private repository. */
    fun contentsPath(owner: String, repo: String): String =
        "/repos/$owner/$repo/contents/" + enc(FILE_NAME)

    private fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8")

    // ------------------------------------------------------------ snapshot (pure)

    /**
     * The explicit whitelist — the source of truth for what syncs. Anything secret
     * (API keys, tokens, auth headers, keystore passwords) is simply not here.
     */
    val SETTINGS: List<SyncSetting> = listOf(
        SyncSetting("editor_font", { it.editorFontSize.toString() }, { s, v -> v.toIntOrNull()?.let { s.editorFontSize = it } }),
        SyncSetting("editor_wrap", { b(it.editorWrap) }, { s, v -> s.editorWrap = unbool(v) }),
        SyncSetting("term_font", { it.terminalFontSize.toString() }, { s, v -> v.toIntOrNull()?.let { s.terminalFontSize = it } }),
        SyncSetting("agent_mode", { it.agentMode }, { s, v -> s.agentMode = if (v == "team") "team" else "single" }),
        SyncSetting("approve_all", { b(it.approveAll) }, { s, v -> s.approveAll = unbool(v) }),
        SyncSetting("auto_compact", { b(it.autoCompact) }, { s, v -> s.autoCompact = unbool(v) }),
        SyncSetting("falcon_router", { b(it.falconRouter) }, { s, v -> s.falconRouter = unbool(v) }),
        SyncSetting("keep_background", { b(it.keepInBackground) }, { s, v -> s.keepInBackground = unbool(v) }),
        SyncSetting("voice_auto_send", { b(it.voiceAutoSend) }, { s, v -> s.voiceAutoSend = unbool(v) }),
        SyncSetting("voice_lang", { it.voiceLang }, { s, v -> s.voiceLang = v }),
        SyncSetting("dictation_mode", { it.dictation }, { s, v -> s.dictation = v }),
        SyncSetting("desert_mode", { it.desert }, { s, v -> s.desert = v }),
        SyncSetting("review_model", { it.reviewModel }, { s, v -> s.reviewModel = v }),
        SyncSetting("planner_model", { it.plannerModel }, { s, v -> s.plannerModel = v }),
        SyncSetting("review_auto_tests", { b(it.reviewAutoTests) }, { s, v -> s.reviewAutoTests = unbool(v) }),
        SyncSetting("monthly_budget", { it.monthlyBudget?.toString() }, { s, v -> s.monthlyBudget = v.toDoubleOrNull() }),
        SyncSetting("prices", { com.ctodrs.saqrcode.agent.PriceTable.encode(it.userPrices) }, { s, v -> s.userPrices = com.ctodrs.saqrcode.agent.PriceTable.decode(v) }),
        SyncSetting("vision_overrides", { com.ctodrs.saqrcode.agent.Vision.encode(it.visionOverrides) }, { s, v -> s.visionOverrides = com.ctodrs.saqrcode.agent.Vision.decode(v) }),
        SyncSetting("git_name", { it.gitName }, { s, v -> s.gitName = v }),
        SyncSetting("git_email", { it.gitEmail }, { s, v -> s.gitEmail = v }),
        SyncSetting("gh_client_id", { it.githubClientId }, { s, v -> s.githubClientId = v }),
        SyncSetting("aapt2_mirror", { it.aapt2Mirror }, { s, v -> s.aapt2Mirror = v }),
        SyncSetting("mcp_servers", { com.ctodrs.saqrcode.agent.Mcp.encodeConfig(it.mcpServers) }, { s, v -> s.mcpServers = com.ctodrs.saqrcode.agent.Mcp.decodeConfig(v) }),
        SyncSetting("mcp_stdio", { com.ctodrs.saqrcode.agent.McpStdio.encodeConfig(it.mcpStdioServers) }, { s, v -> s.mcpStdioServers = com.ctodrs.saqrcode.agent.McpStdio.decodeConfig(v) }),
        SyncSetting("mcp_oauth_configs", { com.ctodrs.saqrcode.agent.McpOAuth.encodeConfigs(com.ctodrs.saqrcode.agent.McpOAuth.decodeConfigs(it.mcpOAuthJson)) }, { s, v -> s.mcpOAuthJson = v }),
        SyncSetting("mcp_disabled", { com.ctodrs.saqrcode.agent.Mcp.encodeDisabled(it.mcpDisabledTools) }, { s, v -> s.mcpDisabledTools = com.ctodrs.saqrcode.agent.Mcp.decodeDisabled(v) }),
        // v0.15 Autonomous Core, v0.16 Agent Team, v0.17 Memory/Router, v0.18 Skills switches (v0.19 sync round).
        // Behaviour switches and numeric limits only: no keys, no tokens, no installed-skill bodies.
        SyncSetting("parallel_tools", { b(it.parallelTools) }, { s, v -> s.parallelTools = unbool(v) }),
        SyncSetting("confirm_risky", { b(it.confirmRiskyCommands) }, { s, v -> s.confirmRiskyCommands = unbool(v) }),
        SyncSetting("smart_loop_guard", { b(it.smartLoopGuard) }, { s, v -> s.smartLoopGuard = unbool(v) }),
        SyncSetting("verify_before_done", { b(it.verifyBeforeDone) }, { s, v -> s.verifyBeforeDone = unbool(v) }),
        SyncSetting("retry_llm", { b(it.retryLlm) }, { s, v -> s.retryLlm = unbool(v) }),
        SyncSetting("fallback_model", { com.ctodrs.saqrcode.agent.LlmRetry.encode(it.fallbackModel) }, { s, v -> s.fallbackModel = com.ctodrs.saqrcode.agent.LlmRetry.decode(v) }),
        SyncSetting("task_journal", { b(it.taskJournal) }, { s, v -> s.taskJournal = unbool(v) }),
        SyncSetting("audit_log", { b(it.auditLog) }, { s, v -> s.auditLog = unbool(v) }),
        SyncSetting("untrusted_marking", { b(it.untrustedMarking) }, { s, v -> s.untrustedMarking = unbool(v) }),
        SyncSetting("max_steps", { it.maxSteps.toString() }, { s, v -> v.toIntOrNull()?.let { s.maxSteps = it } }),
        SyncSetting("max_minutes", { it.maxMinutes.toString() }, { s, v -> v.toIntOrNull()?.let { s.maxMinutes = it } }),
        SyncSetting("max_cost_usd", { it.maxCostUsd.toString() }, { s, v -> v.toDoubleOrNull()?.let { s.maxCostUsd = it } }),
        SyncSetting("chief_mode", { b(it.chiefMode) }, { s, v -> s.chiefMode = unbool(v) }),
        SyncSetting("team_max_concurrent", { it.teamMaxConcurrent.toString() }, { s, v -> v.toIntOrNull()?.let { s.teamMaxConcurrent = it } }),
        SyncSetting("team_cost_estimate", { b(it.teamCostEstimate) }, { s, v -> s.teamCostEstimate = unbool(v) }),
        SyncSetting("team_disabled_roles", { it.teamDisabledRoles.sorted().joinToString(",") }, { s, v -> s.teamDisabledRoles = v.split(',').map { r -> r.trim() }.filter { r -> r.isNotEmpty() }.toSet() }),
        SyncSetting("memory_relevance", { b(it.memoryRelevance) }, { s, v -> s.memoryRelevance = unbool(v) }),
        SyncSetting("memory_top_k", { it.memoryTopK.toString() }, { s, v -> v.toIntOrNull()?.let { s.memoryTopK = it } }),
        SyncSetting("session_summary", { b(it.sessionSummary) }, { s, v -> s.sessionSummary = unbool(v) }),
        SyncSetting("memory_lessons", { b(it.memoryLessons) }, { s, v -> s.memoryLessons = unbool(v) }),
        SyncSetting("global_memory", { b(it.globalMemory) }, { s, v -> s.globalMemory = unbool(v) }),
        SyncSetting("smart_router", { b(it.smartRouter) }, { s, v -> s.smartRouter = unbool(v) }),
        SyncSetting("daily_spend_cap", { it.dailySpendCap.toString() }, { s, v -> v.toDoubleOrNull()?.let { s.dailySpendCap = it } }),
        SyncSetting("monthly_spend_cap", { it.monthlySpendCap.toString() }, { s, v -> v.toDoubleOrNull()?.let { s.monthlySpendCap = it } }),
        SyncSetting("skills_enabled", { b(it.skillsEnabled) }, { s, v -> s.skillsEnabled = unbool(v) }),
        SyncSetting("auto_attach_skills", { b(it.autoAttachSkills) }, { s, v -> s.autoAttachSkills = unbool(v) }),
    )

    private fun b(v: Boolean) = if (v) "1" else "0"
    private fun unbool(v: String) = v == "1" || v == "true"

    /** The current device state as a payload — secrets excluded by the whitelist's construction. */
    fun snapshot(settings: SettingsStore, device: String, app: String, now: Long): SyncPayload {
        val settingsMap = SETTINGS.mapNotNull { ss -> ss.get(settings)?.let { ss.key to it } }.toMap()
        val themes = mapOf(
            "mode" to settings.themeMode,
            "active" to settings.customThemeJson,
            "saved" to settings.savedThemesJson,
        )
        return SyncPayload(
            version = VERSION,
            app = app,
            createdAt = now,
            device = device.take(40),
            settings = settingsMap,
            themes = themes,
            commandsJson = com.ctodrs.saqrcode.agent.SlashCommands.encode(settings.customCommands),
            packsJson = settings.packsJson,
        )
    }

    // ------------------------------------------------------------ codec (pure)

    fun toJson(p: SyncPayload): String {
        val data = JSONObject()
        data.put("settings", JSONObject(p.settings))
        data.put("themes", JSONObject(p.themes))
        data.put("commands", p.commandsJson)
        data.put("packs", p.packsJson)
        return JSONObject()
            .put("saqrsync", p.version)
            .put("app", p.app)
            .put("created", p.createdAt)
            .put("device", p.device)
            .put("data", data)
            .toString()
    }

    /** Strict parse: null on anything that is not a Saqr sync payload of the current version. */
    fun parse(text: String?): SyncPayload? = runCatching {
        val o = JSONObject(text ?: return null)
        if (o.optInt("saqrsync", -1) != VERSION) return null
        val data = o.optJSONObject("data") ?: return null
        val settingsObj = data.optJSONObject("settings") ?: return null
        val themesObj = data.optJSONObject("themes") ?: return null
        fun flatten(x: JSONObject): Map<String, String> {
            val m = LinkedHashMap<String, String>()
            val keys = x.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = x.opt(k)
                if (v is String) m[k] = v
            }
            return m
        }
        SyncPayload(
            version = VERSION,
            app = o.optString("app"),
            createdAt = o.optLong("created"),
            device = o.optString("device"),
            settings = flatten(settingsObj),
            themes = flatten(themesObj),
            commandsJson = data.optString("commands"),
            packsJson = data.optString("packs"),
        )
    }.getOrNull()

    /** Applies a payload to the store — **replace** semantics, confirmed by the UI first. */
    fun apply(p: SyncPayload, settings: SettingsStore): SyncApplied {
        var nSettings = 0
        val byKey = SETTINGS.associateBy { it.key }
        p.settings.forEach { (k, v) ->
            byKey[k]?.let { ss ->
                runCatching { ss.set(settings, v) }.onSuccess { nSettings++ }
            }
        }
        p.themes["mode"]?.let { settings.themeMode = it }
        p.themes["active"]?.let { settings.customThemeJson = it }
        val saved = p.themes["saved"].orEmpty()
        settings.savedThemesJson = saved
        val nThemes = runCatching { org.json.JSONArray(saved).length() }.getOrDefault(0)
        settings.customCommands = com.ctodrs.saqrcode.agent.SlashCommands.decode(p.commandsJson)
        val nCommands = com.ctodrs.saqrcode.agent.SlashCommands.decode(p.commandsJson).size
        settings.packsJson = p.packsJson
        val nPacks = SaqrPackCodec.decodePacks(p.packsJson).size
        return SyncApplied(nSettings, nThemes, nCommands, nPacks)
    }

    // ------------------------------------------------------------ vault (pure crypto)

    private val RANDOM = SecureRandom()

    fun passphraseProblem(pass: String): String? = when {
        pass.trim().length < MIN_PASSPHRASE -> "short"
        else -> null
    }

    /** `SAQRSYNC1.<base64(salt ‖ iv ‖ ciphertext)>` — one opaque, shareable line. */
    fun encrypt(plain: String, passphrase: String): String {
        passphraseProblem(passphrase)?.let { throw SyncException(it) }
        val salt = ByteArray(SALT_BYTES).also(RANDOM::nextBytes)
        val iv = ByteArray(IV_BYTES).also(RANDOM::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val blob = salt + iv + ct
        return MAGIC + "." + java.util.Base64.getEncoder().encodeToString(blob)
    }

    /** Decrypts a container; throws [SyncException] (`container` / `passphrase`) with honest reasons. */
    fun decrypt(container: String, passphrase: String): String {
        val parts = container.trim().split('.')
        if (parts.size != 2 || parts[0] != MAGIC) throw SyncException("container")
        val blob = runCatching { java.util.Base64.getDecoder().decode(parts[1]) }.getOrNull()
            ?: throw SyncException("container")
        if (blob.size < SALT_BYTES + IV_BYTES + 16) throw SyncException("container")
        val salt = blob.copyOfRange(0, SALT_BYTES)
        val iv = blob.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
        val ct = blob.copyOfRange(SALT_BYTES + IV_BYTES, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
            return String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            // Wrong passphrase or damaged blob — the tag never verifies. One honest reason.
            throw SyncException("passphrase")
        }
    }

    private fun key(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, KDF_ITERATIONS, 256)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(f.generateSecret(spec).encoded, "AES")
    }

    /** The payload JSON inside a container — decrypt then parse, in that order. */
    fun open(container: String, passphrase: String): SyncPayload? = parse(decrypt(container, passphrase))

    /** Snapshot → payload JSON → encrypted container, in one step. */
    fun seal(p: SyncPayload, passphrase: String): String = encrypt(toJson(p), passphrase)

    // ------------------------------------------------------------ repo slug (pure)

    /**
     * Every shape the user might type: `owner/repo`, a full GitHub URL, or just a name
     * (owned by the linked account). Non-GitHub URLs are refused — sync is GitHub-only.
     */
    fun repoSlug(input: String, fallbackOwner: String): Pair<String, String>? {
        val t = input.trim().removeSuffix("/").removeSuffix(".git")
        if (t.isEmpty()) return fallbackOwner.takeIf { it.isNotBlank() }?.let { it to DEFAULT_REPO }
        if ("://" in t || t.startsWith("git@")) {
            return CloudBuildKit.ownerRepo(t.removeSuffix(".git"))
        }
        val parts = t.split('/')
        return when {
            parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank() -> parts[0] to parts[1]
            parts.size == 1 && t.isNotBlank() && !t.contains(' ') -> fallbackOwner.takeIf { it.isNotBlank() }?.let { it to t }
            else -> null
        }
    }
}

/**
 * v0.14.4 · the network half — one GitHub private repository, the contents API only. The
 * token goes into the Authorization header and nowhere else; bodies are the same contents
 * base64 the cloud build uses; 404s are honest (the repo or file may not exist yet).
 */
class SyncClient(
    private val token: String,
    private val baseUrl: String = "https://api.github.com",
    private val http: OkHttpClient = GitHubApi.defaultClient,
) {
    private val JSON = "application/json".toMediaType()

    /** Creates the private sync repository on the user's own account. */
    fun createPrivateRepo(name: String): GhRepo =
        GitHubApi(token, baseUrl, http).createRepo(name, private = true, description = "Saqr Code encrypted sync — صقر كود مزامنة مشفرة")

    /** The blob sha of the sync file, or null when it does not exist yet (404). */
    fun headSha(owner: String, repo: String): String? = runCatching {
        JSONObject(get(SyncKit.contentsPath(owner, repo))).optString("sha").ifBlank { null }
    }.getOrElse { e -> if (e is GhException && e.code == 404) null else throw e }

    /** Creates or updates the sync file; returns the commit's html url. */
    fun put(owner: String, repo: String, content: String, message: String, sha: String?): String {
        val body = JSONObject()
            .put("message", message)
            .put("content", CloudBuildKit.encodeContent(content))
        if (sha != null) body.put("sha", sha)
        val b = Request.Builder().url(baseUrl.trimEnd('/') + SyncKit.contentsPath(owner, repo))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "SaqrCode-Android")
            .put(body.toString().toRequestBody(JSON))
        http.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw GhException(r.code, "GitHub: HTTP ${r.code}")
            return runCatching { JSONObject(r.body?.string().orEmpty()).optString("commit") }
                .getOrNull()?.let { c -> runCatching { JSONObject(c).optString("html_url") }.getOrNull() }.orEmpty()
        }
    }

    /** The sync file's content — GitHub's line-broken base64 is stripped before decoding. */
    fun get(owner: String, repo: String): String {
        val o = JSONObject(get(SyncKit.contentsPath(owner, repo)))
        return CloudBuildKit.decodeContent(o.optString("content")) ?: throw GhException(502, "GitHub: unreadable sync file")
    }

    private fun get(path: String): String {
        val b = Request.Builder().url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "SaqrCode-Android")
            .get()
        http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw GhException(r.code, "GitHub: HTTP ${r.code}")
            return text
        }
    }
}
