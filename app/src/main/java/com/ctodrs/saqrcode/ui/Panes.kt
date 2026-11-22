package com.ctodrs.saqrcode.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.editor.CodePalette
import com.ctodrs.saqrcode.editor.HighlightTransformation
import com.ctodrs.saqrcode.editor.SyntaxHighlighter
import com.ctodrs.saqrcode.git.GitService
import com.ctodrs.saqrcode.terminal.LinuxEnv
import com.ctodrs.saqrcode.ui.chat.OutputBox
import com.ctodrs.saqrcode.ui.components.EmptyState
import com.ctodrs.saqrcode.ui.components.GhostButton
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.SaqrCard
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.components.SecondaryButton
import com.ctodrs.saqrcode.ui.components.Spinner
import com.ctodrs.saqrcode.ui.components.Tag
import com.ctodrs.saqrcode.ui.components.pressable
import com.ctodrs.saqrcode.ui.icons.FileTypeIcon
import com.ctodrs.saqrcode.ui.icons.FolderIcon
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.launch
import java.io.File

// ------------------------------------------------------------------ Files + editor

data class FsNode(val file: File, val rel: String, val depth: Int)

fun buildTree(root: File, expanded: Collection<String>): List<FsNode> {
    val out = ArrayList<FsNode>()
    fun walk(d: File, depth: Int) {
        (d.listFiles() ?: emptyArray()).filter { it.name != ".git" }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { f ->
                val rel = f.relativeTo(root).path
                out += FsNode(f, rel, depth)
                if (f.isDirectory && rel in expanded) walk(f, depth + 1)
            }
    }
    walk(root, 0)
    return out
}

@Composable
fun FilesPane(ws: Workspace) {
    var showTree by remember { mutableStateOf(ws.tabs.isEmpty()) }
    val expanded = remember { mutableStateListOf<String>() }
    var createDir by remember { mutableStateOf<Boolean?>(null) }
    var toDelete by remember { mutableStateOf<FsNode?>(null) }
    val nodes = remember(ws.fsVersion, expanded.toList()) { buildTree(ws.dir, expanded) }

    FilesPaneContent(
        nodes = nodes, expanded = expanded, tabs = ws.tabs, activeTab = ws.activeTab, showTree = showTree,
        onShowTree = { showTree = it },
        onActivate = { ws.activeTab = it; showTree = false },
        onClose = { ws.close(it) },
        onSave = { ws.save(it) },
        onOpen = { ws.open(it); showTree = false },
        onLongPress = { toDelete = it },
        onNewFile = { createDir = false }, onNewFolder = { createDir = true }, onRefresh = { ws.fsVersion++ },
    )

    val c = Saqr.colors
    createDir?.let { isDir ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createDir = null }, containerColor = c.surface1,
            icon = { SIcon(if (isDir) Lucide.FolderPlus else Lucide.FilePlus, size = 24.dp, tint = c.accentText) },
            title = { Text(stringResource(if (isDir) R.string.new_folder else R.string.new_file), style = Saqr.type.title2) },
            text = { SaqrField(name, { name = it }, label = stringResource(R.string.file_name), placeholder = "src/main.js", mono = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    runCatching {
                        val f = com.ctodrs.saqrcode.agent.Tools.resolve(ws.dir, name)
                        if (isDir) f.mkdirs() else { f.parentFile?.mkdirs(); if (!f.exists()) f.writeText(""); ws.open(f.relativeTo(ws.dir).path); showTree = false }
                    }
                    ws.fsVersion++; createDir = null
                }) { Text(stringResource(R.string.create), color = c.accentText) }
            },
            dismissButton = { TextButton(onClick = { createDir = null }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
    }
    toDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, containerColor = c.surface1,
            icon = { SIcon(Lucide.Trash2, size = 24.dp, tint = c.danger) },
            text = { Text(stringResource(R.string.delete_q, n.rel), style = Saqr.type.body) },
            confirmButton = {
                TextButton(onClick = {
                    n.file.deleteRecursively()
                    ws.tabs.indexOfFirst { it.path == n.rel }.takeIf { it >= 0 }?.let { ws.close(it) }
                    ws.fsVersion++; toDelete = null
                }) { Text(stringResource(R.string.delete), color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesPaneContent(
    nodes: List<FsNode>, expanded: SnapshotStateList<String>, tabs: List<OpenFile>, activeTab: Int, showTree: Boolean,
    onShowTree: (Boolean) -> Unit, onActivate: (Int) -> Unit, onClose: (Int) -> Unit, onSave: (OpenFile) -> Unit,
    onOpen: (String) -> Unit, onLongPress: (FsNode) -> Unit, onNewFile: () -> Unit, onNewFolder: () -> Unit, onRefresh: () -> Unit,
) {
    val c = Saqr.colors
    Column(Modifier.fillMaxSize()) {
        // ---- tab strip
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Sp.md, vertical = Sp.xs), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).pressable { onShowTree(!showTree) }.clip(RoundedCornerShape(Radius.sm))
                        .background(if (showTree) c.accentSoft else c.surface2).border(1.dp, if (showTree) c.accent.copy(alpha = 0.4f) else c.border, RoundedCornerShape(Radius.sm)),
                    contentAlignment = Alignment.Center,
                ) { SIcon(Lucide.FolderOpen, size = 18.dp, tint = if (showTree) c.accentText else c.text1) }
                Spacer(Modifier.width(Sp.sm))
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tabs.forEachIndexed { i, t ->
                        val sel = i == activeTab && !showTree
                        Row(
                            Modifier.height(38.dp).clip(RoundedCornerShape(Radius.sm))
                                .background(if (sel) c.surface2 else Color.Transparent)
                                .border(1.dp, if (sel) c.borderStrong else c.border.copy(alpha = 0.5f), RoundedCornerShape(Radius.sm))
                                .clickable { onActivate(i) }.padding(start = 10.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FileTypeIcon(t.path.substringAfterLast('/'), 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(t.path.substringAfterLast('/'), style = Saqr.type.monoSmall, color = if (sel) c.text1 else c.text2, maxLines = 1)
                            Box(Modifier.size(26.dp).clip(CircleShape).clickable { onClose(i) }, contentAlignment = Alignment.Center) {
                                if (t.dirty) Box(Modifier.size(7.dp).clip(CircleShape).background(c.accent))
                                else SIcon(Lucide.X, size = 13.dp, tint = c.text3, contentDescription = stringResource(R.string.close))
                            }
                        }
                    }
                }
                val cur = tabs.getOrNull(activeTab)
                if (cur != null && !showTree) {
                    Spacer(Modifier.width(Sp.sm))
                    Box(
                        Modifier.size(38.dp).pressable(cur.dirty) { onSave(cur) }.clip(RoundedCornerShape(Radius.sm))
                            .background(if (cur.dirty) c.accent else c.surface2).border(1.dp, if (cur.dirty) c.accent else c.border, RoundedCornerShape(Radius.sm)),
                        contentAlignment = Alignment.Center,
                    ) { SIcon(Lucide.Save, size = 17.dp, tint = if (cur.dirty) c.onAccent else c.text3, contentDescription = stringResource(R.string.save)) }
                }
            }
        }

        if (showTree) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Sp.sm), verticalAlignment = Alignment.CenterVertically) {
                GhostButton(stringResource(R.string.new_file), onNewFile, icon = Lucide.FilePlus)
                GhostButton(stringResource(R.string.new_folder), onNewFolder, icon = Lucide.FolderPlus)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onRefresh), contentAlignment = Alignment.Center) {
                    SIcon(Lucide.RefreshCw, size = 17.dp)
                }
            }
            if (nodes.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.empty_project_title), stringResource(R.string.empty_project_body), icon = Lucide.FolderOpen)
            } else CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = Sp.sm)) {
                    items(nodes, key = { it.rel }) { n ->
                        val isDir = n.file.isDirectory
                        val open = n.rel in expanded
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(Radius.sm)).combinedClickable(
                                onClick = { if (isDir) { if (open) expanded.remove(n.rel) else expanded.add(n.rel) } else onOpen(n.rel) },
                                onLongClick = { onLongPress(n) },
                            ).padding(start = (8 + n.depth * 18).dp, end = Sp.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // indent guides
                            Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) {
                                if (isDir) SIcon(if (open) Lucide.ChevronDown else Lucide.ChevronRight, size = 15.dp, tint = c.text3)
                            }
                            Spacer(Modifier.width(4.dp))
                            if (isDir) FolderIcon(open, 19.dp) else FileTypeIcon(n.file.name, 19.dp)
                            Spacer(Modifier.width(Sp.sm + 2.dp))
                            Text(n.file.name, style = Saqr.type.monoSmall.copy(fontSize = Saqr.type.mono.fontSize),
                                color = if (isDir) c.text1 else c.text1.copy(alpha = 0.9f),
                                fontWeight = if (isDir) FontWeight.Medium else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            if (!isDir) Text(sizeLabel(n.file.length()), style = Saqr.type.monoMicro, color = c.text3)
                        }
                    }
                }
            }
        } else {
            val cur = tabs.getOrNull(activeTab)
            if (cur == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.no_open_file_title), stringResource(R.string.open_file_hint), icon = Lucide.FileCode,
                    action = { SecondaryButton(stringResource(R.string.browse_files), { onShowTree(true) }, icon = Lucide.FolderOpen) })
            } else CodeEditor(cur)
        }
    }
}

private fun sizeLabel(b: Long) = when {
    b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
    b >= 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "$b B"
}

@Composable
fun CodeEditor(f: OpenFile) {
    val c = Saqr.colors
    val palette = if (c.isDark) CodePalette.Dark else CodePalette.Light
    val lang = remember(f.path) { SyntaxHighlighter.languageOf(f.path) }
    val transformation = remember(lang, palette) { HighlightTransformation(lang, palette) }
    val style = Saqr.type.mono.copy(color = c.text1, lineHeight = Saqr.type.mono.lineHeight)
    val lines = remember(f.value.text) { f.value.text.count { it == '\n' } + 1 }
    val curLine = remember(f.value.selection.start, f.value.text) { f.value.text.take(f.value.selection.start.coerceAtMost(f.value.text.length)).count { it == '\n' } + 1 }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = Sp.md).height(1.dp).background(c.border))
            Row(Modifier.weight(1f).fillMaxWidth().background(c.codeBg).verticalScroll(rememberScrollState())) {
                Text(
                    buildAnnotatedString {
                        for (i in 1..lines) {
                            withStyle(SpanStyle(color = if (i == curLine) c.accentText else c.text3.copy(alpha = 0.7f))) { append(i.toString()) }
                            if (i < lines) append('\n')
                        }
                    },
                    Modifier.padding(start = Sp.md, end = Sp.sm, top = Sp.md, bottom = Sp.md).widthIn(min = 26.dp),
                    style = style.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End),
                )
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
                Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    BasicTextField(
                        value = f.value, onValueChange = { f.value = it }, textStyle = style, cursorBrush = SolidColor(c.accent),
                        visualTransformation = transformation,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                        modifier = Modifier.padding(horizontal = Sp.md, vertical = Sp.md).widthIn(min = 600.dp),
                    )
                }
            }
            // status bar
            Row(Modifier.fillMaxWidth().background(c.surface1).padding(horizontal = Sp.lg, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(lang.uppercase(), style = Saqr.type.monoMicro, color = c.accentText)
                Spacer(Modifier.width(Sp.md))
                Text("Ln $curLine · $lines lines", style = Saqr.type.monoMicro, color = c.text3)
                Spacer(Modifier.weight(1f))
                Text(if (f.dirty) "● " + stringResource(R.string.unsaved) else "UTF-8", style = Saqr.type.micro, color = if (f.dirty) c.accentText else c.text3)
            }
        }
    }
}

// ------------------------------------------------------------------ Terminal

@Composable
fun TerminalPane(ws: Workspace) {
    val linux = SaqrApp.instance.linux
    val state by linux.state.collectAsState()
    val progress by linux.progress.collectAsState()
    val scope = rememberCoroutineScope()
    val shell = ws.shell
    LaunchedEffect(Unit) { shell.start() }
    val output by shell.output.collectAsState()
    TerminalContent(
        output = output, linuxReady = state == LinuxEnv.State.READY && linux.prootAvailable,
        installing = state == LinuxEnv.State.INSTALLING, error = state == LinuxEnv.State.ERROR, progress = progress,
        onInstall = { scope.launch { if (linux.install().isSuccess) shell.restart() } },
        onSend = { if (it.trim() == "clear") shell.clear() else shell.send(it) }, onRestart = { shell.restart() },
    )
}

@Composable
fun TerminalContent(
    output: String, linuxReady: Boolean, installing: Boolean, error: Boolean, progress: String,
    onInstall: () -> Unit, onSend: (String) -> Unit, onRestart: () -> Unit,
) {
    val c = Saqr.colors
    var cmd by remember { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }
    var hIdx by remember { mutableIntStateOf(-1) }
    val scroll = rememberScrollState()
    LaunchedEffect(output.length) { scroll.scrollTo(scroll.maxValue) }
    fun submit(s: String = cmd) { if (s.isNotBlank()) { onSend(s); history.add(0, s); hIdx = -1; cmd = "" } }

    Column(Modifier.fillMaxSize().padding(horizontal = Sp.md).padding(bottom = Sp.sm)) {
        if (!linuxReady) {
            SaqrCard(Modifier.fillMaxWidth().padding(bottom = Sp.sm), padding = androidx.compose.foundation.layout.PaddingValues(Sp.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(c.info.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        SIcon(Lucide.Box, size = 19.dp, tint = c.info)
                    }
                    Spacer(Modifier.width(Sp.md))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.terminal_setup), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                        Text(if (progress.isNotEmpty()) progress else stringResource(R.string.terminal_fallback), style = Saqr.type.caption,
                            color = if (error) c.danger else c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(Sp.sm))
                    if (installing) Spinner(22.dp) else PrimaryButton(stringResource(R.string.install), onInstall, icon = Lucide.Zap)
                }
            }
        }
        val shape = RoundedCornerShape(Radius.lg)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.weight(1f).fillMaxWidth().clip(shape).background(c.termBg).border(1.dp, if (c.isDark) c.border else Color(0xFF23272E), shape)) {
                // window chrome
                Row(Modifier.fillMaxWidth().background(Color(0xFF101216)).padding(horizontal = Sp.md, vertical = Sp.sm), verticalAlignment = Alignment.CenterVertically) {
                    listOf(Color(0xFFF2555A), Color(0xFFF0A93B), Color(0xFF46C98B)).forEach {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(it.copy(alpha = 0.85f))); Spacer(Modifier.width(6.dp))
                    }
                    Spacer(Modifier.width(Sp.sm))
                    Text(if (linuxReady) "alpine · /workspace" else "sh · android", style = Saqr.type.monoMicro, color = Color(0xFF8A919B), modifier = Modifier.weight(1f))
                    Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onRestart), contentAlignment = Alignment.Center) {
                        SIcon(Lucide.RotateCcw, size = 15.dp, tint = Color(0xFF8A919B), contentDescription = stringResource(R.string.restart_shell))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SelectionContainer {
                        Text(colorizeTerminal(output, c.accent), Modifier.fillMaxSize().verticalScroll(scroll).padding(Sp.md),
                            style = Saqr.type.monoSmall.copy(color = c.termText))
                    }
                }
                // quick keys
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Sp.sm, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickKey("↑") { if (history.isNotEmpty()) { hIdx = (hIdx + 1).coerceAtMost(history.lastIndex); cmd = history[hIdx] } }
                    QuickKey("↓") { if (hIdx > 0) { hIdx--; cmd = history[hIdx] } else { hIdx = -1; cmd = "" } }
                    listOf("ls -la", "pwd", "clear", "apk update", "git status").forEach { q -> QuickKey(q) { submit(q) } }
                }
                Row(Modifier.fillMaxWidth().background(Color(0xFF0C0E11)).padding(start = Sp.md, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("❯", style = Saqr.type.mono, color = c.accent)
                    Spacer(Modifier.width(Sp.sm))
                    BasicTextField(
                        cmd, { cmd = it }, Modifier.weight(1f), singleLine = true,
                        textStyle = Saqr.type.mono.copy(color = Color(0xFFEDEEF0)), cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        decorationBox = { inner -> Box { if (cmd.isEmpty()) Text(stringResource(R.string.command_hint), style = Saqr.type.bodySmall, color = Color(0xFF59606A)); inner() } },
                    )
                    Box(Modifier.size(36.dp).pressable { submit() }.clip(CircleShape).background(if (cmd.isNotBlank()) c.accent else Color(0xFF1B1F25)),
                        contentAlignment = Alignment.Center) {
                        SIcon(Lucide.Play, size = 15.dp, tint = if (cmd.isNotBlank()) c.onAccent else Color(0xFF6D737D), contentDescription = stringResource(R.string.run))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickKey(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF15181D)).border(1.dp, Color(0xFF262A31), RoundedCornerShape(8.dp))
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(label, style = Saqr.type.monoMicro, color = Color(0xFFB5BCC6))
    }
}

/** Highlights "$ cmd" echo lines and [exit n] markers in the plain output. */
private fun colorizeTerminal(out: String, accent: Color) = buildAnnotatedString {
    out.split('\n').forEachIndexed { i, line ->
        if (i > 0) append('\n')
        when {
            line.startsWith("$ ") -> { withStyle(SpanStyle(color = accent)) { append("❯ ") }; withStyle(SpanStyle(color = Color(0xFFFFFFFF), fontWeight = FontWeight.Medium)) { append(line.drop(2)) } }
            line.startsWith("[process exited]") || line.contains("not found") || line.startsWith("Failed") -> withStyle(SpanStyle(color = Color(0xFFF4736B))) { append(line) }
            line.startsWith("Alpine Linux") || line.startsWith("Android sh") -> withStyle(SpanStyle(color = Color(0xFF6CCBB6))) { append(line) }
            else -> append(line)
        }
    }
}

// ------------------------------------------------------------------ Git

@Composable
fun GitPane(ws: Workspace) {
    val app = SaqrApp.instance
    val settings = app.settings
    val git = remember { GitService(app.filesDir) }
    val scope = rememberCoroutineScope()
    val c = Saqr.colors
    var repo by remember { mutableStateOf(listOf(settings.githubUser, ws.name).filter { it.isNotBlank() }.joinToString("/")) }
    var message by remember { mutableStateOf("Update from Saqr Code") }
    var create by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    val needToken = stringResource(R.string.git_need_token)

    LaunchedEffect(ws.fsVersion) { if (!busy) log = runCatching { git.status(ws.dir) }.getOrElse { it.message ?: "" } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sp.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
        SaqrCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                        SIcon(Lucide.Github, size = 19.dp, tint = c.accentText)
                    }
                    Spacer(Modifier.width(Sp.md))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.git_title), style = Saqr.type.title3.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                        Text(stringResource(R.string.git_subtitle), style = Saqr.type.caption, color = c.text2)
                    }
                    Tag(if (settings.githubToken != null) stringResource(R.string.connected) else stringResource(R.string.not_connected),
                        color = if (settings.githubToken != null) c.success else c.text3, dot = true)
                }
                SaqrField(repo, { repo = it }, label = stringResource(R.string.git_remote), placeholder = "owner/repo", mono = true)
                SaqrField(message, { message = it }, label = stringResource(R.string.git_commit_msg), singleLine = false)
                Row(Modifier.clip(RoundedCornerShape(Radius.sm)).clickable { create = !create }.padding(end = Sp.sm), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(create, { create = it }, colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent, uncheckedColor = c.borderStrong))
                    Text(stringResource(R.string.git_create_repo), style = Saqr.type.bodySmall, color = c.text1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm), verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton(stringResource(R.string.git_push), enabled = !busy && repo.contains('/'), icon = Lucide.Upload, modifier = Modifier.weight(1f), onClick = {
                        val token = settings.githubToken
                        if (token == null) { log = needToken; return@PrimaryButton }
                        busy = true
                        scope.launch {
                            log = runCatching {
                                val (owner, name) = repo.trim().split('/').let { it[0] to it[1] }
                                val pre = if (create) git.ensureRepo(owner, name, token) + "\n" else ""
                                pre + git.commitAndPush(ws.dir, repo, settings.githubUser, token, message)
                            }.getOrElse { "✗ " + (it.message ?: it.toString()) }
                            busy = false
                        }
                    })
                    SecondaryButton(stringResource(R.string.git_status), enabled = !busy, icon = Lucide.RefreshCw, onClick = {
                        scope.launch { log = runCatching { git.status(ws.dir) }.getOrElse { it.message ?: "" } }
                    })
                    if (busy) Spinner(20.dp)
                }
            }
        }
        if (log.isNotBlank()) OutputBox(log, maxHeight = 420)
    }
}

// ------------------------------------------------------------------ Preview

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewPane(ws: Workspace) {
    val entry = remember(ws.fsVersion) {
        File(ws.dir, "index.html").takeIf { it.isFile }
            ?: ws.dir.walkTopDown().maxDepth(3).onEnter { it.name !in setOf("node_modules", ".git") }.firstOrNull { it.isFile && it.extension in setOf("html", "htm") }
    }
    var reload by remember { mutableIntStateOf(0) }
    val c = Saqr.colors
    if (entry == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(stringResource(R.string.preview_empty_title), stringResource(R.string.preview_empty_body), icon = Lucide.Globe)
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = Sp.md).padding(bottom = Sp.sm).navigationBarsPadding()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().padding(bottom = Sp.sm).clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape)
                .padding(start = Sp.md, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SIcon(Lucide.Globe, size = 15.dp, tint = c.text3)
                Spacer(Modifier.width(Sp.sm))
                Text(entry.relativeTo(ws.dir).path, style = Saqr.type.monoSmall, color = c.text2, modifier = Modifier.weight(1f), maxLines = 1)
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { reload++ }, contentAlignment = Alignment.Center) { SIcon(Lucide.RefreshCw, size = 15.dp) }
            }
        }
        val shape = RoundedCornerShape(Radius.lg)
        Box(Modifier.weight(1f).fillMaxWidth().clip(shape).border(1.dp, c.border, shape).background(Color.White)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                    }
                },
                update = { wv -> wv.tag.let { if (it != reload) { wv.tag = reload; wv.loadUrl("file://" + entry.absolutePath) } } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
