package com.ctodrs.saqrcode.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.editor.HighlightTransformation
import com.ctodrs.saqrcode.editor.SyntaxHighlighter
import com.ctodrs.saqrcode.git.GitService
import com.ctodrs.saqrcode.terminal.LinuxEnv
import kotlinx.coroutines.launch
import java.io.File

// ------------------------------------------------------------------ Files + editor

private data class Node(val file: File, val rel: String, val depth: Int)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesPane(ws: Workspace) {
    var showTree by remember { mutableStateOf(ws.tabs.isEmpty()) }
    val expanded = remember { mutableStateListOf<String>() }
    var createDir by remember { mutableStateOf<Boolean?>(null) }
    var toDelete by remember { mutableStateOf<Node?>(null) }

    val nodes = remember(ws.fsVersion, expanded.toList()) {
        val out = ArrayList<Node>()
        fun walk(d: File, depth: Int) {
            (d.listFiles() ?: emptyArray()).filter { it.name != ".git" }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { f ->
                    val rel = f.relativeTo(ws.dir).path
                    out += Node(f, rel, depth)
                    if (f.isDirectory && rel in expanded) walk(f, depth + 1)
                }
        }
        walk(ws.dir, 0); out
    }

    Column(Modifier.fillMaxSize()) {
        // tab strip
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showTree = !showTree }) { Icon(Icons.AutoMirrored.Filled.List, null, tint = if (showTree) Gold else Color.Unspecified) }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                ws.tabs.forEachIndexed { i, t ->
                    val sel = i == ws.activeTab && !showTree
                    Row(
                        Modifier.clickable { ws.activeTab = i; showTree = false }
                            .background(if (sel) MaterialTheme.colorScheme.background else Color.Transparent)
                            .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text((if (t.dirty) "● " else "") + t.path.substringAfterLast('/'), fontSize = 13.sp,
                            color = if (sel) Gold else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        IconButton(onClick = { ws.close(i) }, Modifier.size(28.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.close), Modifier.size(14.dp)) }
                    }
                }
            }
            val cur = ws.tabs.getOrNull(ws.activeTab)
            if (cur != null && !showTree) IconButton(enabled = cur.dirty, onClick = { ws.save(cur) }) {
                Icon(Icons.Filled.Done, stringResource(R.string.save), tint = if (cur.dirty) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (showTree) {
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { createDir = false }) { Icon(Icons.Filled.Create, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.new_file)) }
                TextButton(onClick = { createDir = true }) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.new_folder)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { ws.fsVersion++ }) { Icon(Icons.Filled.Refresh, null) }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(nodes, key = { it.rel }) { n ->
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = {
                                    if (n.file.isDirectory) { if (n.rel in expanded) expanded.remove(n.rel) else expanded.add(n.rel) }
                                    else { ws.open(n.rel); showTree = false }
                                },
                                onLongClick = { toDelete = n },
                            ).padding(start = (12 + n.depth * 16).dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (n.file.isDirectory) Icon(
                                if (n.rel in expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                null, Modifier.size(18.dp), tint = Gold)
                            else Spacer(Modifier.width(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(n.file.name, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                                fontWeight = if (n.file.isDirectory) FontWeight.Medium else FontWeight.Normal)
                        }
                    }
                }
            }
        } else {
            val cur = ws.tabs.getOrNull(ws.activeTab)
            if (cur == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.open_file_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else CodeEditor(cur)
        }
    }

    createDir?.let { isDir ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createDir = null },
            title = { Text(stringResource(if (isDir) R.string.new_folder else R.string.new_file)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.file_name)) }, placeholder = { Text("src/main.js") }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    runCatching {
                        val f = com.ctodrs.saqrcode.agent.Tools.resolve(ws.dir, name)
                        if (isDir) f.mkdirs() else { f.parentFile?.mkdirs(); if (!f.exists()) f.writeText(""); ws.open(f.relativeTo(ws.dir).path); showTree = false }
                    }
                    ws.fsVersion++; createDir = null
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { createDir = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    toDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            text = { Text(stringResource(R.string.delete) + ": " + n.rel + " ?") },
            confirmButton = {
                TextButton(onClick = {
                    n.file.deleteRecursively()
                    ws.tabs.indexOfFirst { it.path == n.rel }.takeIf { it >= 0 }?.let { ws.close(it) }
                    ws.fsVersion++; toDelete = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun CodeEditor(f: OpenFile) {
    val lang = remember(f.path) { SyntaxHighlighter.languageOf(f.path) }
    val transformation = remember(lang) { HighlightTransformation(lang) }
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFFE6E6E6))
    val lines = remember(f.value.text) { f.value.text.count { it == '\n' } + 1 }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val vScroll = rememberScrollState()
        Row(Modifier.fillMaxSize().background(Color(0xFF0A0D12)).verticalScroll(vScroll)) {
            Text(
                (1..lines).joinToString("\n"),
                Modifier.background(Color(0xFF0E1218)).padding(horizontal = 8.dp, vertical = 8.dp).widthIn(min = 28.dp),
                style = style.copy(color = Color(0xFF59616D)),
            )
            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                BasicTextField(
                    value = f.value,
                    onValueChange = { f.value = it },
                    textStyle = style,
                    cursorBrush = SolidColor(Gold),
                    visualTransformation = transformation,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                    modifier = Modifier.padding(8.dp).widthIn(min = 600.dp),
                )
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
    var cmd by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(output.length) { scroll.scrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize()) {
        if (state != LinuxEnv.State.READY || !linux.prootAvailable) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.terminal_fallback), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(enabled = state != LinuxEnv.State.INSTALLING, onClick = {
                            scope.launch { if (linux.install().isSuccess) shell.restart() }
                        }) { Text(stringResource(if (state == LinuxEnv.State.INSTALLING) R.string.terminal_installing else R.string.terminal_install)) }
                        if (state == LinuxEnv.State.INSTALLING) { Spacer(Modifier.width(8.dp)); CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                    }
                    if (progress.isNotEmpty()) Text(progress, style = MaterialTheme.typography.bodySmall,
                        color = if (state == LinuxEnv.State.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF07090C))) {
                SelectionContainer {
                    Text(output, Modifier.fillMaxSize().verticalScroll(scroll).padding(10.dp),
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, color = Color(0xFFCFD6DE))
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$ ", fontFamily = FontFamily.Monospace, color = Gold)
                OutlinedTextField(cmd, { cmd = it }, Modifier.weight(1f), singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    placeholder = { Text(stringResource(R.string.command_hint)) },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (cmd.isNotBlank()) { shell.send(cmd); cmd = "" } }))
                Spacer(Modifier.width(6.dp))
                FilledIconButton(onClick = { if (cmd.isNotBlank()) { shell.send(cmd); cmd = "" } }) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.run)) }
                IconButton(onClick = { shell.restart() }) { Icon(Icons.Filled.Refresh, stringResource(R.string.restart_shell)) }
            }
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
    var repo by remember { mutableStateOf(listOf(settings.githubUser, ws.name).filter { it.isNotBlank() }.joinToString("/")) }
    var message by remember { mutableStateOf("Update from Saqr Code") }
    var create by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("") }
    val needToken = stringResource(R.string.git_need_token)

    LaunchedEffect(ws.fsVersion) { if (!busy) log = runCatching { git.status(ws.dir) }.getOrElse { it.message ?: "" } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(repo, { repo = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.git_remote)) })
        OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.git_commit_msg)) })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(create, { create = it }); Text(stringResource(R.string.git_create_repo))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !busy && repo.contains('/'), onClick = {
                val token = settings.githubToken
                if (token == null) { log = needToken; return@Button }
                busy = true
                scope.launch {
                    log = runCatching {
                        val (owner, name) = repo.trim().split('/').let { it[0] to it[1] }
                        val pre = if (create) git.ensureRepo(owner, name, token) + "\n" else ""
                        pre + git.commitAndPush(ws.dir, repo, settings.githubUser, token, message)
                    }.getOrElse { "✗ " + (it.message ?: it.toString()) }
                    busy = false
                }
            }) { Text(stringResource(R.string.git_push)) }
            TextButton(enabled = !busy, onClick = { scope.launch { log = runCatching { git.status(ws.dir) }.getOrElse { it.message ?: "" } } }) {
                Text(stringResource(R.string.git_status))
            }
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (log.isNotBlank()) CodeBox(log, 400)
    }
}
