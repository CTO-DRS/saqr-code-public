package com.ctodrs.saqrcode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.memory.MemoryRecall
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.ui.components.ListRow
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.RowDivider
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.components.SaqrSwitch
import com.ctodrs.saqrcode.ui.components.SectionLabel
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

/**
 * v0.17 «محرك الذاكرة والموجّه» settings: every memory-engine and router feature has its own
 * switch; the risky ones (relevance retrieval, session summary, global memory, smart router)
 * are off by default so an upgrade behaves exactly like v0.16 until the user opts in.
 */
class MemoryRouterUi(
    val memoryRelevance: Boolean = false,
    val onMemoryRelevance: (Boolean) -> Unit = {},
    val memoryTopK: Int = MemoryRecall.DEFAULT_K,
    val sessionSummary: Boolean = false,
    val onSessionSummary: (Boolean) -> Unit = {},
    val memoryLessons: Boolean = true,
    val onMemoryLessons: (Boolean) -> Unit = {},
    val globalMemory: Boolean = false,
    val onGlobalMemory: (Boolean) -> Unit = {},
    val smartRouter: Boolean = false,
    val onSmartRouter: (Boolean) -> Unit = {},
    val dailySpendCap: Double = 0.0,
    val monthlySpendCap: Double = 0.0,
    /** (topK, daily cap, monthly cap) */
    val onNumbers: (Int, Double, Double) -> Unit = { _, _, _ -> },
) {
    companion object {
        fun from(settings: SettingsStore) = MemoryRouterUi(
            memoryRelevance = settings.memoryRelevance, onMemoryRelevance = { settings.memoryRelevance = it },
            memoryTopK = settings.memoryTopK,
            sessionSummary = settings.sessionSummary, onSessionSummary = { settings.sessionSummary = it },
            memoryLessons = settings.memoryLessons, onMemoryLessons = { settings.memoryLessons = it },
            globalMemory = settings.globalMemory, onGlobalMemory = { settings.globalMemory = it },
            smartRouter = settings.smartRouter, onSmartRouter = { settings.smartRouter = it },
            dailySpendCap = settings.dailySpendCap, monthlySpendCap = settings.monthlySpendCap,
            onNumbers = { k, d, m -> settings.memoryTopK = k; settings.dailySpendCap = d; settings.monthlySpendCap = m },
        )
    }
}

@Composable
internal fun MemoryRouterSection(ui: MemoryRouterUi) {
    SectionLabel(stringResource(R.string.memory_engine_section), Modifier.padding(top = Sp.md))
    Group {
        Column {
            ListRow(stringResource(R.string.memory_relevance), stringResource(R.string.memory_relevance_desc), icon = Lucide.Search,
                trailing = { SaqrSwitch(ui.memoryRelevance, ui.onMemoryRelevance) })
            RowDivider()
            ListRow(stringResource(R.string.session_summary), stringResource(R.string.session_summary_desc), icon = Lucide.History,
                trailing = { SaqrSwitch(ui.sessionSummary, ui.onSessionSummary) })
            RowDivider()
            ListRow(stringResource(R.string.memory_lessons), stringResource(R.string.memory_lessons_desc), icon = Lucide.Bug,
                trailing = { SaqrSwitch(ui.memoryLessons, ui.onMemoryLessons) })
            RowDivider()
            ListRow(stringResource(R.string.global_memory), stringResource(R.string.global_memory_desc), icon = Lucide.Globe,
                trailing = { SaqrSwitch(ui.globalMemory, ui.onGlobalMemory) })
        }
    }
    Group {
        ListRow(stringResource(R.string.smart_router), stringResource(R.string.smart_router_desc), icon = Lucide.Route,
            trailing = { SaqrSwitch(ui.smartRouter, ui.onSmartRouter) })
    }
    Group { NumbersEditor(ui) }
}

@Composable
private fun NumbersEditor(ui: MemoryRouterUi) {
    val c = Saqr.colors
    var k by remember(ui.memoryTopK) { mutableStateOf(ui.memoryTopK.toString()) }
    var daily by remember(ui.dailySpendCap) { mutableStateOf(if (ui.dailySpendCap > 0.0) ui.dailySpendCap.toString() else "0") }
    var monthly by remember(ui.monthlySpendCap) { mutableStateOf(if (ui.monthlySpendCap > 0.0) ui.monthlySpendCap.toString() else "0") }
    var saved by remember { mutableStateOf(false) }
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Column(Modifier.padding(Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
        LabeledIcon(Lucide.TrendingDown, stringResource(R.string.spend_caps_title))
        Text(stringResource(R.string.spend_caps_desc), style = Saqr.type.caption, color = c.text3)
        SaqrField(k, { k = it.filter(Char::isDigit).take(2); saved = false }, label = stringResource(R.string.memory_top_k), mono = true,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
        SaqrField(daily, { daily = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8); saved = false }, label = stringResource(R.string.daily_spend_cap), mono = true, keyboard = dec)
        SaqrField(monthly, { monthly = it.filter { ch -> ch.isDigit() || ch == '.' }.take(8); saved = false }, label = stringResource(R.string.monthly_spend_cap), mono = true, keyboard = dec)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(stringResource(R.string.save), {
                ui.onNumbers(
                    (k.toIntOrNull() ?: MemoryRecall.DEFAULT_K).coerceIn(MemoryRecall.MIN_K, MemoryRecall.MAX_K),
                    (daily.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                    (monthly.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                )
                saved = true
            })
            if (saved) Text("  " + stringResource(R.string.saved), style = Saqr.type.caption, color = c.text2)
        }
    }
}
