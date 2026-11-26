package com.ctodrs.saqrcode.ui

import androidx.compose.ui.semantics.Role
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.git.ChangeKind
import com.ctodrs.saqrcode.git.Forge
import com.ctodrs.saqrcode.git.GitBranch
import com.ctodrs.saqrcode.git.GitCommitInfo
import com.ctodrs.saqrcode.git.GitController
import com.ctodrs.saqrcode.git.GitFileStatus
import com.ctodrs.saqrcode.git.GitRemote
import com.ctodrs.saqrcode.git.GitStatus
import com.ctodrs.saqrcode.git.GitToast
import com.ctodrs.saqrcode.ui.components.Chip
import com.ctodrs.saqrcode.ui.components.DiffChip
import com.ctodrs.saqrcode.ui.components.EmptyState
import com.ctodrs.saqrcode.ui.components.GhostButton
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.components.SaqrSwitch
import com.ctodrs.saqrcode.ui.components.SecondaryButton
import com.ctodrs.saqrcode.ui.components.Segmented
import com.ctodrs.saqrcode.ui.components.Spinner
import com.ctodrs.saqrcode.ui.components.latinDigits
import com.ctodrs.saqrcode.ui.components.pressable
import com.ctodrs.saqrcode.ui.icons.FileTypeIcon
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.launch

/** Everything the Git screen renders; [GitScreen] fills it from a [GitController]. */
data class GitUiState(
    val status: GitStatus,
    val log: List<GitCommitInfo> = emptyList(),
    val branches: List<GitBranch> = emptyList(),
    val remotes: List<GitRemote> = emptyList(),
    val busy: String? = null,
    val toast: GitToast? = null,
    val githubLogin: String? = null,
    val identity: String = "",
)

class GitActions(
    val onInit: () -> Unit = {}, val onClone: () -> Unit = {},
    val onToggle: (GitFileStatus) -> Unit = {}, val onStageAll: () -> Unit = {}, val onUnstageAll: () -> Unit = {},
    val onDiscard: (String) -> Unit = {}, val onOpenDiff: (GitFileStatus) -> Unit = {},
    val onCommit: (String, Boolean, Boolean) -> Unit = { _, _, _ -> },
    val onFetch: () -> Unit = {}, val onPull: () -> Unit = {}, val onPush: () -> Unit = {},
    val onCheckout: (String) -> Unit = {}, val onNewBranch: (String) -> Unit = {}, val onDeleteBranch: (String) -> Unit = {},
    val onMerge: (String) -> Unit = {}, val onAbortMerge: () -> Unit = {},
    val onOpenCommit: (GitCommitInfo) -> Unit = {}, val onAddRemote: (String, String) -> Unit = { _, _ -> },
    val onRemoveRemote: (String) -> Unit = {}, val onPublish: () -> Unit = {}, val onPullRequest: () -> Unit = {},
    val onEditGitignore: () -> Unit = {}, val onAccounts: () -> Unit = {}, val onDismissToast: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
)

object GitTab { const val CHANGES = 0; const val HISTORY = 1; const val BRANCHES = 2 }

@Composable
fun GitScreen(ws: Workspace, onAccounts: () -> Unit = {}) {
    val app = SaqrApp.instance
    val ctl = ws.git
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(ws.fsVersion) { ctl.refresh() }
    var diff by remember { mutableStateOf<Pair<String, String>?>(null) }
    var publish by remember { mutableStateOf(false) }
    var pr by remember { mutableStateOf(false) }
    val settings = app.settings
    val state = GitUiState(
        status = ctl.status, log = ctl.log, branches = ctl.branches, remotes = ctl.remotes, busy = ctl.busy, toast = ctl.toast,
        githubLogin = settings.accounts.github?.login, identity = listOf(settings.gitName, settings.gitEmail).filter { it.isNotBlank() }.joinToString(" · "),
    )
    GitScreenContent(
        state, ws.gitTab, { ws.gitTab = it },
        GitActions(
            onInit = { ctl.init() },
            onToggle = { ctl.toggleStage(it) }, onStageAll = { ctl.stageAll() }, onUnstageAll = { ctl.unstageAll() },
            onDiscard = { ctl.discard(it); ws.fsVersion++ },
            onOpenDiff = { f -> scope.launch { diff = f.path to ctl.diffOf(f.path, staged = f.isStaged && !f.isUnstaged) } },
            onCommit = { m, amend, push -> ctl.commit(m, amend, push) },
            onFetch = { ctl.fetch() }, onPull = { ctl.pull(); ws.fsVersion++ }, onPush = { ctl.push() },
            onCheckout = { ctl.checkout(it); ws.fsVersion++ }, onNewBranch = { ctl.createBranch(it) }, onDeleteBranch = { ctl.deleteBranch(it) },
            onMerge = { ctl.merge(it); ws.fsVersion++ }, onAbortMerge = { ctl.abortMerge(); ws.fsVersion++ },
            onOpenCommit = { c -> scope.launch { diff = (c.shortId + " · " + c.message) to ctl.commitDiff(c.id) } },
            onAddRemote = { n, u -> ctl.setRemote(n, u) }, onRemoveRemote = { ctl.removeRemote(it) },
            onPublish = { publish = true }, onPullRequest = { pr = true },
            onEditGitignore = { ctl.git.ensureGitignore(ws.dir); ws.open(".gitignore"); ws.tab = Pane.FILES; ws.fsVersion++ },
            onAccounts = onAccounts, onDismissToast = { ctl.toast = null },
            onOpenLink = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
        ),
    )
    diff?.let { (title, text) -> DiffViewerDialog(title, text) { diff = null } }
    if (publish) PublishDialog(ws.name, settings.accounts, onDismiss = { publish = false }) { host, name, priv, desc ->
        publish = false
        if (host == "github.com") ctl.publishToGitHub(name, priv, desc) else ctl.publishToForge(host, name, priv, desc)
    }
    if (pr) PullRequestDialog(
        branch = ctl.status.branch ?: "", bases = ctl.branches.filter { !it.remote }.map { it.shortName },
        forgeHost = ForgeHost.of(ctl.originUrl, settings.accounts),
        onDismiss = { pr = false },
    ) { title, base, body ->
        pr = false
        val host = ForgeHost.of(ctl.originUrl, settings.accounts)
        if (host == null) ctl.openPullRequest(title, base, body) else ctl.openForgePullRequest(host, title, base, body)
    }
}

/** v0.10: the forge host of origin, when it is not GitHub and has a token — null = GitHub. */
object ForgeHost {
    fun of(originUrl: String?, accounts: com.ctodrs.saqrcode.data.AccountStore): String? {
        if (originUrl == null) return null
        val h = com.ctodrs.saqrcode.data.AccountStore.normalizeHost(originUrl) ?: return null
        if (h == "github.com") return null
        val kind = Forge.detect(h, accounts.hostKind(h)) ?: return null
        if (accounts.forUrl("https://$h") == null) return null
        return h
    }
}

@Composable
fun GitScreenContent(state: GitUiState, tab: Int, onTab: (Int) -> Unit, a: GitActions) {
    val c = Saqr.colors
    val s = state.status
    if (!s.isRepo) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(stringResource(R.string.git_no_repo_title), stringResource(R.string.git_no_repo_body), icon = Lucide.GitBranch, action = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                    PrimaryButton(stringResource(R.string.git_init), a.onInit, icon = Lucide.Plus)
                    if (state.githubLogin == null) GhostButton(stringResource(R.string.git_link_github), a.onAccounts, icon = Lucide.Github)
                }
            })
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = Sp.lg)) {
        RepoHeader(state, a)
        Spacer(Modifier.height(Sp.md))
        Segmented(
            listOf(
                stringResource(R.string.git_tab_changes) + if (s.files.isNotEmpty()) "  ${s.files.size}" else "",
                stringResource(R.string.git_tab_history),
                stringResource(R.string.git_tab_branches),
            ), tab, onTab,
        )
        Spacer(Modifier.height(Sp.sm))
        state.toast?.let { ToastBar(it, a) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                GitTab.HISTORY -> HistoryList(state.log, a)
                GitTab.BRANCHES -> BranchesList(state, a)
                else -> ChangesList(state, a)
            }
        }
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun RepoHeader(state: GitUiState, a: GitActions) {
    val c = Saqr.colors
    val s = state.status
    val shape = RoundedCornerShape(Radius.lg)
    val origin = state.remotes.firstOrNull { it.name == "origin" } ?: state.remotes.firstOrNull()
    val slug = com.ctodrs.saqrcode.git.GitService.githubSlug(origin?.url)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface1).border(1.dp, c.border, shape).padding(Sp.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var menu by remember { mutableStateOf(false) }
            Box {
                Row(
                    Modifier.height(40.dp).pressable { menu = true }.clip(RoundedCornerShape(Radius.sm)).background(c.accentSoft)
                        .border(1.dp, c.accent.copy(alpha = 0.35f), RoundedCornerShape(Radius.sm)).padding(horizontal = Sp.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SIcon(Lucide.GitBranch, size = 16.dp, tint = c.accentText)
                    Spacer(Modifier.width(6.dp))
                    Text(s.branch ?: "—", style = Saqr.type.mono.copy(fontWeight = FontWeight.Medium), color = c.text1, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
                    Spacer(Modifier.width(4.dp))
                    SIcon(Lucide.ChevronsUpDown, size = 14.dp, tint = c.text3)
                }
                DropdownMenu(menu, { menu = false }, Modifier.background(c.surface2).heightIn(max = 380.dp)) {
                    state.branches.filter { !it.remote }.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.shortName, style = Saqr.type.mono, color = if (b.current) c.accentText else c.text1) },
                            leadingIcon = { SIcon(if (b.current) Lucide.Check else Lucide.GitBranch, size = 16.dp, tint = if (b.current) c.accentText else c.text3) },
                            onClick = { menu = false; if (!b.current) a.onCheckout(b.shortName) },
                        )
                    }
                }
            }
            Spacer(Modifier.width(Sp.sm))
            if (s.ahead > 0 || s.behind > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.ahead > 0) SyncCount(Lucide.ArrowUp, s.ahead)
                    if (s.behind > 0) { Spacer(Modifier.width(4.dp)); SyncCount(Lucide.ArrowDown, s.behind) }
                }
            }
            Spacer(Modifier.weight(1f))
            if (state.busy != null) { Spinner(18.dp); Spacer(Modifier.width(Sp.sm)) }
            HeaderButton(Lucide.RefreshCw, stringResource(R.string.git_fetch), state.busy == null && origin != null, a.onFetch)
            HeaderButton(Lucide.ArrowDownToLine, stringResource(R.string.git_pull), state.busy == null && origin != null, a.onPull)
            HeaderButton(Lucide.ArrowUpFromLine, stringResource(R.string.git_push), state.busy == null && origin != null && s.hasCommits, a.onPush, accent = s.ahead > 0)
        }
        Spacer(Modifier.height(Sp.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(if (slug != null) Lucide.Github else Lucide.Link, size = 14.dp, tint = c.text3)
            Spacer(Modifier.width(6.dp))
            if (origin == null) {
                Text(stringResource(R.string.git_no_remote), style = Saqr.type.caption, color = c.text3, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.git_publish), style = Saqr.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accentText,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(role = Role.Button, onClick = a.onPublish).padding(horizontal = 6.dp, vertical = 2.dp))
            } else {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(slug?.let { "${it.first}/${it.second}" } ?: origin.url, style = Saqr.type.monoSmall, color = c.text2, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                s.upstream?.let { Text(it, style = Saqr.type.monoMicro, color = c.text3, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun SyncCount(icon: ImageVector, n: Int) {
    val c = Saqr.colors
    Row(Modifier.clip(RoundedCornerShape(6.dp)).background(c.surface3).padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        SIcon(icon, size = 12.dp, tint = c.text2)
        Text(n.toString(), style = Saqr.type.monoMicro, color = c.text1)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, accent: Boolean = false) {
    val c = Saqr.colors
    Box(
        Modifier.padding(start = 4.dp).size(38.dp).pressable(enabled, onClick).clip(CircleShape)
            .background(if (accent && enabled) c.accent else c.surface2)
            .border(1.dp, if (accent && enabled) c.accent else c.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) { SIcon(icon, size = 17.dp, tint = when { !enabled -> c.text3.copy(alpha = 0.5f); accent -> c.onAccent; else -> c.text1 }, contentDescription = label) }
}

@Composable
private fun ToastBar(t: GitToast, a: GitActions) {
    val c = Saqr.colors
    val fg = if (t.error) c.danger else c.success
    Row(
        Modifier.fillMaxWidth().padding(bottom = Sp.sm).clip(RoundedCornerShape(Radius.sm)).background(fg.copy(alpha = 0.10f))
            .border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(Radius.sm)).padding(start = Sp.md, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(if (t.error) Lucide.CircleAlert else Lucide.CircleCheck, size = 16.dp, tint = fg)
        Spacer(Modifier.width(Sp.sm))
        Text(t.text, style = Saqr.type.caption.copy(textDirection = TextDirection.Content), color = c.text1, maxLines = 4,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (t.link != null) GhostButton(stringResource(R.string.open), { a.onOpenLink(t.link) }, icon = Lucide.ExternalLink)
        Box(Modifier.size(30.dp).clip(CircleShape).clickable(role = Role.Button, onClick = a.onDismissToast), contentAlignment = Alignment.Center) {
            SIcon(Lucide.X, size = 14.dp, tint = c.text3, contentDescription = stringResource(R.string.close))
        }
    }
}

// ------------------------------------------------------------------ changes

@Composable
private fun ChangesList(state: GitUiState, a: GitActions) {
    val c = Saqr.colors
    val s = state.status
    var message by remember { mutableStateOf("") }
    var amend by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        if (s.merging || s.conflicts.isNotEmpty()) ConflictBanner(s, a)
        if (s.clean && !s.merging) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.git_clean_title), stringResource(R.string.git_clean_body), icon = Lucide.CheckCheck)
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = Sp.sm)) {
            val staged = s.staged
            val unstaged = s.files.filter { it.isUnstaged || it.conflict }
            if (staged.isNotEmpty()) {
                item("hs") { SectionHeader(stringResource(R.string.git_staged), staged.size, stringResource(R.string.git_unstage_all), a.onUnstageAll) }
                items(staged, key = { "s:" + it.path }) { f -> FileRow(f, staged = true, a) }
            }
            if (unstaged.isNotEmpty()) {
                item("hu") { SectionHeader(stringResource(R.string.git_changes), unstaged.size, stringResource(R.string.git_stage_all), a.onStageAll) }
                items(unstaged, key = { "u:" + it.path }) { f -> FileRow(f, staged = false, a) }
            }
            item("gi") {
                Row(Modifier.fillMaxWidth().padding(top = Sp.sm), horizontalArrangement = Arrangement.End) {
                    GhostButton(".gitignore", a.onEditGitignore, icon = Lucide.Filter, tint = c.text2)
                }
            }
        }
        CommitBox(
            message, { message = it }, amend, { amend = it }, s,
            onCommit = { push -> a.onCommit(message, amend, push); message = ""; amend = false },
            identity = state.identity, busy = state.busy != null, canPush = state.remotes.isNotEmpty(),
        )
    }
}

@Composable
private fun ConflictBanner(s: GitStatus, a: GitActions) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.md)
    Row(
        Modifier.fillMaxWidth().padding(bottom = Sp.sm).clip(shape).background(c.danger.copy(alpha = 0.08f)).border(1.dp, c.danger.copy(alpha = 0.4f), shape).padding(Sp.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(Lucide.GitMerge, size = 20.dp, tint = c.danger)
        Spacer(Modifier.width(Sp.md))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.git_conflicts_title, s.conflicts.size.toString()), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
            Text(stringResource(R.string.git_conflicts_body), style = Saqr.type.caption, color = c.text2)
        }
        if (s.merging) GhostButton(stringResource(R.string.git_abort_merge), a.onAbortMerge, tint = c.danger)
    }
}

@Composable
private fun SectionHeader(title: String, n: Int, action: String, onAction: () -> Unit) {
    val c = Saqr.colors
    Row(Modifier.fillMaxWidth().padding(top = Sp.sm, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text2)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.clip(CircleShape).background(c.surface3).padding(horizontal = 7.dp, vertical = 1.dp)) {
            Text(n.toString(), style = Saqr.type.monoMicro, color = c.text2)
        }
        Spacer(Modifier.weight(1f))
        GhostButton(action, onAction)
    }
}

@Composable
fun KindBadge(kind: ChangeKind) {
    val c = Saqr.colors
    val (letter, color) = when (kind) {
        ChangeKind.ADDED -> "A" to c.success
        ChangeKind.UNTRACKED -> "U" to c.success
        ChangeKind.MODIFIED -> "M" to c.accentText
        ChangeKind.DELETED -> "D" to c.danger
        ChangeKind.CONFLICT -> "C" to c.danger
    }
    Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Text(letter, style = Saqr.type.monoMicro.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(f: GitFileStatus, staged: Boolean, a: GitActions) {
    val c = Saqr.colors
    var menu by remember { mutableStateOf(false) }
    val kind = if (f.conflict) ChangeKind.CONFLICT else if (staged) f.staged ?: ChangeKind.MODIFIED else f.unstaged ?: ChangeKind.MODIFIED
    val name = f.path.substringAfterLast('/')
    val parent = f.path.substringBeforeLast('/', "")
    Box {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(Radius.sm))
                    .combinedClickable(onClick = { a.onOpenDiff(f) }, onLongClick = { menu = true }).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileTypeIcon(name, 20.dp)
                Spacer(Modifier.width(Sp.md))
                Column(Modifier.weight(1f)) {
                    Text(name, style = Saqr.type.mono.copy(fontWeight = FontWeight.Medium), color = if (kind == ChangeKind.DELETED) c.text2 else c.text1,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (parent.isNotEmpty()) Text(parent, style = Saqr.type.monoMicro, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(Sp.sm))
                DiffChip(f.added.takeIf { it > 0 }, f.removed.takeIf { it > 0 })
                Spacer(Modifier.width(Sp.sm))
                KindBadge(kind)
                Spacer(Modifier.width(Sp.xs))
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Checkbox) { a.onToggle(f) }, contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (staged) c.accent else Color.Transparent)
                            .border(1.5.dp, if (staged) c.accent else c.borderStrong, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (staged) SIcon(Lucide.Check, size = 13.dp, tint = c.onAccent, contentDescription = stringResource(R.string.git_unstage))
                        else SIcon(Lucide.Plus, size = 13.dp, tint = c.text3, contentDescription = stringResource(R.string.git_stage))
                    }
                }
            }
        }
        DropdownMenu(menu, { menu = false }, Modifier.background(c.surface2)) {
            DropdownMenuItem(text = { Text(stringResource(R.string.git_view_diff), color = c.text1) }, leadingIcon = { SIcon(Lucide.FileDiff, size = 16.dp) },
                onClick = { menu = false; a.onOpenDiff(f) })
            DropdownMenuItem(text = { Text(stringResource(if (staged) R.string.git_unstage else R.string.git_stage), color = c.text1) },
                leadingIcon = { SIcon(if (staged) Lucide.Minus else Lucide.Plus, size = 16.dp) }, onClick = { menu = false; a.onToggle(f) })
            if (!staged) DropdownMenuItem(text = { Text(stringResource(R.string.git_discard), color = c.danger) }, leadingIcon = { SIcon(Lucide.Undo2, size = 16.dp, tint = c.danger) },
                onClick = { menu = false; a.onDiscard(f.path) })
        }
    }
}

@Composable
private fun CommitBox(
    message: String, onMessage: (String) -> Unit, amend: Boolean, onAmend: (Boolean) -> Unit, s: GitStatus,
    onCommit: (push: Boolean) -> Unit, identity: String, busy: Boolean, canPush: Boolean,
) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.lg)
    val count = if (s.staged.isNotEmpty()) s.staged.size else s.files.count { !it.conflict }
    val can = message.isNotBlank() && !busy && s.conflicts.isEmpty() && (count > 0 || amend || s.merging)
    Column(
        Modifier.fillMaxWidth().padding(bottom = Sp.sm).navigationBarsPadding().clip(shape).background(c.surface1).border(1.dp, c.border, shape).padding(Sp.md),
        verticalArrangement = Arrangement.spacedBy(Sp.sm),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(Radius.sm)).background(c.surface3.copy(alpha = 0.6f))
            .border(1.dp, c.border, RoundedCornerShape(Radius.sm)).padding(horizontal = Sp.md, vertical = 10.dp)) {
            BasicTextField(
                message, onMessage, Modifier.fillMaxWidth(), textStyle = Saqr.type.body.copy(color = c.text1, textDirection = TextDirection.Content),
                cursorBrush = SolidColor(c.accent), maxLines = 5,
                decorationBox = { inner -> Box { if (message.isEmpty()) Text(stringResource(R.string.git_commit_hint), style = Saqr.type.bodySmall, color = c.text3); inner() } },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(Modifier.clip(RoundedCornerShape(6.dp)).clickable { onAmend(!amend) }.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(if (amend) c.accent else Color.Transparent)
                        .border(1.5.dp, if (amend) c.accent else c.borderStrong, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        if (amend) SIcon(Lucide.Check, size = 11.dp, tint = c.onAccent)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.git_amend), style = Saqr.type.caption, color = c.text2)
                }
                if (identity.isNotBlank()) Text(identity, style = Saqr.type.monoMicro, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (canPush) {
                SecondaryButton(stringResource(R.string.git_commit_push), { onCommit(true) }, enabled = can, icon = Lucide.ArrowUpFromLine)
                Spacer(Modifier.width(Sp.sm))
            }
            PrimaryButton(stringResource(R.string.git_commit_n, count.toString()), { onCommit(false) }, enabled = can, icon = Lucide.GitCommitHorizontal)
        }
    }
}

// ------------------------------------------------------------------ history

@Composable
private fun HistoryList(log: List<GitCommitInfo>, a: GitActions, now: Long = System.currentTimeMillis()) {
    val c = Saqr.colors
    if (log.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(stringResource(R.string.git_no_commits_title), stringResource(R.string.git_no_commits_body), icon = Lucide.GitCommitHorizontal)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = Sp.xs, bottom = Sp.xl)) {
        items(log.size, key = { log[it].id }) { i ->
            val cm = log[i]
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).clickable { a.onOpenCommit(cm) }.padding(end = Sp.xs)) {
                // timeline rail
                Box(Modifier.width(28.dp).heightIn(min = 64.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.padding(top = if (i == 0) 18.dp else 0.dp).width(2.dp).height(if (i == log.lastIndex) 18.dp else 72.dp)
                        .background(c.border))
                    Box(Modifier.padding(top = 12.dp).size(14.dp).clip(CircleShape).background(if (i == 0) c.accent else c.surface1)
                        .border(2.dp, if (i == 0) c.accent.copy(alpha = 0.5f) else c.borderStrong, CircleShape))
                }
                Spacer(Modifier.width(Sp.sm))
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(cm.message, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content), color = c.text1,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Chip(cm.shortId, c.text2, c.surface3)
                        Spacer(Modifier.width(6.dp))
                        Text(cm.author + " · " + DateUtils.getRelativeTimeSpanString(cm.time, now, DateUtils.MINUTE_IN_MILLIS).toString().latinDigits(),
                            style = Saqr.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                    if (cm.refs.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            cm.refs.forEach { r ->
                                val remote = r.contains('/')
                                Chip(r, if (remote) c.info else c.accentText, if (remote) c.info.copy(alpha = 0.12f) else c.accentSoft)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ branches & remotes

@Composable
private fun BranchesList(state: GitUiState, a: GitActions) {
    val c = Saqr.colors
    var newBranch by remember { mutableStateOf(false) }
    var addRemote by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val local = state.branches.filter { !it.remote }
    val remote = state.branches.filter { it.remote }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Sp.xl)) {
        item("lh") {
            Row(Modifier.fillMaxWidth().padding(top = Sp.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.git_local_branches), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text2, modifier = Modifier.weight(1f))
                GhostButton(stringResource(R.string.git_new_branch), { newBranch = true }, icon = Lucide.Plus, enabled = state.status.hasCommits)
            }
        }
        items(local, key = { it.name }) { b -> BranchRow(b, a) { confirmDelete = it } }
        if (remote.isNotEmpty()) {
            item("rh") { Text(stringResource(R.string.git_remote_branches), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text2,
                modifier = Modifier.padding(top = Sp.lg, bottom = Sp.xs)) }
            items(remote, key = { it.name }) { b -> BranchRow(b, a) {} }
        }
        item("remotes") {
            Row(Modifier.fillMaxWidth().padding(top = Sp.lg), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.git_remotes), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text2, modifier = Modifier.weight(1f))
                GhostButton(stringResource(R.string.git_add_remote), { addRemote = true }, icon = Lucide.Plus)
            }
        }
        items(state.remotes, key = { "r:" + it.name }) { r ->
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SIcon(if (r.url.contains("github.com")) Lucide.Github else Lucide.Link, size = 17.dp, tint = c.text2)
                    Spacer(Modifier.width(Sp.md))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = Saqr.type.mono.copy(fontWeight = FontWeight.Medium), color = c.text1)
                        Text(r.url, style = Saqr.type.monoMicro, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.size(34.dp).clip(CircleShape).clickable(role = Role.Button) { a.onRemoveRemote(r.name) }, contentAlignment = Alignment.Center) {
                        SIcon(Lucide.Trash2, size = 15.dp, tint = c.text3, contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
        item("gh") {
            Column(Modifier.fillMaxWidth().padding(top = Sp.lg), verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
                if (state.remotes.isEmpty()) SecondaryButton(stringResource(R.string.git_publish_github), a.onPublish, Modifier.fillMaxWidth(), icon = Lucide.Github)
                else if (state.remotes.any { it.url.contains("github.com") })
                    SecondaryButton(stringResource(R.string.git_open_pr), a.onPullRequest, Modifier.fillMaxWidth(), icon = Lucide.GitPullRequest,
                        enabled = state.status.hasCommits)
                if (state.githubLogin == null) GhostButton(stringResource(R.string.git_link_github), a.onAccounts, icon = Lucide.Github)
            }
        }
    }
    if (newBranch) TextPromptDialog(stringResource(R.string.git_new_branch), stringResource(R.string.git_branch_name), "feature/login", Lucide.GitBranch,
        onDismiss = { newBranch = false }) { newBranch = false; a.onNewBranch(it) }
    if (addRemote) RemoteDialog(onDismiss = { addRemote = false }) { n, u -> addRemote = false; a.onAddRemote(n, u) }
    confirmDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null }, containerColor = c.surface1,
            icon = { SIcon(Lucide.Trash2, size = 24.dp, tint = c.danger) },
            text = { Text(stringResource(R.string.git_delete_branch_q, n), style = Saqr.type.body) },
            confirmButton = { TextButton(onClick = { confirmDelete = null; a.onDeleteBranch(n) }) { Text(stringResource(R.string.delete), color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel), color = c.text2) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BranchRow(b: GitBranch, a: GitActions, onDelete: (String) -> Unit) {
    val c = Saqr.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(Radius.sm))
                    .background(if (b.current) c.accentSoft else Color.Transparent)
                    .combinedClickable(onClick = { menu = true }, onLongClick = { menu = true }).padding(horizontal = Sp.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SIcon(if (b.current) Lucide.CircleDot else Lucide.GitBranch, size = 17.dp, tint = if (b.current) c.accentText else if (b.remote) c.info else c.text2)
                Spacer(Modifier.width(Sp.md))
                Text(b.shortName, style = Saqr.type.mono.copy(fontWeight = if (b.current) FontWeight.SemiBold else FontWeight.Normal),
                    color = c.text1, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(b.headShort, style = Saqr.type.monoMicro, color = c.text3)
            }
        }
        DropdownMenu(menu, { menu = false }, Modifier.background(c.surface2)) {
            if (!b.current) DropdownMenuItem(text = { Text(stringResource(R.string.git_switch), color = c.text1) }, leadingIcon = { SIcon(Lucide.Check, size = 16.dp) },
                onClick = { menu = false; a.onCheckout(b.shortName) })
            if (!b.current) DropdownMenuItem(text = { Text(stringResource(R.string.git_merge_into_current), color = c.text1) }, leadingIcon = { SIcon(Lucide.GitMerge, size = 16.dp) },
                onClick = { menu = false; a.onMerge(b.shortName) })
            if (!b.current && !b.remote) DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = c.danger) }, leadingIcon = { SIcon(Lucide.Trash2, size = 16.dp, tint = c.danger) },
                onClick = { menu = false; onDelete(b.shortName) })
            if (b.current) DropdownMenuItem(text = { Text(stringResource(R.string.git_current_branch), color = c.text3) }, onClick = { menu = false })
        }
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
fun TextPromptDialog(title: String, label: String, placeholder: String, icon: ImageVector, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    val c = Saqr.colors
    var v by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface1,
        icon = { SIcon(icon, size = 24.dp, tint = c.accentText) },
        title = { Text(title, style = Saqr.type.title2) },
        text = { SaqrField(v, { v = it }, label = label, placeholder = placeholder, mono = true) },
        confirmButton = { TextButton(enabled = v.isNotBlank(), onClick = { onOk(v.trim()) }) { Text(stringResource(R.string.create), color = c.accentText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.text2) } },
    )
}

@Composable
private fun RemoteDialog(onDismiss: () -> Unit, onOk: (String, String) -> Unit) {
    val c = Saqr.colors
    var name by remember { mutableStateOf("origin") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface1,
        icon = { SIcon(Lucide.Link, size = 24.dp, tint = c.accentText) },
        title = { Text(stringResource(R.string.git_add_remote), style = Saqr.type.title2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                SaqrField(name, { name = it }, label = stringResource(R.string.git_remote_name), mono = true)
                SaqrField(url, { url = it }, label = "URL", placeholder = "https://github.com/owner/repo.git", mono = true,
                    supporting = stringResource(R.string.git_remote_hint))
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && url.isNotBlank(), onClick = { onOk(name.trim(), url.trim()) }) { Text(stringResource(R.string.save), color = c.accentText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.text2) } },
    )
}

@Composable
fun PublishDialog(
    projectName: String,
    accounts: com.ctodrs.saqrcode.data.AccountStore,
    onDismiss: () -> Unit,
    onOk: (String, String, Boolean, String?) -> Unit,
) {
    val c = Saqr.colors
    val forgeHosts = remember { accounts.hosts().map { it.host } }
    var host by remember { mutableStateOf(if (forgeHosts.isEmpty()) "github.com" else "github.com") }
    var name by remember { mutableStateOf(projectName.lowercase()) }
    var private by remember { mutableStateOf(true) }
    var desc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface1,
        icon = { SIcon(if (host == "github.com") Lucide.Github else Lucide.Globe, size = 24.dp, tint = c.accentText) },
        title = { Text(stringResource(R.string.git_publish_title), style = Saqr.type.title2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                // v0.10: pick GitHub or one of the linked forge hosts (GitLab / Gitea).
                Segmented(
                    listOf("GitHub") + forgeHosts,
                    if (host == "github.com") 0 else 1 + forgeHosts.indexOf(host),
                    onSelect = { i -> host = if (i == 0) "github.com" else forgeHosts[i - 1] },
                )
                SaqrField(name, { name = it }, label = stringResource(R.string.git_repo_name), mono = true)
                SaqrField(desc, { desc = it }, label = stringResource(R.string.git_repo_desc))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SIcon(Lucide.Lock, size = 16.dp, tint = c.text2)
                    Spacer(Modifier.width(Sp.sm))
                    Text(stringResource(R.string.git_private), style = Saqr.type.body, color = c.text1, modifier = Modifier.weight(1f))
                    SaqrSwitch(private) { private = it }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onOk(host, name.trim(), private, desc.ifBlank { null }) }) { Text(stringResource(R.string.git_publish), color = c.accentText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.text2) } },
    )
}

@Composable
private fun PullRequestDialog(
    branch: String,
    bases: List<String>,
    forgeHost: String?,
    onDismiss: () -> Unit,
    onOk: (String, String, String?) -> Unit,
) {
    val c = Saqr.colors
    var title by remember { mutableStateOf(branch.substringAfterLast('/').replace('-', ' ').replaceFirstChar { it.uppercase() }) }
    var base by remember { mutableStateOf(bases.firstOrNull { it == "main" } ?: bases.firstOrNull { it == "master" } ?: bases.firstOrNull { it != branch } ?: "main") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.surface1,
        icon = { SIcon(Lucide.GitPullRequest, size = 24.dp, tint = c.accentText) },
        title = { Text(stringResource(if (forgeHost == null) R.string.git_open_pr else R.string.git_open_mr), style = Saqr.type.title2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Sp.md)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text("$branch  →  $base", style = Saqr.type.mono, color = c.text2)
                }
                SaqrField(title, { title = it }, label = stringResource(R.string.git_pr_title))
                SaqrField(base, { base = it }, label = stringResource(R.string.git_pr_base), mono = true)
                SaqrField(body, { body = it }, label = stringResource(R.string.git_pr_body), singleLine = false)
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank() && base.isNotBlank() && base != branch, onClick = { onOk(title, base, body) }) { Text(stringResource(R.string.create), color = c.accentText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.text2) } },
    )
}

/** Full-screen unified-diff viewer with old/new line numbers. */
@Composable
fun DiffViewerDialog(title: String, diff: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DiffViewerContent(title, diff, onDismiss)
    }
}

@Composable
fun DiffViewerContent(title: String, diff: String, onClose: () -> Unit) {
    val c = Saqr.colors
    val lines = remember(diff) { UnifiedDiff.parse(diff) }
    val (add, del) = remember(diff) { com.ctodrs.saqrcode.git.GitService.diffStats(diff) }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(Sp.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.surface2).clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                SIcon(Lucide.X, size = 18.dp, tint = c.text1, contentDescription = stringResource(R.string.close))
            }
            Spacer(Modifier.width(Sp.md))
            FileTypeIcon(title.substringAfterLast('/'), 20.dp)
            Spacer(Modifier.width(Sp.sm))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(title, style = Saqr.type.mono.copy(fontWeight = FontWeight.Medium), color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
            DiffChip(add, del)
        }
        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.git_no_diff), style = Saqr.type.bodySmall, color = c.text3) }
            return@Column
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            SelectionContainer {
                LazyColumn(Modifier.fillMaxSize().background(c.codeBg)) {
                    items(lines.size) { i -> DiffLineRow(lines[i]) }
                }
            }
        }
    }
}

@Composable
private fun DiffLineRow(l: UnifiedDiff.Line) {
    val c = Saqr.colors
    val numW: Dp = 34.dp
    when (l.kind) {
        UnifiedDiff.Kind.FILE -> Row(Modifier.fillMaxWidth().background(c.surface2).padding(horizontal = Sp.md, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FileTypeIcon(l.text.substringAfterLast('/'), 16.dp); Spacer(Modifier.width(Sp.sm))
            Text(l.text, style = Saqr.type.monoSmall.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        UnifiedDiff.Kind.HUNK -> Text(l.text, Modifier.fillMaxWidth().background(c.info.copy(alpha = 0.08f)).padding(horizontal = Sp.md, vertical = 3.dp),
            style = Saqr.type.monoMicro, color = c.info, maxLines = 1)
        UnifiedDiff.Kind.META -> {}
        else -> {
            val (bg, fg, sign) = when (l.kind) {
                UnifiedDiff.Kind.ADD -> Triple(c.diffAddBg, c.diffAdd, "+")
                UnifiedDiff.Kind.DEL -> Triple(c.diffDelBg, c.diffDel, "−")
                else -> Triple(Color.Transparent, c.text3, " ")
            }
            Row(Modifier.fillMaxWidth().background(bg).horizontalScroll(rememberScrollState())) {
                Text(l.oldNo?.toString() ?: "", Modifier.width(numW).padding(end = 4.dp), style = Saqr.type.monoMicro.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End), color = c.text3.copy(alpha = 0.7f))
                Text(l.newNo?.toString() ?: "", Modifier.width(numW).padding(end = 4.dp), style = Saqr.type.monoMicro.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End), color = c.text3.copy(alpha = 0.7f))
                Text(sign, Modifier.width(14.dp), style = Saqr.type.monoSmall, color = fg)
                Text(l.text, style = Saqr.type.monoSmall, color = if (l.kind == UnifiedDiff.Kind.CONTEXT) c.text2 else c.text1, softWrap = false,
                    modifier = Modifier.padding(end = Sp.lg))
            }
        }
    }
}

/** Parses `git diff` output into displayable lines with old/new numbers. */
object UnifiedDiff {
    enum class Kind { FILE, HUNK, ADD, DEL, CONTEXT, META }
    data class Line(val kind: Kind, val text: String, val oldNo: Int? = null, val newNo: Int? = null)

    private val hunk = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@(.*)$""")

    fun parse(diff: String): List<Line> {
        val out = ArrayList<Line>()
        var o = 0; var n = 0
        for (raw in diff.split('\n')) {
            when {
                raw.startsWith("diff --git ") -> out += Line(Kind.FILE, raw.removePrefix("diff --git ").substringAfter(" b/").ifBlank { raw })
                raw.startsWith("+++") || raw.startsWith("---") || raw.startsWith("index ") || raw.startsWith("new file") ||
                    raw.startsWith("deleted file") || raw.startsWith("similarity") || raw.startsWith("rename ") || raw.startsWith("old mode") || raw.startsWith("new mode") -> {}
                raw.startsWith("@@") -> {
                    val m = hunk.find(raw)
                    if (m != null) { o = m.groupValues[1].toInt(); n = m.groupValues[2].toInt() }
                    out += Line(Kind.HUNK, raw)
                }
                raw.startsWith("+") -> out += Line(Kind.ADD, raw.drop(1), newNo = n++)
                raw.startsWith("-") -> out += Line(Kind.DEL, raw.drop(1), oldNo = o++)
                raw.startsWith(" ") -> out += Line(Kind.CONTEXT, raw.drop(1), o++, n++)
                raw.startsWith("\\") -> out += Line(Kind.META, raw)
                raw.isNotEmpty() -> out += Line(Kind.META, raw)
            }
        }
        return out
    }
}

