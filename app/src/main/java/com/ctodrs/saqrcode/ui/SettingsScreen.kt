package com.ctodrs.saqrcode.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.ctodrs.saqrcode.BuildConfig
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.agent.LlmClient
import com.ctodrs.saqrcode.agent.ProviderDef
import com.ctodrs.saqrcode.agent.Providers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = SaqrApp.instance.settings
    val version by settings.version.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Language
            Section(stringResource(R.string.language)) {
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to R.string.lang_system, "ar" to R.string.lang_ar, "en" to R.string.lang_en).forEach { (tag, label) ->
                        FilterChip(
                            selected = current.startsWith(tag) && (tag.isNotEmpty() || current.isEmpty()),
                            onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }

            // Providers
            Section(stringResource(R.string.providers)) {
                version // read to recompose on change
                Providers.all.forEach { def ->
                    ProviderCard(def, active = settings.activeProvider == def.id, onActivate = { settings.activeProvider = def.id })
                }
            }

            // Approval
            Section(stringResource(R.string.approval_mode)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.approval_mode_desc), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = settings.approveAll.also { version }, onCheckedChange = { settings.approveAll = it })
                }
            }

            // GitHub
            Section(stringResource(R.string.github)) {
                var user by remember { mutableStateOf(settings.githubUser) }
                var token by remember { mutableStateOf("") }
                var saved by remember { mutableStateOf(false) }
                OutlinedTextField(user, { user = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.github_user)) })
                OutlinedTextField(token, { token = it; saved = false }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.github_token)) },
                    placeholder = { Text(if (settings.githubToken != null) stringResource(R.string.key_set) else "github_pat_…") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text(stringResource(R.string.github_token_hint)) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        settings.githubUser = user
                        if (token.isNotBlank()) settings.githubToken = token
                        token = ""; saved = true
                    }) { Text(stringResource(R.string.save)) }
                    if (saved) { Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.saved), color = Teal) }
                }
            }

            Section(stringResource(R.string.about)) {
                Text(stringResource(R.string.about_text, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Gold)
        content()
        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ProviderCard(def: ProviderDef, active: Boolean, onActivate: () -> Unit) {
    val settings = SaqrApp.instance.settings
    val cfg = settings.config(def.id)
    var expanded by remember { mutableStateOf(false) }
    var baseUrl by remember(def.id) { mutableStateOf(cfg.baseUrl) }
    var model by remember(def.id) { mutableStateOf(cfg.model) }
    var key by remember(def.id) { mutableStateOf("") }
    var models by remember(def.id) { mutableStateOf(def.suggestedModels) }
    var menu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.saved)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = active, onClick = onActivate)
                Column(Modifier.weight(1f)) {
                    Text(def.displayName, fontWeight = FontWeight.Medium)
                    Text(
                        (if (cfg.hasKey) stringResource(R.string.key_set) else stringResource(R.string.key_not_set)) +
                            (if (cfg.model.isNotBlank()) " · ${cfg.model}" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (cfg.hasKey) Icon(Icons.Filled.CheckCircle, null, tint = Teal)
            }
            if (expanded) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.base_url)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.api_key)) },
                        placeholder = { Text(if (cfg.hasKey) "••••••••  (" + stringResource(R.string.key_set) + ")" else def.keyUrl) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Column {
                        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.model)) },
                            trailingIcon = { IconButton(onClick = { menu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } })
                        DropdownMenu(menu, { menu = false }, Modifier.heightIn(max = 360.dp)) {
                            models.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { model = m; menu = false }) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            settings.saveProvider(def.id, baseUrl, model, key.ifBlank { null })
                            key = ""; status = savedText
                        }) { Text(stringResource(R.string.save)) }
                        TextButton(onClick = {
                            status = "…"
                            scope.launch {
                                val k = key.ifBlank { settings.apiKey(def.id) }
                                status = runCatching { LlmClient(def, baseUrl, model, k).listModels() }
                                    .fold({ models = it.ifEmpty { def.suggestedModels }; "${it.size} models" }, { it.message })
                            }
                        }) { Text(stringResource(R.string.fetch_models)) }
                        if (!active) TextButton(onClick = onActivate) { Text(stringResource(R.string.use_this)) }
                    }
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
