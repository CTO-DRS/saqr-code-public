package com.ctodrs.saqrcode.ui.chat

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.AgentSession
import com.ctodrs.saqrcode.agent.SessionMeta
import com.ctodrs.saqrcode.agent.SessionStore
import com.ctodrs.saqrcode.ui.components.latinDigits
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.delay

/** Sessions sheet wired to an [AgentSession] (search runs on disk, debounced). */
@Composable
fun SessionsSheet(agent: AgentSession, onOpened: () -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SessionMeta>?>(null) }
    LaunchedEffect(Unit) { agent.saveNow(); agent.refreshSessions() }
    LaunchedEffect(query, agent.sessions.size) {
        if (query.isBlank()) { results = null; return@LaunchedEffect }
        delay(250)
        results = agent.searchSessions(query)
    }
    // the current chat may not be saved yet: show it on top so it is never missing
    val list = (results ?: agent.sessions.toList()).let { l ->
        if (query.isBlank() && agent.items.isNotEmpty() && l.none { it.id == agent.sessionId })
            listOf(SessionMeta(agent.sessionId, agent.sessionTitle, true, System.currentTimeMillis(), System.currentTimeMillis(), agent.items.size)) + l
        else l
    }
    SaqrSheet(onDismiss) {
        SessionsSheetContent(
            sessions = list, currentId = agent.sessionId, query = query, onQuery = { query = it },
            storageBytes = agent.storageBytes, running = agent.running,
            onOpen = { if (agent.openSession(it.id)) { onOpened(); onDismiss() } },
            onNew = { agent.newSession(); onOpened(); onDismiss() },
            onRename = { m, t -> agent.renameSession(m.id, t) },
            onDelete = { agent.deleteSession(it.id) },
        )
    }
}

/** Stateless sessions list (also used by the screenshot tests). */
@Composable
fun SessionsSheetContent(
    sessions: List<SessionMeta>,
    currentId: String,
    query: String,
    onQuery: (String) -> Unit,
    storageBytes: Long,
    running: Boolean,
    onOpen: (SessionMeta) -> Unit,
    onNew: () -> Unit,
    onRename: (SessionMeta, String) -> Unit,
    onDelete: (SessionMeta) -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    val c = Saqr.colors
    var renaming by remember { mutableStateOf<SessionMeta?>(null) }
    var deleting by remember { mutableStateOf<SessionMeta?>(null) }
    Row(Modifier.padding(bottom = Sp.md), verticalAlignment = Alignment.CenterVertically) {
        SIcon(Lucide.MessagesSquare, size = 20.dp, tint = c.accentText)
        Spacer(Modifier.width(Sp.sm))
        Text(stringResource(R.string.sessions), style = Saqr.type.title2, color = c.text1, modifier = Modifier.weight(1f))
        Box(Modifier.clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape).padding(horizontal = 10.dp, vertical = 2.dp)) {
            Text(stringResource(R.string.sessions_count, sessions.size.toString()), style = Saqr.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.text2)
        }
    }
    // new session
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.accentSoft)
            .border(1.dp, c.accent.copy(alpha = 0.35f), RoundedCornerShape(Radius.md))
            .clickable(enabled = !running, onClick = onNew).padding(horizontal = Sp.md, vertical = Sp.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(Lucide.MessageSquarePlus, size = 19.dp, tint = c.accentText)
        Spacer(Modifier.width(Sp.md))
        Text(stringResource(R.string.new_session), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
    }
    Spacer(Modifier.height(Sp.sm))
    // search
    val shape = RoundedCornerShape(Radius.md)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.surface2).border(1.dp, c.border, shape).padding(horizontal = Sp.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(Lucide.Search, size = 17.dp, tint = c.text3)
        Spacer(Modifier.width(Sp.sm))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(stringResource(R.string.sessions_search), style = Saqr.type.body, color = c.text3, maxLines = 1)
            BasicTextField(query, onQuery, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = Saqr.type.body.copy(color = c.text1, textDirection = TextDirection.Content), cursorBrush = SolidColor(c.accent))
        }
        if (query.isNotEmpty()) Box(Modifier.minimumInteractiveComponentSize().size(24.dp).clip(CircleShape).clickable(role = Role.Button) { onQuery("") }, contentAlignment = Alignment.Center) {
            SIcon(Lucide.X, size = 15.dp, tint = c.text3, contentDescription = stringResource(R.string.cancel))
        }
    }
    if (storageBytes > SessionStore.WARN_BYTES) {
        Spacer(Modifier.height(Sp.sm))
        Row(
            Modifier.fillMaxWidth().clip(shape).background(c.warningBg).border(1.dp, c.warningBorder, shape).padding(Sp.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SIcon(Lucide.TriangleAlert, size = 16.dp, tint = c.warning)
            Spacer(Modifier.width(Sp.sm))
            Text(stringResource(R.string.sessions_storage_warn, ltr(formatBytes(storageBytes))), style = Saqr.type.caption, color = c.text1)
        }
    }
    Spacer(Modifier.height(Sp.sm))
    if (sessions.isEmpty()) {
        Text(stringResource(if (query.isBlank()) R.string.sessions_empty else R.string.sessions_no_match), style = Saqr.type.bodySmall, color = c.text2,
            modifier = Modifier.padding(vertical = Sp.lg))
    } else {
        LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
            items(sessions, key = { it.id }) { m ->
                SessionRow(m, current = m.id == currentId, enabled = !running || m.id == currentId, now = now,
                    onOpen = { onOpen(m) }, onRename = { renaming = m }, onDelete = { deleting = m })
            }
        }
    }
    Spacer(Modifier.height(Sp.sm))
    Text(stringResource(R.string.sessions_note, ltr(formatBytes(storageBytes)), SessionStore.MAX_SESSIONS.toString()), style = Saqr.type.caption, color = c.text3)

    renaming?.let { m ->
        var text by remember(m.id) { mutableStateOf(m.title) }
        AlertDialog(
            onDismissRequest = { renaming = null }, containerColor = c.surface1,
            title = { Text(stringResource(R.string.rename_session), style = Saqr.type.title2) },
            text = {
                Box(Modifier.fillMaxWidth().clip(shape).background(c.surface2).border(1.dp, c.accent.copy(alpha = 0.5f), shape).padding(Sp.md)) {
                    BasicTextField(text, { text = it.take(120) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = Saqr.type.body.copy(color = c.text1, textDirection = TextDirection.Content), cursorBrush = SolidColor(c.accent))
                }
            },
            confirmButton = { TextButton(onClick = { onRename(m, text); renaming = null }) { Text(stringResource(R.string.save), color = c.accentText) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
    }
    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null }, containerColor = c.surface1,
            icon = { SIcon(Lucide.Trash2, size = 22.dp, tint = c.danger) },
            title = { Text(stringResource(R.string.delete_session_title), style = Saqr.type.title2) },
            text = { Text(stringResource(R.string.delete_session_body, m.title.ifBlank { stringResource(R.string.untitled_session) }), style = Saqr.type.body) },
            confirmButton = { TextButton(onClick = { onDelete(m); deleting = null }) { Text(stringResource(R.string.delete), color = c.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
    }
}

@Composable
private fun SessionRow(m: SessionMeta, current: Boolean, enabled: Boolean, now: Long, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = Saqr.colors
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (current) c.surface3 else c.surface2)
            .then(if (current) Modifier.border(1.dp, c.accent.copy(alpha = 0.45f), shape) else Modifier)
            .clickable(enabled = enabled, onClick = onOpen).padding(start = Sp.md, top = Sp.sm, bottom = Sp.sm, end = Sp.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (current) c.accent else c.border))
        Spacer(Modifier.width(Sp.md))
        Column(Modifier.weight(1f)) {
            Text(m.title.ifBlank { stringResource(R.string.untitled_session) }, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content),
                color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.preview.isNotBlank()) Text(m.preview, style = Saqr.type.caption.copy(textDirection = TextDirection.Content), color = c.text2,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(DateUtils.getRelativeTimeSpanString(m.updatedAt, now, DateUtils.MINUTE_IN_MILLIS).toString().latinDigits() + " · " +
                stringResource(R.string.session_messages, m.messages.toString()) + if (current) " · " + stringResource(R.string.session_current) else "",
                style = Saqr.type.caption, color = if (current) c.accentText else c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            Box(Modifier.minimumInteractiveComponentSize().size(40.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }, contentAlignment = Alignment.Center) {
                SIcon(Lucide.EllipsisVertical, size = 18.dp, tint = c.text2, contentDescription = stringResource(R.string.more))
            }
            DropdownMenu(menu, { menu = false }, Modifier.background(c.surface2)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename_session), style = Saqr.type.label, color = c.text1) },
                    leadingIcon = { SIcon(Lucide.Pencil, size = 16.dp, tint = c.text2) },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), style = Saqr.type.label, color = c.danger) },
                    leadingIcon = { SIcon(Lucide.Trash2, size = 16.dp, tint = c.danger) },
                    enabled = enabled, onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}
