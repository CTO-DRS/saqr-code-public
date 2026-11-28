package com.ctodrs.saqrcode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.Budgets
import com.ctodrs.saqrcode.agent.FallbackTarget
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.ui.components.ListRow
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.SecondaryButton
import com.ctodrs.saqrcode.ui.components.RowDivider
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.components.SaqrSwitch
import com.ctodrs.saqrcode.ui.components.SectionLabel
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

/**
 * v0.15 «النواة المستقلة» settings: every autonomous-core feature has its own switch, the
 * fallback model is picked among providers the user already configured, and the run budgets
 * are plain numbers (0 = off). A plain holder so previews/screenshot tests can build it.
 */
class AutonomyUi(
    val parallelTools: Boolean = true,
    val onParallelTools: (Boolean) -> Unit = {},
    val learningMode: Boolean = false,
    val onLearningMode: (Boolean) -> Unit = {},
    val sentinelDigest: Boolean = false,
    val onSentinelDigest: (Boolean) -> Unit = {},
    val ciWatchRepo: String = "",
    val onCiWatchRepo: (String) -> Unit = {},
    val confirmRiskyCommands: Boolean = true,
    val onConfirmRiskyCommands: (Boolean) -> Unit = {},
    val smartLoopGuard: Boolean = true,
    val onSmartLoopGuard: (Boolean) -> Unit = {},
    val verifyBeforeDone: Boolean = true,
    val onVerifyBeforeDone: (Boolean) -> Unit = {},
    val retryLlm: Boolean = true,
    val onRetryLlm: (Boolean) -> Unit = {},
    val fallback: FallbackTarget? = null,
    /** Fallback choices: (provider id, display name, model) on providers that already have a key. */
    val fallbackOptions: List<FallbackTarget> = emptyList(),
    val onFallback: (FallbackTarget?) -> Unit = {},
    val taskJournal: Boolean = true,
    val onTaskJournal: (Boolean) -> Unit = {},
    val auditLog: Boolean = true,
    val onAuditLog: (Boolean) -> Unit = {},
    val untrustedMarking: Boolean = true,
    val onUntrustedMarking: (Boolean) -> Unit = {},
    val maxSteps: Int = Budgets.DEFAULT_STEPS,
    val maxMinutes: Int = 0,
    val maxCostUsd: Double = 0.0,
    val onBudgets: (Int, Int, Double) -> Unit = { _, _, _ -> },
    val cloudEnabled: Boolean = false,
    val onCloudEnabled: (Boolean) -> Unit = {},
    val cloudTokenSet: Boolean = false,
    val onCloudToken: (String) -> Unit = {},
) {
    companion object {
        /** Wires the holder to the store. Fallback options never include a provider without a key. */
        fun from(settings: SettingsStore): AutonomyUi {
            val options = com.ctodrs.saqrcode.agent.Providers.all
                .filter { settings.providerConfigured(it.id) }
                .flatMap { def ->
                    val cfg = settings.config(def.id)
                    (listOf(cfg.model) + def.suggestedModels).filter { it.isNotBlank() }.distinct().take(6)
                        .map { FallbackTarget(def.id, it) }
                }
            return AutonomyUi(
                parallelTools = settings.parallelTools, onParallelTools = { settings.parallelTools = it },
                learningMode = settings.learningMode, onLearningMode = { settings.learningMode = it },
                sentinelDigest = settings.sentinelDigest,
                onSentinelDigest = { settings.sentinelDigest = it; com.ctodrs.saqrcode.service.SentinelReceiver.schedule(com.ctodrs.saqrcode.SaqrApp.instance, it) },
                ciWatchRepo = settings.ciWatchRepo,
                onCiWatchRepo = {
                    settings.ciWatchRepo = it
                    com.ctodrs.saqrcode.service.CiWatchReceiver.schedule(com.ctodrs.saqrcode.SaqrApp.instance, com.ctodrs.saqrcode.watch.CiWatch.validRepo(it))
                },
                confirmRiskyCommands = settings.confirmRiskyCommands, onConfirmRiskyCommands = { settings.confirmRiskyCommands = it },
                smartLoopGuard = settings.smartLoopGuard, onSmartLoopGuard = { settings.smartLoopGuard = it },
                verifyBeforeDone = settings.verifyBeforeDone, onVerifyBeforeDone = { settings.verifyBeforeDone = it },
                retryLlm = settings.retryLlm, onRetryLlm = { settings.retryLlm = it },
                fallback = settings.fallbackModel, fallbackOptions = options, onFallback = { settings.fallbackModel = it },
                taskJournal = settings.taskJournal, onTaskJournal = { settings.taskJournal = it },
                auditLog = settings.auditLog, onAuditLog = { settings.auditLog = it },
                untrustedMarking = settings.untrustedMarking, onUntrustedMarking = { settings.untrustedMarking = it },
                maxSteps = settings.maxSteps, maxMinutes = settings.maxMinutes, maxCostUsd = settings.maxCostUsd,
                onBudgets = { s, m, c -> settings.maxSteps = s; settings.maxMinutes = m; settings.maxCostUsd = c },
                cloudEnabled = settings.cloudEnabled, onCloudEnabled = { settings.cloudEnabled = it },
                cloudTokenSet = settings.cloudProxyToken.isNotBlank(), onCloudToken = { settings.cloudProxyToken = it },
            )
        }
    }
}

/** v0.1.69: the cloud computer gets its own page (it used to hide inside the autonomy list). */
@Composable
internal fun CloudSection(ui: AutonomyUi) {
    Group {
        Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
            val ready = ui.cloudEnabled && ui.cloudTokenSet
            Text(stringResource(if (ready) R.string.cloud_status_ready else if (ui.cloudTokenSet) R.string.cloud_status_off else R.string.cloud_status_needs_token),
                style = Saqr.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Saqr.colors.text1)
            Text(stringResource(R.string.cloud_computer_about), style = Saqr.type.caption, color = Saqr.colors.text2)
        }
    }
    Group {
        Column {
            var cloudToken by remember { mutableStateOf("") }
            ListRow(stringResource(R.string.cloud_workspace), stringResource(R.string.cloud_workspace_desc), icon = Lucide.CloudUpload,
                trailing = { SaqrSwitch(ui.cloudEnabled, ui.onCloudEnabled) })
            if (ui.cloudEnabled) {
                Column(Modifier.padding(horizontal = Sp.lg, vertical = Sp.sm), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    SaqrField(cloudToken, { cloudToken = it.trim() }, label = stringResource(R.string.cloud_token),
                        placeholder = if (ui.cloudTokenSet) stringResource(R.string.cloud_token_saved) else null, mono = true,
                        supporting = stringResource(R.string.cloud_token_hint))
                    PrimaryButton(stringResource(R.string.save), { ui.onCloudToken(cloudToken); cloudToken = "" })
                }
            }
        }
    }
    CloudSecretsGroup()
}

@Composable
private fun CloudSecretsGroup() {
    val store = remember { com.ctodrs.saqrcode.cloud.ProjectSecretStore(com.ctodrs.saqrcode.SaqrApp.instance.secure) }
    var names by remember { mutableStateOf(store.list()) }
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val nameOk = name.isEmpty() || com.ctodrs.saqrcode.cloud.ProjectSecrets.validName(name)
    Group {
        Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
            Text(stringResource(R.string.cloud_secrets), style = Saqr.type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Saqr.colors.text1)
            Text(stringResource(R.string.cloud_secrets_desc), style = Saqr.type.caption, color = Saqr.colors.text2)
            names.forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    Text(n, style = Saqr.type.body, color = Saqr.colors.text1, modifier = Modifier.weight(1f))
                    SecondaryButton(stringResource(R.string.delete), { store.remove(n); names = store.list() })
                }
            }
            SaqrField(name, { name = it.uppercase().trim() }, label = stringResource(R.string.cloud_secret_name), placeholder = "NPM_TOKEN", mono = true, isError = !nameOk)
            SaqrField(value, { value = it.trim() }, label = stringResource(R.string.cloud_secret_value), mono = true,
                visual = androidx.compose.ui.text.input.PasswordVisualTransformation())
            PrimaryButton(stringResource(R.string.save), {
                if (store.put(name, value)) { name = ""; value = ""; names = store.list() }
            })
        }
    }
}

@Composable
internal fun AutonomySection(ui: AutonomyUi, teamMode: Boolean) {
    SectionLabel(stringResource(R.string.autonomy_section), Modifier.padding(top = Sp.md))
    Group {
        Column {
            ListRow(stringResource(R.string.parallel_tools), stringResource(R.string.parallel_tools_desc), icon = Lucide.Layers,
                trailing = { SaqrSwitch(ui.parallelTools, ui.onParallelTools) })
            RowDivider()
            ListRow(stringResource(R.string.learning_mode), stringResource(R.string.learning_mode_desc), icon = Lucide.BookMarked,
                trailing = { SaqrSwitch(ui.learningMode, ui.onLearningMode) })
            RowDivider()
            ListRow(stringResource(R.string.sentinel_digest), stringResource(R.string.sentinel_digest_desc), icon = Lucide.Bell,
                trailing = { SaqrSwitch(ui.sentinelDigest, ui.onSentinelDigest) })
            RowDivider()
            var ciRepo by remember { mutableStateOf(ui.ciWatchRepo) }
            Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
                SaqrField(ciRepo, { ciRepo = it; if (it.isBlank() || com.ctodrs.saqrcode.watch.CiWatch.validRepo(it)) ui.onCiWatchRepo(it) },
                    label = stringResource(R.string.ci_watch), placeholder = "owner/repo", mono = true,
                    isError = ciRepo.isNotBlank() && !com.ctodrs.saqrcode.watch.CiWatch.validRepo(ciRepo))
                Text(stringResource(R.string.ci_watch_desc), style = Saqr.type.caption, color = Saqr.colors.text2)
            }
            RowDivider()
            ListRow(stringResource(R.string.confirm_risky_commands), stringResource(R.string.confirm_risky_commands_desc), icon = Lucide.ShieldCheck,
                trailing = { SaqrSwitch(ui.confirmRiskyCommands, ui.onConfirmRiskyCommands) })
            RowDivider()
            ListRow(stringResource(R.string.smart_loop_guard), stringResource(R.string.smart_loop_guard_desc), icon = Lucide.RefreshCw,
                trailing = { SaqrSwitch(ui.smartLoopGuard, ui.onSmartLoopGuard) })
            if (teamMode) {
                RowDivider()
                ListRow(stringResource(R.string.verify_before_done), stringResource(R.string.verify_before_done_desc), icon = Lucide.CircleCheck,
                    trailing = { SaqrSwitch(ui.verifyBeforeDone, ui.onVerifyBeforeDone) })
            }
            RowDivider()
            ListRow(stringResource(R.string.untrusted_marking), stringResource(R.string.untrusted_marking_desc), icon = Lucide.Globe,
                trailing = { SaqrSwitch(ui.untrustedMarking, ui.onUntrustedMarking) })
        }
    }
    Group {
        Column {
            ListRow(stringResource(R.string.retry_llm), stringResource(R.string.retry_llm_desc), icon = Lucide.RotateCcw,
                trailing = { SaqrSwitch(ui.retryLlm, ui.onRetryLlm) })
            RowDivider()
            FallbackPicker(ui)
        }
    }
    Group {
        Column {
            ListRow(stringResource(R.string.task_journal), stringResource(R.string.task_journal_desc), icon = Lucide.ListTree,
                trailing = { SaqrSwitch(ui.taskJournal, ui.onTaskJournal) })
            RowDivider()
            ListRow(stringResource(R.string.audit_log), stringResource(R.string.audit_log_desc), icon = Lucide.History,
                trailing = { SaqrSwitch(ui.auditLog, ui.onAuditLog) })
        }
    }
    Group { BudgetEditor(ui) }
}

@Composable
private fun FallbackPicker(ui: AutonomyUi) {
    val c = Saqr.colors
    var menu by remember { mutableStateOf(false) }
    val none = stringResource(R.string.fallback_none)
    Box {
        ListRow(
            title = stringResource(R.string.fallback_model),
            subtitle = (ui.fallback?.label ?: none) + "\n" + stringResource(R.string.fallback_model_desc),
            icon = Lucide.Route,
            onClick = { menu = true },
            trailing = { SIcon(Lucide.ChevronDown, size = 16.dp) },
        )
        DropdownMenu(menu, { menu = false }, Modifier.heightIn(max = 360.dp).background(c.surface2)) {
            DropdownMenuItem(text = { Text(none, style = Saqr.type.body, color = c.text1) }, onClick = { ui.onFallback(null); menu = false })
            ui.fallbackOptions.forEach { t ->
                DropdownMenuItem(text = { Text(t.label, style = Saqr.type.monoSmall, color = c.text1) }, onClick = { ui.onFallback(t); menu = false })
            }
        }
    }
}

@Composable
private fun BudgetEditor(ui: AutonomyUi) {
    val c = Saqr.colors
    var steps by remember(ui.maxSteps) { mutableStateOf(ui.maxSteps.toString()) }
    var minutes by remember(ui.maxMinutes) { mutableStateOf(ui.maxMinutes.toString()) }
    var cost by remember(ui.maxCostUsd) { mutableStateOf(if (ui.maxCostUsd > 0.0) ui.maxCostUsd.toString() else "0") }
    var saved by remember { mutableStateOf(false) }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
        LabeledIcon(Lucide.Clock, stringResource(R.string.budgets_title))
        Text(stringResource(R.string.budgets_desc), style = Saqr.type.caption, color = c.text3)
        SaqrField(steps, { steps = it.filter(Char::isDigit).take(3); saved = false }, label = stringResource(R.string.budget_steps), mono = true, keyboard = num)
        SaqrField(minutes, { minutes = it.filter(Char::isDigit).take(3); saved = false }, label = stringResource(R.string.budget_minutes), mono = true, keyboard = num)
        SaqrField(cost, { cost = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8); saved = false }, label = stringResource(R.string.budget_cost), mono = true,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(stringResource(R.string.save), {
                ui.onBudgets(
                    Budgets.clampSteps(steps.toIntOrNull() ?: Budgets.DEFAULT_STEPS),
                    (minutes.toIntOrNull() ?: 0).coerceIn(0, 600),
                    (cost.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                )
                saved = true
            })
            if (saved) Text("  " + stringResource(R.string.saved), style = Saqr.type.caption, color = c.text2)
        }
    }
}
