package com.ctodrs.saqrcode.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.Diff
import com.ctodrs.saqrcode.agent.FileChange
import com.ctodrs.saqrcode.agent.PlanItem
import com.ctodrs.saqrcode.agent.QuickReplies
import com.ctodrs.saqrcode.agent.Tools
import com.ctodrs.saqrcode.ui.components.DiffChip
import com.ctodrs.saqrcode.ui.components.EmptyState
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.ProgressLine
import com.ctodrs.saqrcode.ui.components.SecondaryButton
import com.ctodrs.saqrcode.ui.components.Spinner
import com.ctodrs.saqrcode.ui.components.Status
import com.ctodrs.saqrcode.ui.components.StatusIcon
import com.ctodrs.saqrcode.ui.components.pressable
import com.ctodrs.saqrcode.ui.components.shimmer
import com.ctodrs.saqrcode.ui.icons.FileTypeIcon
import com.ctodrs.saqrcode.ui.icons.FolderIcon
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.ProviderBadge
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Motion
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import java.util.Locale

/** Everything the chat screen shows. Stateless so it can be previewed and screenshot-tested. */
class ChatUiState(
    val items: List<ChatItem>,
    val running: Boolean,
    val step: Int,
    val maxSteps: Int,
    val contextTokens: Int,
    val contextWindow: Int,
    val providerId: String,
    val model: String,
    val models: List<String>,
    val ready: Boolean,
    val projectFiles: () -> List<String> = { emptyList() },
)

@Composable
fun ChatContent(
    state: ChatUiState,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onDecide: (ChatItem.Approval, Boolean) -> Unit,
    onPickModel: (String) -> Unit,
    onManageProviders: () -> Unit,
    topInset: androidx.compose.ui.unit.Dp,
    listState: LazyListState = rememberLazyListState(),
    autoScroll: Boolean = true,
    onAnswer: (ChatItem.AskUser, Boolean) -> Unit = { _, _ -> },
) {
    val c = Saqr.colors
    val last = state.items.lastOrNull()
    val lastLen = (last as? ChatItem.Assistant)?.text?.length ?: 0
    if (autoScroll) LaunchedEffect(state.items.size, lastLen) {
        if (state.items.isNotEmpty()) listState.scrollToItem(state.items.size, Int.MAX_VALUE / 2)
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(top = topInset), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = stringResource(if (state.ready) R.string.agent_empty_title else R.string.no_provider_title),
                        body = stringResource(if (state.ready) R.string.agent_empty else R.string.no_provider),
                        action = if (state.ready) null else ({ PrimaryButton(stringResource(R.string.open_settings), onManageProviders, icon = Lucide.KeyRound) }),
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(), state = listState,
                    contentPadding = PaddingValues(start = Sp.lg, end = Sp.lg, top = topInset + Sp.sm, bottom = Sp.lg),
                ) {
                    items(state.items, key = { it.key }) { item ->
                        Box(Modifier.padding(vertical = spacingFor(item))) { ChatRow(item, onDecide, onAnswer) }
                    }
                    val showWorking = state.running && when (last) {
                        is ChatItem.Assistant -> !last.streaming
                        is ChatItem.Tool -> last.result != null
                        is ChatItem.Approval -> last.decision != null
                        is ChatItem.AskUser -> last.status != ChatItem.AskUser.Status.PENDING
                        else -> true
                    }
                    if (showWorking) item(key = "working") {
                        WorkingRow(Modifier.padding(vertical = Sp.sm))
                    }
                    item(key = "end") { Spacer(Modifier.height(1.dp)) }
                }
            }
            // soft fade above the composer
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(16.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, c.bg))))
        }
        if (state.items.isNotEmpty()) StepMeter(state)
        val replyText = if (!state.running) (last as? ChatItem.Assistant)?.takeIf { !it.streaming }?.text else null
        val quick = remember(replyText) { replyText?.let { QuickReplies.detect(it) }.orEmpty() }
        if (quick.isNotEmpty() && state.ready) QuickReplyChips(quick) { onInput(it) }
        Composer(state, input, onInput, onSend, onStop, onPickModel, onManageProviders)
    }
}

private fun spacingFor(item: ChatItem) = when (item) {
    is ChatItem.Tool -> 0.dp
    is ChatItem.Approval -> if (item.decision == null) 8.dp else 0.dp
    is ChatItem.AskUser -> if (item.status == ChatItem.AskUser.Status.PENDING) 8.dp else 4.dp
    is ChatItem.User -> 10.dp
    is ChatItem.Notice -> 8.dp
    is ChatItem.Assistant -> 6.dp
}

@Composable
fun ChatRow(item: ChatItem, onDecide: (ChatItem.Approval, Boolean) -> Unit, onAnswer: (ChatItem.AskUser, Boolean) -> Unit = { _, _ -> }) {
    when (item) {
        is ChatItem.User -> UserBubble(item.text)
        is ChatItem.Assistant -> if (item.text.isBlank() && item.streaming) WorkingRow() else MarkdownText(item.text)
        is ChatItem.Tool -> ToolRow(item)
        is ChatItem.Approval -> if (item.decision == null) ApprovalCard(item, onDecide)
            else if (item.action != null) ActionDecisionRow(item)
            else Column { item.changes.forEach { ChangeRow(it, item.decision == true, item.durationMs) } }
        is ChatItem.AskUser -> AskUserCard(item, onAnswer)
        is ChatItem.Notice -> NoticeCard(item)
    }
}

@Composable
private fun UserBubble(text: String) {
    val c = Saqr.colors
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val mw = this.maxWidth
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.widthIn(max = mw * 0.86f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                    .background(c.surface2)
                    .border(1.dp, c.border, RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                    .padding(horizontal = Sp.lg, vertical = Sp.md),
            ) {
                SelectionContainer { Text(text, style = Saqr.type.body.copy(color = c.text1, textDirection = TextDirection.Content)) }
            }
        }
    }
}

@Composable
fun WorkingRow(modifier: Modifier = Modifier) {
    val c = Saqr.colors
    Row(modifier.padding(vertical = Sp.xs), verticalAlignment = Alignment.CenterVertically) {
        Spinner(14.dp, c.accent, 1.8.dp)
        Spacer(Modifier.width(Sp.sm + 2.dp))
        Text(stringResource(R.string.thinking), style = Saqr.type.bodySmall.copy(fontWeight = FontWeight.Medium), color = c.text2, modifier = Modifier.shimmer())
    }
}

// ---------------------------------------------------------------- tool rows

@Composable
fun formatDuration(ms: Long): String {
    if (ms < 0) return ""
    val s = ms / 1000.0
    return when {
        s < 10 -> stringResource(R.string.dur_seconds, String.format(Locale.US, "%.1f", s).removeSuffix(".0"))
        s < 60 -> stringResource(R.string.dur_seconds, s.toInt().toString())
        else -> stringResource(R.string.dur_minutes, (s / 60).toInt().toString(), (s % 60).toInt().toString())
    }
}

/** One compact, expandable row: icon · label · chips · duration · status · chevron. */
@Composable
private fun CompactRow(
    leading: @Composable () -> Unit,
    title: @Composable () -> Unit,
    durationMs: Long,
    status: Status,
    expandable: Boolean,
    details: @Composable () -> Unit,
) {
    val c = Saqr.colors
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, tween(Motion.base, easing = Motion.emphasized), label = "chev")
    Column(Modifier.fillMaxWidth().animateContentSize(tween(Motion.base, easing = Motion.emphasized))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(Radius.sm))
                .clickable(enabled = expandable) { open = !open }.padding(horizontal = 2.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { leading() }
            Spacer(Modifier.width(Sp.md))
            Box(Modifier.weight(1f)) { title() }
            Spacer(Modifier.width(Sp.sm))
            val d = formatDuration(durationMs)
            if (d.isNotEmpty() && status != Status.RUNNING) {
                Text(d, style = Saqr.type.caption, color = c.text3, maxLines = 1)
                Spacer(Modifier.width(Sp.sm))
            }
            StatusIcon(status, 20.dp)
            Spacer(Modifier.width(Sp.sm))
            SIcon(Lucide.ChevronDown, Modifier.rotate(rot), size = 18.dp, tint = if (expandable) c.text3 else Color.Transparent)
        }
        AnimatedVisibility(open, enter = expandVertically(tween(Motion.base)) + fadeIn(), exit = shrinkVertically(tween(Motion.base)) + fadeOut()) {
            Box(Modifier.padding(start = 34.dp, bottom = Sp.sm, top = 2.dp)) { details() }
        }
    }
}

/** "Tafqit.java (Edited) +5 −5" — filename stays LTR inside RTL rows. */
@Composable
private fun FileTitle(name: String, action: String, added: Int? = null, removed: Int? = null) {
    val c = Saqr.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Ltr), color = c.text1,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Text("($action)", style = Saqr.type.caption, color = c.text3, maxLines = 1)
        if ((added ?: 0) > 0 || (removed ?: 0) > 0) { Spacer(Modifier.width(Sp.sm)); DiffChip(added, removed) }
    }
}

@Composable
private fun LabeledMono(label: String, value: String) {
    val c = Saqr.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(value, style = Saqr.type.monoSmall, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ToolRow(item: ChatItem.Tool) {
    val c = Saqr.colors
    val status = when { item.result == null -> Status.RUNNING; item.ok -> Status.OK; else -> Status.ERROR }
    val fileName = item.summary.substringAfterLast('/')
    CompactRow(
        leading = {
            when (item.name) {
                Tools.READ -> FileTypeIcon(fileName, 20.dp)
                Tools.LIST -> FolderIcon(open = false, size = 20.dp)
                Tools.RUN -> SIcon(Lucide.SquareTerminal, size = 20.dp, tint = c.info)
                Tools.SEARCH -> SIcon(Lucide.Search, size = 19.dp, tint = c.info)
                else -> SIcon(Lucide.Bot, size = 20.dp, tint = c.text2)
            }
        },
        title = {
            when (item.name) {
                Tools.READ -> FileTitle(fileName.ifBlank { item.summary }, stringResource(R.string.act_read))
                Tools.LIST -> FileTitle(item.summary.ifBlank { "." }, stringResource(R.string.act_listed))
                Tools.RUN -> LabeledMono(stringResource(R.string.tool_terminal), item.summary)
                Tools.SEARCH -> LabeledMono(stringResource(R.string.tool_search), item.summary)
                else -> LabeledMono(item.name, item.summary)
            }
        },
        durationMs = item.durationMs, status = status, expandable = item.result != null,
    ) {
        val r = item.result ?: ""
        if (item.name == Tools.RUN) OutputBox(r.removePrefix("[exit 0]\n"), command = item.summary)
        else OutputBox(r.lines().take(400).joinToString("\n"))
    }
}

@Composable
fun ChangeRow(ch: FileChange, applied: Boolean, durationMs: Long) {
    val diff = remember(ch) { Diff.lines(ch.before ?: "", ch.after ?: "") }
    val (add, del) = remember(diff) { Diff.stats(diff) }
    val action = stringResource(when { ch.isDelete -> R.string.act_deleted; ch.isNew -> R.string.act_wrote; else -> R.string.act_edited })
    CompactRow(
        leading = { FileTypeIcon(ch.path.substringAfterLast('/'), 20.dp) },
        title = { FileTitle(ch.path.substringAfterLast('/'), action, add, del) },
        durationMs = durationMs, status = if (applied) Status.OK else Status.ERROR, expandable = true,
    ) { DiffView(diff) }
}

@Composable
fun DiffView(diff: List<Diff.Line>) {
    val c = Saqr.colors
    val compact = remember(diff) { Diff.compact(diff).take(500) }
    val shape = RoundedCornerShape(Radius.sm)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth().clip(shape).background(c.codeBg).border(1.dp, c.border, shape)) {
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                compact.forEach { l ->
                    if (l == null) Text("   ⋯", style = Saqr.type.monoSmall, color = c.text3)
                    else {
                        val (prefix, bg, fg) = when (l.kind) {
                            Diff.Kind.ADD -> Triple("+ ", c.diffAddBg, c.diffAdd)
                            Diff.Kind.DEL -> Triple("− ", c.diffDelBg, c.diffDel)
                            Diff.Kind.SAME -> Triple("  ", Color.Transparent, c.text3)
                        }
                        Row(Modifier.background(bg).widthIn(min = 600.dp).padding(horizontal = Sp.sm)) {
                            Text(prefix, style = Saqr.type.monoSmall, color = fg)
                            Text(l.text, style = Saqr.type.monoSmall, color = if (l.kind == Diff.Kind.SAME) c.text2 else c.text1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(item: ChatItem.Approval, onDecide: (ChatItem.Approval, Boolean) -> Unit) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.lg)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.surface1).border(1.dp, c.accent.copy(alpha = 0.35f), shape).padding(Sp.lg),
        verticalArrangement = Arrangement.spacedBy(Sp.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                SIcon(Lucide.ListChecks, size = 17.dp, tint = c.accentText)
            }
            Spacer(Modifier.width(Sp.md))
            Column(Modifier.weight(1f)) {
                if (item.action != null) {
                    Text(stringResource(R.string.push_review_title), style = Saqr.type.title3, color = c.text1)
                    Text(stringResource(R.string.push_review_sub), style = Saqr.type.caption, color = c.text2)
                } else {
                    Text(stringResource(R.string.plan_review_title), style = Saqr.type.title3, color = c.text1)
                    Text(stringResource(R.string.plan_title, item.changes.size.toString()), style = Saqr.type.caption, color = c.text2)
                }
            }
        }
        Spacer(Modifier.height(Sp.xs))
        item.action?.let { act ->
            Text(act, style = Saqr.type.monoSmall, color = c.text1,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2).padding(Sp.md))
        }
        item.changes.forEach { PendingChangeRow(it) }
        Spacer(Modifier.height(Sp.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
            PrimaryButton(stringResource(R.string.approve), { onDecide(item, true) }, Modifier.weight(1f), icon = Lucide.Check)
            SecondaryButton(stringResource(R.string.reject), { onDecide(item, false) }, Modifier.weight(1f), icon = Lucide.X)
        }
    }
}

@Composable
private fun ActionDecisionRow(item: ChatItem.Approval) {
    val c = Saqr.colors
    val ok = item.decision == true
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        SIcon(if (ok) Lucide.ArrowUpFromLine else Lucide.X, size = 16.dp, tint = if (ok) c.success else c.danger)
        Spacer(Modifier.width(Sp.sm))
        Text(item.action!!.lineSequence().first(), style = Saqr.type.monoSmall, color = c.text2, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Sp.sm))
        Text(stringResource(if (ok) R.string.approved else R.string.rejected), style = Saqr.type.caption, color = if (ok) c.success else c.danger)
    }
}

@Composable
private fun PendingChangeRow(ch: FileChange) {
    val diff = remember(ch) { Diff.lines(ch.before ?: "", ch.after ?: "") }
    val (add, del) = remember(diff) { Diff.stats(diff) }
    val action = stringResource(when { ch.isDelete -> R.string.act_deleted; ch.isNew -> R.string.act_wrote; else -> R.string.act_edited })
    CompactRow(
        leading = { FileTypeIcon(ch.path.substringAfterLast('/'), 20.dp) },
        title = { FileTitle(ch.path, action, add, del) },
        durationMs = -1, status = Status.PENDING, expandable = true,
    ) { DiffView(diff) }
}

@Composable
private fun NoticeCard(item: ChatItem.Notice) {
    val c = Saqr.colors
    if (item.kind == ChatItem.Notice.Kind.STOPPED) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Row(Modifier.clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape).padding(horizontal = Sp.md, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SIcon(Lucide.Square, size = 12.dp, tint = c.text2)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.stopped), style = Saqr.type.caption, color = c.text2)
            }
        }
        return
    }
    val error = item.kind == ChatItem.Notice.Kind.ERROR
    val fg = if (error) c.danger else c.warning
    val bg = if (error) c.danger.copy(alpha = if (c.isDark) 0.08f else 0.06f) else c.warningBg
    val border = if (error) c.danger.copy(alpha = 0.4f) else c.warningBorder
    val shape = RoundedCornerShape(Radius.lg)
    Column(Modifier.fillMaxWidth().clip(shape).background(bg).border(1.dp, border, shape).padding(Sp.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(if (error) Lucide.CircleAlert else Lucide.TriangleAlert, size = 20.dp, tint = fg)
            Spacer(Modifier.width(Sp.sm))
            Text(stringResource(if (error) R.string.notice_error else R.string.notice_system),
                style = Saqr.type.title3.copy(fontWeight = FontWeight.SemiBold), color = fg)
        }
        Spacer(Modifier.height(Sp.sm))
        val body = when (item.kind) {
            ChatItem.Notice.Kind.STEP_LIMIT -> stringResource(R.string.notice_step_limit)
            ChatItem.Notice.Kind.RESTORED -> stringResource(R.string.notice_restored, item.text)
            else -> item.text
        }
        Text(body, style = Saqr.type.bodySmall.copy(color = c.text1, textDirection = TextDirection.Content))
    }
}

// ---------------------------------------------------------------- meter + composer

fun formatTokens(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
    n >= 1000 -> String.format(Locale.US, "%.1fk", n / 1000.0).replace(".0k", "k")
    else -> n.toString()
}

@Composable
private fun StepMeter(state: ChatUiState) {
    val c = Saqr.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = Sp.xxl, vertical = Sp.xs), verticalAlignment = Alignment.CenterVertically) {
        ProgressLine(state.step / state.maxSteps.toFloat(), Modifier.weight(1f))
        Spacer(Modifier.width(Sp.md))
        Text(stringResource(R.string.steps_n, state.step.toString()), style = Saqr.type.micro, color = c.text3)
        Spacer(Modifier.width(Sp.md))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text("${formatTokens(state.contextTokens)} / ${formatTokens(state.contextWindow)}", style = Saqr.type.monoMicro, color = c.text2)
        }
    }
}

@Composable
private fun Composer(
    state: ChatUiState, input: String, onInput: (String) -> Unit, onSend: () -> Unit, onStop: () -> Unit,
    onPickModel: (String) -> Unit, onManageProviders: () -> Unit,
) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.xxl)
    var attach by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = Sp.md, end = Sp.md, bottom = Sp.md, top = Sp.xs)
            .clip(shape).background(c.surface1).border(1.dp, c.borderStrong.copy(alpha = 0.7f), shape)
            .padding(start = Sp.md, end = Sp.md, top = Sp.md, bottom = Sp.sm),
    ) {
        BasicTextField(
            input, onInput, Modifier.fillMaxWidth().heightIn(min = 30.dp, max = 168.dp).padding(horizontal = Sp.xs),
            textStyle = Saqr.type.body.copy(color = c.text1, textDirection = TextDirection.Content),
            cursorBrush = SolidColor(c.accent), maxLines = 7,
            decorationBox = { inner ->
                Box {
                    if (input.isEmpty()) Text(stringResource(R.string.ask_agent_hint), style = Saqr.type.body, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(Sp.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(Modifier.size(40.dp).pressable { attach = true }.clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape),
                    contentAlignment = Alignment.Center) { SIcon(Lucide.Plus, size = 20.dp, tint = c.text1, contentDescription = stringResource(R.string.attach_file)) }
                DropdownMenu(attach, { attach = false }, Modifier.heightIn(max = 360.dp).background(c.surface2)) {
                    Text(stringResource(R.string.attach_file), style = Saqr.type.caption, color = c.text3, modifier = Modifier.padding(horizontal = Sp.lg, vertical = Sp.xs))
                    val files = remember(attach) { if (attach) state.projectFiles() else emptyList() }
                    files.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f, style = Saqr.type.monoSmall, color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { FileTypeIcon(f.substringAfterLast('/'), 18.dp) },
                            onClick = { attach = false; onInput((input.trimEnd() + " @" + f + " ").trimStart()) },
                        )
                    }
                }
            }
            Spacer(Modifier.width(Sp.sm))
            Box(Modifier.weight(1f, fill = false)) {
                Row(
                    Modifier.height(40.dp).pressable { modelMenu = true }.clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape)
                        .padding(start = 6.dp, end = Sp.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderBadge(state.providerId, 28.dp)
                    Spacer(Modifier.width(Sp.sm))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(state.model.ifBlank { "—" }, style = Saqr.type.label, color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 170.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    SIcon(Lucide.ChevronDown, size = 16.dp, tint = c.text3)
                }
                DropdownMenu(modelMenu, { modelMenu = false }, Modifier.background(c.surface2)) {
                    state.models.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, style = Saqr.type.label, color = c.text1) },
                            trailingIcon = { if (m == state.model) SIcon(Lucide.Check, size = 16.dp, tint = c.accentText) },
                            onClick = { modelMenu = false; onPickModel(m) },
                        )
                    }
                    HorizontalDivider(color = c.border)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_providers), style = Saqr.type.label, color = c.text1) },
                        leadingIcon = { SIcon(Lucide.Settings, size = 16.dp) },
                        onClick = { modelMenu = false; onManageProviders() },
                    )
                }
            }
            Spacer(Modifier.weight(0.001f).widthIn(min = Sp.sm))
            SendButton(running = state.running, enabled = input.isNotBlank() && state.ready, onSend = onSend, onStop = onStop)
        }
    }
}

@Composable
private fun SendButton(running: Boolean, enabled: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val c = Saqr.colors
    if (running) {
        Box(Modifier.size(42.dp).pressable(onClick = onStop), contentAlignment = Alignment.Center) {
            Spinner(42.dp, c.accent, 2.dp)
            Box(Modifier.size(34.dp).clip(CircleShape).background(c.surface3), contentAlignment = Alignment.Center) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c.text1))
            }
        }
    } else {
        Box(
            Modifier.size(42.dp).pressable(enabled, onSend).clip(CircleShape)
                .background(if (enabled) Brush.verticalGradient(listOf(c.accent, Color(0xFFCF962F))) else SolidColor(c.surface3)),
            contentAlignment = Alignment.Center,
        ) { SIcon(Lucide.ArrowUp, size = 20.dp, tint = if (enabled) c.onAccent else c.text3, contentDescription = stringResource(R.string.send)) }
    }
}

// ---------------------------------------------------------------- plan pill (used by the top bar)

@Composable
fun PlanPillContent(plan: List<PlanItem>, running: Boolean, onPause: () -> Unit, title: String) {
    val c = Saqr.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                Box(Modifier.size(32.dp).pressable(onClick = onPause).clip(CircleShape), contentAlignment = Alignment.Center) {
                    SIcon(Lucide.Pause, size = 18.dp, tint = c.text1, contentDescription = stringResource(R.string.stop))
                }
            } else Spacer(Modifier.width(Sp.sm))
            Row(
                Modifier.clip(CircleShape).clickable(enabled = plan.isNotEmpty()) { open = true }.padding(start = Sp.xs, end = Sp.sm, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (plan.isNotEmpty()) {
                    val cur = (plan.indexOfFirst { it.active }.takeIf { it >= 0 } ?: plan.count { it.done }.coerceAtMost(plan.size - 1)) + 1
                    Text(stringResource(R.string.plan_pill, cur.toString(), plan.size.toString()), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    SIcon(Lucide.ChevronDown, size = 16.dp, tint = c.text3)
                } else {
                    if (running) { Box(Modifier.size(7.dp).clip(CircleShape).background(c.accent)); Spacer(Modifier.width(Sp.sm)) }
                    Text(title, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp).then(if (running) Modifier.shimmer() else Modifier))
                }
            }
        }
        DropdownMenu(open, { open = false }, Modifier.widthIn(min = 260.dp).background(c.surface2)) {
            Text(stringResource(R.string.plan_header), style = Saqr.type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.text3,
                modifier = Modifier.padding(horizontal = Sp.lg, vertical = Sp.xs))
            plan.forEach { p ->
                Row(Modifier.padding(horizontal = Sp.lg, vertical = Sp.sm), verticalAlignment = Alignment.CenterVertically) {
                    when {
                        p.done -> StatusIcon(Status.OK, 18.dp)
                        p.active && running -> StatusIcon(Status.RUNNING, 18.dp)
                        p.active -> StatusIcon(Status.PENDING, 18.dp)
                        else -> Box(Modifier.size(18.dp).clip(CircleShape).border(1.5.dp, c.borderStrong, CircleShape))
                    }
                    Spacer(Modifier.width(Sp.md))
                    Text(p.title, style = Saqr.type.bodySmall.copy(textDirection = TextDirection.Content),
                        color = if (p.done) c.text3 else c.text1, textDecoration = if (p.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                }
            }
        }
    }
}
