package com.ctodrs.saqrcode.ui.chat

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.Budget
import com.ctodrs.saqrcode.agent.Checkpoints
import com.ctodrs.saqrcode.agent.ModelPrice
import com.ctodrs.saqrcode.agent.PriceTable
import com.ctodrs.saqrcode.agent.Usage
import com.ctodrs.saqrcode.ui.components.ProgressLine
import com.ctodrs.saqrcode.ui.components.latinDigits
import com.ctodrs.saqrcode.ui.icons.FileTypeIcon
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import java.util.Locale

/** Figures for the usage sheet; costs are computed from [prices] when shown. */
class UsageSummary(
    val session: Map<String, Usage>,
    val today: Map<String, Usage>,
    val month: Map<String, Usage>,
    val prices: List<ModelPrice>,
    val budget: Double?,
    /** v0.1.15: the optional daily cap, shown with today's spend. */
    val dailyBudget: Double? = null,
    /** v0.8 team mode: reviewer / planner spend of this session, shown as their own rows. */
    val review: Map<String, Usage> = emptyMap(),
    val planner: Map<String, Usage> = emptyMap(),
) {
    fun cost(m: Map<String, Usage>) = PriceTable.cost(m, prices)
    val monthSpent get() = cost(month) ?: 0.0
    val todaySpent get() = cost(today) ?: 0.0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaqrSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Saqr.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface1, contentColor = c.text1, scrimColor = c.bg.copy(alpha = 0.6f),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = Sp.lg, end = Sp.lg, bottom = Sp.xl)) { content() }
    }
}

@Composable
private fun SheetTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    val c = Saqr.colors
    Row(Modifier.padding(bottom = Sp.md), verticalAlignment = Alignment.CenterVertically) {
        SIcon(icon, size = 20.dp, tint = c.accentText)
        Spacer(Modifier.width(Sp.sm))
        Text(title, style = Saqr.type.title2, color = c.text1)
    }
}

// ---------------------------------------------------------------- usage

@Composable
fun UsageSheet(summary: UsageSummary, onDismiss: () -> Unit, runStats: (() -> com.ctodrs.saqrcode.agent.RunStats)? = null, running: Boolean = false) {
    SaqrSheet(onDismiss) { UsageSheetContent(summary, runStats, running) }
}

/** v0.1.15: sheet body on its own so visual tests can render it without the modal wrapper. */
@Composable
fun UsageSheetContent(summary: UsageSummary, runStats: (() -> com.ctodrs.saqrcode.agent.RunStats)? = null, running: Boolean = false) {
    val c = Saqr.colors
    // live-refresh the per-task row while a run is in flight
    var stats by remember { mutableStateOf(runStats?.invoke()) }
    LaunchedEffect(running) {
        while (running && runStats != null) { stats = runStats.invoke(); kotlinx.coroutines.delay(2_000) }
        stats = runStats?.invoke()
    }
    Column {
        SheetTitle(Lucide.Zap, stringResource(R.string.usage_title))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
            stats?.let { RunRow(it) }
            UsageRow(stringResource(R.string.usage_session), summary.session, summary.cost(summary.session))
            if (summary.review.isNotEmpty()) UsageRow(stringResource(R.string.usage_review), summary.review, summary.cost(summary.review))
            if (summary.planner.isNotEmpty()) UsageRow(stringResource(R.string.usage_planner), summary.planner, summary.cost(summary.planner))
            UsageRow(stringResource(R.string.usage_today), summary.today, summary.cost(summary.today))
            UsageRow(stringResource(R.string.usage_month), summary.month, summary.cost(summary.month))
            summary.budget?.let { b ->
                val spent = summary.monthSpent
                val level = Budget.level(spent, b)
                Column(Modifier.fillMaxWidth().padding(top = Sp.xs)) {
                    Text(stringResource(R.string.usage_budget, ltr(formatUsd(spent)), ltr(formatUsd(b))), style = Saqr.type.label, color = c.text1)
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(Budget.fraction(spent, b), Modifier.fillMaxWidth(),
                        color = when (level) { Budget.Level.OVER -> c.danger; Budget.Level.WARN -> c.warning; else -> c.accent })
                }
            }
            summary.dailyBudget?.let { b ->
                val spent = summary.todaySpent
                val level = Budget.level(spent, b)
                Column(Modifier.fillMaxWidth().padding(top = Sp.xs)) {
                    Text(stringResource(R.string.usage_budget_daily, ltr(formatUsd(spent)), ltr(formatUsd(b))), style = Saqr.type.label, color = c.text1)
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(Budget.fraction(spent, b), Modifier.fillMaxWidth(),
                        color = when (level) { Budget.Level.OVER -> c.danger; Budget.Level.WARN -> c.warning; else -> c.accent })
                }
            }
            val all = summary.session.values + summary.month.values
            if (all.any { it.estimated }) Text(stringResource(R.string.usage_estimated), style = Saqr.type.caption, color = c.text3)
            Text(stringResource(R.string.usage_prices_note), style = Saqr.type.caption, color = c.text3)
        }
    }
}

@Composable
private fun UsageRow(label: String, byModel: Map<String, Usage>, cost: Double?) {
    val c = Saqr.colors
    val total = byModel.values.fold(Usage.ZERO) { a, b -> a + b }
    val shape = RoundedCornerShape(Radius.md)
    val est = if (total.estimated) "~" else ""
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface2).border(1.dp, c.border, shape).padding(Sp.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, modifier = Modifier.weight(1f))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(if (cost != null) formatUsd(cost) else "—", style = Saqr.type.mono.copy(fontWeight = FontWeight.SemiBold),
                    color = if (cost != null) c.accentText else c.text3)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.usage_in_out, est + formatTokens(total.input), est + formatTokens(total.output)),
            style = Saqr.type.caption, color = c.text2)
        if (byModel.size > 1 || (byModel.isNotEmpty() && cost == null)) {
            byModel.forEach { (m, u) ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(m, style = Saqr.type.monoMicro, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(Sp.sm))
                    Text((if (u.estimated) "~" else "") + formatTokens(u.total), style = Saqr.type.monoMicro, color = c.text3, maxLines = 1)
                }
            }
            if (cost == null) Text(stringResource(R.string.usage_no_price), style = Saqr.type.caption, color = c.text3, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** v0.1.15: the current (or last) run's own numbers — time, steps, files, commands, tokens, cost. */
@Composable
private fun RunRow(stats: com.ctodrs.saqrcode.agent.RunStats) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.md)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface2).border(1.dp, c.border, shape).padding(Sp.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.usage_run), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, modifier = Modifier.weight(1f))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(stats.cost?.let { formatUsd(it) } ?: "—", style = Saqr.type.mono.copy(fontWeight = FontWeight.SemiBold),
                    color = if (stats.cost != null) c.accentText else c.text3)
            }
        }
        Spacer(Modifier.height(2.dp))
        val parts = ArrayList<String>()
        if (stats.activeMs > 0) parts += formatDuration(stats.activeMs)
        parts += stringResource(R.string.usage_run_parts, stats.steps.toString(), stats.files.toString(), stats.commands.toString())
        parts += (if (stats.estimatedTokens) "~" else "") + formatTokens(stats.tokens) + " " + stringResource(R.string.usage_tokens_word)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(parts.joinToString(" · "), style = Saqr.type.caption, color = c.text2)
        }
    }
}

// ---------------------------------------------------------------- checkpoints

fun formatBytes(n: Long): String = when {
    n >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", n / 1048576.0)
    n >= 1024 -> String.format(Locale.US, "%.0f KB", n / 1024.0)
    else -> "$n B"
}

@Composable
fun CheckpointsSheet(points: List<Checkpoints.Point>, storageBytes: Long, onPick: (Checkpoints.Point) -> Unit, onDismiss: () -> Unit) {
    val c = Saqr.colors
    SaqrSheet(onDismiss) {
        SheetTitle(Lucide.History, stringResource(R.string.checkpoints))
        if (points.isEmpty()) {
            Text(stringResource(R.string.checkpoints_empty), style = Saqr.type.bodySmall, color = c.text2, modifier = Modifier.padding(vertical = Sp.md))
        } else {
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
                items(points, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2).clickable { onPick(p) }.padding(Sp.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.prompt.ifBlank { "—" }, style = Saqr.type.label.copy(textDirection = TextDirection.Content), color = c.text1,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                DateUtils.getRelativeTimeSpanString(p.time).toString().latinDigits() + " · " +
                                    stringResource(R.string.cp_files_n, p.files.size.toString()),
                                style = Saqr.type.caption, color = c.text3, maxLines = 1,
                            )
                        }
                        Spacer(Modifier.width(Sp.sm))
                        SIcon(Lucide.RotateCcw, size = 18.dp, tint = c.accentText, contentDescription = stringResource(R.string.restore_to_here))
                    }
                }
            }
        }
        Spacer(Modifier.height(Sp.sm))
        Text(stringResource(R.string.checkpoints_note, formatBytes(storageBytes)), style = Saqr.type.caption, color = c.text3)
    }
}

/** Confirm dialog listing the files a restore will revert / delete / skip. */
@Composable
fun RestoreConfirmDialog(point: Checkpoints.Point, plan: Checkpoints.Plan, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = Saqr.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface1,
        icon = { SIcon(Lucide.RotateCcw, size = 24.dp, tint = c.accentText) },
        title = { Text(stringResource(R.string.checkpoint_confirm_title), style = Saqr.type.title2) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("“" + point.prompt + "”", style = Saqr.type.bodySmall.copy(textDirection = TextDirection.Content), color = c.text2,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Sp.xs))
                if (plan.isEmpty) Text(stringResource(R.string.checkpoint_nothing), style = Saqr.type.bodySmall, color = c.text2)
                else Text(stringResource(R.string.checkpoint_confirm_body), style = Saqr.type.bodySmall, color = c.text1)
                plan.restored.forEach { FileLine(it, stringResource(R.string.cp_will_restore), c.accentText) }
                plan.deleted.forEach { FileLine(it, stringResource(R.string.cp_will_delete), c.danger) }
                plan.skipped.forEach { FileLine(it, stringResource(R.string.cp_skipped), c.text3) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = plan.changedCount > 0) { Text(stringResource(R.string.restore), color = if (plan.changedCount > 0) c.accentText else c.text3) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.text2) } },
    )
}

@Composable
private fun FileLine(path: String, action: String, color: androidx.compose.ui.graphics.Color) {
    val c = Saqr.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        FileTypeIcon(path.substringAfterLast('/'), 16.dp)
        Spacer(Modifier.width(Sp.sm))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(path, style = Saqr.type.monoSmall, color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.width(Sp.sm))
        Text(action, style = Saqr.type.caption, color = color, maxLines = 1)
    }
}
