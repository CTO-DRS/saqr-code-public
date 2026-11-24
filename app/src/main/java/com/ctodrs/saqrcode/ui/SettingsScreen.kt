package com.ctodrs.saqrcode.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.ctodrs.saqrcode.agent.LlmClient
import com.ctodrs.saqrcode.agent.ModelPrice
import com.ctodrs.saqrcode.agent.PriceTable
import com.ctodrs.saqrcode.agent.SlashCommand
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
    val onAccounts: () -> Unit = {},
    val customCommands: List<SlashCommand> = emptyList(),
    /** Returns an error key from [SlashCommands.validate], or null when added. */
    val onAddCommand: (name: String, template: String) -> String? = { _, _ -> null },
    val onDeleteCommand: (String) -> Unit = {},
    val monthlyBudget: Double? = null,
    val onBudget: (Double?) -> Unit = {},
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
    /** v0.8 MCP servers (add returns an error message, or null on success). */
    val mcpServers: List<com.ctodrs.saqrcode.agent.Mcp.Server> = emptyList(),
    val onMcpAdd: (String, String, String) -> String? = { _, _, _ -> null },
    val onMcpRemove: (String) -> Unit = {},
    /** v0.9: the decrypted auth header of an MCP server (for the tools browser). */
    val mcpAuthFor: (String) -> String? = { null },
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
)

@Composable
fun SettingsScreen(onBack: () -> Unit, onAccounts: () -> Unit = {}, onPaywall: () -> Unit = {}) {
    val settings = SaqrApp.instance.settings
    val ctx = LocalContext.current
    val version by settings.version.collectAsState()
    version
    var showBattery by remember { mutableStateOf(false) }
    var teamGate by remember { mutableStateOf(false) }
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
        prices = settings.prices(),
        onPrice = { settings.setPrice(it) },
        activeModel = settings.config(settings.activeProvider).model,
        visionMode = settings.visionMode(settings.config(settings.activeProvider).model),
        onVisionMode = { settings.setVisionMode(settings.config(settings.activeProvider).model, it) },
        voiceAutoSend = settings.voiceAutoSend,
        onVoiceAutoSend = { settings.voiceAutoSend = it },
        voiceLang = settings.voiceLang,
        onVoiceLang = { settings.voiceLang = it },
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
        },
        mcpAuthFor = { name -> settings.mcpAuth(name) },
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
    )
    if (showBattery) BatteryDialog(onDismiss = { showBattery = false })
    com.ctodrs.saqrcode.billing.PaywallDialog(
        show = teamGate, onDismiss = { teamGate = false }, onOpenPaywall = onPaywall,
    )
    SettingsContent(ui, onBack)
}

@Composable
fun SettingsContent(ui: SettingsUi, onBack: () -> Unit, initiallyExpanded: String? = null) {
    val c = Saqr.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Sp.md, vertical = Sp.md), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Lucide.ArrowLeft, stringResource(R.string.back), onBack)
            Spacer(Modifier.width(Sp.md))
            Text(stringResource(R.string.settings), style = Saqr.type.title1, color = c.text1)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Sp.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Sp.sm),
        ) {
            // ---- appearance
            SectionLabel(stringResource(R.string.appearance), Modifier.padding(top = Sp.xs))
            Group {
                Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                    LabeledIcon(Lucide.Palette, stringResource(R.string.theme))
                    val themes = listOf("dark", "light", "oled", "system")
                    Segmented(listOf(stringResource(R.string.theme_dark), stringResource(R.string.theme_light), stringResource(R.string.theme_oled), stringResource(R.string.theme_system)),
                        themes.indexOf(ui.themeMode).coerceAtLeast(0), { ui.onTheme(themes[it]) })
                    Spacer(Modifier.height(Sp.xs))
                    ThemeStudioCard(ui)
                    Spacer(Modifier.height(Sp.xs))
                    LabeledIcon(Lucide.Languages, stringResource(R.string.language))
                    val langs = listOf("", "ar", "en")
                    val cur = when { ui.langTag.startsWith("ar") -> 1; ui.langTag.startsWith("en") -> 2; else -> 0 }
                    Segmented(listOf(stringResource(R.string.lang_system), stringResource(R.string.lang_ar), stringResource(R.string.lang_en)), cur, { ui.onLang(langs[it]) })
                }
            }

            // ---- editor (v0.12)
            SectionLabel(stringResource(R.string.editor_section), Modifier.padding(top = Sp.md))
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

            // ---- providers
            SectionLabel(stringResource(R.string.providers), Modifier.padding(top = Sp.md))
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

            // ---- agent
            SectionLabel(stringResource(R.string.agent_section), Modifier.padding(top = Sp.md))
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
            }
            if (ui.activeModel.isNotBlank()) Group { VisionEditor(ui) }
            // ---- v0.8/v0.9 MCP servers
            SectionLabel(stringResource(R.string.mcp_section), Modifier.padding(top = Sp.md))
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
                                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { ui.onMcpRemove(s.name) }, contentAlignment = Alignment.Center) {
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
                                    Box(Modifier.size(44.dp).clip(CircleShape).clickable { ui.onMcpStdioRemove(s.name) }, contentAlignment = Alignment.Center) {
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
                    onDismiss = { browsingServer = null },
                )
            }
            // ---- v0.8 on-device build
            SectionLabel(stringResource(R.string.build_section), Modifier.padding(top = Sp.md))
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

            // ---- voice
            SectionLabel(stringResource(R.string.voice_section), Modifier.padding(top = Sp.md))
            Group {
                Column {
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

            // ---- slash commands
            SectionLabel(stringResource(R.string.slash_section), Modifier.padding(top = Sp.md))
            Group { SlashCommandsEditor(ui) }

            // ---- usage & budget
            SectionLabel(stringResource(R.string.usage_section), Modifier.padding(top = Sp.md))
            Group { BudgetEditor(ui) }
            Group { PricesEditor(ui) }

            // ---- accounts
            SectionLabel(stringResource(R.string.accounts), Modifier.padding(top = Sp.md))
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

            // ---- v0.11 billing & licensing
            SectionLabel(stringResource(R.string.billing_section), Modifier.padding(top = Sp.md))
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

            // ---- about
            SectionLabel(stringResource(R.string.about), Modifier.padding(top = Sp.md))
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
            Spacer(Modifier.height(Sp.xxl))
        }
    }
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
                    Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = suggestions.isNotEmpty()) { menu = true }, contentAlignment = Alignment.Center) {
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
                                Box(Modifier.size(34.dp).clip(CircleShape).clickable { onToggleTool(raw, !disabled) }, contentAlignment = Alignment.Center) {
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
                SaqrField(key, { key = it }, label = stringResource(R.string.api_key),
                    placeholder = if (cfg.hasKey) "••••••••  " + stringResource(R.string.key_set) else def.keyUrl.removePrefix("https://"),
                    visual = PasswordVisualTransformation(), keyboard = KeyboardOptions(keyboardType = KeyboardType.Password), mono = true)
                Box {
                    SaqrField(model, { model = it }, label = stringResource(R.string.model), mono = true, trailing = {
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) { SIcon(Lucide.ChevronDown, size = 16.dp) }
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
                                .fold({ models = it.ifEmpty { def.suggestedModels }; modelsFmt.format(it.size.toString()) }, { it.message })
                        }
                    }, icon = Lucide.RefreshCw)
                    if (!active) GhostButton(stringResource(R.string.use_this), { ui.onActivate(def.id) }, icon = Lucide.Check)
                }
                status?.let { Text(it, style = Saqr.type.caption, color = c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis) }
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
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { ui.onDeleteCommand(cmd.name) }, contentAlignment = Alignment.Center) {
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
    var mood by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var genPack by remember { mutableStateOf<com.ctodrs.saqrcode.ui.theme.ThemePack?>(null) }
    var importJson by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
        LabeledIcon(Lucide.Sparkles, stringResource(R.string.studio_section))
        Text(stringResource(R.string.studio_packs), style = Saqr.type.caption, color = c.text2)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            com.ctodrs.saqrcode.ui.theme.ThemeStudio.builtins.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { p ->
                        PackChip(p, Modifier.weight(1f), active = ui.activePackId == p.id) {
                            ui.onApplyPack(p); toast(ctx, R.string.studio_applied)
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
                        Box(Modifier.size(30.dp).clip(CircleShape).clickable { ui.onCopyPack(p) }, contentAlignment = Alignment.Center) {
                            SIcon(Lucide.Copy, size = 14.dp, tint = c.text3, contentDescription = stringResource(R.string.studio_copy))
                        }
                        Box(Modifier.size(30.dp).clip(CircleShape).clickable { ui.onDeletePack(p.id) }, contentAlignment = Alignment.Center) {
                            SIcon(Lucide.Trash2, size = 14.dp, tint = c.danger, contentDescription = stringResource(R.string.delete))
                        }
                        Box(Modifier.size(30.dp).clip(CircleShape).clickable { ui.onApplyPack(p); toast(ctx, R.string.studio_applied) }, contentAlignment = Alignment.Center) {
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
        }
        genPack?.let { p ->
            PreviewRow(p,
                onApply = { ui.onApplyPack(p); toast(ctx, R.string.studio_applied) },
                onSave = { ui.onSavePack(p); toast(ctx, R.string.studio_saved) },
                onCopy = { ui.onCopyPack(p) })
        }
        SaqrField(importJson, { importJson = it }, label = stringResource(R.string.studio_import), placeholder = "{\"name\":…}", mono = true)
        GhostButton(stringResource(R.string.studio_import_apply), {
            val p = com.ctodrs.saqrcode.ui.theme.ThemePack.fromJson(importJson)
            if (p != null) { ui.onApplyPack(p); toast(ctx, R.string.studio_applied); err = null } else err = R.string.studio_bad_json
        }, icon = Lucide.Upload)
        err?.let { Text(stringResource(it), style = Saqr.type.caption, color = c.danger) }
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
