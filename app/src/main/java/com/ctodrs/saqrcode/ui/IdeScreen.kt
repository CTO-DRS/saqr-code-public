package com.ctodrs.saqrcode.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.Diff
import com.ctodrs.saqrcode.agent.FileChange
import com.ctodrs.saqrcode.agent.Providers
import com.ctodrs.saqrcode.agent.Snapshots

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeScreen(projectName: String, onBack: () -> Unit, onSettings: () -> Unit) {
    val ws = remember(projectName) { Workspace.get(projectName) }
    if (ws == null) { LaunchedEffect(Unit) { onBack() }; return }
    var menu by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }

    // Any agent activity may change files: refresh tree + clean editor tabs.
    LaunchedEffect(ws.agent.items.size, ws.agent.running) { ws.fsVersion++; ws.syncFromDisk() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(projectName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.restore_points)) }, onClick = { menu = false; showRestore = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.clear_chat)) }, onClick = { menu = false; ws.agent.clear() })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val tabs = listOf(
                    Triple(R.string.tab_agent, Icons.Filled.Face, 0),
                    Triple(R.string.tab_files, Icons.AutoMirrored.Filled.List, 1),
                    Triple(R.string.tab_terminal, Icons.Filled.Build, 2),
                    Triple(R.string.tab_git, Icons.Filled.Share, 3),
                )
                tabs.forEach { (label, icon, i) ->
                    NavigationBarItem(selected = ws.tab == i, onClick = { ws.tab = i },
                        icon = { Icon(icon, null) }, label = { Text(stringResource(label)) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).imePadding()) {
            when (ws.tab) {
                0 -> ChatPane(ws)
                1 -> FilesPane(ws)
                2 -> TerminalPane(ws)
                else -> GitPane(ws)
            }
        }
    }

    if (showRestore) RestoreDialog(ws, onDismiss = { showRestore = false })
}

@Composable
private fun ChatPane(ws: Workspace) {
    val agent = ws.agent
    val settings = SaqrApp.instance.settings
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val last = agent.items.lastOrNull()
    val lastLen = (last as? ChatItem.Assistant)?.text?.length ?: 0
    LaunchedEffect(agent.items.size, lastLen) {
        if (agent.items.isNotEmpty()) list.scrollToItem(agent.items.size - 1, Int.MAX_VALUE / 2)
    }
    Column(Modifier.fillMaxSize()) {
        val def = Providers.byId(settings.activeProvider)
        Text(
            "${def.displayName} · ${settings.config(def.id).model}",
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (agent.items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FalconLogo(72)
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(if (settings.activeReady()) R.string.agent_empty else R.string.no_provider),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(agent.items, key = { it.key }) { item -> ChatRow(item, onDecide = { a, ok -> agent.decide(a, ok) }) }
                if (agent.running) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.thinking), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f).heightIn(max = 160.dp),
                placeholder = { Text(stringResource(R.string.ask_agent_hint)) }, maxLines = 6)
            Spacer(Modifier.width(8.dp))
            if (agent.running) {
                FilledIconButton(onClick = { agent.stop() }) { Icon(Icons.Filled.Close, stringResource(R.string.stop)) }
            } else {
                FilledIconButton(enabled = input.isNotBlank() && settings.activeReady(), onClick = {
                    agent.send(input); input = ""
                }) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send)) }
            }
        }
    }
}

@Composable
private fun ChatRow(item: ChatItem, onDecide: (ChatItem.Approval, Boolean) -> Unit) {
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(color = Gold.copy(alpha = 0.18f), shape = RoundedCornerShape(14.dp)) {
                SelectionContainer { Text(item.text, Modifier.padding(12.dp)) }
            }
        }
        is ChatItem.Assistant -> MarkdownLite(item.text)
        is ChatItem.Tool -> ToolRow(item)
        is ChatItem.Approval -> ApprovalCard(item, onDecide)
        is ChatItem.Notice -> Text(item.text, style = MaterialTheme.typography.bodySmall,
            color = if (item.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Plain text with fenced code blocks rendered as LTR monospace boxes. */
@Composable
private fun MarkdownLite(text: String) {
    val parts = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEachIndexed { i, part ->
            if (i % 2 == 0) {
                if (part.isNotBlank()) SelectionContainer { Text(part.trim('\n')) }
            } else {
                val code = part.substringAfter('\n', part)
                CodeBox(code.trimEnd())
            }
        }
    }
}

@Composable
fun CodeBox(code: String, maxHeight: Int = 320) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(color = Color(0xFF0A0D12), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(code, Modifier.heightIn(max = maxHeight.dp).horizontalScroll(rememberScrollState()).padding(10.dp),
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun ToolRow(item: ChatItem.Tool) {
    var open by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable { open = !open }.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = when { item.result == null -> Gold; item.ok -> Teal; else -> MaterialTheme.colorScheme.error }
                Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(8.dp))
                Text(item.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(item.summary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (open) item.result?.let { Spacer(Modifier.size(6.dp)); CodeBox(it, 260) }
        }
    }
}

@Composable
private fun ApprovalCard(item: ChatItem.Approval, onDecide: (ChatItem.Approval, Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.plan_title, item.changes.size), fontWeight = FontWeight.Bold, color = Gold)
            item.changes.forEach { ChangeRow(it, startOpen = item.changes.size == 1) }
            when (item.decision) {
                null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDecide(item, true) }, colors = ButtonDefaults.buttonColors(containerColor = Teal)) {
                        Text(stringResource(R.string.approve))
                    }
                    OutlinedButton(onClick = { onDecide(item, false) }) { Text(stringResource(R.string.reject)) }
                }
                true -> Text("✓ " + stringResource(R.string.approved), color = Teal, style = MaterialTheme.typography.bodySmall)
                false -> Text("✗ " + stringResource(R.string.rejected), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ChangeRow(ch: FileChange, startOpen: Boolean) {
    var open by remember { mutableStateOf(startOpen) }
    val diff = remember(ch) { Diff.lines(ch.before ?: "", ch.after ?: "") }
    val (add, del) = remember(diff) { Diff.stats(diff) }
    Column {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val tag = when { ch.isDelete -> "D"; ch.isNew -> "A"; else -> "M" }
                Text(tag, fontFamily = FontFamily.Monospace, color = Gold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(ch.path, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("+$add", color = Color(0xFF3FB950), fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text("-$del", color = Color(0xFFF85149), fontSize = 12.sp)
            }
            if (open) DiffView(diff)
        }
    }
}

@Composable
private fun DiffView(diff: List<Diff.Line>) {
    val compact = remember(diff) { Diff.compact(diff).take(600) }
    Surface(color = Color(0xFF0A0D12), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.heightIn(max = 360.dp).horizontalScroll(rememberScrollState()).padding(6.dp)) {
            LazyColumn(Modifier.heightIn(max = 348.dp).width(900.dp)) {
                items(compact.size) { i ->
                    val l = compact[i]
                    if (l == null) Text("  ⋯", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    else {
                        val (prefix, bg) = when (l.kind) {
                            Diff.Kind.ADD -> "+ " to AddBg
                            Diff.Kind.DEL -> "- " to DelBg
                            Diff.Kind.SAME -> "  " to Color.Transparent
                        }
                        Text(prefix + l.text, Modifier.fillMaxWidth().background(bg), fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoreDialog(ws: Workspace, onDismiss: () -> Unit) {
    var points by remember { mutableStateOf(ws.agent.snapshots.list()) }
    var confirm by remember { mutableStateOf<Snapshots.Point?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_points)) },
        text = {
            if (points.isEmpty()) Text(stringResource(R.string.no_restore_points))
            else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(points, key = { it.id }) { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(DateUtils.getRelativeTimeSpanString(p.time).toString(), fontWeight = FontWeight.Medium)
                            Text(p.label, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { confirm = p }) { Icon(Icons.Filled.Refresh, stringResource(R.string.restore)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(stringResource(R.string.restore) + ": " + DateUtils.getRelativeTimeSpanString(p.time)) },
            confirmButton = {
                TextButton(onClick = {
                    ws.agent.snapshots.restore(p)
                    ws.agent.items += ChatItem.Notice("↺ " + p.label)
                    ws.fsVersion++; ws.syncFromDisk()
                    points = ws.agent.snapshots.list(); confirm = null
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
