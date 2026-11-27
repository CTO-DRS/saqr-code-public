package com.ctodrs.saqrcode.ui.chat

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.AgentSession
import com.ctodrs.saqrcode.agent.MemoryBank
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.components.Tag
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

/** v0.17: the three memory layers the sheet shows. */
enum class MemoryTab { PROJECT, GLOBAL, SESSION }

/**
 * v0.13 بنك الذاكرة · The per-project memory bank sheet: the user sees, adds, edits and
 * deletes what Saqr remembers; the agent maintains it through memory_save/memory_forget.
 * v0.17: tabs per layer (project · global · session summary), search, a type filter, stale
 * file-ref tags, and «make global» for a preference when the global layer is on.
 */
@Composable
fun MemorySheet(agent: AgentSession, globalEnabled: Boolean, onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(MemoryTab.PROJECT) }
    val bank = if (tab == MemoryTab.GLOBAL) agent.globalBank else agent.memoryBank
    val entries = remember { mutableStateListOf<MemoryBank.Entry>() }
    fun reload() { entries.clear(); if (tab != MemoryTab.SESSION) entries.addAll(bank.all) }
    remember(Unit) { runCatching { agent.refreshMemoryStale() }; true }
    remember(tab) { reload(); true }
    SaqrSheet(onDismiss) {
        MemorySheetContent(
            entries = entries,
            onAdd = { k, v, kind -> bank.save(k, v, kind, source = "user"); reload() },
            onUpdate = { old, k, v, kind ->
                bank.remove(old)
                bank.save(k, v, kind, if (kind == old.kind) old.type else null, "user", old.files)
                reload()
            },
            onDelete = { bank.remove(it); reload() },
            onClear = { bank.clear(); reload() },
            tab = tab,
            onTab = { tab = it },
            globalEnabled = globalEnabled,
            sessionSummary = agent.sessionSummary?.text,
            onClearSummary = { agent.clearSessionSummary() },
            onPromote = { e -> com.ctodrs.saqrcode.agent.memory.MemoryEngine.promote(e, agent.globalBank, globalEnabled) },
        )
    }
}

@Composable
fun MemorySheetContent(
    entries: List<MemoryBank.Entry>,
    onAdd: (String, String, MemoryBank.Kind) -> Unit,
    onUpdate: (MemoryBank.Entry, String, String, MemoryBank.Kind) -> Unit,
    onDelete: (MemoryBank.Entry) -> Unit,
    onClear: () -> Unit,
    tab: MemoryTab = MemoryTab.PROJECT,
    onTab: ((MemoryTab) -> Unit)? = null,
    globalEnabled: Boolean = false,
    sessionSummary: String? = null,
    onClearSummary: () -> Unit = {},
    onPromote: ((MemoryBank.Entry) -> String)? = null,
) {
    val c = Saqr.colors
    var query by remember { mutableStateOf("") }
    var typeFilter by remember(tab) { mutableStateOf<MemoryBank.Type?>(null) }
    var promoted by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryBank.Entry?>(null) }
    var confirmingClear by remember { mutableStateOf(false) }

    Row(Modifier.padding(bottom = Sp.md), verticalAlignment = Alignment.CenterVertically) {
        SIcon(Lucide.BookMarked, size = 20.dp, tint = c.accentText)
        Spacer(Modifier.width(Sp.sm))
        Text(stringResource(R.string.memory_title), style = Saqr.type.title2, color = c.text1)
        Spacer(Modifier.width(Sp.sm))
        Tag(entries.size.toString(), color = c.text2)
        Spacer(Modifier.weight(1f))
        val clearLabel = stringResource(R.string.memory_clear_all)
        val addLabel = stringResource(R.string.memory_add)
        Box(
            Modifier.size(48.dp).padding(7.dp).clip(RoundedCornerShape(Radius.sm)).background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(Radius.sm))
                .clickable(enabled = entries.isNotEmpty(), onClickLabel = clearLabel, role = androidx.compose.ui.semantics.Role.Button) { confirmingClear = true },
            contentAlignment = Alignment.Center,
        ) { SIcon(Lucide.Trash2, size = 16.dp, tint = if (entries.isEmpty()) c.text3 else c.danger, contentDescription = clearLabel) }
        Spacer(Modifier.width(Sp.sm))
        Box(
            Modifier.size(48.dp).padding(7.dp).clip(RoundedCornerShape(Radius.sm)).background(c.accent)
                .clickable(onClickLabel = addLabel, role = androidx.compose.ui.semantics.Role.Button) { adding = true },
            contentAlignment = Alignment.Center,
        ) { SIcon(Lucide.Plus, size = 18.dp, tint = c.onAccent, contentDescription = addLabel) }
    }
    Text(stringResource(R.string.memory_hint), style = Saqr.type.caption, color = c.text2)
    if (onTab != null) {
        Spacer(Modifier.height(Sp.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MemoryTab.entries.forEach { t ->
                Chip(stringResource(when (t) {
                    MemoryTab.PROJECT -> R.string.memory_layer_project
                    MemoryTab.GLOBAL -> R.string.memory_layer_global
                    MemoryTab.SESSION -> R.string.memory_layer_session
                }), t == tab) { onTab(t) }
            }
        }
    }
    Spacer(Modifier.height(Sp.md))
    if (tab == MemoryTab.SESSION) {
        Text(stringResource(R.string.memory_session_hint), style = Saqr.type.caption, color = c.text3)
        Spacer(Modifier.height(Sp.sm))
        if (sessionSummary.isNullOrBlank()) {
            Text(stringResource(R.string.memory_session_empty), style = Saqr.type.bodySmall, color = c.text2)
        } else {
            Text(sessionSummary, style = Saqr.type.bodySmall, color = c.text1, maxLines = 18, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Sp.sm))
            TextButton(onClick = onClearSummary) { Text(stringResource(R.string.memory_session_clear), color = c.danger) }
        }
        return
    }
    if (tab == MemoryTab.GLOBAL && !globalEnabled) {
        Text(stringResource(R.string.memory_global_off), style = Saqr.type.caption, color = c.warning)
        Spacer(Modifier.height(Sp.sm))
    }
    if (entries.size > 4) {
        SaqrField(query, { query = it }, label = stringResource(R.string.memory_search), placeholder = stringResource(R.string.memory_search_ph))
        Spacer(Modifier.height(Sp.sm))
    }
    val types = entries.map { it.type }.distinct()
    if (types.size > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(stringResource(R.string.memory_type_all), typeFilter == null) { typeFilter = null }
            types.take(5).forEach { t -> Chip(typeLabel(t), typeFilter == t) { typeFilter = if (typeFilter == t) null else t } }
        }
        Spacer(Modifier.height(Sp.sm))
    }
    promoted?.let { Text(it, style = Saqr.type.caption, color = c.text2); Spacer(Modifier.height(Sp.sm)) }
    val shown = remember(entries.toList(), query, typeFilter) { filterEntries(entries, query, typeFilter) }

    if (entries.isEmpty()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 180.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SIcon(Lucide.BookMarked, size = 30.dp, tint = c.text3)
                Spacer(Modifier.height(Sp.sm))
                Text(stringResource(R.string.memory_empty_title), style = Saqr.type.body, color = c.text1, fontWeight = FontWeight.Medium)
                Text(stringResource(R.string.memory_empty_body), style = Saqr.type.bodySmall, color = c.text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
            items(shown, key = { "${it.createdAt}-${it.key}" }) { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(Radius.md)).clickable { editing = e }
                        .padding(Sp.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tag(if (e.type == MemoryBank.Type.NOTE) kindLabel(e.kind) else typeLabel(e.type), color = kindColor(e.kind), dot = true)
                            if (e.stale) Tag(stringResource(R.string.memory_stale), color = c.warning)
                            Text(e.key, style = Saqr.type.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(e.flatValue, style = Saqr.type.bodySmall, color = c.text2, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        if (e.files.isNotEmpty()) {
                            Text(e.files.joinToString(" · "), style = Saqr.type.micro, color = c.text3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (tab == MemoryTab.PROJECT && globalEnabled && onPromote != null && e.kind == MemoryBank.Kind.PREFERENCE && e.files.isEmpty()) {
                            Text(stringResource(R.string.memory_make_global), style = Saqr.type.micro, color = c.accentText,
                                modifier = Modifier.clickable { promoted = onPromote(e) }.padding(vertical = 4.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            DateUtils.getRelativeTimeSpanString(e.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                            style = Saqr.type.micro, color = c.text3,
                        )
                    }
                    val deleteLabel = stringResource(R.string.delete)
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(Radius.sm))
                            .clickable(onClickLabel = deleteLabel, role = androidx.compose.ui.semantics.Role.Button) { onDelete(e) },
                        contentAlignment = Alignment.Center,
                    ) { SIcon(Lucide.Trash2, size = 18.dp, tint = c.text3, contentDescription = deleteLabel) }
                }
            }
        }
    }

    if (adding || editing != null) {
        var key by remember(editing, adding) { mutableStateOf(editing?.key ?: "") }
        var value by remember(editing, adding) { mutableStateOf(editing?.value ?: "") }
        var kind by remember(editing, adding) { mutableStateOf(editing?.kind ?: MemoryBank.Kind.FACT) }
        MemoryEditDialog(
            title = stringResource(if (editing != null) R.string.memory_edit else R.string.memory_add),
            key = key, onKey = { key = it },
            value = value, onValue = { value = it },
            kind = kind, onKind = { kind = it },
            onDismiss = { adding = false; editing = null },
            onSave = {
                val ed = editing
                if (ed != null) onUpdate(ed, key, value, kind) else onAdd(key, value, kind)
                adding = false; editing = null
            },
            valid = key.isNotBlank() && value.isNotBlank(),
        )
    }
    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false }, containerColor = c.surface1,
            icon = { SIcon(Lucide.Trash2, size = 24.dp, tint = c.danger) },
            title = { Text(stringResource(R.string.memory_clear), style = Saqr.type.title2) },
            text = { Text(stringResource(R.string.memory_clear_q), style = Saqr.type.body) },
            confirmButton = {
                TextButton(onClick = { onClear(); confirmingClear = false }) { Text(stringResource(R.string.delete), color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
    }
}

@Composable
private fun MemoryEditDialog(
    title: String,
    key: String, onKey: (String) -> Unit,
    value: String, onValue: (String) -> Unit,
    kind: MemoryBank.Kind, onKind: (MemoryBank.Kind) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    valid: Boolean,
) {
    val c = Saqr.colors
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface1,
        icon = { SIcon(Lucide.BookMarked, size = 24.dp, tint = c.accentText) },
        title = { Text(title, style = Saqr.type.title2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                SaqrField(key, onKey, label = stringResource(R.string.memory_key), placeholder = stringResource(R.string.memory_key_ph))
                SaqrField(value, onValue, label = stringResource(R.string.memory_value), placeholder = stringResource(R.string.memory_value_ph), singleLine = false)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MemoryBank.Kind.entries.forEach { k ->
                        val sel = k == kind
                        Box(
                            Modifier.clip(RoundedCornerShape(Radius.sm))
                                .background(if (sel) c.accentSoft else c.surface2)
                                .border(1.dp, if (sel) c.accent else c.border, RoundedCornerShape(Radius.sm))
                                .clickable { onKind(k) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) { Text(kindLabel(k), style = Saqr.type.micro, color = if (sel) c.accentText else c.text2) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = onSave) { Text(stringResource(R.string.save), color = if (valid) c.accentText else c.text3) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.text2) } },
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Saqr.colors
    Box(
        Modifier.clip(RoundedCornerShape(Radius.sm))
            .background(if (selected) c.accentSoft else c.surface2)
            .border(1.dp, if (selected) c.accent else c.border, RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(text, style = Saqr.type.micro, color = if (selected) c.accentText else c.text2) }
}

/** v0.17: search (relevance-ranked, then plain contains) and type filter for the sheet. */
internal fun filterEntries(entries: List<MemoryBank.Entry>, query: String, type: MemoryBank.Type?): List<MemoryBank.Entry> {
    val base = if (type == null) entries else entries.filter { it.type == type }
    if (query.isBlank()) return base
    val ranked = com.ctodrs.saqrcode.agent.memory.MemoryRecall.Index(base).rank(query).map { it.first }
    val q = MemoryBank.normalize(query)
    val contains = base.filter { q.isNotEmpty() && (MemoryBank.normalize(it.key).contains(q) || MemoryBank.normalize(it.value).contains(q)) }
    return (ranked + contains).distinct()
}

@Composable
fun typeLabel(type: MemoryBank.Type): String = stringResource(
    when (type) {
        MemoryBank.Type.NOTE -> R.string.memory_type_note
        MemoryBank.Type.PREFERENCE -> R.string.memory_kind_preference
        MemoryBank.Type.DECISION -> R.string.memory_kind_decision
        MemoryBank.Type.ARCHITECTURE -> R.string.memory_type_architecture
        MemoryBank.Type.DEPENDENCY -> R.string.memory_type_dependency
        MemoryBank.Type.CONVENTION -> R.string.memory_type_convention
        MemoryBank.Type.LESSON -> R.string.memory_type_lesson
    },
)

@Composable
fun kindLabel(kind: MemoryBank.Kind): String = stringResource(
    when (kind) {
        MemoryBank.Kind.PREFERENCE -> R.string.memory_kind_preference
        MemoryBank.Kind.DECISION -> R.string.memory_kind_decision
        MemoryBank.Kind.FACT -> R.string.memory_kind_fact
    },
)

@Composable
private fun kindColor(kind: MemoryBank.Kind) = when (kind) {
    MemoryBank.Kind.PREFERENCE -> Saqr.colors.accentText
    MemoryBank.Kind.DECISION -> Saqr.colors.success
    MemoryBank.Kind.FACT -> Saqr.colors.text2
}
