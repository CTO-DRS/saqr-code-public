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
)

@Composable
fun SettingsScreen(onBack: () -> Unit, onAccounts: () -> Unit = {}) {
    val settings = SaqrApp.instance.settings
    val version by settings.version.collectAsState()
    version
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
                    val themes = listOf("dark", "light", "system")
                    Segmented(listOf(stringResource(R.string.theme_dark), stringResource(R.string.theme_light), stringResource(R.string.theme_system)),
                        themes.indexOf(ui.themeMode).coerceAtLeast(0), { ui.onTheme(themes[it]) })
                    Spacer(Modifier.height(Sp.xs))
                    LabeledIcon(Lucide.Languages, stringResource(R.string.language))
                    val langs = listOf("", "ar", "en")
                    val cur = when { ui.langTag.startsWith("ar") -> 1; ui.langTag.startsWith("en") -> 2; else -> 0 }
                    Segmented(listOf(stringResource(R.string.lang_system), stringResource(R.string.lang_ar), stringResource(R.string.lang_en)), cur, { ui.onLang(langs[it]) })
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
