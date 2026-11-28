package com.ctodrs.saqrcode.ui

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.ctodrs.saqrcode.BuildConfig
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.agent.FalconRouter
import com.ctodrs.saqrcode.agent.CoachSnapshot
import com.ctodrs.saqrcode.agent.SaqrCoach
import com.ctodrs.saqrcode.agent.LlmClient
import com.ctodrs.saqrcode.agent.ModelPrice
import com.ctodrs.saqrcode.agent.PriceTable
import com.ctodrs.saqrcode.agent.SavingsLedger
import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.ui.chat.formatUsd
import com.ctodrs.saqrcode.ui.chat.ltr
import java.io.File
import java.util.Locale
import com.ctodrs.saqrcode.agent.SlashCommands
import com.ctodrs.saqrcode.agent.ProviderDef
import com.ctodrs.saqrcode.agent.Providers
import com.ctodrs.saqrcode.data.ProviderConfig
import com.ctodrs.saqrcode.ui.components.FalconMark
import com.ctodrs.saqrcode.ui.components.GhostButton
import com.ctodrs.saqrcode.ui.components.GlassIconButton
import com.ctodrs.saqrcode.ui.components.ListRow
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.RowDivider
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.components.SaqrSwitch
import com.ctodrs.saqrcode.ui.components.SectionLabel
import com.ctodrs.saqrcode.ui.components.Segmented
import com.ctodrs.saqrcode.ui.components.Tag
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.ProviderBadge
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the settings screen needs from storage; [SettingsScreen] wires it to the real stores. */
class SettingsUi(
    val themeMode: String,
    val langTag: String,
    val providers: List<Pair<ProviderDef, ProviderConfig>>,
    val activeProvider: String,
    val approveAll: Boolean,
    val githubUser: String,
    val githubTokenSet: Boolean,
    val versionName: String,
    val onTheme: (String) -> Unit,
    val onLang: (String) -> Unit,
    val onActivate: (String) -> Unit,
    val onSaveProvider: (id: String, baseUrl: String, model: String, key: String?) -> Unit,
    val fetchModels: suspend (def: ProviderDef, baseUrl: String, model: String, key: String?) -> Result<List<String>>,
    val onApproveAll: (Boolean) -> Unit,
    /** v0.19.1: remove a provider's stored API key (was in SettingsStore.clearKey with no UI). */
    val onClearKey: (id: String) -> Unit = {},
    val onAccounts: () -> Unit = {},
    val customCommands: List<SlashCommand> = emptyList(),
    /** Returns an error key from [SlashCommands.validate], or null when added. */
    val onAddCommand: (name: String, template: String) -> String? = { _, _ -> null },
    val onDeleteCommand: (String) -> Unit = {},
    val monthlyBudget: Double? = null,
    val onBudget: (Double?) -> Unit = {},
    val dailyBudget: Double? = null,
    val onDailyBudget: (Double?) -> Unit = {},
    val prices: List<ModelPrice> = PriceTable.seeds,
    val onPrice: (ModelPrice) -> Unit = {},
    /** Active model and its vision setting (Vision.AUTO / ON / OFF). */
    val activeModel: String = "",
    val visionMode: String = com.ctodrs.saqrcode.agent.Vision.AUTO,
    val onVisionMode: (String) -> Unit = {},
    /** v0.6 voice + background. */
    val voiceAutoSend: Boolean = false,
    val onVoiceAutoSend: (Boolean) -> Unit = {},
    val voiceLang: String = "",
    val onVoiceLang: (String) -> Unit = {},
    /** v0.13.4 hands-free: Dictation.Mode.key (""/"on"/"wake"). */
    val dictation: String = "",
    val onDictation: (String) -> Unit = {},
    /** v0.13.5 desert mode: DesertMode.Mode.key (""/"auto"/"always"). */
    val desert: String = "",
    val onDesert: (String) -> Unit = {},
    val keepInBackground: Boolean = true,
    val onKeepInBackground: (Boolean) -> Unit = {},
    val onBatterySettings: () -> Unit = {},
    /** v0.7 team mode + context compaction. */
    val teamMode: Boolean = false,
    val onTeamMode: (Boolean) -> Unit = {},
    val reviewModel: String = "",
    val onReviewModel: (String) -> Unit = {},
    val autoCompact: Boolean = true,
    val onAutoCompact: (Boolean) -> Unit = {},
    /** v0.8 team mode: planner stage + automatic checks fed to the reviewer. */
    val plannerModel: String = "",
    val onPlannerModel: (String) -> Unit = {},
    val reviewAutoTests: Boolean = true,
    val onReviewAutoTests: (Boolean) -> Unit = {},
    /** v0.13.2 Falcon Router: route the light stages to the cheapest capable model. */
    val falconRouter: Boolean = true,
    val onFalconRouter: (Boolean) -> Unit = {},
    /** v0.15 Autonomous Core switches, fallback model and run budgets. */
    val autonomy: AutonomyUi = AutonomyUi(),
    /** v0.17 memory engine & smart router. */
    val memoryRouter: MemoryRouterUi = MemoryRouterUi(),
    /** v0.16 Agent team (Chief mode) settings. */
    val team: TeamUi = TeamUi(),
    /** v0.8 MCP servers (add returns an error message, or null on success). */
    val mcpServers: List<com.ctodrs.saqrcode.agent.Mcp.Server> = emptyList(),
    val onMcpAdd: (String, String, String) -> String? = { _, _, _ -> null },
    val onMcpRemove: (String) -> Unit = {},
    /** v0.9: the decrypted auth header of an MCP server (for the tools browser). */
    val mcpAuthFor: (String) -> String? = { null },
    /** v0.14.1 MCP OAuth: per-server config + tokens + the PKCE flow callbacks. */
    val mcpOAuthConfigFor: (String) -> com.ctodrs.saqrcode.agent.McpOAuth.Config? = { null },
    val mcpTokensFor: (String) -> com.ctodrs.saqrcode.agent.McpOAuth.Tokens? = { null },
    val onMcpOAuthSave: (String, com.ctodrs.saqrcode.agent.McpOAuth.Config) -> Unit = { _, _ -> },
    val onMcpOAuthRemove: (String) -> Unit = {},
    val onMcpOAuthPending: (com.ctodrs.saqrcode.agent.McpOAuth.Pending) -> Unit = {},
    /** v0.10: per-server MCP tool allow/deny — is (server, rawTool) hidden from the agent. */
    val mcpToolDisabled: (server: String, rawTool: String) -> Boolean = { _, _ -> false },
    val onMcpToolDisabled: (server: String, rawTool: String, disabled: Boolean) -> Unit = { _, _, _ -> },
    /** v0.9: models suggested for the active provider (review/planner pickers). */
    val suggestedModels: List<String> = emptyList(),
    /** v0.8/v0.9 on-device build: aapt2 source — blank, a preset id, or a direct URL. */
    val aapt2Mirror: String = "",
    val onAapt2Mirror: (String) -> Unit = {},
    /** v0.10: «keep only the last APK» — auto-clean after a successful build. */
    val keepLastApk: Boolean = false,
    val onKeepLastApk: (Boolean) -> Unit = {},
    /** v0.11 billing: the tier in force, its expiry line, and the paywall navigation. */
    val planTier: com.ctodrs.saqrcode.billing.PlanTier = com.ctodrs.saqrcode.billing.PlanTier.FREE,
    val planUntil: String = "",
    val onPaywall: () -> Unit = {},
    /** v0.11: MCP servers speaking stdio through the terminal env. */
    val mcpStdioServers: List<com.ctodrs.saqrcode.agent.McpStdio.StdioServer> = emptyList(),
    val onMcpStdioAdd: (name: String, command: String, env: String) -> String? = { _, _, _ -> null },
    val onMcpStdioRemove: (String) -> Unit = {},
    /** v0.12 theme studio: saved packs, the active pack id, and the model generation callback. */
    val savedPacks: List<com.ctodrs.saqrcode.ui.theme.ThemePack> = emptyList(),
    val activePackId: String? = null,
    val onApplyPack: (com.ctodrs.saqrcode.ui.theme.ThemePack) -> Unit = {},
    val onSavePack: (com.ctodrs.saqrcode.ui.theme.ThemePack) -> Unit = {},
    val onDeletePack: (String) -> Unit = {},
    val onCopyPack: (com.ctodrs.saqrcode.ui.theme.ThemePack) -> Unit = {},
    val generateTheme: suspend (String) -> Result<com.ctodrs.saqrcode.ui.theme.ThemePack> = { Result.failure(IllegalStateException()) },
    /** v0.12 editor: font size (12–20sp) + line wrap. */
    val editorFontSize: Int = 13,
    val onEditorFontSize: (Int) -> Unit = {},
    val editorWrap: Boolean = false,
    val onEditorWrap: (Boolean) -> Unit = {},
    /** v0.13.3 Saqr Coach: completed-steps label and the reopen callback. */
    val coachProgressLabel: String = "",
    val onCoach: () -> Unit = {},
    /** v0.14 skill packs: installed .saqrpack bundles + install/remove callbacks. */
    val packs: List<com.ctodrs.saqrcode.data.SaqrPack> = emptyList(),
    /** Raw pack JSON → an error key, or null when installed. */
    val onInstallPack: (String) -> String? = { null },
    val onInstallFlask: () -> Unit = {},
    val onRemovePack: (String) -> Unit = {},
    /** v0.18 agent skills: switches, installed skills and the shared install pipeline. */
    val skills: SkillsUi = SkillsUi(),
)

@Composable
fun SettingsScreen(onBack: () -> Unit, onAccounts: () -> Unit = {}, onPaywall: () -> Unit = {}) {
    val settings = SaqrApp.instance.settings
    val ctx = LocalContext.current
    val version by settings.version.collectAsState()
    version
    var showBattery by remember { mutableStateOf(false) }
    var teamGate by remember { mutableStateOf(false) }
    // v0.13.3 Saqr Coach: reopen the path from settings (also unhides the home card)
    var coachOpen by remember { mutableStateOf(false) }
    val coachSnap = remember(version) {
        val app = SaqrApp.instance
        CoachSnapshot(
            hasKey = settings.activeReady(),
            hasProject = app.projects.list().isNotEmpty(),
            hasFirstMessage = settings.coachFirstMessageAt > 0L,
            hasBuildAttempt = app.builds.anyAttempt(),
            hasSuccessfulBuild = app.builds.anySuccess(),
        )
    }
    val ui = SettingsUi(
        themeMode = settings.themeMode,
        langTag = AppCompatDelegate.getApplicationLocales().toLanguageTags(),
        providers = Providers.all.map { it to settings.config(it.id) },
        activeProvider = settings.activeProvider,
        approveAll = settings.approveAll,
        githubUser = settings.accounts.github?.login ?: "",
        githubTokenSet = settings.githubToken != null,
        versionName = BuildConfig.VERSION_NAME,
        onTheme = { settings.themeMode = it },
        onLang = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) },
        onActivate = { settings.activeProvider = it },
        onSaveProvider = { id, b, m, k -> settings.saveProvider(id, b, m, k) },
        fetchModels = { def, b, m, k -> runCatching { LlmClient(def, b, m, k ?: settings.apiKey(def.id)).listModels() } },
        onApproveAll = { settings.approveAll = it },
        onClearKey = { settings.clearKey(it) },
        onAccounts = onAccounts,
        customCommands = settings.customCommands,
        onAddCommand = { n, t ->
            val existing = settings.customCommands
            SlashCommands.validate(n, t, existing).also { err ->
                if (err == null) settings.customCommands = existing + SlashCommand(n.trim().removePrefix("/"), t.trim())
            }
        },
        onDeleteCommand = { n -> settings.customCommands = settings.customCommands.filter { it.name != n } },
        monthlyBudget = settings.monthlyBudget,
        onBudget = { settings.monthlyBudget = it },
        dailyBudget = settings.dailyBudget,
        onDailyBudget = { settings.dailyBudget = it },
        prices = settings.prices(),
        onPrice = { settings.setPrice(it) },
        activeModel = settings.config(settings.activeProvider).model,
        visionMode = settings.visionMode(settings.config(settings.activeProvider).model),
        onVisionMode = { settings.setVisionMode(settings.config(settings.activeProvider).model, it) },
        voiceAutoSend = settings.voiceAutoSend,
        onVoiceAutoSend = { settings.voiceAutoSend = it },
        voiceLang = settings.voiceLang,
        onVoiceLang = { settings.voiceLang = it },
        dictation = settings.dictation,
        onDictation = { settings.dictation = it },
        desert = settings.desert,
        onDesert = {
            settings.desert = it
            com.ctodrs.saqrcode.agent.DesertState.mode = com.ctodrs.saqrcode.agent.DesertMode.Mode.from(it)
        },
        keepInBackground = settings.keepInBackground,
        onKeepInBackground = { settings.keepInBackground = it },
        onBatterySettings = { showBattery = true },
        teamMode = settings.agentMode == "team",
        onTeamMode = {
            // v0.11: team agents are a paid-tier feature — free users get the paywall gate.
            if (it && !settings.limits().teamAgents) teamGate = true
            else settings.agentMode = if (it) "team" else "single"
        },
        reviewModel = settings.reviewModel,
        onReviewModel = { settings.reviewModel = it },
        autoCompact = settings.autoCompact,
        onAutoCompact = { settings.autoCompact = it },
        plannerModel = settings.plannerModel,
        onPlannerModel = { settings.plannerModel = it },
        reviewAutoTests = settings.reviewAutoTests,
        onReviewAutoTests = { settings.reviewAutoTests = it },
        falconRouter = settings.falconRouter,
        onFalconRouter = { settings.falconRouter = it },
        autonomy = AutonomyUi.from(settings),
        memoryRouter = MemoryRouterUi.from(settings),
        team = TeamUi.from(settings),
        mcpServers = settings.mcpServers,
        onMcpAdd = { name, url, auth ->
            val err = McpSettings.validate(name, url, settings.mcpServers)
            if (err == null) {
                // v0.11: HTTP + stdio servers share the plan's MCP budget.
                if (settings.mcpServers.size + settings.mcpStdioServers.size >= settings.limits().maxMcpServers) {
                    teamGate = true
                    "Free plan: only 1 MCP server — upgrade to Pro for more."
                } else {
                    settings.mcpServers = settings.mcpServers + com.ctodrs.saqrcode.agent.Mcp.Server(name, url)
                    settings.setMcpAuth(name, auth.ifBlank { null })
                    null
                }
            } else err
        },
        onMcpRemove = { name ->
            settings.mcpServers = settings.mcpServers.filter { it.name != name }
            settings.setMcpAuth(name, null)
            settings.setMcpOAuthConfig(name, null)
            settings.setMcpTokens(name, null)
        },
        mcpAuthFor = { name -> settings.mcpAuthHeader(name) },
        mcpOAuthConfigFor = { name -> settings.mcpOAuthConfig(name) },
        mcpTokensFor = { name -> settings.mcpTokens(name) },
        onMcpOAuthSave = { name, cfg -> settings.setMcpOAuthConfig(name, cfg) },
        onMcpOAuthRemove = { name ->
            settings.setMcpOAuthConfig(name, null)
            settings.setMcpTokens(name, null)
        },
        onMcpOAuthPending = { settings.setMcpOAuthPending(it) },
        mcpToolDisabled = { s, t -> settings.mcpToolDisabled(s, t) },
        onMcpToolDisabled = { s, t, d -> settings.setMcpToolDisabled(s, t, d) },
        suggestedModels = com.ctodrs.saqrcode.agent.Providers.byId(settings.activeProvider).suggestedModels,
        aapt2Mirror = settings.aapt2Mirror,
        onAapt2Mirror = { settings.aapt2Mirror = it },
        keepLastApk = settings.keepLastApk,
        onKeepLastApk = { settings.keepLastApk = it },
        planTier = settings.planTier(),
        planUntil = settings.entitlement()?.expiresAt?.let { exp ->
            java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(exp))
        } ?: "",
        onPaywall = onPaywall,
        mcpStdioServers = settings.mcpStdioServers,
        onMcpStdioAdd = { name, command, env ->
            val err = McpStdioSettings.validate(name, command, settings.mcpStdioServers)
            if (err == null) {
                if (settings.mcpServers.size + settings.mcpStdioServers.size >= settings.limits().maxMcpServers) {
                    teamGate = true
                    "Free plan: only 1 MCP server — upgrade to Pro for more."
                } else {
                    settings.mcpStdioServers = settings.mcpStdioServers + com.ctodrs.saqrcode.agent.McpStdio.StdioServer(name, command, env)
                    null
                }
            } else err
        },
        onMcpStdioRemove = { name -> settings.mcpStdioServers = settings.mcpStdioServers.filter { it.name != name } },
        savedPacks = com.ctodrs.saqrcode.ui.theme.ThemeStudio.decodePacks(settings.savedThemesJson),
        activePackId = com.ctodrs.saqrcode.ui.theme.ThemePack.fromJson(settings.customThemeJson)?.id.takeIf { settings.themeMode == "custom" },
        onApplyPack = { pack ->
            settings.customThemeJson = pack.toJson()
            settings.themeMode = "custom"
        },
        onSavePack = { pack ->
            val studio = com.ctodrs.saqrcode.ui.theme.ThemeStudio
            val cur = studio.decodePacks(settings.savedThemesJson).filter { it.id != pack.id }
            settings.savedThemesJson = studio.encodePacks(cur + pack)
        },
        onDeletePack = { id ->
            val studio = com.ctodrs.saqrcode.ui.theme.ThemeStudio
            settings.savedThemesJson = studio.encodePacks(studio.decodePacks(settings.savedThemesJson).filter { it.id != id })
        },
        onCopyPack = { pack ->
            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("saqr-theme", pack.toJson()))
            android.widget.Toast.makeText(ctx, R.string.studio_export_copied, android.widget.Toast.LENGTH_SHORT).show()
        },
        coachProgressLabel = "${SaqrCoach.progress(coachSnap)}/${SaqrCoach.STEPS.size}",
        onCoach = { settings.coachDismissed = false; coachOpen = true },
        generateTheme = { mood ->
            runCatching {
                require(settings.activeReady()) { "no_provider" }
                val def = Providers.byId(settings.activeProvider)
                val cfg = settings.config(def.id)
                val client = LlmClient(def, cfg.baseUrl, cfg.model, settings.apiKey(def.id))
                val turn = client.complete(
                    com.ctodrs.saqrcode.ui.theme.ThemeStudio.SYSTEM_PROMPT,
                    listOf(com.ctodrs.saqrcode.agent.Msg.User(com.ctodrs.saqrcode.ui.theme.ThemeStudio.buildRequest(mood))),
                    emptyList(),
                    object : com.ctodrs.saqrcode.agent.StreamListener { override fun onText(delta: String) {} },
                )
                com.ctodrs.saqrcode.ui.theme.ThemeStudio.parseModelOutput(turn.text) ?: error("theme_json")
            }
        },
        editorFontSize = settings.editorFontSize,
        onEditorFontSize = { settings.editorFontSize = it },
        editorWrap = settings.editorWrap,
        onEditorWrap = { settings.editorWrap = it },
        packs = com.ctodrs.saqrcode.data.SaqrPackCodec.decodePacks(settings.packsJson),
        // v0.18: every pack (with or without skills) goes through the one skill-aware pipeline;
        // a v0.14 pack ends exactly as before (commands merged, pack upserted for the home screen)
        onInstallPack = { raw ->
            when (val plan = com.ctodrs.saqrcode.agent.skills.SkillManager.plan(raw, com.ctodrs.saqrcode.agent.skills.SkillSource.PASTE, settings.installedSkills)) {
                is com.ctodrs.saqrcode.agent.skills.InstallPlan.Refused -> plan.reasons.firstOrNull()?.code ?: "json"
                is com.ctodrs.saqrcode.agent.skills.InstallPlan.Ready -> { SkillsUi.applyPlan(settings, plan); null }
            }
        },
        onInstallFlask = {
            val (cmds, packs) = com.ctodrs.saqrcode.data.SaqrPackCodec.install(
                settings.customCommands,
                com.ctodrs.saqrcode.data.SaqrPackCodec.decodePacks(settings.packsJson),
                com.ctodrs.saqrcode.data.SaqrPackCodec.flaskSample(), System.currentTimeMillis(),
            )
            settings.customCommands = cmds
            settings.packsJson = com.ctodrs.saqrcode.data.SaqrPackCodec.encodePacks(packs)
        },
        onRemovePack = { id ->
            settings.packsJson = com.ctodrs.saqrcode.data.SaqrPackCodec.encodePacks(
                com.ctodrs.saqrcode.data.SaqrPackCodec.remove(
                    com.ctodrs.saqrcode.data.SaqrPackCodec.decodePacks(settings.packsJson), id))
        },
        skills = SkillsUi.from(settings),
    )
    if (showBattery) BatteryDialog(onDismiss = { showBattery = false })
    com.ctodrs.saqrcode.billing.PaywallDialog(
        show = teamGate, onDismiss = { teamGate = false }, onOpenPaywall = onPaywall,
    )
    if (coachOpen) CoachSheet(
        snap = coachSnap,
        progress = settings.coachProgress(),
        template = remember(version) {
            val latest = SaqrApp.instance.projects.list().firstOrNull()
            if (latest == null) "WEB"
            else SaqrCoach.guessTemplate(
                latest.dir.walkTopDown().onEnter { it.name !in setOf(".git", "node_modules", ".saqr") }
                    .filter { it.isFile }.take(60).map { it.name }.toList())
        },
        onDismiss = { coachOpen = false },
        onHide = { settings.coachDismissed = true; coachOpen = false },
    )
    SettingsContent(ui, onBack)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SettingsContent(ui: SettingsUi, onBack: () -> Unit, initiallyExpanded: String? = null) {
    val c = Saqr.colors
    var page by remember { mutableStateOf(if (initiallyExpanded != null) "models" else "") }
    androidx.activity.compose.BackHandler(enabled = page.isNotEmpty()) { page = "" }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Sp.md, vertical = Sp.md), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Lucide.ArrowLeft, stringResource(R.string.back), { if (page.isNotEmpty()) page = "" else onBack() })
            Spacer(Modifier.width(Sp.md))
            Text(stringResource(if (page.isEmpty()) R.string.settings else settingsCatTitle(page)), style = Saqr.type.title1, color = c.text1)
        }
        var query by remember { mutableStateOf("") }
        var pending by remember { mutableStateOf<String?>(null) }
        val jump = remember { SectionJump() }
        val sections = settingsSectionIndex()
        LaunchedEffect(pending, query.isBlank(), page) {
            val k = pending
            if (k != null && query.isBlank() && page == settingsCatOf(k)) {
                androidx.compose.runtime.withFrameNanos { }
                jump.reqs[k]?.bringIntoView()
                pending = null
            }
        }
        SaqrField(
            query, { query = it }, Modifier.padding(horizontal = Sp.lg).padding(bottom = Sp.sm),
            placeholder = stringResource(R.string.settings_search),
        )
        val results = SettingsSearch.match(sections, query)
        if (query.isNotBlank()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Sp.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                if (results.isEmpty()) Text(stringResource(R.string.settings_search_none), style = Saqr.type.body, color = c.text3, modifier = Modifier.padding(Sp.lg).polite())
                else Group {
                    Column {
                        results.forEachIndexed { i, k ->
                            if (i > 0) RowDivider()
                            ListRow(title = sections.first { it.key == k }.title, icon = Lucide.Search, onClick = { pending = k; page = settingsCatOf(k); query = "" })
                        }
                    }
                }
            }
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Sp.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Sp.sm),
        ) {
            if (page == "interface") {
            if (page == "") {
                SETTINGS_CATS.forEach { (key, ic) ->
                    Group {
                        ListRow(
                            title = stringResource(settingsCatTitle(key)), subtitle = stringResource(settingsCatDesc(key)), icon = ic,
                            onClick = { page = key },
                            trailing = { SIcon(Lucide.ChevronRight, size = 18.dp, tint = c.text3) },
                        )
                    }
                }
            }
            // ---- appearance
            JumpLabel(jump, "appearance", stringResource(R.string.appearance), Modifier.padding(top = Sp.xs))
            Group {
                Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                    LabeledIcon(Lucide.Palette, stringResource(R.string.theme))
                    val themes = buildList {
                        addAll(listOf("dark", "light", "oled", "system"))
                        if (android.os.Build.VERSION.SDK_INT >= 31) add("dynamic")
                    }
                    Segmented(themes.map { t ->
                        stringResource(when (t) {
                            "dark" -> R.string.theme_dark; "light" -> R.string.theme_light
                            "oled" -> R.string.theme_oled; "system" -> R.string.theme_system
                            else -> R.string.theme_dynamic
                        })
                    }, themes.indexOf(ui.themeMode).coerceAtLeast(0), { ui.onTheme(themes[it]) })
                    Spacer(Modifier.height(Sp.xs))
                    ThemeStudioCard(ui)
                    Spacer(Modifier.height(Sp.xs))
                    LabeledIcon(Lucide.Languages, stringResource(R.string.language))
                    val langs = listOf("", "ar", "en")
                    val cur = when { ui.langTag.startsWith("ar") -> 1; ui.langTag.startsWith("en") -> 2; else -> 0 }
                    Segmented(listOf(stringResource(R.string.lang_system), stringResource(R.string.lang_ar), stringResource(R.string.lang_en)), cur, { ui.onLang(langs[it]) })
                }
            }

            }
            if (page == "interface") {
            // ---- editor (v0.12)
            JumpLabel(jump, "editor_section", stringResource(R.string.editor_section), Modifier.padding(top = Sp.md))
            Group {
                Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                    LabeledIcon(Lucide.CodeXml, stringResource(R.string.editor_font))
                    val sizes = listOf(12, 13, 14, 16, 18, 20)
                    Segmented(sizes.map { it.toString() }, sizes.indexOf(ui.editorFontSize).coerceAtLeast(0), { ui.onEditorFontSize(sizes[it]) })
                    Spacer(Modifier.height(Sp.xs))
                    ListRow(
                        title = stringResource(R.string.editor_wrap), subtitle = stringResource(R.string.editor_wrap_desc), icon = Lucide.ArrowUpDown,
                        trailing = { SaqrSwitch(ui.editorWrap, ui.onEditorWrap) },
                    )
                }
            }

            }
            if (page == "models") {
            // ---- providers
            JumpLabel(jump, "providers", stringResource(R.string.providers), Modifier.padding(top = Sp.md))
            Group {
                Column {
                    ui.providers.forEachIndexed { i, (def, cfg) ->
                        if (i > 0) RowDivider()
                        ProviderItem(def, cfg, active = ui.activeProvider == def.id, ui = ui, startOpen = initiallyExpanded == def.id)
                    }
                }
            }
            Row(Modifier.padding(horizontal = Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                SIcon(Lucide.ShieldCheck, size = 14.dp, tint = c.success)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.keys_secure_note), style = Saqr.type.caption, color = c.text3)
            }

            }
            if (page == "agent") {
            // ---- agent
            JumpLabel(jump, "agent_section", stringResource(R.string.agent_section), Modifier.padding(top = Sp.md))
            Group {
                ListRow(
                    title = stringResource(R.string.approval_mode), subtitle = stringResource(R.string.approval_mode_desc), icon = Lucide.ListChecks,
                    trailing = { SaqrSwitch(ui.approveAll, ui.onApproveAll) },
                )
            }
            Group {
                Column {
                    ListRow(
                        title = stringResource(R.string.team_mode), subtitle = stringResource(R.string.team_mode_desc), icon = Lucide.Users,
                        trailing = { SaqrSwitch(ui.teamMode, ui.onTeamMode) },
                    )
                    RowDivider()
                    ListRow(
                        title = stringResource(R.string.auto_compact), subtitle = stringResource(R.string.auto_compact_desc), icon = Lucide.RefreshCw,
                        trailing = { SaqrSwitch(ui.autoCompact, ui.onAutoCompact) },
                    )
                }
            }
            if (ui.teamMode) {
                Group { ReviewModelEditor(ui) }
                Group { PlannerModelEditor(ui) }
                Group {
                    ListRow(
                        title = stringResource(R.string.review_auto_tests), subtitle = stringResource(R.string.review_auto_tests_desc), icon = Lucide.CircleCheck,
                        trailing = { SaqrSwitch(ui.reviewAutoTests, ui.onReviewAutoTests) },
                    )
                }
                // ---- v0.16 Agent team (Chief mode) — a team-mode option, so it sits under the team gate
                TeamSection(ui.team)
            }
            // v0.13.2 Falcon Router — works in solo mode too (compaction), so it sits outside the team gate
            Group {
                ListRow(
                    title = stringResource(R.string.falcon_router), subtitle = stringResource(R.string.falcon_router_desc), icon = Lucide.Route,
                    trailing = { SaqrSwitch(ui.falconRouter, ui.onFalconRouter) },
                )
            }
            if (ui.activeModel.isNotBlank()) Group { VisionEditor(ui) }
            // ---- v0.15 Autonomous Core
            AutonomySection(ui.autonomy, ui.teamMode)
            // ---- v0.17 Memory engine & smart router
            MemoryRouterSection(ui.memoryRouter)
            }
            if (page == "tools") {
            // ---- v0.8/v0.9 MCP servers
            JumpLabel(jump, "mcp_section", stringResource(R.string.mcp_section), Modifier.padding(top = Sp.md))
            var browsingServer by remember { mutableStateOf<com.ctodrs.saqrcode.agent.Mcp.Server?>(null) }
            if (ui.mcpServers.isNotEmpty()) {
                Group {
                    Column {
                        ui.mcpServers.forEachIndexed { i, s ->
                            if (i > 0) RowDivider()
                            ListRow(
                                title = s.name, subtitle = s.url,
                                icon = Lucide.Plug,
                                onClick = { browsingServer = s },
                                trailing = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Tag(stringResource(R.string.mcp_browse), color = c.accentText, dot = false)
                                        Spacer(Modifier.width(Sp.xs))
                                        Box(Modifier.minimumInteractiveComponentSize().size(44.dp).clip(CircleShape).clickable(role = Role.Button) { ui.onMcpRemove(s.name) }, contentAlignment = Alignment.Center) {
                                            SIcon(Lucide.Trash2, size = 18.dp, tint = Saqr.colors.danger, contentDescription = stringResource(R.string.delete))
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Group { McpSettings(ui) }
            // ---- v0.11: stdio MCP servers (run inside the terminal env)
            if (ui.mcpStdioServers.isNotEmpty()) {
                Group {
                    Column {
                        ui.mcpStdioServers.forEachIndexed { i, s ->
                            if (i > 0) RowDivider()
                            ListRow(
                                title = s.name, subtitle = s.command,
                                icon = Lucide.SquareTerminal,
                                trailing = {
                                    Box(Modifier.minimumInteractiveComponentSize().size(44.dp).clip(CircleShape).clickable(role = Role.Button) { ui.onMcpStdioRemove(s.name) }, contentAlignment = Alignment.Center) {
                                        SIcon(Lucide.Trash2, size = 18.dp, tint = Saqr.colors.danger, contentDescription = stringResource(R.string.delete))
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Group { McpStdioSettings(ui) }
            browsingServer?.let { server ->
                McpToolsBrowser(
                    server = server,
                    auth = ui.mcpAuthFor(server.name),
                    disabledFor = { raw -> ui.mcpToolDisabled(server.name, raw) },
                    onToggleTool = { raw, d -> ui.onMcpToolDisabled(server.name, raw, d) },
                    oauthCfg = ui.mcpOAuthConfigFor(server.name),
                    oauthTokens = ui.mcpTokensFor(server.name),
                    onSaveOAuth = { ui.onMcpOAuthSave(server.name, it) },
                    onRemoveOAuth = { ui.onMcpOAuthRemove(server.name) },
                    onStartOAuth = { ui.onMcpOAuthPending(it) },
                    onDismiss = { browsingServer = null },
                )
            }
            }
            if (page == "project") {
            // ---- v0.8 on-device build
            JumpLabel(jump, "build_section", stringResource(R.string.build_section), Modifier.padding(top = Sp.md))
            Group { Aapt2MirrorEditor(ui) }
            Group {
                Column {
                    ListRow(
                        title = stringResource(R.string.keep_last_apk), subtitle = stringResource(R.string.keep_last_apk_desc), icon = Lucide.HardDrive,
                        trailing = { SaqrSwitch(ui.keepLastApk, ui.onKeepLastApk) },
                    )
                }
            }
            Group {
                Column {
                    ListRow(
                        title = stringResource(R.string.keep_background), subtitle = stringResource(R.string.keep_background_desc), icon = Lucide.Zap,
                        trailing = { SaqrSwitch(ui.keepInBackground, ui.onKeepInBackground) },
                    )
                    RowDivider()
                    ListRow(
                        title = stringResource(R.string.battery_title), subtitle = stringResource(R.string.battery_row_desc), icon = Lucide.BatteryCharging,
                        onClick = ui.onBatterySettings, trailing = { SIcon(Lucide.ChevronRight, size = 18.dp, tint = Saqr.colors.text3) },
                    )
                }
            }

            }
            if (page == "interface") {
            // ---- voice
            JumpLabel(jump, "voice_section", stringResource(R.string.voice_section), Modifier.padding(top = Sp.md))
            Group {
                Column {
                    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                        LabeledIcon(Lucide.AudioLines, stringResource(R.string.hands_free))
                        val modes = com.ctodrs.saqrcode.agent.Dictation.Mode.entries
                        val cur = modes.indexOfFirst { it.key == ui.dictation }.coerceAtLeast(0)
                        Segmented(listOf(stringResource(R.string.hands_free_off), stringResource(R.string.hands_free_typing), stringResource(R.string.hands_free_wake)),
                            cur, { ui.onDictation(modes[it].key) })
                        Text(stringResource(R.string.hands_free_hint), style = Saqr.type.caption, color = Saqr.colors.text3)
                    }
                    RowDivider()
                    ListRow(
                        title = stringResource(R.string.voice_auto_send), subtitle = stringResource(R.string.voice_auto_send_desc), icon = Lucide.Mic,
                        trailing = { SaqrSwitch(ui.voiceAutoSend, ui.onVoiceAutoSend) },
                    )
                    RowDivider()
                    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                        LabeledIcon(Lucide.Languages, stringResource(R.string.voice_language))
                        val tags = listOf("", com.ctodrs.saqrcode.agent.VoiceLocale.AR, com.ctodrs.saqrcode.agent.VoiceLocale.EN)
                        val cur = when { ui.voiceLang.startsWith("ar") -> 1; ui.voiceLang.startsWith("en") -> 2; else -> 0 }
                        Segmented(listOf(stringResource(R.string.voice_follow_app), stringResource(R.string.lang_ar), stringResource(R.string.lang_en)), cur, { ui.onVoiceLang(tags[it]) })
                        Text(stringResource(R.string.voice_language_hint), style = Saqr.type.caption, color = Saqr.colors.text3)
                    }
                }
            }

            }
            if (page == "agent") {
            // ---- desert mode (v0.13.5): offline-first rationing with a live status line
            JumpLabel(jump, "desert_section", stringResource(R.string.desert_section), Modifier.padding(top = Sp.md))
            Group {
                Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                    LabeledIcon(Lucide.WifiOff, stringResource(R.string.desert_title))
                    val modes = com.ctodrs.saqrcode.agent.DesertMode.Mode.entries
                    val cur = modes.indexOfFirst { it.key == ui.desert }.coerceAtLeast(0)
                    Segmented(
                        listOf(stringResource(R.string.desert_off), stringResource(R.string.desert_auto), stringResource(R.string.desert_always)),
                        cur, { ui.onDesert(modes[it].key) },
                    )
                    Text(stringResource(R.string.desert_hint), style = Saqr.type.caption, color = Saqr.colors.text3)
                    val activeNow = com.ctodrs.saqrcode.agent.DesertState.active
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                        SIcon(if (activeNow) Lucide.WifiOff else Lucide.CircleCheck, size = 14.dp,
                            tint = if (activeNow) Saqr.colors.warning else Saqr.colors.success)
                        Text(
                            stringResource(if (activeNow) R.string.desert_status_active else R.string.desert_status_idle),
                            style = Saqr.type.caption, color = Saqr.colors.text3,
                        )
                    }
                }
            }

            }
            if (page == "agent") {
            // ---- slash commands
            JumpLabel(jump, "slash_section", stringResource(R.string.slash_section), Modifier.padding(top = Sp.md))
            Group { SlashCommandsEditor(ui) }

            }
            if (page == "tools") {
            // ---- v0.14 skill packs: one-tap .saqrpack install
            JumpLabel(jump, "pack_section", stringResource(R.string.pack_section), Modifier.padding(top = Sp.md))
            Group { SaqrPacksCard(ui) }

            }
            if (page == "tools") {
            // ---- v0.14.3 community gallery: browse + one-tap install from the public repo
            JumpLabel(jump, "gallery_section", stringResource(R.string.gallery_section), Modifier.padding(top = Sp.md))
            Group { GallerySection(ui) }

            }
            if (page == "tools") {
            // ---- v0.18 agent skills: switches + the skills manager (list, search, details, update, export)
            JumpLabel(jump, "skills_section", stringResource(R.string.skills_section), Modifier.padding(top = Sp.md))
            Group { SkillsSection(ui.skills) }

            }
            if (page == "project") {
            // ---- v0.14.4 repository sync: encrypted snapshot → the user's own private repo or a file
            JumpLabel(jump, "sync_section", stringResource(R.string.sync_section), Modifier.padding(top = Sp.md))
            Group { SyncSection(ui) }

            }
            if (page == "models") {
            // ---- usage & budget
            JumpLabel(jump, "usage_section", stringResource(R.string.usage_section), Modifier.padding(top = Sp.md))
            Group { BudgetEditor(ui) }
            Group { FalconSavingsRow() }
            Group { PricesEditor(ui) }

            }
            if (page == "account") {
            // ---- accounts
            JumpLabel(jump, "accounts", stringResource(R.string.accounts), Modifier.padding(top = Sp.md))
            Group {
                ListRow(
                    title = stringResource(R.string.github),
                    subtitle = if (ui.githubTokenSet && ui.githubUser.isNotBlank()) "@" + ui.githubUser else stringResource(R.string.accounts_desc),
                    icon = Lucide.Github, onClick = ui.onAccounts,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tag(stringResource(if (ui.githubTokenSet) R.string.connected else R.string.not_connected),
                                color = if (ui.githubTokenSet) c.success else c.text3, dot = true)
                            Spacer(Modifier.width(Sp.xs))
                            SIcon(Lucide.ChevronRight, size = 18.dp, tint = c.text3)
                        }
                    },
                )
            }

            }
            if (page == "account") {
            // ---- v0.11 billing & licensing
            JumpLabel(jump, "billing_section", stringResource(R.string.billing_section), Modifier.padding(top = Sp.md))
            Group {
                Column {
                    ListRow(
                        title = stringResource(when (ui.planTier) {
                            com.ctodrs.saqrcode.billing.PlanTier.PRO -> R.string.tier_pro
                            com.ctodrs.saqrcode.billing.PlanTier.STUDENT -> R.string.tier_student
                            com.ctodrs.saqrcode.billing.PlanTier.LIFETIME -> R.string.tier_lifetime
                            com.ctodrs.saqrcode.billing.PlanTier.FREE -> R.string.tier_free
                        }),
                        subtitle = stringResource(R.string.billing_row_desc, ui.planUntil.ifBlank { "—" }),
                        icon = Lucide.Star,
                        onClick = ui.onPaywall,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Tag(stringResource(R.string.manage), color = Saqr.colors.accentText, dot = false)
                                Spacer(Modifier.width(Sp.xs))
                                SIcon(Lucide.ChevronRight, size = 18.dp, tint = Saqr.colors.text3)
                            }
                        },
                    )
                }
            }
            Group { com.ctodrs.saqrcode.billing.MerchantToolsCard(settings = SaqrApp.instance.settings) }

            }
            if (page == "help") {
            // ---- v0.13.3 Saqr Coach
            JumpLabel(jump, "coach_title", stringResource(R.string.coach_title), Modifier.padding(top = Sp.md))
            Group {
                ListRow(
                    title = stringResource(R.string.coach_row_title),
                    subtitle = stringResource(R.string.coach_row_desc),
                    icon = Lucide.ListChecks,
                    onClick = ui.onCoach,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (ui.coachProgressLabel.isNotBlank()) {
                                Tag(ui.coachProgressLabel, color = Saqr.colors.accentText, dot = false)
                                Spacer(Modifier.width(Sp.xs))
                            }
                            SIcon(Lucide.ChevronRight, size = 18.dp, tint = c.text3)
                        }
                    },
                )
            }

            }
            if (page == "help") {
            // ---- v0.1.46 user guide
            JumpLabel(jump, "guide_section", stringResource(R.string.guide_section), Modifier.padding(top = Sp.md))
            Group { GuideList() }

            }
            if (page == "help") {
            // ---- about
            JumpLabel(jump, "about", stringResource(R.string.about), Modifier.padding(top = Sp.md))
            Group {
                Row(Modifier.padding(Sp.lg), verticalAlignment = Alignment.CenterVertically) {
                    FalconMark(44.dp)
                    Spacer(Modifier.width(Sp.md))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), style = Saqr.type.title3.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                        Text(stringResource(R.string.about_text, ui.versionName), style = Saqr.type.caption, color = c.text2)
                    }
                }
            }
            val crashCtx = androidx.compose.ui.platform.LocalContext.current
            var crash by remember { mutableStateOf(com.ctodrs.saqrcode.data.CrashLog.latest(crashCtx)) }
            crash?.let { report ->
                Group {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(Sp.lg), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.crash_title), style = Saqr.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                            Text(stringResource(R.string.crash_body), style = Saqr.type.caption, color = c.text2)
                        }
                        Text(stringResource(R.string.crash_share), style = Saqr.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accentText,
                            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) {
                                runCatching {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Saqr Code crash report")
                                        putExtra(android.content.Intent.EXTRA_TEXT, report)
                                    }
                                    crashCtx.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }.padding(horizontal = Sp.sm, vertical = Sp.md))
                        Text(stringResource(R.string.crash_clear), style = Saqr.type.caption, color = c.text3,
                            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) {
                                com.ctodrs.saqrcode.data.CrashLog.clear(crashCtx); crash = null
                            }.padding(horizontal = Sp.sm, vertical = Sp.md))
                    }
                }
            }
            if (!SaqrApp.instance.settings.secretsEncrypted) {
                Group {
                    Column(Modifier.fillMaxWidth().padding(Sp.lg).semantics(mergeDescendants = true) {}) {
                        Text(stringResource(R.string.secrets_plain_title), style = Saqr.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                        Text(stringResource(R.string.secrets_plain_body), style = Saqr.type.caption, color = c.text2)
                    }
                }
            }
            }
            if (page == "help") {
            JumpLabel(jump, "about_developer", stringResource(R.string.about_developer), Modifier.padding(top = Sp.md))
            val dctx = androidx.compose.ui.platform.LocalContext.current
            val ver = remember { runCatching { dctx.packageManager.getPackageInfo(dctx.packageName, 0).versionName }.getOrNull().orEmpty() }
            val banners = remember { DeveloperBanners.load(dctx) }
            DeveloperBannerCarousel(banners, Modifier.padding(bottom = Sp.md)) { url -> runCatching { dctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } }
            DeveloperCard(versionName = ver)
            }
            Spacer(Modifier.height(Sp.xxl))
        }
    }
}

internal val SETTINGS_CATS: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>> = listOf(
    "models" to Lucide.Cpu, "agent" to Lucide.Bot, "tools" to Lucide.Plug, "project" to Lucide.Hammer,
    "interface" to Lucide.Palette, "account" to Lucide.User, "help" to Lucide.Info,
)

internal fun settingsCatOf(key: String): String = when (key) {
    "appearance", "editor_section", "voice_section" -> "interface"
    "providers", "usage_section" -> "models"
    "agent_section", "desert_section", "slash_section" -> "agent"
    "mcp_section", "pack_section", "gallery_section", "skills_section" -> "tools"
    "build_section", "sync_section" -> "project"
    "accounts", "billing_section" -> "account"
    else -> "help"
}

internal fun settingsCatTitle(cat: String): Int = when (cat) {
    "models" -> R.string.cat_models; "agent" -> R.string.cat_agent; "tools" -> R.string.cat_tools; "project" -> R.string.cat_project
    "interface" -> R.string.cat_interface; "account" -> R.string.cat_account; else -> R.string.cat_help
}

internal fun settingsCatDesc(cat: String): Int = when (cat) {
    "models" -> R.string.cat_models_d; "agent" -> R.string.cat_agent_d; "tools" -> R.string.cat_tools_d; "project" -> R.string.cat_project_d
    "interface" -> R.string.cat_interface_d; "account" -> R.string.cat_account_d; else -> R.string.cat_help_d
}

@Composable
internal fun Group(content: @Composable () -> Unit) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.lg)
    Box(Modifier.fillMaxWidth().clip(shape).background(c.surface1).border(1.dp, c.border, shape)) { content() }
}

/** v0.7 team mode: the reviewer model id (blank = the coder's model). */
@Composable
private fun ReviewModelEditor(ui: SettingsUi) {
    val c = Saqr.colors
    var text by remember(ui.reviewModel) { mutableStateOf(ui.reviewModel) }
    val savedText = stringResource(R.string.saved)
    var status by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
        LabeledIcon(Lucide.ShieldCheck, stringResource(R.string.review_model))
        Text(stringResource(R.string.review_model_desc), style = Saqr.type.caption, color = c.text3)
        ModelPickerField(text, { text = it; status = null }, ui.suggestedModels, onSave = { ui.onReviewModel(text); status = savedText })
        status?.let { Text(it, style = Saqr.type.caption, color = c.text2) }
    }
}

/** v0.8 team mode: the planner model id (blank = the coder's model). */
@Composable
private fun PlannerModelEditor(ui: SettingsUi) {
    val c = Saqr.colors
    var text by remember(ui.plannerModel) { mutableStateOf(ui.plannerModel) }
    val savedText = stringResource(R.string.saved)
    var status by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
        LabeledIcon(Lucide.ListTree, stringResource(R.string.planner_model))
        Text(stringResource(R.string.planner_model_desc), style = Saqr.type.caption, color = c.text3)
        ModelPickerField(text, { text = it; status = null }, ui.suggestedModels, onSave = { ui.onPlannerModel(text); status = savedText })
        status?.let { Text(it, style = Saqr.type.caption, color = c.text2) }
    }
}

/**
 * v0.9: free-text model field with a suggestions menu from the active provider, shared by the
 * reviewer and planner editors (per-provider pickers instead of typing ids blind).
 */
@Composable
private fun ModelPickerField(value: String, onChange: (String) -> Unit, suggestions: List<String>, onSave: () -> Unit) {
    val c = Saqr.colors
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            SaqrField(value, onChange, label = stringResource(R.string.model),
                placeholder = "claude-haiku-4-5 / gemini-2.5-flash", mono = true, trailing = {
                    Box(Modifier.minimumInteractiveComponentSize().size(32.dp).clip(CircleShape).clickable(role = Role.Button, enabled = suggestions.isNotEmpty()) { menu = true }, contentAlignment = Alignment.Center) {
                        SIcon(Lucide.ChevronDown, size = 16.dp)
                    }
                })
            DropdownMenu(menu, { menu = false }, Modifier.heightIn(max = 360.dp).background(c.surface2)) {
                suggestions.forEach { m -> DropdownMenuItem(text = { Text(m, style = Saqr.type.monoSmall, color = c.text1) }, onClick = { onChange(m); menu = false }) }
            }
        }
        Spacer(Modifier.width(Sp.sm))
        GhostButton(stringResource(R.string.save), onSave, icon = Lucide.Save)
    }
}

/** v0.11: stdio MCP servers — name + one-line command (+ optional K=V env lines). */
object McpStdioSettings {
    fun validate(
        name: String, command: String,
        existing: List<com.ctodrs.saqrcode.agent.McpStdio.StdioServer>,
    ): String? = when {
        name.isBlank() -> "The server needs a name."
        com.ctodrs.saqrcode.agent.McpStdio.validateCommand(command) != null -> "The command must be one non-empty shell line."
        existing.any { it.name.equals(name.trim(), ignoreCase = true) } -> "A server with this name already exists."
        else -> null
    }

    @Composable
    operator fun invoke(ui: SettingsUi) {
        val c = Saqr.colors
        var name by remember { mutableStateOf("") }
        var command by remember { mutableStateOf("") }
        var env by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
            LabeledIcon(Lucide.SquareTerminal, stringResource(R.string.mcp_stdio_add))
            Text(stringResource(R.string.mcp_stdio_desc), style = Saqr.type.caption, color = Saqr.colors.text3)
            SaqrField(value = name, onChange = { name = it }, label = stringResource(R.string.mcp_name))
            SaqrField(value = command, onChange = { command = it }, label = stringResource(R.string.mcp_stdio_command), mono = true)
            SaqrField(value = env, onChange = { env = it }, label = stringResource(R.string.mcp_stdio_env), mono = true, singleLine = false)
            err?.let { Text(it, style = Saqr.type.caption, color = Saqr.colors.danger) }
            PrimaryButton(stringResource(R.string.add), {
                err = ui.onMcpStdioAdd(name, command, env)
                if (err == null) { name = ""; command = ""; env = "" }
            })
        }
    }
}

object McpSettings {
    fun validate(name: String, url: String, existing: List<com.ctodrs.saqrcode.agent.Mcp.Server>): String? = when {
        name.isBlank() -> "The server needs a name."
        url.isBlank() -> "The server needs a URL."
        !url.trim().startsWith("https://") -> "The URL must start with https://"
        existing.any { it.name.equals(name.trim(), ignoreCase = true) } -> "A server with this name already exists."
        else -> null
    }

    @Composable
    operator fun invoke(ui: SettingsUi) {
        val c = Saqr.colors
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var auth by remember { mutableStateOf("") }
        var status by remember { mutableStateOf<String?>(null) }
        val addedText = stringResource(R.string.mcp_added)
        Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
            LabeledIcon(Lucide.Plug, stringResource(R.string.mcp_add))
            Text(stringResource(R.string.mcp_add_desc), style = Saqr.type.caption, color = c.text3)
            SaqrField(name, { name = it; status = null }, label = stringResource(R.string.mcp_name), placeholder = "context7")
            SaqrField(url, { url = it; status = null }, label = "URL", placeholder = "https://mcp.example.com/mcp", mono = true,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
            SaqrField(auth, { auth = it; status = null }, label = stringResource(R.string.mcp_auth),
                placeholder = "Bearer … (optional)", mono = true, visual = PasswordVisualTransformation(),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Password))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(stringResource(R.string.mcp_add_btn), {
                    val err = ui.onMcpAdd(name, url, auth)
                    if (err == null) {
                        name = ""; url = ""; auth = ""; status = addedText
                    } else status = err
                }, icon = Lucide.Plus)
            }
            status?.let { Text(it, style = Saqr.type.caption, color = c.text2) }
        }
    }
}

/** v0.8/v0.9: the arm64-aapt2 source — curated presets (Auto lookup / ReVanced) or a direct URL. */
@Composable
private fun Aapt2MirrorEditor(ui: SettingsUi) {
    val c = Saqr.colors
    val savedText = stringResource(R.string.saved)
    var status by remember { mutableStateOf<String?>(null) }
    val M = com.ctodrs.saqrcode.ondevice.Aapt2Mirrors
    val preset = remember(ui.aapt2Mirror) { M.resolve(ui.aapt2Mirror) }
    val options = listOf(M.AUTO.id, M.REVANCED.id, "custom")
    val selected = when (preset.id) {
        M.AUTO.id -> 0; M.REVANCED.id -> 1; else -> 2
    }
    var customUrl by remember(ui.aapt2Mirror) {
        mutableStateOf(if (preset.id == "custom") ui.aapt2Mirror else "")
    }
    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
        LabeledIcon(Lucide.CloudDownload, stringResource(R.string.aapt2_mirror))
        Text(stringResource(R.string.aapt2_mirror_desc), style = Saqr.type.caption, color = c.text3)
        Segmented(
            listOf(stringResource(R.string.aapt2_preset_auto), M.REVANCED.label, stringResource(R.string.aapt2_preset_custom)),
            selected,
            { i ->
                when (options[i]) {
                    M.AUTO.id -> ui.onAapt2Mirror("")
                    M.REVANCED.id -> ui.onAapt2Mirror(M.REVANCED.id)
                    else -> if (customUrl.isNotBlank()) ui.onAapt2Mirror(customUrl)
                }
                status = savedText
            },
        )
        if (selected == 2) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SaqrField(customUrl, { customUrl = it; status = null }, Modifier.weight(1f), label = "URL",
                    placeholder = "https://…/aapt2", mono = true,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
                Spacer(Modifier.width(Sp.sm))
                GhostButton(stringResource(R.string.save), { ui.onAapt2Mirror(customUrl); status = savedText }, icon = Lucide.Save)
            }
        }
        status?.let { Text(it, style = Saqr.type.caption, color = c.text2) }
    }
}

/**
 * v0.9 · مستعرض أدوات MCP · MCP tools browser: lists a server's tools and lets the user run
 * one manually with hand-edited JSON arguments. Purely a Settings-side helper — the agent's own
 * tool wiring is untouched.
 */
@Composable
private fun McpToolsBrowser(
    server: com.ctodrs.saqrcode.agent.Mcp.Server,
    auth: String?,
    disabledFor: (String) -> Boolean = { false },
    onToggleTool: (String, Boolean) -> Unit = { _, _ -> },
    oauthCfg: com.ctodrs.saqrcode.agent.McpOAuth.Config? = null,
    oauthTokens: com.ctodrs.saqrcode.agent.McpOAuth.Tokens? = null,
    onSaveOAuth: (com.ctodrs.saqrcode.agent.McpOAuth.Config) -> Unit = {},
    onRemoveOAuth: () -> Unit = {},
    onStartOAuth: (com.ctodrs.saqrcode.agent.McpOAuth.Pending) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val c = Saqr.colors
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tools by remember { mutableStateOf<List<com.ctodrs.saqrcode.agent.Mcp.Tool>>(emptyList()) }
    var openTool by remember { mutableStateOf<String?>(null) }
    var argsText by remember { mutableStateOf("{}") }
    var runResult by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }

    fun load() {
        loading = true; error = null; tools = emptyList()
        scope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val client = com.ctodrs.saqrcode.agent.McpClient(server.copy(authHeader = auth))
                client.listTools() to client.endpointError
            }
            val (list, err) = result
            if (list == null) error = err ?: "server unreachable"
            else tools = list
            loading = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(server.name) { load() }

    fun run(toolName: String) {
        running = true; runResult = null
        val parsed = runCatching { org.json.JSONObject(argsText) }.getOrElse { e ->
            running = false; runResult = "Error: invalid JSON — " + e.message; return
        }
        scope.launch {
            val out = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val client = com.ctodrs.saqrcode.agent.McpClient(server.copy(authHeader = auth))
                client.callTool(com.ctodrs.saqrcode.agent.Mcp.splitToolName(toolName)?.second ?: toolName, parsed)
            }
            runResult = out
            running = false
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface1,
        title = { Text(stringResource(R.string.mcp_tools_title, server.name), style = Saqr.type.title3) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                Text(server.url, style = Saqr.type.monoSmall, color = c.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // v0.14.1: OAuth (PKCE) sign-in for protected servers, right above the tool list.
                McpOAuthSection(
                    server = server,
                    cfg = oauthCfg,
                    tokens = oauthTokens,
                    onSave = onSaveOAuth,
                    onRemove = onRemoveOAuth,
                    onStart = onStartOAuth,
                )
                when {
                    loading -> Text("…", style = Saqr.type.caption, color = c.text3)
                    error != null -> Column {
                        Text(error!!, style = Saqr.type.caption, color = c.danger)
                        GhostButton(stringResource(R.string.mcp_tools_retry), { load() }, icon = Lucide.RefreshCw)
                    }
                    tools.isEmpty() -> Text(stringResource(R.string.mcp_tools_empty), style = Saqr.type.caption, color = c.text3)
                    else -> tools.forEach { t ->
                        val raw = com.ctodrs.saqrcode.agent.Mcp.splitToolName(t.name)?.second ?: t.name
                        val disabled = disabledFor(raw)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2)
                                .padding(horizontal = Sp.md, vertical = Sp.xs),
                        ) {
                            Row(Modifier.fillMaxWidth().clickable {
                                openTool = if (openTool == t.name) null else t.name
                                argsText = com.ctodrs.saqrcode.agent.Mcp.sampleArgs(t.inputSchema).toString()
                                runResult = null
                            }, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    t.name,
                                    style = Saqr.type.monoSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (disabled) c.text3 else c.accentText,
                                    modifier = Modifier.weight(1f),
                                )
                                // v0.10: hide/show this tool for the agent (allow/deny per server).
                                Box(Modifier.minimumInteractiveComponentSize().size(34.dp).clip(CircleShape).clickable(role = Role.Button) { onToggleTool(raw, !disabled) }, contentAlignment = Alignment.Center) {
                                    SIcon(if (disabled) Lucide.EyeOff else Lucide.Eye, size = 15.dp, tint = if (disabled) c.text3 else c.text2)
                                }
                                Spacer(Modifier.width(Sp.xs))
                                SIcon(Lucide.ChevronDown, size = 14.dp, tint = c.text3)
                            }
                            if (disabled) {
                                Text(stringResource(R.string.mcp_tool_hidden), style = Saqr.type.caption, color = c.text3)
                            }
                            Text(t.description, style = Saqr.type.caption, color = c.text2, maxLines = if (openTool == t.name) 6 else 2, overflow = TextOverflow.Ellipsis)
                            if (openTool == t.name) {
                                Text(com.ctodrs.saqrcode.agent.Mcp.describeSchema(t.inputSchema), style = Saqr.type.caption, color = c.text3)
                                SaqrField(argsText, { argsText = it; runResult = null }, label = stringResource(R.string.mcp_args),
                                    placeholder = "{}", mono = true, singleLine = false)
                                Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                                    GhostButton(stringResource(R.string.mcp_run), { run(t.name) }, icon = Lucide.Play, enabled = !running)
                                    if (running) Text("…", style = Saqr.type.caption, color = c.text3)
                                }
                                runResult?.let {
                                    Text(it, style = Saqr.type.monoSmall, color = if (it.startsWith("Error:")) c.danger else c.text2, maxLines = 10, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = c.accentText) }
        },
    )
}

/**
 * v0.14.1 · قسم OAuth داخل مستعرض الخادم · OAuth (PKCE) section of the MCP tools browser:
 * paste a client id (and optionally scopes / manual endpoints), tap sign in — Saqr discovers
 * the endpoints from the server's metadata, persists the config, saves the pending PKCE flow
 * and opens the system browser; the redirect lands on [com.ctodrs.saqrcode.OAuthCallbackActivity]
 * which exchanges the code and stores the tokens. A connected server shows a status line with
 * «sign in again» and «disconnect».
 */
@Composable
private fun McpOAuthSection(
    server: com.ctodrs.saqrcode.agent.Mcp.Server,
    cfg: com.ctodrs.saqrcode.agent.McpOAuth.Config?,
    tokens: com.ctodrs.saqrcode.agent.McpOAuth.Tokens?,
    onSave: (com.ctodrs.saqrcode.agent.McpOAuth.Config) -> Unit,
    onRemove: () -> Unit,
    onStart: (com.ctodrs.saqrcode.agent.McpOAuth.Pending) -> Unit,
) {
    val c = Saqr.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var clientId by remember(server.name) { mutableStateOf(cfg?.clientId ?: "") }
    var scopes by remember(server.name) { mutableStateOf(cfg?.scopes?.joinToString(" ") ?: "") }
    var authUrl by remember(server.name) { mutableStateOf(cfg?.authUrl ?: "") }
    var tokenUrl by remember(server.name) { mutableStateOf(cfg?.tokenUrl ?: "") }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val discoveringText = stringResource(R.string.mcp_oauth_discovering)
    val discoverFailText = stringResource(R.string.mcp_oauth_discover_fail)
    val openingText = stringResource(R.string.mcp_oauth_opening)
    val needClientText = stringResource(R.string.mcp_oauth_need_client)
    val oauth = com.ctodrs.saqrcode.agent.McpOAuth

    fun signIn() {
        val id = clientId.trim()
        if (id.isEmpty()) { status = needClientText; return }
        working = true; status = discoveringText
        scope.launch {
            // v0.14.1: endpoints come from the server's metadata; pasted overrides win.
            val discovered = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { com.ctodrs.saqrcode.agent.McpOAuthHttp.discoverEndpoints(server.url) }.getOrNull()
            }
            val au = authUrl.trim().ifEmpty { discovered?.authorizationEndpoint }
            val tu = tokenUrl.trim().ifEmpty { discovered?.tokenEndpoint }
            if (au == null || tu == null) {
                working = false
                status = discoverFailText
                return@launch
            }
            val sc = (scopes.trim().ifEmpty { discovered?.scopes?.joinToString(" ") }).orEmpty()
            val config = com.ctodrs.saqrcode.agent.McpOAuth.Config(id, sc.split(Regex("[\\s,]+")).filter { it.isNotBlank() }, au, tu, server.url)
            val state = oauth.newState()
            val verifier = oauth.codeVerifier()
            onStart(com.ctodrs.saqrcode.agent.McpOAuth.Pending(server.name, state, verifier, id, oauth.REDIRECT_URI, tu, server.url))
            onSave(config)
            val url = oauth.authorizeUrl(au, id, oauth.REDIRECT_URI, oauth.codeChallenge(verifier), state, config.scopes, server.url)
            status = openingText
            runCatching {
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            working = false
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2).padding(Sp.md),
        verticalArrangement = Arrangement.spacedBy(Sp.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(Lucide.KeyRound, size = 15.dp, tint = c.accentText)
            Spacer(Modifier.width(Sp.xs))
            Text(stringResource(R.string.mcp_oauth_title), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
        }
        Text(stringResource(R.string.mcp_oauth_hint), style = Saqr.type.caption, color = c.text3)
        tokens?.let {
            val expired = oauth.needsRefresh(it, System.currentTimeMillis())
            Text(
                stringResource(if (expired) R.string.mcp_oauth_status_expired else R.string.mcp_oauth_status_valid),
                style = Saqr.type.caption,
                color = if (expired) c.danger else c.success,
            )
        }
        SaqrField(clientId, { clientId = it; status = null }, label = stringResource(R.string.mcp_oauth_client_id), mono = true)
        SaqrField(scopes, { scopes = it; status = null }, label = stringResource(R.string.mcp_oauth_scopes), mono = true)
        SaqrField(authUrl, { authUrl = it; status = null }, label = stringResource(R.string.mcp_oauth_endpoint_auth),
            placeholder = "https://…/authorize", mono = true,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
        SaqrField(tokenUrl, { tokenUrl = it; status = null }, label = stringResource(R.string.mcp_oauth_endpoint_token),
            placeholder = "https://…/token", mono = true,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = Alignment.CenterVertically) {
            GhostButton(
                stringResource(if (cfg == null) R.string.mcp_oauth_connect else R.string.mcp_oauth_reconnect),
                { signIn() },
                icon = Lucide.KeyRound,
                enabled = !working,
            )
            if (cfg != null) {
                GhostButton(stringResource(R.string.mcp_oauth_disconnect), onRemove, icon = Lucide.Trash2)
            }
        }
        if (working) Text("…", style = Saqr.type.caption, color = c.text3)
        status?.let { Text(it, style = Saqr.type.caption, color = c.text2) }
    }
}

@Composable
internal fun LabeledIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    val c = Saqr.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SIcon(icon, size = 17.dp, tint = c.accentText)
        Spacer(Modifier.width(Sp.sm))
        Text(text, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
    }
}

@Composable
private fun ProviderItem(def: ProviderDef, cfg: ProviderConfig, active: Boolean, ui: SettingsUi, startOpen: Boolean) {
    val c = Saqr.colors
    var expanded by remember { mutableStateOf(startOpen) }
    var baseUrl by remember(def.id) { mutableStateOf(cfg.baseUrl) }
    var model by remember(def.id) { mutableStateOf(cfg.model) }
    var key by remember(def.id) { mutableStateOf("") }
    var models by remember(def.id) { mutableStateOf(def.suggestedModels) }
    var menu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.saved)
    val modelsFmt = stringResource(R.string.n_models)
    val keyRemovedText = stringResource(R.string.key_removed)
    val fetchCtx = androidx.compose.ui.platform.LocalContext.current

    Column {
        ListRow(
            title = def.displayName,
            subtitle = (if (cfg.hasKey) stringResource(R.string.key_set) else if (def.keyOptional) stringResource(R.string.key_optional) else stringResource(R.string.key_not_set)) +
                (if (cfg.model.isNotBlank()) " · ${cfg.model}" else ""),
            leading = { ProviderBadge(def.id, 36.dp) },
            onClick = { expanded = !expanded },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (active) Tag(stringResource(R.string.active), color = c.accentText, dot = true)
                    else if (cfg.hasKey) SIcon(Lucide.CircleCheck, size = 18.dp, tint = c.success)
                    Spacer(Modifier.width(Sp.xs))
                    SIcon(Lucide.ChevronDown, size = 18.dp, tint = c.text3)
                }
            },
        )
        AnimatedVisibility(expanded, enter = expandVertically(tween(220)) + fadeIn(), exit = shrinkVertically(tween(200)) + fadeOut()) {
            Column(Modifier.padding(start = Sp.lg, end = Sp.lg, bottom = Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                SaqrField(baseUrl, { baseUrl = it }, label = stringResource(R.string.base_url), mono = true,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
                if (def.id == "saqr-free") {
                    Text(stringResource(R.string.free_ai_privacy), style = Saqr.type.caption, color = c.text2)
                }
                SaqrField(key, { key = it }, label = stringResource(R.string.api_key),
                    placeholder = if (cfg.hasKey) "••••••••  " + stringResource(R.string.key_set) else def.keyUrl.removePrefix("https://"),
                    visual = PasswordVisualTransformation(), keyboard = KeyboardOptions(keyboardType = KeyboardType.Password), mono = true)
                Box {
                    SaqrField(model, { model = it }, label = stringResource(R.string.model), mono = true, trailing = {
                        Box(Modifier.minimumInteractiveComponentSize().size(32.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }, contentAlignment = Alignment.Center) { SIcon(Lucide.ChevronDown, size = 16.dp) }
                    })
                    DropdownMenu(menu, { menu = false }, Modifier.heightIn(max = 360.dp).background(c.surface2)) {
                        models.forEach { m -> DropdownMenuItem(text = { Text(m, style = Saqr.type.monoSmall, color = c.text1) }, onClick = { model = m; menu = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(stringResource(R.string.save), { ui.onSaveProvider(def.id, baseUrl, model, key.ifBlank { null }); key = ""; status = savedText }, icon = Lucide.Save)
                    GhostButton(stringResource(R.string.fetch_models), {
                        status = "…"
                        scope.launch {
                            status = ui.fetchModels(def, baseUrl, model, key.ifBlank { null })
                                .fold({ list: List<String> -> models = list.ifEmpty { def.suggestedModels }; modelsFmt.format(list.size.toString()) }, { e: Throwable ->
                                    // Say why in plain words (key rejected, offline, rate limit); never a blank line.
                                    val why: String? = errorExplanationRes(com.ctodrs.saqrcode.agent.ErrorExplainer.classify(e))?.let { resId -> fetchCtx.getString(resId) }
                                    why ?: errorLine(e)
                                })
                        }
                    }, icon = Lucide.RefreshCw)
                    if (!active) GhostButton(stringResource(R.string.use_this), { ui.onActivate(def.id) }, icon = Lucide.Check)
                    if (cfg.hasKey) GhostButton(stringResource(R.string.remove_key), { ui.onClearKey(def.id); key = ""; status = keyRemovedText }, icon = Lucide.Trash2, tint = c.danger)
                }
                status?.let { Text(it, style = Saqr.type.caption, color = c.text2, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}


@Composable
private fun SlashCommandsEditor(ui: SettingsUi) {
    val c = Saqr.colors
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
        Text(stringResource(R.string.slash_desc), style = Saqr.type.caption, color = c.text2)
        ui.customCommands.forEach { cmd ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2).padding(start = Sp.md, top = Sp.xs, bottom = Sp.xs),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("/" + cmd.name, style = Saqr.type.mono.copy(fontWeight = FontWeight.SemiBold, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),
                        color = c.accentText, maxLines = 1)
                    Text(cmd.template, style = Saqr.type.caption.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                        color = c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.minimumInteractiveComponentSize().size(44.dp).clip(CircleShape).clickable(role = Role.Button) { ui.onDeleteCommand(cmd.name) }, contentAlignment = Alignment.Center) {
                    SIcon(Lucide.Trash2, size = 17.dp, tint = c.danger, contentDescription = stringResource(R.string.delete))
                }
            }
        }
        SaqrField(name, { name = it; error = null }, label = stringResource(R.string.slash_name), placeholder = "deploy", mono = true)
        SaqrField(template, { template = it; error = null }, label = stringResource(R.string.slash_template),
            placeholder = "Build {file} and deploy it to staging. {args}", singleLine = false)
        error?.let { Text(stringResource(when (it) {
            "builtin" -> R.string.slash_err_builtin
            "duplicate" -> R.string.slash_err_duplicate
            "template" -> R.string.slash_err_template
            else -> R.string.slash_err_name
        }), style = Saqr.type.caption, color = c.danger) }
        GhostButton(stringResource(R.string.slash_add), {
            val err = ui.onAddCommand(name, template)
            if (err == null) { name = ""; template = "" } else error = err
        }, icon = Lucide.Plus)
    }
}

private fun fmtNum(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toBigDecimal().stripTrailingZeros().toPlainString()

/**
 * v0.13.2 Falcon Router: the published savings number — what the routed planner / review /
 * compaction calls cost this month versus running them on the coder's model. Hidden until
 * the first routed call lands in the ledger (and unreadable rows are ignored, never guessed).
 */
@Composable
private fun FalconSavingsRow() {
    val c = Saqr.colors
    val m = remember {
        runCatching {
            com.ctodrs.saqrcode.agent.SavingsLedger(java.io.File(SaqrApp.instance.filesDir, "usage/falcon.json")).month()
        }.getOrNull()
    } ?: return
    val (actual, baseline) = m
    if (baseline <= 0.0) return
    val pct = com.ctodrs.saqrcode.agent.FalconRouter.savingsPct(actual, baseline) ?: return
    val saved = baseline - actual
    ListRow(
        title = stringResource(R.string.falcon_savings),
        subtitle = stringResource(R.string.falcon_savings_desc,
            com.ctodrs.saqrcode.ui.chat.ltr(com.ctodrs.saqrcode.ui.chat.formatUsd(saved)),
            com.ctodrs.saqrcode.ui.chat.ltr(com.ctodrs.saqrcode.ui.chat.formatUsd(baseline))),
        icon = Lucide.TrendingDown,
        iconTint = c.success,
        trailing = { Tag(ltr(String.format(Locale.US, "%.0f%%", pct * 100)), color = c.success, dot = false) },
    )
}

@Composable
private fun BudgetEditor(ui: SettingsUi) {
    val c = Saqr.colors
    var text by remember(ui.monthlyBudget) { mutableStateOf(ui.monthlyBudget?.let { fmtNum(it) } ?: "") }
    val savedText = stringResource(R.string.saved)
    var status by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
        LabeledIcon(Lucide.Zap, stringResource(R.string.monthly_budget))
        Text(stringResource(R.string.monthly_budget_desc), style = Saqr.type.caption, color = c.text2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SaqrField(text, { text = it.filter { ch -> ch.isDigit() || ch == '.' }; status = null }, Modifier.weight(1f), placeholder = "10", mono = true,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Spacer(Modifier.width(Sp.sm))
            GhostButton(stringResource(R.string.save), { ui.onBudget(text.toDoubleOrNull()?.takeIf { it > 0 }); status = savedText }, icon = Lucide.Save)
        }
        status?.let { Text(it, style = Saqr.type.caption, color = c.text2) }

        Spacer(Modifier.height(Sp.md))
        var dayText by remember(ui.dailyBudget) { mutableStateOf(ui.dailyBudget?.let { fmtNum(it) } ?: "") }
        LabeledIcon(Lucide.Sun, stringResource(R.string.daily_budget))
        Text(stringResource(R.string.daily_budget_desc), style = Saqr.type.caption, color = c.text2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SaqrField(dayText, { dayText = it.filter { ch -> ch.isDigit() || ch == '.' }; status = null }, Modifier.weight(1f), placeholder = "2", mono = true,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Spacer(Modifier.width(Sp.sm))
            GhostButton(stringResource(R.string.save), { ui.onDailyBudget(dayText.toDoubleOrNull()?.takeIf { it > 0 }); status = savedText }, icon = Lucide.Save)
        }
    }
}

@Composable
private fun PricesEditor(ui: SettingsUi) {
    val c = Saqr.colors
    var editing by remember { mutableStateOf<ModelPrice?>(null) }
    Column(Modifier.padding(vertical = Sp.md)) {
        LabeledIcon(Lucide.Tag, stringResource(R.string.prices_title), Modifier.padding(horizontal = Sp.lg))
        Text(stringResource(R.string.prices_desc), style = Saqr.type.caption, color = c.text2, modifier = Modifier.padding(horizontal = Sp.lg, vertical = Sp.xs))
        ui.prices.forEach { p ->
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { editing = p }.padding(horizontal = Sp.lg),
                verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.model, style = Saqr.type.monoSmall, color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("$" + fmtNum(p.inputPerM) + " / $" + fmtNum(p.outputPerM), style = Saqr.type.monoSmall, color = c.text2, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(Sp.sm))
                SIcon(Lucide.Pencil, size = 15.dp, tint = c.text3)
            }
        }
        GhostButton(stringResource(R.string.add_price), { editing = ModelPrice("", 0.0, 0.0) }, Modifier.padding(horizontal = Sp.sm), icon = Lucide.Plus)
    }
    editing?.let { p ->
        var model by remember(p) { mutableStateOf(p.model) }
        var inp by remember(p) { mutableStateOf(fmtNum(p.inputPerM)) }
        var out by remember(p) { mutableStateOf(fmtNum(p.outputPerM)) }
        val num = { v: String -> v.filter { it.isDigit() || it == '.' } }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = c.surface1,
            title = { Text(stringResource(R.string.prices_title), style = Saqr.type.title3) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    SaqrField(model, { model = it }, label = stringResource(R.string.price_model), mono = true)
                    SaqrField(inp, { inp = num(it) }, label = stringResource(R.string.price_in), mono = true, keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    SaqrField(out, { out = num(it) }, label = stringResource(R.string.price_out), mono = true, keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (model.isNotBlank()) ui.onPrice(ModelPrice(model.trim(), inp.toDoubleOrNull() ?: 0.0, out.toDoubleOrNull() ?: 0.0))
                    editing = null
                }) { Text(stringResource(R.string.save), color = c.accentText) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
    }
}

/** Per-model "supports images" switch: Auto (name heuristic) / On / Off. */
@Composable
private fun VisionEditor(ui: SettingsUi) {
    val c = Saqr.colors
    val V = com.ctodrs.saqrcode.agent.Vision
    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
        LabeledIcon(Lucide.Image, stringResource(R.string.vision_title))
        Text(stringResource(R.string.vision_desc, com.ctodrs.saqrcode.ui.chat.ltr(ui.activeModel)), style = Saqr.type.caption, color = c.text3)
        val modes = listOf(V.AUTO, V.ON, V.OFF)
        val guess = stringResource(if (V.heuristic(ui.activeModel)) R.string.vision_yes else R.string.vision_no)
        Segmented(listOf(stringResource(R.string.vision_auto, guess), stringResource(R.string.vision_on), stringResource(R.string.vision_off)),
            modes.indexOf(ui.visionMode).coerceAtLeast(0), { ui.onVisionMode(modes[it]) })
    }
}

// ------------------------------------------------------------------ v0.12 Theme Studio

@Composable
private fun ThemeStudioCard(ui: SettingsUi) {
    val c = Saqr.colors
    val ctx = LocalContext.current
    val haptics = com.ctodrs.saqrcode.ui.theme.rememberSaqrHaptics()
    var mood by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var genPack by remember { mutableStateOf<com.ctodrs.saqrcode.ui.theme.ThemePack?>(null) }
    var importJson by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    fun apply(p: com.ctodrs.saqrcode.ui.theme.ThemePack) {
        ui.onApplyPack(p)
        haptics.perform(com.ctodrs.saqrcode.ui.theme.HapticKind.CONFIRM)
    }

    // v0.12.1: image → theme. The bitmap is down-sampled in-process; the palette itself
    // is extracted offline by ImagePalette (histogram + saturation pick) in milliseconds.
    val imgPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; err = null
        val pack = runCatching {
            val px = imagePixels(ctx, uri) ?: error("decode")
            com.ctodrs.saqrcode.ui.theme.ImagePalette.packFromImage(
                px, ctx.getString(R.string.studio_image_theme), "From image", ctx.getString(R.string.studio_image_mood),
            )
        }.getOrNull()
        busy = false
        genPack = pack
        if (pack != null) haptics.perform(com.ctodrs.saqrcode.ui.theme.HapticKind.POP)
        else err = R.string.studio_image_fail
    }

    Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
        LabeledIcon(Lucide.Sparkles, stringResource(R.string.studio_section))
        Text(stringResource(R.string.studio_packs), style = Saqr.type.caption, color = c.text2)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            com.ctodrs.saqrcode.ui.theme.ThemeStudio.builtins.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { p ->
                        PackChip(p, Modifier.weight(1f), active = ui.activePackId == p.id) {
                            apply(p); toast(ctx, R.string.studio_applied)
                        }
                    }
                }
            }
        }
        if (ui.savedPacks.isNotEmpty()) {
            Text(stringResource(R.string.studio_saved_packs), style = Saqr.type.caption, color = c.text2)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.savedPacks.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2)
                            .border(1.dp, if (ui.activePackId == p.id) c.accent.copy(alpha = 0.5f) else c.border, RoundedCornerShape(Radius.sm))
                            .padding(horizontal = Sp.md, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1)
                            Text(p.nameEn.ifBlank { p.mood }, style = Saqr.type.micro, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        ColorDot(p.colors.accent); Spacer(Modifier.width(4.dp)); ColorDot(p.colors.bg)
                        Spacer(Modifier.width(Sp.sm))
                        Box(Modifier.minimumInteractiveComponentSize().size(30.dp).clip(CircleShape).clickable(role = Role.Button) { ui.onCopyPack(p) }, contentAlignment = Alignment.Center) {
                            SIcon(Lucide.Copy, size = 14.dp, tint = c.text3, contentDescription = stringResource(R.string.studio_copy))
                        }
                        Box(Modifier.minimumInteractiveComponentSize().size(30.dp).clip(CircleShape).clickable(role = Role.Button) { ui.onDeletePack(p.id) }, contentAlignment = Alignment.Center) {
                            SIcon(Lucide.Trash2, size = 14.dp, tint = c.danger, contentDescription = stringResource(R.string.delete))
                        }
                        Box(Modifier.minimumInteractiveComponentSize().size(30.dp).clip(CircleShape).clickable(role = Role.Button) { apply(p); toast(ctx, R.string.studio_applied) }, contentAlignment = Alignment.Center) {
                            SIcon(Lucide.Check, size = 15.dp, tint = c.accentText, contentDescription = stringResource(R.string.studio_apply))
                        }
                    }
                }
            }
        }
        SaqrField(mood, { mood = it; err = null }, label = stringResource(R.string.studio_mood), placeholder = "غروب على الكورنيش")
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(
                stringResource(if (busy) R.string.studio_generating else R.string.studio_generate),
                {
                    busy = true; err = null
                    scope.launch {
                        val r = ui.generateTheme(mood)
                        busy = false
                        genPack = r.getOrNull()
                        if (genPack == null) err = R.string.studio_bad_json
                    }
                },
                icon = Lucide.Sparkles, enabled = !busy && mood.isNotBlank(),
            )
            GhostButton(stringResource(R.string.studio_from_image), { imgPicker.launch("image/*") }, icon = Lucide.Image, enabled = !busy)
        }
        genPack?.let { p ->
            PreviewRow(p,
                onApply = { apply(p); toast(ctx, R.string.studio_applied) },
                onSave = { ui.onSavePack(p); toast(ctx, R.string.studio_saved) },
                onCopy = { ui.onCopyPack(p) })
        }
        SaqrField(importJson, { importJson = it }, label = stringResource(R.string.studio_import), placeholder = "{\"name\":…}", mono = true)
        GhostButton(stringResource(R.string.studio_import_apply), {
            val p = com.ctodrs.saqrcode.ui.theme.ThemePack.fromJson(importJson)
            if (p != null) { apply(p); toast(ctx, R.string.studio_applied); err = null } else err = R.string.studio_bad_json
        }, icon = Lucide.Upload)
        err?.let { Text(stringResource(it), style = Saqr.type.caption, color = c.danger) }
    }
}

/** v0.14 skill packs: one-tap install of a .saqrpack (template + commands + rules + tools). */
@Composable
private fun SaqrPacksCard(ui: SettingsUi) {
    val c = Saqr.colors
    val ctx = LocalContext.current
    var importJson by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<Int?>(null) }
    // v0.18: the same pipeline as the gallery — refusal reasons or an update dialog when needed
    val installer = rememberSkillInstaller(ui.skills)

    Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
        Text(stringResource(R.string.pack_hint), style = Saqr.type.caption, color = c.text2)
        ui.packs.forEach { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2)
                    .border(1.dp, c.border, RoundedCornerShape(Radius.sm))
                    .padding(horizontal = Sp.md, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1)
                    Text(
                        p.description.ifBlank { p.nameAr },
                        style = Saqr.type.micro, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.pack_commands_n, p.commands.size) +
                            if (p.templateRef.isNotBlank()) "  ·  " + stringResource(R.string.pack_template_of, p.templateRef) else "",
                        style = Saqr.type.micro, color = c.text3, maxLines = 1,
                    )
                }
                Box(Modifier.minimumInteractiveComponentSize().size(30.dp).clip(CircleShape).clickable(role = Role.Button) { ui.onRemovePack(p.id); err = null }, contentAlignment = Alignment.Center) {
                    SIcon(Lucide.Trash2, size = 14.dp, tint = c.danger, contentDescription = stringResource(R.string.delete))
                }
            }
        }
        GhostButton(stringResource(R.string.pack_flask_button), {
            ui.onInstallFlask(); err = null
            toast(ctx, R.string.pack_flask_toast)
        }, icon = Lucide.Package)
        SaqrField(importJson, { importJson = it; err = null }, label = stringResource(R.string.pack_import), placeholder = "{\"saqrpack\":1,…}", mono = true)
        GhostButton(stringResource(R.string.pack_install), {
            installer.install(importJson, com.ctodrs.saqrcode.agent.skills.SkillSource.PASTE, onRefused = { err = R.string.pack_bad_json }) { ok ->
                if (ok) {
                    importJson = ""; err = null
                    toast(ctx, R.string.pack_installed)
                }
            }
        }, icon = Lucide.Upload)
        err?.let { Text(stringResource(it), style = Saqr.type.caption, color = c.danger) }
    }
}

/** v0.14.3 community gallery: browse the public index, search Arabic-aware, install in one tap. */
@Composable
private fun GallerySection(ui: SettingsUi) {
    val c = Saqr.colors
    val ctx = LocalContext.current
    val settings = SaqrApp.instance.settings
    val scope = rememberCoroutineScope()

    // The cache decodes keyed on the fetch stamp: fresh fetch → re-decode; a pack install
    // (which bumps the settings version but not the stamp) never re-decodes underneath.
    var index by remember(settings.galleryFetchedAt) {
        mutableStateOf(com.ctodrs.saqrcode.data.GalleryKit.parseIndex(settings.galleryCache))
    }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var fromCache by remember { mutableStateOf(settings.galleryFetchedAt > 0L) }
    var errRes by remember { mutableStateOf<Int?>(null) }

    // First visit with nothing cached: fetch once automatically — browsing is one tap.
    LaunchedEffect(Unit) {
        if (settings.galleryFetchedAt == 0L && settings.galleryCache.isEmpty()) {
            loading = true
            val (body, at, code) = fetchIndex()
            loading = false
            val parsed = applyFetch(body, at, code)
            if (parsed != null) {
                index = parsed; fromCache = false
                settings.galleryCache = body; settings.galleryFetchedAt = at
            } else errRes = fetchErrorRes(code)
        }
    }

    fun refresh() {
        loading = true; errRes = null
        scope.launch {
            val (body, at, code) = fetchIndex()
            loading = false
            val parsed = applyFetch(body, at, code)
            if (parsed != null) {
                index = parsed; fromCache = false
                settings.galleryCache = body; settings.galleryFetchedAt = at
            } else errRes = fetchErrorRes(code)
        }
    }

    val shown = remember(index, query) {
        com.ctodrs.saqrcode.data.GalleryKit.search(index?.entries ?: emptyList(), query)
    }
    val installed = com.ctodrs.saqrcode.data.GalleryKit.installedIds(ui.packs, ui.skills.installed)
    val installer = rememberSkillInstaller(ui.skills)

    Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
        Text(stringResource(R.string.gallery_desc), style = Saqr.type.caption, color = c.text2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
            GhostButton(
                if (loading) stringResource(R.string.gallery_loading) else stringResource(R.string.gallery_refresh),
                { refresh() }, icon = Lucide.RefreshCw, enabled = !loading,
            )
            Text(
                stringResource(R.string.gallery_count, shown.size),
                style = Saqr.type.micro, color = c.text3,
            )
        }
        SaqrField(query, { query = it }, placeholder = stringResource(R.string.gallery_search))
        shown.forEach { e ->
            val isInstalled = e.id in installed
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2)
                    .border(1.dp, if (isInstalled) c.accent.copy(alpha = 0.45f) else c.border, RoundedCornerShape(Radius.sm))
                    .padding(horizontal = Sp.md, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.nameAr.ifBlank { e.name },
                            style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1,
                        )
                        Text(
                            if (e.nameAr.isNotBlank()) e.name else e.description,
                            style = Saqr.type.micro, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val updatable = isInstalled && com.ctodrs.saqrcode.data.GalleryKit.updateAvailable(e, ui.packs, ui.skills.installed)
                    if (updatable) Tag(stringResource(R.string.skills_update_available), color = c.accentText, dot = true)
                    else if (isInstalled) Tag(stringResource(R.string.gallery_installed), color = c.success, dot = true)
                }
                // v0.18 optional entry fields: version, skills, rating (display only), permissions, checksum
                val meta = buildList {
                    if (e.version.isNotBlank()) add("v" + e.version)
                    if (e.skillCount > 0) add(stringResource(R.string.skills_gallery_skills, e.skillCount))
                    e.rating?.let { add(stringResource(R.string.skills_gallery_rating, String.format(java.util.Locale.ROOT, "%.1f", it), e.ratingCount)) }
                }
                if (meta.isNotEmpty()) Text(meta.joinToString("  ·  "), style = Saqr.type.micro, color = c.text3, maxLines = 1)
                if (e.permissions.isNotEmpty()) {
                    Text(stringResource(R.string.skills_gallery_perms, e.permissions.joinToString(", ")),
                        style = Saqr.type.micro, color = c.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                when (e.integrity) {
                    com.ctodrs.saqrcode.data.GalleryEntry.Integrity.OK -> Tag(stringResource(R.string.skills_gallery_verified), color = c.success, dot = true)
                    com.ctodrs.saqrcode.data.GalleryEntry.Integrity.MISMATCH -> Tag(stringResource(R.string.skills_gallery_mismatch), color = c.danger, dot = true)
                    com.ctodrs.saqrcode.data.GalleryEntry.Integrity.NONE -> Unit
                }
                if (e.description.isNotBlank() && e.nameAr.isNotBlank()) {
                    Text(e.description, style = Saqr.type.micro, color = c.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    buildString {
                        if (e.author.isNotBlank()) append(stringResource(R.string.gallery_by, e.author))
                        if (e.tags.isNotEmpty()) {
                            if (isNotEmpty()) append("  ·  ")
                            append(e.tags.joinToString(" · "))
                        }
                    },
                    style = Saqr.type.micro, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    val updatable = isInstalled && com.ctodrs.saqrcode.data.GalleryKit.updateAvailable(e, ui.packs, ui.skills.installed)
                    GhostButton(
                        stringResource(when {
                            updatable -> R.string.skills_update
                            isInstalled -> R.string.gallery_reinstall
                            else -> R.string.gallery_install
                        }),
                        {
                            // v0.18: the gallery's SHA-256 (when listed) must match, and updates show their changelog first
                            installer.install(e.packJson, com.ctodrs.saqrcode.agent.skills.SkillSource.GALLERY, e.sha256,
                                onRefused = { errRes = R.string.gallery_install_failed }) { ok ->
                                if (ok) {
                                    errRes = null
                                    val skillsOnly = e.pack?.templateRef.isNullOrBlank() && e.pack?.files.isNullOrEmpty()
                                    toast(ctx, if (skillsOnly) R.string.skills_installed_toast else R.string.gallery_installed_toast)
                                }
                            }
                        },
                        icon = if (updatable) Lucide.RefreshCw else Lucide.Package,
                    )
                    if (e.changelogUrl.isNotBlank()) GhostButton(stringResource(R.string.skills_gallery_changelog), {
                        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(e.changelogUrl))) }
                    }, icon = Lucide.ExternalLink)
                }
            }
        }
        if (index != null && shown.isEmpty()) {
            Text(stringResource(R.string.gallery_empty), style = Saqr.type.caption, color = c.text3)
        }
        if (index == null && !loading && errRes == null) {
            Text(stringResource(R.string.gallery_empty_all), style = Saqr.type.caption, color = c.text3)
        }
        if (settings.galleryFetchedAt > 0L) {
            Text(
                stringResource(
                    R.string.gallery_updated,
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                        .format(java.util.Date(settings.galleryFetchedAt)),
                ) + if (fromCache) "  ·  " + stringResource(R.string.gallery_cached) else "",
                style = Saqr.type.micro, color = c.text3,
            )
        }
        errRes?.let { Text(stringResource(it), style = Saqr.type.caption, color = c.danger, modifier = Modifier.polite()) }
        GhostButton(stringResource(R.string.gallery_contribute), {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.ctodrs.saqrcode.data.GalleryKit.REPO_URL))
                )
            }
        }, icon = Lucide.ExternalLink)
    }
}

/** One anonymous GET of the raw index: (body, wall-clock on success, http code on failure). */
private suspend fun fetchIndex(): Triple<String, Long, Int> =
    withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val body = com.ctodrs.saqrcode.data.GalleryClient().fetch()
            Triple(body, System.currentTimeMillis(), 200)
        }.getOrElse { e ->
            Triple("", 0L, if (e is com.ctodrs.saqrcode.git.GhException) e.code else -1)
        }
    }

/** Applies a fetch: null when the body could not become a usable index (caller shows the error). */
private fun applyFetch(
    body: String,
    at: Long,
    code: Int,
): com.ctodrs.saqrcode.data.GalleryIndex? {
    if (code != 200) return null
    return com.ctodrs.saqrcode.data.GalleryKit.parseIndex(body)
}

/** A readable error for the failed fetch: 404 names the missing index, everything else is the network. */
private fun fetchErrorRes(code: Int): Int = when (code) {
    404 -> R.string.gallery_no_index
    else -> R.string.gallery_fetch_fail
}

/** v0.14.4 repository sync: the encrypted snapshot travels through the user's own private repo — or a file. */
@Composable
private fun SyncSection(ui: SettingsUi) {
    val c = Saqr.colors
    val ctx = LocalContext.current
    val settings = SaqrApp.instance.settings
    val scope = rememberCoroutineScope()

    var pass by remember { mutableStateOf("") }
    var repoText by remember { mutableStateOf(settings.syncRepo) }
    var busyKind by remember { mutableStateOf<String?>(null) }
    var errRes by remember { mutableStateOf<Int?>(null) }
    var pushedRepo by remember { mutableStateOf<String?>(null) }
    var repoMissing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<com.ctodrs.saqrcode.data.SyncPayload?>(null) }

    // The import-from-file picker lands in the same decrypt path as a pull.
    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (com.ctodrs.saqrcode.data.SyncKit.passphraseProblem(pass) != null) {
            errRes = R.string.sync_pass_short; return@rememberLauncherForActivityResult
        }
        busyKind = "import"
        scope.launch {
            val text = withContext(kotlinx.coroutines.Dispatchers.IO) { syncReadText(ctx, uri) }
            busyKind = null
            val (payload, err) = syncOpenContainer(text, pass)
            if (payload != null) { errRes = null; pending = payload } else errRes = err
        }
    }

    var chatMsg by remember { mutableStateOf<String?>(null) }
    val chatsRoot = remember { java.io.File(ctx.filesDir, "chats") }
    val chatSave = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val pw = pass
        scope.launch {
            val ok = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val text = com.ctodrs.saqrcode.data.ChatBackup.seal(chatsRoot, pw, System.currentTimeMillis())
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
                }.getOrDefault(false)
            }
            chatMsg = ctx.getString(if (ok) R.string.chat_backup_done else R.string.chat_backup_fail)
        }
    }
    val chatOpen = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val pw = pass
        scope.launch {
            val msg = withContext(kotlinx.coroutines.Dispatchers.IO) {
                val text = syncReadText(ctx, uri)
                try {
                    val r = com.ctodrs.saqrcode.data.ChatBackup.restore(chatsRoot, text.orEmpty(), pw)
                    ctx.getString(R.string.chat_restore_done, r.sessions, r.skipped)
                } catch (e: com.ctodrs.saqrcode.data.SyncException) {
                    ctx.getString(when (e.reason) { "short" -> R.string.sync_pass_short; "passphrase" -> R.string.sync_bad_pass; else -> R.string.sync_bad_container })
                }
            }
            chatMsg = msg
        }
    }

    fun tokenAndSlug(): Pair<String, Pair<String, String>>? {
        val tok = settings.githubToken
        if (tok == null) { errRes = R.string.sync_needs_account; return null }
        val owner = settings.accounts.github?.login.orEmpty()
        val slug = com.ctodrs.saqrcode.data.SyncKit.repoSlug(repoText, owner)
        if (slug == null || slug.second.isBlank()) { errRes = R.string.sync_bad_repo; return null }
        return tok to slug
    }

    fun failOf(e: Exception) {
        when (e) {
            is com.ctodrs.saqrcode.data.SyncException -> errRes = when (e.reason) {
                "short" -> R.string.sync_pass_short
                "passphrase" -> R.string.sync_bad_pass
                else -> R.string.sync_bad_container
            }
            is com.ctodrs.saqrcode.git.GhException -> when (e.code) {
                404 -> { repoMissing = true; errRes = R.string.sync_missing_repo }
                403 -> errRes = R.string.sync_no_scope
                else -> errRes = R.string.sync_net_fail
            }
            else -> errRes = R.string.sync_net_fail
        }
    }

    fun push() {
        errRes = null
        if (com.ctodrs.saqrcode.data.SyncKit.passphraseProblem(pass) != null) { errRes = R.string.sync_pass_short; return }
        val (tok, slug) = tokenAndSlug() ?: return
        busyKind = "push"
        scope.launch {
            val res = runCatching {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val client = com.ctodrs.saqrcode.data.SyncClient(tok)
                    val payload = com.ctodrs.saqrcode.data.SyncKit.snapshot(
                        settings, android.os.Build.MODEL, BuildConfig.VERSION_NAME, System.currentTimeMillis())
                    val container = com.ctodrs.saqrcode.data.SyncKit.seal(payload, pass)
                    val sha = client.headSha(slug.first, slug.second)
                    client.put(slug.first, slug.second, container, "sync: encrypted Saqr snapshot", sha)
                }
            }
            busyKind = null
            res.fold(onSuccess = {
                pushedRepo = slug.second; repoMissing = false
                settings.syncRepo = repoText.trim(); settings.syncAt = System.currentTimeMillis()
                android.widget.Toast.makeText(ctx, ctx.getString(R.string.sync_pushed, slug.second), android.widget.Toast.LENGTH_SHORT).show()
            }, onFailure = { failOf(it as Exception) })
        }
    }

    fun pull() {
        errRes = null
        if (com.ctodrs.saqrcode.data.SyncKit.passphraseProblem(pass) != null) { errRes = R.string.sync_pass_short; return }
        val (tok, slug) = tokenAndSlug() ?: return
        busyKind = "pull"
        scope.launch {
            val res = runCatching {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.ctodrs.saqrcode.data.SyncClient(tok).get(slug.first, slug.second)
                }
            }
            busyKind = null
            res.fold(onSuccess = { body ->
                repoMissing = false
                try {
                    val p = com.ctodrs.saqrcode.data.SyncKit.open(body, pass)
                    if (p != null) { errRes = null; pending = p } else errRes = R.string.sync_bad_container
                } catch (e: com.ctodrs.saqrcode.data.SyncException) {
                    errRes = when (e.reason) { "passphrase" -> R.string.sync_bad_pass; else -> R.string.sync_bad_container }
                }
            }, onFailure = { failOf(it as Exception) })
        }
    }

    fun createAndPush() {
        val (tok, slug) = tokenAndSlug() ?: return
        busyKind = "create"
        scope.launch {
            val res = runCatching {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.ctodrs.saqrcode.data.SyncClient(tok).createPrivateRepo(slug.second)
                }
            }
            busyKind = null
            res.fold(onSuccess = {
                android.widget.Toast.makeText(ctx, ctx.getString(R.string.sync_created, slug.second), android.widget.Toast.LENGTH_SHORT).show()
                push()
            }, onFailure = { e ->
                if (e is com.ctodrs.saqrcode.git.GhException && e.code == 422) {
                    // The name is taken — if it's the user's own repo, a plain push will do.
                    errRes = R.string.sync_repo_exists
                } else failOf(e as Exception)
            })
        }
    }

    fun share() {
        errRes = null
        if (com.ctodrs.saqrcode.data.SyncKit.passphraseProblem(pass) != null) { errRes = R.string.sync_pass_short; return }
        val payload = com.ctodrs.saqrcode.data.SyncKit.snapshot(
            settings, android.os.Build.MODEL, BuildConfig.VERSION_NAME, System.currentTimeMillis())
        val container = com.ctodrs.saqrcode.data.SyncKit.seal(payload, pass)
        runCatching {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "saqr-sync.saqrsync")
                putExtra(android.content.Intent.EXTRA_TEXT, container)
            }
            ctx.startActivity(android.content.Intent.createChooser(send, null))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
        Text(stringResource(R.string.sync_desc), style = Saqr.type.caption, color = c.text2)
        Text(stringResource(R.string.sync_whats_in), style = Saqr.type.micro, color = c.text3)
        SaqrField(
            pass, { pass = it }, label = stringResource(R.string.sync_passphrase),
            visual = PasswordVisualTransformation(), supporting = stringResource(R.string.sync_pass_hint),
        )
        SaqrField(
            repoText, { repoText = it }, label = stringResource(R.string.sync_repo),
            supporting = stringResource(R.string.sync_repo_hint),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
            GhostButton(
                if (busyKind == "push" || busyKind == "create") stringResource(R.string.sync_tag_pushing) else stringResource(R.string.sync_push),
                { push() }, icon = Lucide.CloudUpload, enabled = busyKind == null,
            )
            GhostButton(
                if (busyKind == "pull" || busyKind == "import") stringResource(R.string.sync_tag_pulling) else stringResource(R.string.sync_pull),
                { pull() }, icon = Lucide.ArrowDownToLine, enabled = busyKind == null,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
            GhostButton(stringResource(R.string.sync_share), { share() }, icon = Lucide.ArrowUpFromLine, enabled = busyKind == null)
            GhostButton(stringResource(R.string.sync_import), { filePicker.launch(arrayOf("*/*")) }, icon = Lucide.HardDrive, enabled = busyKind == null)
        }
        Text(stringResource(R.string.chat_backup_desc), style = Saqr.type.micro, color = c.text3)
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
            GhostButton(stringResource(R.string.chat_backup), {
                if (com.ctodrs.saqrcode.data.SyncKit.passphraseProblem(pass) != null) errRes = R.string.sync_pass_short
                else { errRes = null; chatSave.launch(com.ctodrs.saqrcode.data.ChatBackup.FILE_NAME) }
            }, icon = Lucide.ArrowUpFromLine, enabled = busyKind == null)
            GhostButton(stringResource(R.string.chat_restore), {
                if (com.ctodrs.saqrcode.data.SyncKit.passphraseProblem(pass) != null) errRes = R.string.sync_pass_short
                else { errRes = null; chatOpen.launch(arrayOf("*/*")) }
            }, icon = Lucide.HardDrive, enabled = busyKind == null)
        }
        chatMsg?.let { Text(it, style = Saqr.type.micro, color = c.text2, modifier = Modifier.polite()) }
        if (settings.accounts.github == null) {
            Text(stringResource(R.string.sync_needs_account), style = Saqr.type.micro, color = c.text3)
        }
        if (settings.syncAt > 0L) {
            Text(stringResource(R.string.sync_last, syncFmt(settings.syncAt)), style = Saqr.type.micro, color = c.text3)
        }
        pushedRepo?.let {
            Text(stringResource(R.string.sync_pushed, it), style = Saqr.type.micro, color = c.success, modifier = Modifier.polite())
        }

        pending?.let { p ->
            val saved = com.ctodrs.saqrcode.ui.theme.ThemeStudio.decodePacks(p.themes["saved"].orEmpty()).size
            val cmds = com.ctodrs.saqrcode.agent.SlashCommands.decode(p.commandsJson).size
            val packs = com.ctodrs.saqrcode.data.SaqrPackCodec.decodePacks(p.packsJson).size
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2)
                    .border(1.dp, c.accent.copy(alpha = 0.45f), RoundedCornerShape(Radius.sm)).padding(Sp.md),
                verticalArrangement = Arrangement.spacedBy(Sp.sm),
            ) {
                Text(
                    stringResource(R.string.sync_snapshot_from, p.device.ifBlank { "—" }, syncFmt(p.createdAt)),
                    style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1,
                )
                Text(stringResource(R.string.sync_counts, p.settings.size, saved, cmds, packs), style = Saqr.type.micro, color = c.text2)
                Text(stringResource(R.string.sync_apply_warn), style = Saqr.type.micro, color = c.warning)
                PrimaryButton(stringResource(R.string.sync_apply), {
                    val a = com.ctodrs.saqrcode.data.SyncKit.apply(p, settings)
                    settings.syncAt = System.currentTimeMillis()
                    pending = null
                    android.widget.Toast.makeText(
                        ctx,
                        ctx.getString(R.string.sync_applied, a.settings, a.themes, a.commands, a.packs) +
                            (if (a.hasSkillNews) "\n" + ctx.getString(R.string.sync_applied_skills, a.skills, a.skillsRefused) else ""),
                        if (a.hasSkillNews) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }, icon = Lucide.Check)
            }
        }
        if (repoMissing) {
            GhostButton(stringResource(R.string.sync_create_repo), { createAndPush() }, icon = Lucide.ExternalLink, enabled = busyKind == null)
        }
        errRes?.let { Text(stringResource(it), style = Saqr.type.caption, color = c.danger, modifier = Modifier.polite()) }
    }
}

private fun syncFmt(ms: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(ms))

/** One text read of a picked document, or null (the caller refuses honestly). */
private fun syncReadText(ctx: android.content.Context, uri: android.net.Uri): String? = runCatching {
    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
}.getOrNull()

/** File-import path = pull path: decrypt with the same passphrase, parse strictly. */
private fun syncOpenContainer(text: String?, pass: String): Pair<com.ctodrs.saqrcode.data.SyncPayload?, Int> {
    if (text.isNullOrBlank()) return null to R.string.sync_bad_container
    return try {
        val p = com.ctodrs.saqrcode.data.SyncKit.open(text, pass)
        if (p == null) null to R.string.sync_bad_container else p to 0
    } catch (e: com.ctodrs.saqrcode.data.SyncException) {
        null to when (e.reason) {
            "short" -> R.string.sync_pass_short
            "passphrase" -> R.string.sync_bad_pass
            else -> R.string.sync_bad_container
        }
    }
}

@Composable
private fun PackChip(p: com.ctodrs.saqrcode.ui.theme.ThemePack, modifier: Modifier, active: Boolean, onClick: () -> Unit) {
    val c = Saqr.colors
    Column(
        modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(Radius.sm))
            .background(if (active) c.accentSoft else c.surface2)
            .border(1.dp, if (active) c.accent.copy(alpha = 0.55f) else c.border, RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick).padding(horizontal = Sp.md, vertical = 6.dp),
    ) {
        Text(p.name, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.nameEn, style = Saqr.type.micro, color = c.text3, maxLines = 1, modifier = Modifier.weight(1f))
            ColorDot(p.colors.accent); Spacer(Modifier.width(4.dp)); ColorDot(p.colors.bg)
        }
    }
}

@Composable
private fun ColorDot(argb: Int) {
    val c = Saqr.colors
    Box(Modifier.size(12.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(argb)).border(1.dp, c.border, CircleShape))
}

@Composable
private fun PreviewRow(p: com.ctodrs.saqrcode.ui.theme.ThemePack, onApply: () -> Unit, onSave: () -> Unit, onCopy: () -> Unit) {
    val c = Saqr.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2)
            .border(1.dp, c.accent.copy(alpha = 0.45f), RoundedCornerShape(Radius.sm)).padding(Sp.md),
        verticalArrangement = Arrangement.spacedBy(Sp.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1)
                Text(p.nameEn.ifBlank { p.mood }, style = Saqr.type.micro, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ColorDot(p.colors.bg); Spacer(Modifier.width(4.dp)); ColorDot(p.colors.surface1); Spacer(Modifier.width(4.dp)); ColorDot(p.colors.accent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
            PrimaryButton(stringResource(R.string.studio_apply), onApply, icon = Lucide.Check)
            GhostButton(stringResource(R.string.studio_save), onSave, icon = Lucide.Save)
            GhostButton(stringResource(R.string.studio_copy), onCopy, icon = Lucide.Copy)
        }
    }
}

private fun toast(ctx: android.content.Context, res: Int) =
    android.widget.Toast.makeText(ctx, res, android.widget.Toast.LENGTH_SHORT).show()

/** v0.12.1: decodes a picked image into a small pixel array (≤128px on the long side). */
private fun imagePixels(ctx: android.content.Context, uri: android.net.Uri): IntArray? = runCatching {
    val src = android.graphics.BitmapFactory.decodeStream(ctx.contentResolver.openInputStream(uri)) ?: error("decode")
    val maxSide = 128
    val scale = maxOf(1, maxOf(src.width, src.height) / maxSide)
    val bmp = if (scale > 1) android.graphics.Bitmap.createScaledBitmap(src, src.width / scale, src.height / scale, true) else src
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    if (bmp !== src) bmp.recycle()
    if (src != bmp) src.recycle()
    px
}.getOrNull()

/** Section id to scroll target, filled as the section labels compose. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal class SectionJump { val reqs = mutableMapOf<String, androidx.compose.foundation.relocation.BringIntoViewRequester>() }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun JumpLabel(jump: SectionJump, key: String, text: String, modifier: Modifier = Modifier) {
    val r = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    jump.reqs[key] = r
    SectionLabel(text, modifier.bringIntoViewRequester(r))
}

/** One entry per section: its label plus the row titles inside it, in the current language. */
@Composable
private fun settingsSectionIndex(): List<SettingsSearch.Entry> {
    fun e(key: String, title: Int, vararg rows: Int) = Triple(key, title, rows.toList())
    val defs = listOf(
        e("appearance", R.string.appearance, R.string.theme, R.string.language),
        e("editor_section", R.string.editor_section, R.string.editor_font, R.string.editor_wrap),
        e("providers", R.string.providers, R.string.keys_secure_note),
        e("agent_section", R.string.agent_section, R.string.approval_mode, R.string.team_mode, R.string.auto_compact, R.string.review_auto_tests, R.string.falcon_router),
        e("mcp_section", R.string.mcp_section),
        e("build_section", R.string.build_section, R.string.keep_last_apk, R.string.keep_background, R.string.battery_title),
        e("voice_section", R.string.voice_section, R.string.hands_free, R.string.voice_auto_send, R.string.voice_language),
        e("desert_section", R.string.desert_section, R.string.desert_title),
        e("slash_section", R.string.slash_section),
        e("pack_section", R.string.pack_section),
        e("gallery_section", R.string.gallery_section),
        e("skills_section", R.string.skills_section),
        e("sync_section", R.string.sync_section),
        e("usage_section", R.string.usage_section),
        e("accounts", R.string.accounts, R.string.github),
        e("billing_section", R.string.billing_section),
        e("coach_title", R.string.coach_title, R.string.coach_row_title),
        e("guide_section", R.string.guide_section, *GUIDE_STRINGS.flatMap { listOf(it.first, it.second) }.toIntArray()),
        e("about", R.string.about, R.string.crash_title),
        e("about_developer", R.string.about_developer),
    )
    return defs.map { (k, t, rows) -> SettingsSearch.Entry(k, stringResource(t), rows.map { stringResource(it) }) }
}


/** Wave E: TalkBack announces this text when it appears or changes (status and error messages). */
internal fun Modifier.polite(): Modifier = this.semantics {
    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
}


/** v0.1.46: the in-app user guide, one (title, body) pair per topic. Also feeds Settings search. */
private val GUIDE_STRINGS = listOf(
    R.string.guide_1_title to R.string.guide_1_body,
    R.string.guide_2_title to R.string.guide_2_body,
    R.string.guide_3_title to R.string.guide_3_body,
    R.string.guide_4_title to R.string.guide_4_body,
    R.string.guide_5_title to R.string.guide_5_body,
    R.string.guide_6_title to R.string.guide_6_body,
)

@Composable
private fun GuideList() {
    val c = Saqr.colors
    Column {
        GUIDE_STRINGS.forEachIndexed { i, (title, body) ->
            var open by remember { mutableStateOf(false) }
            val t = stringResource(title)
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { open = !open }
                        .padding(horizontal = Sp.lg, vertical = Sp.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t, style = Saqr.type.body.copy(fontWeight = FontWeight.Medium), color = c.text1, modifier = Modifier.weight(1f))
                    SIcon(Lucide.ChevronDown, size = 18.dp, tint = c.text3)
                }
                if (open) Text(stringResource(body), style = Saqr.type.bodySmall, color = c.text2,
                    modifier = Modifier.padding(start = Sp.lg, end = Sp.lg, bottom = Sp.md))
                if (i < GUIDE_STRINGS.lastIndex) RowDivider()
            }
        }
    }
}
