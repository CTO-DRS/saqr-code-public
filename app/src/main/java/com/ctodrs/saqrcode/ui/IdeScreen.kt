package com.ctodrs.saqrcode.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.agent.AgentSession
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.Providers
import com.ctodrs.saqrcode.agent.Snapshots
import com.ctodrs.saqrcode.agent.Tools
import com.ctodrs.saqrcode.ui.chat.ChatContent
import com.ctodrs.saqrcode.ui.chat.ChatUiState
import com.ctodrs.saqrcode.ui.chat.PlanPillContent
import com.ctodrs.saqrcode.ui.components.FalconMark
import com.ctodrs.saqrcode.ui.components.latinDigits
import com.ctodrs.saqrcode.ui.components.GlassIconButton
import com.ctodrs.saqrcode.ui.components.GlassPill
import com.ctodrs.saqrcode.ui.components.PillIcon
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.launch

object Pane { const val AGENT = 0; const val FILES = 1; const val TERMINAL = 2; const val GIT = 3; const val PREVIEW = 4 }

val TopBarHeight: Dp = 68.dp

@Composable
fun IdeScreen(projectName: String, onBack: () -> Unit, onSettings: () -> Unit, onAccounts: () -> Unit = onSettings) {
    val ws = remember(projectName) { Workspace.get(projectName) }
    if (ws == null) { LaunchedEffect(Unit) { onBack() }; return }
    val app = SaqrApp.instance
    val settings = app.settings
    val version by settings.version.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showRestore by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    // Any agent activity may change files: refresh tree + clean editor tabs.
    LaunchedEffect(ws.agent.items.size, ws.agent.running) { ws.fsVersion++; ws.syncFromDisk() }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            IdeDrawer(
                projectName = projectName, current = ws.tab,
                onPane = { ws.tab = it; scope.launch { drawer.close() } },
                onNewChat = { ws.agent.clear(); ws.tab = Pane.AGENT; scope.launch { drawer.close() } },
                onRestore = { showRestore = true; scope.launch { drawer.close() } },
                onSettings = { scope.launch { drawer.close() }; onSettings() },
                onProjects = { scope.launch { drawer.close() }; onBack() },
            )
        },
    ) {
        IdeFrame(
            topBar = {
                IdeTopBar(
                    pane = ws.tab, onDrawer = { scope.launch { drawer.open() } },
                    onPane = { ws.tab = if (ws.tab == it) Pane.AGENT else it },
                    center = {
                        if (ws.tab == Pane.AGENT) PlanPillContent(ws.agent.plan, ws.agent.running, onPause = { ws.agent.stop() }, title = projectName)
                        else PaneTitle(ws.tab, projectName)
                    },
                )
            },
        ) { top ->
            AnimatedContent(ws.tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "pane") { tab ->
                when (tab) {
                    Pane.AGENT -> {
                        version
                        val def = Providers.byId(settings.activeProvider)
                        val cfg = settings.config(def.id)
                        val state = ChatUiState(
                            items = ws.agent.items, running = ws.agent.running, step = ws.agent.step, maxSteps = AgentSession.MAX_STEPS,
                            contextTokens = ws.agent.contextTokens, contextWindow = def.contextWindow, providerId = def.id,
                            model = cfg.model, models = (listOf(cfg.model) + def.suggestedModels).filter { it.isNotBlank() }.distinct(),
                            ready = settings.activeReady(),
                            projectFiles = {
                                Tools.listFiles(ws.dir, ".", true, 200).lines().filter { !it.endsWith("/") && !it.contains("(skipped)") && it.isNotBlank() }
                                    .map { it.substringBeforeLast(" (") }
                            },
                        )
                        Box(Modifier.imePadding()) {
                            ChatContent(
                                state, input, { input = it },
                                onSend = { ws.agent.send(input); input = "" }, onStop = { ws.agent.stop() },
                                onDecide = { a, ok -> ws.agent.decide(a, ok) },
                                onPickModel = { settings.setModel(def.id, it) }, onManageProviders = onSettings, topInset = top,
                                onAnswer = { a, skip -> ws.agent.answer(a, skip) },
                            )
                        }
                    }
                    Pane.FILES -> Box(Modifier.padding(top = top).imePadding()) { FilesPane(ws) }
                    Pane.TERMINAL -> Box(Modifier.padding(top = top).imePadding()) { TerminalPane(ws) }
                    Pane.GIT -> Box(Modifier.padding(top = top).imePadding()) { GitPane(ws, onAccounts) }
                    else -> Box(Modifier.padding(top = top)) { PreviewPane(ws) }
                }
            }
        }
    }

    if (showRestore) RestoreDialog(ws, onDismiss = { showRestore = false })
}

/** Content with a floating glass top bar over a soft scrim. `content` receives the top inset to pad by. */
@Composable
fun IdeFrame(topBar: @Composable () -> Unit, content: @Composable (Dp) -> Unit) {
    val c = Saqr.colors
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Box(Modifier.fillMaxSize()) { content(TopBarHeight + statusBarHeight()) }
        Box(Modifier.fillMaxWidth().height(TopBarHeight + statusBarHeight() + 20.dp)
            .background(Brush.verticalGradient(0f to c.bg, 0.62f to c.bg.copy(alpha = 0.92f), 1f to Color.Transparent)))
        Box(Modifier.statusBarsPadding()) { topBar() }
    }
}

@Composable
private fun statusBarHeight(): Dp {
    val insets = androidx.compose.foundation.layout.WindowInsets.statusBars
    val d = androidx.compose.ui.platform.LocalDensity.current
    return with(d) { insets.getTop(d).toDp() }
}

@Composable
fun IdeTopBar(pane: Int, onDrawer: () -> Unit, onPane: (Int) -> Unit, center: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = Sp.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Lucide.PanelLeft, stringResource(R.string.menu), onDrawer)
        Spacer(Modifier.weight(1f))
        GlassPill { center() }
        Spacer(Modifier.weight(1f))
        GlassPill {
            PillIcon(Lucide.Globe, stringResource(R.string.tab_preview), { onPane(Pane.PREVIEW) }, active = pane == Pane.PREVIEW)
            PillIcon(Lucide.SquareTerminal, stringResource(R.string.tab_terminal), { onPane(Pane.TERMINAL) }, active = pane == Pane.TERMINAL)
        }
    }
}

@Composable
fun PaneTitle(pane: Int, projectName: String) {
    val c = Saqr.colors
    val (icon, label) = paneMeta(pane)
    Row(Modifier.padding(horizontal = Sp.sm), verticalAlignment = Alignment.CenterVertically) {
        SIcon(icon, size = 17.dp, tint = c.accentText)
        Spacer(Modifier.width(Sp.sm))
        Text(stringResource(label), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1)
        Text(" · $projectName", style = Saqr.type.label, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp))
    }
}

fun paneMeta(pane: Int): Pair<ImageVector, Int> = when (pane) {
    Pane.FILES -> Lucide.FileCode to R.string.tab_files
    Pane.TERMINAL -> Lucide.SquareTerminal to R.string.tab_terminal
    Pane.GIT -> Lucide.GitBranch to R.string.tab_git
    Pane.PREVIEW -> Lucide.Globe to R.string.tab_preview
    else -> Lucide.Sparkles to R.string.tab_agent
}

@Composable
fun IdeDrawer(
    projectName: String, current: Int, onPane: (Int) -> Unit, onNewChat: () -> Unit, onRestore: () -> Unit,
    onSettings: () -> Unit, onProjects: () -> Unit,
) {
    val c = Saqr.colors
    ModalDrawerSheet(
        drawerContainerColor = c.surface1, drawerContentColor = c.text1,
        drawerShape = RoundedCornerShape(topEnd = Radius.xl, bottomEnd = Radius.xl),
        modifier = Modifier.widthIn(max = 316.dp),
    ) {
        Column(Modifier.fillMaxHeight().statusBarsPadding().padding(Sp.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FalconMark(44.dp)
                Spacer(Modifier.width(Sp.md))
                Column(Modifier.weight(1f)) {
                    Text(projectName, style = Saqr.type.title3.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.app_name), style = Saqr.type.caption, color = c.text3)
                }
            }
            Spacer(Modifier.height(Sp.xl))
            DrawerAction(Lucide.MessageSquarePlus, stringResource(R.string.clear_chat), onNewChat, highlight = true)
            Spacer(Modifier.height(Sp.lg))
            Text(stringResource(R.string.workspace), style = Saqr.type.micro, color = c.text3, modifier = Modifier.padding(horizontal = Sp.md, vertical = Sp.xs))
            listOf(Pane.AGENT, Pane.FILES, Pane.TERMINAL, Pane.GIT, Pane.PREVIEW).forEach { p ->
                val (icon, label) = paneMeta(p)
                DrawerItem(icon, stringResource(label), selected = current == p) { onPane(p) }
            }
            Spacer(Modifier.height(Sp.lg))
            Text(stringResource(R.string.more), style = Saqr.type.micro, color = c.text3, modifier = Modifier.padding(horizontal = Sp.md, vertical = Sp.xs))
            DrawerItem(Lucide.History, stringResource(R.string.restore_points), false, onRestore)
            DrawerItem(Lucide.Settings, stringResource(R.string.settings), false, onSettings)
            Spacer(Modifier.weight(1f))
            DrawerItem(Lucide.House, stringResource(R.string.all_projects), false, onProjects)
        }
    }
}

@Composable
private fun DrawerAction(icon: ImageVector, label: String, onClick: () -> Unit, highlight: Boolean) {
    val c = Saqr.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(if (highlight) c.accentSoft else c.surface2)
            .border(1.dp, if (highlight) c.accent.copy(alpha = 0.35f) else c.border, RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick).padding(horizontal = Sp.md, vertical = Sp.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(icon, size = 19.dp, tint = c.accentText)
        Spacer(Modifier.width(Sp.md))
        Text(label, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Saqr.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(Radius.sm))
            .background(if (selected) c.surface3 else Color.Transparent).clickable(onClick = onClick).padding(horizontal = Sp.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(icon, size = 19.dp, tint = if (selected) c.accentText else c.text2)
        Spacer(Modifier.width(Sp.md))
        Text(label, style = Saqr.type.label, color = if (selected) c.text1 else c.text2, modifier = Modifier.weight(1f))
        if (selected) Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
    }
}

@Composable
private fun RestoreDialog(ws: Workspace, onDismiss: () -> Unit) {
    val c = Saqr.colors
    var points by remember { mutableStateOf(ws.agent.snapshots.list()) }
    var confirm by remember { mutableStateOf<Snapshots.Point?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface1,
        icon = { SIcon(Lucide.History, size = 24.dp, tint = c.accentText) },
        title = { Text(stringResource(R.string.restore_points), style = Saqr.type.title2) },
        text = {
            if (points.isEmpty()) Text(stringResource(R.string.no_restore_points), style = Saqr.type.bodySmall, color = c.text2)
            else LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
                items(points, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2).clickable { confirm = p }.padding(Sp.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(DateUtils.getRelativeTimeSpanString(p.time).toString().latinDigits(), style = Saqr.type.label, color = c.text1)
                            Text(p.label, style = Saqr.type.monoSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = c.text2)
                        }
                        SIcon(Lucide.RotateCcw, size = 18.dp, tint = c.accentText, contentDescription = stringResource(R.string.restore))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = c.accentText) } },
    )
    confirm?.let { p ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = c.surface1,
            text = { Text(stringResource(R.string.restore) + ": " + DateUtils.getRelativeTimeSpanString(p.time), style = Saqr.type.body) },
            confirmButton = {
                TextButton(onClick = {
                    ws.agent.snapshots.restore(p)
                    ws.agent.items += ChatItem.Notice(p.label, kind = ChatItem.Notice.Kind.RESTORED)
                    ws.fsVersion++; ws.syncFromDisk()
                    points = ws.agent.snapshots.list(); confirm = null
                }) { Text(stringResource(R.string.restore), color = c.accentText) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
    }
}
