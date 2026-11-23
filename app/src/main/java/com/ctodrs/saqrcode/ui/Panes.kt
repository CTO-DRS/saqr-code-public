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

// ------------------------------------------------------------------ Git

@Composable
fun GitPane(ws: Workspace, onAccounts: () -> Unit = {}) { GitScreen(ws, onAccounts) }

// Preview: see ElementPicker.kt (PreviewPane + element picker).
