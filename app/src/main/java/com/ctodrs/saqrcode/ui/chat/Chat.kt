package com.ctodrs.saqrcode.ui.chat

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.unit.sp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.Budget
import com.ctodrs.saqrcode.agent.AgentRole
import com.ctodrs.saqrcode.agent.AgentRoles
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.TeamCost
import com.ctodrs.saqrcode.agent.FollowUps
import com.ctodrs.saqrcode.agent.PromptLibrary
import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.agent.SlashCommands
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
    val customCommands: List<SlashCommand> = emptyList(),
    /** Tokens used this session (input + output) and their cost in USD (null = no price known). */
    val sessionTokens: Long = 0,
    val sessionCost: Double? = null,
    val usageEstimated: Boolean = false,
    /** v0.8 team mode: the reviewer's own spend, shown separately in the meter. */
    val reviewTokens: Long = 0,
    val reviewCost: Double? = null,
    /** v0.8 team mode: the planner's own spend. */
    val plannerTokens: Long = 0,
    val plannerCost: Double? = null,
    val budget: Double? = null,
    val monthSpent: Double = 0.0,
    /** Images attached in the composer, not sent yet. */
    val pendingImages: List<com.ctodrs.saqrcode.agent.ImagePart> = emptyList(),
    /** The pending images go out as an "Image → Code" request. */
    val imageToCode: Boolean = false,
    /** v0.6: dictation state for the mic button. */
    val voice: VoiceUi = VoiceUi(),
    /** v0.6: key of the assistant message being read aloud, or -1. */
    val speakingKey: Long = -1L,
) {
    val budgetLevel get() = Budget.level(monthSpent, budget)
}

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
    onDecidePlan: (ChatItem.PlanReview, Boolean) -> Unit = { _, _ -> },
    onMeter: () -> Unit = {},
    onRestore: (ChatItem.User) -> Unit = {},
    onImageAction: (ImageAction) -> Unit = {},
    onRemoveImage: (Int) -> Unit = {},
    onMic: () -> Unit = {},
    onMicLongPress: () -> Unit = {},
    onSpeak: ((ChatItem.Assistant) -> Unit)? = null,
    onResumeTask: () -> Unit = {},
    workspaceDir: java.io.File? = null,
    onRegenerate: (() -> Unit)? = null,
    onEditResend: ((ChatItem.User, String) -> Unit)? = null,
    onSwitchBranch: ((ChatItem.User, Int) -> Unit)? = null,
) {
    val c = Saqr.colors
    var modelMenu by remember { mutableStateOf(false) }
    // turns that only called tools have no text: they stay in the session but are not shown
    val shown = state.items.filter { it !is ChatItem.Assistant || it.visible }
    val last = shown.lastOrNull()
    val lastAssistantIdx = shown.indexOfLast { it is ChatItem.Assistant }
    val lastLen = (last as? ChatItem.Assistant)?.text?.length ?: 0
    if (autoScroll) LaunchedEffect(shown.size, lastLen) {
        if (shown.isNotEmpty()) listState.scrollToItem(shown.size, Int.MAX_VALUE / 2)
    }
    // on tablets the conversation keeps a readable column instead of stretching edge to edge
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 780.dp).fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) {
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
                    contentPadding = PaddingValues(start = Sp.lg, end = Sp.lg, top = topInset + Sp.sm + 52.dp, bottom = Sp.lg),
                ) {
                    itemsIndexed(shown, key = { _, it -> it.key }) { i, item ->
                        Box(Modifier.padding(vertical = spacingFor(item))) {
                            // "Read aloud" sits under the reply that ends a turn
                            val endsTurn = item is ChatItem.Assistant && !item.streaming &&
                                shown.getOrNull(i + 1).let { it == null || it is ChatItem.User || it is ChatItem.Notice } &&
                                !(state.running && i == shown.lastIndex)
                            ChatRow(item, onDecide, onAnswer, onDecidePlan, onRestore = if (state.running) null else onRestore,
                                onSpeak = if (endsTurn) onSpeak else null, speaking = item.key == state.speakingKey,
                                showActions = endsTurn, sources = if (endsTurn) Sources.forTurn(shown, i) else emptyList(),
                                onResumeTask = if (state.running) null else onResumeTask, workspaceDir = workspaceDir,
                                onRegenerate = if (state.running || i != lastAssistantIdx) null else onRegenerate,
                                onEditResend = if (state.running) null else onEditResend,
                                onSwitchBranch = if (state.running) null else onSwitchBranch)
                        }
                    }
                    val showWorking = state.running && when (last) {
                        is ChatItem.Assistant -> !last.streaming
                        is ChatItem.Tool -> last.result != null
                        is ChatItem.Approval -> last.decision != null
                        is ChatItem.AskUser -> last.status != ChatItem.AskUser.Status.PENDING
                        is ChatItem.PlanReview -> last.status != ChatItem.PlanReview.Status.PENDING
                        else -> true
                    }
                    if (showWorking) item(key = "working") {
                        WorkingRow(Modifier.padding(vertical = Sp.sm))
                    }
                    item(key = "end") { Spacer(Modifier.height(1.dp)) }
                }
            }
            if (shown.isNotEmpty()) ChatSearchOverlay(shown, listState, stringResource(R.string.app_name), Modifier.align(Alignment.TopEnd).padding(top = topInset))
            // soft fade above the composer
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(16.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, c.bg))))
        }
        if (shown.isNotEmpty() || state.sessionTokens > 0) StepMeter(state, onMeter)
        if (state.voice.listening) ListeningBar(state.voice)
        if (state.budgetLevel == Budget.Level.WARN || state.budgetLevel == Budget.Level.OVER) BudgetBanner(state, onMeter)
        // v0.13.5 desert mode: the composer names the state before the user wonders why a send does nothing.
        if (com.ctodrs.saqrcode.agent.DesertState.active) DesertBanner()
        val slash = remember(input, state.customCommands) { SlashCommands.suggestions(input, state.customCommands) }
        val replyText = if (!state.running) (last as? ChatItem.Assistant)?.takeIf { !it.streaming }?.text else null
        val quick = remember(replyText) { replyText?.let { QuickReplies.detect(it) }.orEmpty() }
        if (slash.isNotEmpty()) SlashPopup(slash) { cmd ->
            if (cmd.action == SlashCommand.Action.MODEL) { onInput(""); modelMenu = true } else onInput("/" + cmd.name + " ")
        }
        else if (quick.isNotEmpty() && state.ready) QuickReplyChips(quick) { onInput(it) }
        else if (state.ready && input.isBlank()) {
            val kinds = remember(state.items.size, lastLen, state.running, last) { FollowUps.suggest(shown, state.running) }
            if (kinds.isNotEmpty()) {
                val chips = kinds.map { k ->
                    when (k) {
                        FollowUps.Kind.FIX_ERROR -> R.string.followup_label_fix to R.string.followup_fix
                        FollowUps.Kind.RUN_TESTS -> R.string.followup_label_tests to R.string.followup_tests
                        FollowUps.Kind.REVIEW -> R.string.followup_label_review to R.string.followup_review
                        FollowUps.Kind.COMMIT -> R.string.followup_label_commit to R.string.followup_commit
                        FollowUps.Kind.SUMMARIZE -> R.string.followup_label_summarize to R.string.followup_summarize
                        FollowUps.Kind.NEXT_STEPS -> R.string.followup_label_next to R.string.followup_next
                    }
                }.map { (l, t) -> stringResource(l) to stringResource(t) }
                ReplyChips(chips) { onInput(it) }
            }
        }
        val send = {
            if (SlashCommands.parse(input, state.customCommands)?.command?.action == SlashCommand.Action.MODEL) { onInput(""); modelMenu = true }
            else onSend()
        }
        Composer(state, input, onInput, send, onStop, onPickModel, onManageProviders, modelMenu, { modelMenu = it }, onImageAction, onRemoveImage,
            onMic, onMicLongPress)
    }
    }
}

private fun spacingFor(item: ChatItem) = when (item) {
    is ChatItem.Tool -> 0.dp
    is ChatItem.Approval -> if (item.decision == null) 8.dp else 0.dp
    is ChatItem.AskUser -> if (item.status == ChatItem.AskUser.Status.PENDING) 8.dp else 4.dp
    is ChatItem.PlanReview -> 8.dp
    is ChatItem.User -> 10.dp
    is ChatItem.Notice -> 8.dp
    is ChatItem.Review -> 8.dp
    is ChatItem.Assistant -> 6.dp
}

@Composable
fun ChatRow(
    item: ChatItem, onDecide: (ChatItem.Approval, Boolean) -> Unit, onAnswer: (ChatItem.AskUser, Boolean) -> Unit = { _, _ -> },
    onDecidePlan: (ChatItem.PlanReview, Boolean) -> Unit = { _, _ -> },
    onRestore: ((ChatItem.User) -> Unit)? = null,
    onSpeak: ((ChatItem.Assistant) -> Unit)? = null,
    speaking: Boolean = false,
    onResumeTask: (() -> Unit)? = null,
    workspaceDir: java.io.File? = null,
    showActions: Boolean = false,
    sources: List<SourceRef> = emptyList(),
    onRegenerate: (() -> Unit)? = null,
    onEditResend: ((ChatItem.User, String) -> Unit)? = null,
    onSwitchBranch: ((ChatItem.User, Int) -> Unit)? = null,
) {
    when (item) {
        is ChatItem.User -> Column(Modifier.fillMaxWidth()) {
            if (item.images.isNotEmpty()) UserImages(item.images)
            if (item.text.isNotBlank()) UserBubble(item.text)
            val br = item.branches
            if (br != null && br.count > 1 && onSwitchBranch != null) BranchSwitcher(br.active + 1, br.count, { onSwitchBranch(item, -1) }, { onSwitchBranch(item, 1) })
            if (onEditResend != null && item.text.isNotBlank()) EditResend(item.text) { onEditResend(item, it) }
            if (item.checkpointId != null && onRestore != null) RestoreHere { onRestore(item) }
        }
        is ChatItem.Assistant -> if (item.text.isBlank() && item.streaming) WorkingRow() else Column(Modifier.fillMaxWidth()) {
            MarkdownText(item.text, streaming = item.streaming)
            if (!item.streaming && workspaceDir != null) {
                val files = androidx.compose.runtime.remember(item.text, workspaceDir) { Deliverables.find(item.text, workspaceDir) }
                files.forEach { FileCard(it, Modifier.padding(top = Sp.sm)) }
            }
            if (showActions && item.text.isNotBlank()) {
                if (sources.isNotEmpty()) SourcesRow(sources, Modifier.padding(top = Sp.sm))
                MessageActionBar(item.text, item.feedback, { item.feedback = it }, speaking, onSpeak?.let { s -> { s(item) } }, onRegenerate = onRegenerate)
            }
        }
        is ChatItem.Tool -> ToolRow(item)
        is ChatItem.Approval -> if (item.decision == null) ApprovalCard(item, onDecide)
            else if (item.action != null) ActionDecisionRow(item)
            else Column { item.changes.forEach { ChangeRow(it, item.decision == true, item.durationMs) } }
        is ChatItem.AskUser -> AskUserCard(item, onAnswer)
        is ChatItem.PlanReview -> PlanReviewCard(item, onDecidePlan)
        is ChatItem.Review -> ReviewCard(item)
        is ChatItem.Notice -> when (item.kind) {
            ChatItem.Notice.Kind.INTERRUPTED -> InterruptedCard(onResumeTask)
            ChatItem.Notice.Kind.RESUMED -> ResumedPill()
            else -> NoticeCard(item)
        }
    }
}

/** Small "🔊 Read aloud" / "■ Stop reading" action under a finished reply. */
@Composable
private fun ReadAloud(speaking: Boolean, onClick: () -> Unit) {
    val c = Saqr.colors
    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
        Row(
            Modifier.heightIn(min = 32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Sp.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SIcon(if (speaking) Lucide.Square else Lucide.Volume2, size = 14.dp, tint = if (speaking) c.accentText else c.text3)
            Spacer(Modifier.width(5.dp))
            Text(stringResource(if (speaking) R.string.stop_reading else R.string.read_aloud), style = Saqr.type.caption.copy(fontWeight = FontWeight.Medium),
                color = if (speaking) c.accentText else c.text3, maxLines = 1)
        }
    }
}

/** A turn cut off by a restart / phone shutdown, with a Resume button. */
@Composable
private fun InterruptedCard(onResume: (() -> Unit)?) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.lg)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.warningBg).border(1.dp, c.warningBorder, shape).padding(Sp.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(Lucide.TriangleAlert, size = 20.dp, tint = c.warning)
            Spacer(Modifier.width(Sp.sm))
            Text(stringResource(R.string.interrupted_title), style = Saqr.type.title3.copy(fontWeight = FontWeight.SemiBold), color = c.warning)
        }
        Spacer(Modifier.height(Sp.xs))
        Text(stringResource(R.string.interrupted_body), style = Saqr.type.bodySmall, color = c.text1)
        if (onResume != null) {
            Spacer(Modifier.height(Sp.md))
            PrimaryButton(stringResource(R.string.resume_task), onResume, icon = Lucide.RotateCcw)
        }
    }
}

@Composable
private fun ResumedPill() {
    val c = Saqr.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Row(Modifier.clip(CircleShape).background(c.accentSoft).border(1.dp, c.accent.copy(alpha = 0.35f), CircleShape).padding(horizontal = Sp.md, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            SIcon(Lucide.RotateCcw, size = 12.dp, tint = c.accentText)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.resumed_after_interruption), style = Saqr.type.caption, color = c.accentText)
        }
    }
}

/** "Listening… (العربية)" with a level meter, above the composer while dictating. */
@Composable
private fun ListeningBar(v: VoiceUi) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.md)
    val pulse by animateFloatAsState(v.level, tween(120), label = "lvl")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Sp.md, vertical = Sp.xs).clip(shape).background(c.accentSoft)
            .border(1.dp, c.accent.copy(alpha = 0.35f), shape).padding(horizontal = Sp.md, vertical = Sp.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c.danger))
        Spacer(Modifier.width(Sp.sm))
        Text(stringResource(R.string.voice_listening), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
        Spacer(Modifier.width(6.dp))
        Text("· " + stringResource(if (v.lang.startsWith("ar")) R.string.lang_ar else R.string.lang_en), style = Saqr.type.caption, color = c.text2, maxLines = 1)
        Spacer(Modifier.weight(1f))
        // five bars that follow the microphone level
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(0.45f, 0.8f, 1f, 0.7f, 0.4f).forEach { k ->
                Box(Modifier.width(3.dp).height((6 + 16 * (0.15f + 0.85f * pulse) * k).dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
            }
        }
        Spacer(Modifier.width(Sp.sm))
        Text(stringResource(R.string.voice_tap_stop), style = Saqr.type.caption, color = c.text3, maxLines = 1)
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

/** Small "↺ Restore to here" action under a user message that has a checkpoint. */
@Composable
private fun RestoreHere(onClick: () -> Unit) {
    val c = Saqr.colors
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.End) {
        Row(
            Modifier.heightIn(min = 32.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = Sp.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SIcon(Lucide.RotateCcw, size = 13.dp, tint = c.accentText)
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.restore_to_here), style = Saqr.type.caption.copy(fontWeight = FontWeight.Medium), color = c.accentText, maxLines = 1)
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
                    val (title, sub) = when (item.actionKind) {
                        ChatItem.Approval.ActionKind.COMMIT -> R.string.commit_review_title to R.string.commit_review_sub
                        ChatItem.Approval.ActionKind.COMMAND -> R.string.command_review_title to R.string.command_review_sub // v0.15
                        ChatItem.Approval.ActionKind.PUSH -> R.string.push_review_title to R.string.push_review_sub
                    }
                    Text(stringResource(title), style = Saqr.type.title3, color = c.text1)
                    Text(stringResource(sub), style = Saqr.type.caption, color = c.text2)
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
        val icon = when (item.actionKind) {
            ChatItem.Approval.ActionKind.COMMIT -> Lucide.GitCommitHorizontal
            ChatItem.Approval.ActionKind.COMMAND -> Lucide.SquareTerminal
            ChatItem.Approval.ActionKind.PUSH -> Lucide.ArrowUpFromLine
        }
        SIcon(if (ok) icon else Lucide.X, size = 16.dp, tint = if (ok) c.success else c.danger)
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
    // v0.16 Chief mode: routine team events are a quiet pill; conflicts, failures and reassignments a warning card
    if (item.kind == ChatItem.Notice.Kind.TEAM && item.count in TEAM_PILLS) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Row(Modifier.clip(RoundedCornerShape(Radius.lg)).background(c.surface2).border(1.dp, c.border, RoundedCornerShape(Radius.lg))
                .padding(horizontal = Sp.md, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                SIcon(if (item.count == ChatItem.Notice.TEAM_STARTED) Lucide.User else Lucide.Users, size = 12.dp, tint = c.text2)
                Spacer(Modifier.width(6.dp))
                Text(teamNoticeText(item), style = Saqr.type.caption.copy(textDirection = TextDirection.Content), color = c.text2)
            }
        }
        return
    }
    // v0.17: router decisions and captured lessons are quiet pills (the audit log keeps the full line)
    // v0.18: an attached skill is a quiet pill too (the audit log keeps the full reason)
    if (item.kind == ChatItem.Notice.Kind.ROUTER || item.kind == ChatItem.Notice.Kind.LESSON || item.kind == ChatItem.Notice.Kind.SKILL) {
        val text = when (item.kind) {
            ChatItem.Notice.Kind.ROUTER -> {
                val p = item.text.split('|')
                stringResource(R.string.notice_router, p.getOrElse(0) { "" }, p.getOrElse(1) { "" }, p.getOrElse(2) { "" })
            }
            ChatItem.Notice.Kind.SKILL -> {
                val p = item.text.split('|')
                stringResource(if (item.count == 1) R.string.notice_skill_named else R.string.notice_skill_auto,
                    p.getOrElse(0) { "" }, p.getOrElse(1) { "" })
            }
            else -> stringResource(if (item.count == 1) R.string.notice_lesson_updated else R.string.notice_lesson_new, item.text)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Row(Modifier.clip(RoundedCornerShape(Radius.lg)).background(c.surface2).border(1.dp, c.border, RoundedCornerShape(Radius.lg))
                .padding(horizontal = Sp.md, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                SIcon(when (item.kind) {
                    ChatItem.Notice.Kind.ROUTER -> Lucide.Route
                    ChatItem.Notice.Kind.SKILL -> Lucide.Sparkles
                    else -> Lucide.BookMarked
                }, size = 12.dp, tint = c.text2)
                Spacer(Modifier.width(6.dp))
                Text(text, style = Saqr.type.caption.copy(textDirection = TextDirection.Content), color = c.text2)
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
            ChatItem.Notice.Kind.RESTORED -> if (item.count >= 0) stringResource(R.string.notice_checkpoint_restored, item.text, item.count.toString())
                else stringResource(R.string.notice_restored, item.text)
            ChatItem.Notice.Kind.COMPACTED -> stringResource(R.string.notice_compacted)
            ChatItem.Notice.Kind.LOOP -> stringResource(R.string.notice_loop)
            ChatItem.Notice.Kind.PLANNED -> stringResource(R.string.notice_planned, item.count.coerceAtLeast(0).toString())
            ChatItem.Notice.Kind.DESERT -> stringResource(R.string.notice_desert)
            // v0.15 autonomous core
            ChatItem.Notice.Kind.VERIFY -> stringResource(when (item.count) {
                ChatItem.Notice.VERIFY_RETRY -> R.string.notice_verify_retry
                0 -> R.string.notice_verify_passed
                1 -> R.string.notice_verify_failed
                else -> R.string.notice_verify_unverified
            })
            ChatItem.Notice.Kind.BUDGET -> stringResource(when (item.text) {
                "time" -> R.string.notice_budget_time
                "daily_cap" -> R.string.notice_spend_cap_daily
                "monthly_cap" -> R.string.notice_spend_cap_monthly
                else -> R.string.notice_budget_cost
            })
            ChatItem.Notice.Kind.FALLBACK -> stringResource(R.string.notice_fallback, item.text)
            ChatItem.Notice.Kind.TEAM -> teamNoticeText(item)
            else -> item.text
        }
        Text(body, style = Saqr.type.bodySmall.copy(color = c.text1, textDirection = TextDirection.Content))
    }
}

private val TEAM_PILLS = setOf(ChatItem.Notice.TEAM_SELECTED, ChatItem.Notice.TEAM_ESTIMATE, ChatItem.Notice.TEAM_COST, ChatItem.Notice.TEAM_STARTED)

/** v0.16: a team role's localized name. */
@Composable
internal fun roleName(role: AgentRole): String = stringResource(when (role) {
    AgentRole.CHIEF -> R.string.role_chief
    AgentRole.PLANNER -> R.string.role_planner
    AgentRole.RESEARCH -> R.string.role_research
    AgentRole.ARCHITECT -> R.string.role_architect
    AgentRole.DEVELOPER -> R.string.role_developer
    AgentRole.DEBUGGER -> R.string.role_debugger
    AgentRole.SECURITY -> R.string.role_security
    AgentRole.TESTER -> R.string.role_tester
    AgentRole.REVIEWER -> R.string.role_reviewer
    AgentRole.OPTIMIZER -> R.string.role_optimizer
    AgentRole.DOCUMENTATION -> R.string.role_documentation
    AgentRole.DEPLOYMENT -> R.string.role_deployment
})

@Composable
private fun roleNameOf(id: String): String = AgentRoles.byId(id)?.let { roleName(it) } ?: id

/** v0.16: the localized text of a Chief-mode notice (its data is in [ChatItem.Notice.text], see the TEAM_* subtypes). */
@Composable
private fun teamNoticeText(item: ChatItem.Notice): String {
    val p = item.text.split('|')
    fun at(i: Int) = p.getOrNull(i).orEmpty()
    return when (item.count) {
        ChatItem.Notice.TEAM_SELECTED -> if (item.text.isBlank()) stringResource(R.string.team_notice_no_team)
            else stringResource(R.string.team_notice_selected, item.text.split(',').map { roleNameOf(it) }.joinToString(stringResource(R.string.team_sep)))
        ChatItem.Notice.TEAM_ESTIMATE -> if (item.text.isBlank()) stringResource(R.string.team_notice_estimate_unpriced)
            else stringResource(R.string.team_notice_estimate, item.text)
        ChatItem.Notice.TEAM_COST -> {
            val names = AgentRoles.all.associateWith { roleName(it) }
            stringResource(R.string.team_notice_cost, TeamCost.line(TeamCost.decode(item.text)) { names[it] ?: it.nameEn })
        }
        ChatItem.Notice.TEAM_STARTED -> stringResource(R.string.team_notice_started, roleNameOf(at(0)), at(1))
        ChatItem.Notice.TEAM_REASSIGNED -> stringResource(R.string.team_notice_reassigned, at(1), roleNameOf(at(0)))
        ChatItem.Notice.TEAM_CONFLICT -> stringResource(R.string.team_notice_conflict, item.text)
        ChatItem.Notice.TEAM_FAILED -> stringResource(R.string.team_notice_failed, roleNameOf(at(0)), at(1), at(2))
        ChatItem.Notice.TEAM_GRAPH_FALLBACK -> stringResource(R.string.team_notice_graph_fallback)
        ChatItem.Notice.TEAM_REWORK -> stringResource(R.string.team_notice_rework, roleNameOf(at(0)), at(1))
        else -> item.text
    }
}

/** v0.7 team mode: the reviewer's verdict card (running → ok / issues / failed). */
@Composable
private fun ReviewCard(item: ChatItem.Review) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.lg)
    val (icon, fg, bg, border, titleRes) = when (item.status) {
        ChatItem.Review.ReviewStatus.RUNNING -> Quintet(Lucide.ShieldCheck, c.warning, c.warningBg, c.warningBorder, R.string.review_running)
        ChatItem.Review.ReviewStatus.OK -> Quintet(Lucide.CircleCheck, c.success, c.success.copy(alpha = if (c.isDark) 0.08f else 0.06f), c.success.copy(alpha = 0.4f), R.string.review_ok)
        ChatItem.Review.ReviewStatus.ISSUES -> Quintet(Lucide.TriangleAlert, c.warning, c.warningBg, c.warningBorder, R.string.review_issues)
        ChatItem.Review.ReviewStatus.FAILED -> Quintet(Lucide.CircleAlert, c.text3, c.surface1, c.border, R.string.review_failed)
    }
    Column(Modifier.fillMaxWidth().clip(shape).background(bg).border(1.dp, border, shape).padding(Sp.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(icon, size = 20.dp, tint = fg)
            Spacer(Modifier.width(Sp.sm))
            Text(stringResource(titleRes), style = Saqr.type.title3.copy(fontWeight = FontWeight.SemiBold), color = fg)
            Spacer(Modifier.weight(1f))
            Text(ltr(item.model), style = Saqr.type.micro, color = c.text3, maxLines = 1)
        }
        if (item.summary.isNotBlank()) {
            Spacer(Modifier.height(Sp.xs))
            Text(item.summary, style = Saqr.type.bodySmall.copy(color = c.text1, textDirection = TextDirection.Content))
        }
        if (item.issues.isNotEmpty()) {
            Spacer(Modifier.height(Sp.sm))
            item.issues.forEach { issue ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                    SIcon(Lucide.ChevronRight, size = 14.dp, tint = fg)
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(ltr(issue.file + ":" + issue.line), style = Saqr.type.monoMicro, color = c.accentText)
                        Text(issue.comment, style = Saqr.type.bodySmall.copy(color = c.text1, textDirection = TextDirection.Content))
                    }
                }
            }
        }
    }
}

private data class Quintet(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val fg: Color, val bg: Color, val border: Color, val title: Int,
)

fun formatTokens(n: Long): String = formatTokens(n.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

/** "$0.42", "<$0.01" for tiny non-zero amounts. */
fun formatUsd(v: Double): String = when {
    v > 0 && v < 0.01 -> "<$0.01"
    v >= 1000 -> String.format(Locale.US, "$%,.0f", v)
    else -> String.format(Locale.US, "$%.2f", v)
}

/**
 * Keeps "$8.60" / "86%" in one piece inside Arabic sentences: after an Arabic letter, digits
 * become Arabic numbers and the "$" / "%" drift to the other side; an LRM before them prevents it.
 */
fun ltr(s: String) = "\u200E" + s + "\u200E"

fun formatTokens(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
    n >= 1000 -> String.format(Locale.US, "%.1fk", n / 1000.0).replace(".0k", "k")
    else -> n.toString()
}

/** Steps bar + context size + session tokens/cost. Tapping opens the usage sheet. */
@Composable
private fun StepMeter(state: ChatUiState, onClick: () -> Unit) {
    val c = Saqr.colors
    val desc = stringResource(R.string.usage_title)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Sp.lg).clip(RoundedCornerShape(Radius.sm))
            .clickable(onClickLabel = desc, onClick = onClick).padding(horizontal = Sp.md, vertical = Sp.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressLine(state.step / state.maxSteps.toFloat(), Modifier.weight(1f))
        Spacer(Modifier.width(Sp.md))
        Text(stringResource(R.string.steps_n, state.step.toString()), style = Saqr.type.micro, color = c.text3, maxLines = 1)
        Spacer(Modifier.width(Sp.md))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${formatTokens(state.contextTokens)} / ${formatTokens(state.contextWindow)}", style = Saqr.type.monoMicro, color = c.text2, maxLines = 1)
                if (state.sessionTokens > 0) {
                    Text(" · " + (if (state.usageEstimated) "~" else "") + formatTokens(state.sessionTokens) +
                        (state.sessionCost?.let { " · " + formatUsd(it) } ?: ""),
                        style = Saqr.type.monoMicro, color = c.accentText, maxLines = 1)
                }
                if (state.reviewTokens > 0) {
                    Text(" · " + stringResource(R.string.meter_review) + " " + formatTokens(state.reviewTokens) +
                        (state.reviewCost?.let { " · " + formatUsd(it) } ?: ""),
                        style = Saqr.type.monoMicro, color = c.text2, maxLines = 1)
                }
            }
        }
    }
}

/**
 * v0.13.5 desert mode: a slim banner above the composer naming what still works offline.
 */
@Composable
private fun DesertBanner() {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.md)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Sp.md, vertical = Sp.xs).clip(shape)
            .background(c.warningBg).border(1.dp, c.warningBorder, shape)
            .padding(horizontal = Sp.md, vertical = Sp.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(Lucide.WifiOff, size = 16.dp, tint = c.warning)
        Spacer(Modifier.width(Sp.sm))
        Text(stringResource(R.string.desert_banner), style = Saqr.type.caption, color = c.text1, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BudgetBanner(state: ChatUiState, onClick: () -> Unit) {
    val c = Saqr.colors
    val over = state.budgetLevel == Budget.Level.OVER
    val fg = if (over) c.danger else c.warning
    val shape = RoundedCornerShape(Radius.md)
    val budget = state.budget ?: return
    val pct = ltr((state.monthSpent / budget * 100).toInt().toString() + "%")
    val text = if (over) stringResource(R.string.budget_over, ltr(formatUsd(state.monthSpent)), ltr(formatUsd(budget)))
        else stringResource(R.string.budget_warn, pct, ltr(formatUsd(state.monthSpent)), ltr(formatUsd(budget)))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Sp.md, vertical = Sp.xs).clip(shape)
            .background(if (over) c.danger.copy(alpha = if (c.isDark) 0.10f else 0.07f) else c.warningBg)
            .border(1.dp, if (over) c.danger.copy(alpha = 0.4f) else c.warningBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = Sp.md, vertical = Sp.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(if (over) Lucide.CircleAlert else Lucide.TriangleAlert, size = 16.dp, tint = fg)
        Spacer(Modifier.width(Sp.sm))
        Text(text, style = Saqr.type.caption, color = c.text1, modifier = Modifier.weight(1f))
    }
}

/** Popup list of slash commands while the input is "/…" (no space yet). */
@Composable
private fun SlashPopup(commands: List<SlashCommand>, onPick: (SlashCommand) -> Unit) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.lg)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Sp.md, vertical = Sp.xs).clip(shape).background(c.surface1)
            .border(1.dp, c.accent.copy(alpha = 0.35f), shape).heightIn(max = 288.dp).verticalScroll(rememberScrollState())
            .padding(vertical = Sp.xs),
    ) {
        commands.forEach { cmd ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onPick(cmd) }.padding(horizontal = Sp.md, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text("/" + cmd.name, style = Saqr.type.mono.copy(fontWeight = FontWeight.SemiBold), color = c.accentText, maxLines = 1)
                }
                cmd.aliases.firstOrNull()?.let {
                    Spacer(Modifier.width(6.dp))
                    Text("/$it", style = Saqr.type.caption, color = c.text3, maxLines = 1)
                }
                Spacer(Modifier.width(Sp.md))
                Text(slashDescription(cmd), style = Saqr.type.caption.copy(textDirection = TextDirection.Content), color = c.text2,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun slashDescription(cmd: SlashCommand): String = if (!cmd.builtin) cmd.template.lineSequence().first().take(80) else stringResource(when (cmd.name) {
    SlashCommands.FIX -> R.string.slash_fix
    SlashCommands.EXPLAIN -> R.string.slash_explain
    SlashCommands.TEST -> R.string.slash_test
    SlashCommands.REVIEW -> R.string.slash_review
    SlashCommands.PLAN -> R.string.slash_plan
    SlashCommands.CLEAR -> R.string.slash_clear
    SlashCommands.MODEL -> R.string.slash_model
    SlashCommands.COMMIT -> R.string.slash_commit
    SlashCommands.REFACTOR -> R.string.slash_refactor
    SlashCommands.DOCS -> R.string.slash_docs
    SlashCommands.PERF -> R.string.slash_perf
    SlashCommands.SECURE -> R.string.slash_secure
    SlashCommands.DEBUG -> R.string.slash_debug
    else -> R.string.slash_custom
})

@Composable
private fun Composer(
    state: ChatUiState, input: String, onInput: (String) -> Unit, onSend: () -> Unit, onStop: () -> Unit,
    onPickModel: (String) -> Unit, onManageProviders: () -> Unit,
    modelMenu: Boolean, setModelMenu: (Boolean) -> Unit,
    onImageAction: (ImageAction) -> Unit = {}, onRemoveImage: (Int) -> Unit = {},
    onMic: () -> Unit = {}, onMicLongPress: () -> Unit = {},
) {
    val c = Saqr.colors
    var library by remember { mutableStateOf(false) }
    if (library) PromptLibrarySheet(state.customCommands, { cmd -> library = false; onInput(PromptLibrary.insertion(cmd)) }, { library = false })
    val shape = RoundedCornerShape(Radius.xxl)
    var attach by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(start = Sp.md, end = Sp.md, bottom = Sp.md, top = Sp.xs)
            .clip(shape).background(c.surface1).border(1.dp, c.borderStrong.copy(alpha = 0.7f), shape)
            .padding(start = Sp.md, end = Sp.md, top = Sp.md, bottom = Sp.sm),
    ) {
        if (state.pendingImages.isNotEmpty()) PendingImagesStrip(state.pendingImages, state.imageToCode, onRemoveImage)
        BasicTextField(
            input, onInput, Modifier.fillMaxWidth().heightIn(min = 30.dp, max = 168.dp).padding(horizontal = Sp.xs),
            textStyle = Saqr.type.body.copy(color = c.text1, textDirection = TextDirection.Content),
            cursorBrush = SolidColor(c.accent), maxLines = 7,
            decorationBox = { inner ->
                Box {
                    if (input.isEmpty()) Text(stringResource(if (state.imageToCode) R.string.image_to_code_hint else R.string.ask_agent_hint), style = Saqr.type.body, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(Sp.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(Modifier.minimumInteractiveComponentSize().size(40.dp).pressable { attach = true }.clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape),
                    contentAlignment = Alignment.Center) { SIcon(Lucide.Plus, size = 20.dp, tint = c.text1, contentDescription = stringResource(R.string.attach_file)) }
                DropdownMenu(attach, { attach = false }, Modifier.heightIn(max = 420.dp).background(c.surface2)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.prompt_library), style = Saqr.type.label, color = c.text1) },
                        leadingIcon = { SIcon(Lucide.Sparkles, size = 18.dp, tint = c.accentText) },
                        onClick = { attach = false; library = true },
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(stringResource(R.string.image_to_code), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                                Text(stringResource(R.string.image_to_code_desc), style = Saqr.type.caption, color = c.text3)
                            }
                        },
                        leadingIcon = { SIcon(Lucide.ScanEye, size = 18.dp, tint = c.accentText) },
                        onClick = { attach = false; onImageAction(ImageAction.IMAGE_TO_CODE) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.attach_image), style = Saqr.type.label, color = c.text1) },
                        leadingIcon = { SIcon(Lucide.Image, size = 18.dp, tint = c.text2) },
                        onClick = { attach = false; onImageAction(ImageAction.PICK) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.take_photo), style = Saqr.type.label, color = c.text1) },
                        leadingIcon = { SIcon(Lucide.Camera, size = 18.dp, tint = c.text2) },
                        onClick = { attach = false; onImageAction(ImageAction.CAMERA) },
                    )
                    HorizontalDivider(color = c.border)
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
                    Modifier.height(40.dp).pressable { setModelMenu(true) }.clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape)
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
                DropdownMenu(modelMenu, { setModelMenu(false) }, Modifier.background(c.surface2)) {
                    state.models.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, style = Saqr.type.label, color = c.text1) },
                            trailingIcon = { if (m == state.model) SIcon(Lucide.Check, size = 16.dp, tint = c.accentText) },
                            onClick = { setModelMenu(false); onPickModel(m) },
                        )
                    }
                    HorizontalDivider(color = c.border)
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manage_providers), style = Saqr.type.label, color = c.text1) },
                        leadingIcon = { SIcon(Lucide.Settings, size = 16.dp) },
                        onClick = { setModelMenu(false); onManageProviders() },
                    )
                }
            }
            Spacer(Modifier.weight(0.001f).widthIn(min = Sp.sm))
            val showMic = !state.running && state.pendingImages.isEmpty() && (input.isEmpty() || state.voice.listening)
            if (showMic) {
                MicButton(state.voice, onMic, onMicLongPress)
                Spacer(Modifier.width(Sp.sm))
            }
            SendButton(running = state.running, enabled = (input.isNotBlank() || state.pendingImages.isNotEmpty()) && state.ready, onSend = onSend, onStop = onStop)
        }
    }
}

/** Mic: tap to dictate / stop, long-press to switch Arabic ↔ English. Shows the language it will use. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MicButton(v: VoiceUi, onTap: () -> Unit, onLongPress: () -> Unit) {
    val c = Saqr.colors
    val ring by animateFloatAsState(if (v.listening) 1f + 0.15f * v.level else 1f, tween(120), label = "ring")
    val desc = stringResource(if (v.listening) R.string.voice_stop else R.string.voice_start)
    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
        if (v.listening) Box(Modifier.size(42.dp).scale(ring).clip(CircleShape).background(c.accent.copy(alpha = 0.22f)))
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (v.listening) c.accent else c.surface2)
                .border(1.dp, if (v.listening) c.accent else c.border, CircleShape)
                .combinedClickable(onClick = onTap, onLongClick = onLongPress, onClickLabel = desc),
            contentAlignment = Alignment.Center,
        ) {
            if (v.listening) Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c.onAccent))
            else SIcon(Lucide.Mic, size = 19.dp, tint = if (v.available) c.text1 else c.text3, contentDescription = desc)
        }
        // tiny language badge (ع / EN) in the corner
        Box(Modifier.align(Alignment.BottomEnd).clip(CircleShape).background(c.surface1)
            .border(1.dp, c.accent.copy(alpha = 0.6f), CircleShape).padding(horizontal = 4.dp, vertical = 1.dp)) {
            Text(if (v.lang.startsWith("ar")) "ع" else "EN",
                style = Saqr.type.micro.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp, lineHeight = 11.sp), color = c.accentText, maxLines = 1)
        }
    }
}

@Composable
private fun SendButton(running: Boolean, enabled: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val c = Saqr.colors
    if (running) {
        Box(Modifier.minimumInteractiveComponentSize().size(42.dp).pressable(onClick = onStop), contentAlignment = Alignment.Center) {
            Spinner(42.dp, c.accent, 2.dp)
            Box(Modifier.size(34.dp).clip(CircleShape).background(c.surface3), contentAlignment = Alignment.Center) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c.text1))
            }
        }
    } else {
        Box(
            Modifier.minimumInteractiveComponentSize().size(42.dp).pressable(enabled, onSend).clip(CircleShape)
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
                Box(Modifier.minimumInteractiveComponentSize().size(32.dp).pressable(onClick = onPause).clip(CircleShape), contentAlignment = Alignment.Center) {
                    SIcon(Lucide.Pause, size = 18.dp, tint = c.text1, contentDescription = stringResource(R.string.stop))
                }
            } else Spacer(Modifier.width(Sp.sm))
            Row(
                Modifier.clip(CircleShape).clickable(role = Role.Button, enabled = plan.isNotEmpty()) { open = true }.padding(start = Sp.xs, end = Sp.sm, top = 6.dp, bottom = 6.dp),
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


/** Majlis: "edit" under a user bubble; opens a dialog and resends from that message (the old branch is dropped). */
@Composable
private fun EditResend(original: String, onSubmit: (String) -> Unit) {
    val c = Saqr.colors
    var open by remember { mutableStateOf(false) }
    var draft by remember(original) { mutableStateOf(original) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.msg_edit)) { draft = original; open = true },
            contentAlignment = Alignment.Center,
        ) { SIcon(Lucide.Pencil, size = 15.dp, tint = c.text3, contentDescription = stringResource(R.string.msg_edit)) }
    }
    if (open) androidx.compose.material3.AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.msg_edit_title)) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                draft, { draft = it }, Modifier.fillMaxWidth(), minLines = 2, maxLines = 8,
                textStyle = Saqr.type.body.copy(textDirection = TextDirection.Content),
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { open = false; onSubmit(draft) }, enabled = draft.isNotBlank()) { Text(stringResource(R.string.send)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "‹ 2/3 ›" under a user message that has more than one branch. 48dp targets, labelled, mirrors in RTL. */
@Composable
internal fun BranchSwitcher(current: Int, total: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = Saqr.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.branch_prev)) { onPrev() },
            contentAlignment = Alignment.Center,
        ) { SIcon(Lucide.ChevronLeft, size = 18.dp, tint = c.text2, contentDescription = stringResource(R.string.branch_prev)) }
        Text(
            stringResource(R.string.branch_position, current, total),
            style = Saqr.type.caption.copy(textDirection = TextDirection.Ltr), color = c.text2,
            modifier = Modifier.semantics { contentDescription = "$current / $total" },
        )
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.branch_next)) { onNext() },
            contentAlignment = Alignment.Center,
        ) { SIcon(Lucide.ChevronRight, size = 18.dp, tint = c.text2, contentDescription = stringResource(R.string.branch_next)) }
    }
}
