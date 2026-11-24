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

/**
 * v0.13 بنك الذاكرة · The per-project memory bank sheet: the user sees, adds, edits and
 * deletes what Saqr remembers; the agent maintains it through memory_save/memory_forget.
 */
@Composable
fun MemorySheet(agent: AgentSession, onDismiss: () -> Unit) {
    val bank = agent.memoryBank
    val entries = remember { mutableStateListOf<MemoryBank.Entry>() }
    fun reload() { entries.clear(); entries.addAll(bank.all) }
    remember(Unit) { reload(); true }
    SaqrSheet(onDismiss) {
        MemorySheetContent(
            entries = entries,
            onAdd = { k, v, kind -> bank.save(k, v, kind); reload() },
            onUpdate = { old, k, v, kind ->
                bank.forget(old.key)
                bank.save(k, v, kind)
                reload()
            },
            onDelete = { bank.forget(it.key); reload() },
            onClear = { bank.clear(); reload() },
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
) {
    val c = Saqr.colors
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
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(Radius.sm)).background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(Radius.sm)).clickable(enabled = entries.isNotEmpty()) { confirmingClear = true },
            contentAlignment = Alignment.Center,
        ) { SIcon(Lucide.Trash2, size = 16.dp, tint = if (entries.isEmpty()) c.text3 else c.danger) }
        Spacer(Modifier.width(Sp.sm))
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(Radius.sm)).background(c.accent)
                .clickable { adding = true },
            contentAlignment = Alignment.Center,
        ) { SIcon(Lucide.Plus, size = 18.dp, tint = c.onAccent) }
    }
    Text(stringResource(R.string.memory_hint), style = Saqr.type.caption, color = c.text2)
    Spacer(Modifier.height(Sp.md))

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
            items(entries, key = { "${it.createdAt}-${it.key}" }) { e ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.surface2)
                        .border(1.dp, c.border, RoundedCornerShape(Radius.md)).clickable { editing = e }
                        .padding(Sp.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tag(kindLabel(e.kind), color = kindColor(e.kind), dot = true)
                            Text(e.key, style = Saqr.type.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(e.flatValue, style = Saqr.type.bodySmall, color = c.text2, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            DateUtils.getRelativeTimeSpanString(e.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                            style = Saqr.type.micro, color = c.text3,
                        )
                    }
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(Radius.sm)).clickable { onDelete(e) },
                        contentAlignment = Alignment.Center,
                    ) { SIcon(Lucide.Trash2, size = 15.dp, tint = c.text3) }
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
