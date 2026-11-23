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
    val onSaveGithub: (user: String, token: String?) -> Unit,
)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = SaqrApp.instance.settings
    val version by settings.version.collectAsState()
    version
    val ui = SettingsUi(
        themeMode = settings.themeMode,
        langTag = AppCompatDelegate.getApplicationLocales().toLanguageTags(),
        providers = Providers.all.map { it to settings.config(it.id) },
        activeProvider = settings.activeProvider,
        approveAll = settings.approveAll,
        githubUser = settings.githubUser,
        githubTokenSet = settings.githubToken != null,
        versionName = BuildConfig.VERSION_NAME,
        onTheme = { settings.themeMode = it },
        onLang = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) },
        onActivate = { settings.activeProvider = it },
        onSaveProvider = { id, b, m, k -> settings.saveProvider(id, b, m, k) },
        fetchModels = { def, b, m, k -> runCatching { LlmClient(def, b, m, k ?: settings.apiKey(def.id)).listModels() } },
        onApproveAll = { settings.approveAll = it },
        onSaveGithub = { u, t -> settings.githubUser = u; if (t != null) runCatching { settings.accounts.saveGitHub(t, com.ctodrs.saqrcode.data.GitHubAccount(u.ifBlank { "github" }, null, null, 0, null, false)); settings.accountsChanged() } },
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

            // ---- github
            SectionLabel(stringResource(R.string.github), Modifier.padding(top = Sp.md))
            Group {
                var user by remember { mutableStateOf(ui.githubUser) }
                var token by remember { mutableStateOf("") }
                var saved by remember { mutableStateOf(false) }
                Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LabeledIcon(Lucide.Github, stringResource(R.string.github_account), Modifier.weight(1f))
                        Tag(stringResource(if (ui.githubTokenSet) R.string.connected else R.string.not_connected),
                            color = if (ui.githubTokenSet) c.success else c.text3, dot = true)
                    }
                    SaqrField(user, { user = it; saved = false }, label = stringResource(R.string.github_user), placeholder = "octocat", mono = true)
                    SaqrField(token, { token = it; saved = false }, label = stringResource(R.string.github_token),
                        placeholder = if (ui.githubTokenSet) "••••••••  " + stringResource(R.string.key_set) else "github_pat_…",
                        visual = PasswordVisualTransformation(), keyboard = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supporting = stringResource(R.string.github_token_hint), mono = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PrimaryButton(stringResource(R.string.save), { ui.onSaveGithub(user, token.ifBlank { null }); token = ""; saved = true }, icon = Lucide.Save)
                        if (saved) { Spacer(Modifier.width(Sp.md)); SIcon(Lucide.CircleCheck, size = 16.dp, tint = c.success); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.saved), style = Saqr.type.caption, color = c.success) }
                    }
                }
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
private fun Group(content: @Composable () -> Unit) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.lg)
    Box(Modifier.fillMaxWidth().clip(shape).background(c.surface1).border(1.dp, c.border, shape)) { content() }
}

@Composable
private fun LabeledIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
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

