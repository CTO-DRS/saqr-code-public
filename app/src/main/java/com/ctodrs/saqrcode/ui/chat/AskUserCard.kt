package com.ctodrs.saqrcode.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.AskAnswer
import com.ctodrs.saqrcode.agent.AskKind
import com.ctodrs.saqrcode.agent.AskQuestion
import com.ctodrs.saqrcode.agent.AskUserTool
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.pressable
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Motion
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

/** In-chat question card for `ask_user`; collapses to a read-only summary once answered. */
@Composable
fun AskUserCard(item: ChatItem.AskUser, onAnswer: (ChatItem.AskUser, Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth().animateContentSize(tween(Motion.base, easing = Motion.emphasized))) {
        if (item.status == ChatItem.AskUser.Status.PENDING) AskUserForm(item, onAnswer) else AskUserSummary(item)
    }
}

@Composable
private fun AskUserForm(item: ChatItem.AskUser, onAnswer: (ChatItem.AskUser, Boolean) -> Unit) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.lg)
    val req = item.request
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface1).border(1.dp, c.accent.copy(alpha = 0.35f), shape).padding(Sp.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                SIcon(Lucide.Sparkles, size = 17.dp, tint = c.accentText)
            }
            Spacer(Modifier.width(Sp.md))
            // Box keeps the title next to the icon (layout start) even when its text runs the other way.
            Box(Modifier.weight(1f)) {
                Text(req.title ?: stringResource(R.string.ask_default_title), style = Saqr.type.title3.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content),
                    color = c.text1)
            }
        }
        req.questions.forEachIndexed { i, q ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = Sp.md), color = c.border) else Spacer(Modifier.height(Sp.md))
            QuestionBlock(q, item.answers[q.id] ?: AskAnswer()) { item.update(q, it) }
        }
        Spacer(Modifier.height(Sp.lg))
        PrimaryButton(req.submitLabel ?: stringResource(R.string.ask_submit), { onAnswer(item, false) },
            Modifier.fillMaxWidth(), icon = Lucide.Check, enabled = item.canSubmit)
        Spacer(Modifier.height(Sp.xs))
        Text(
            stringResource(R.string.ask_skip), style = Saqr.type.label, color = c.text2, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).clickable(role = Role.Button) { onAnswer(item, true) }.padding(vertical = Sp.sm),
        )
    }
}

@Composable
private fun QuestionBlock(q: AskQuestion, a: AskAnswer, onChange: (AskAnswer) -> Unit) {
    val c = Saqr.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q.prompt, style = Saqr.type.body.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content), color = c.text1,
                modifier = Modifier.weight(1f, fill = false))
            if (!q.required) { Spacer(Modifier.width(Sp.sm)); Text(stringResource(R.string.ask_optional), style = Saqr.type.caption, color = c.text3) }
        }
        if (q.kind == AskKind.MULTI) Text(stringResource(R.string.ask_multi_hint), style = Saqr.type.caption, color = c.text3)
        Spacer(Modifier.height(Sp.xs))
        when (q.kind) {
            AskKind.SINGLE, AskKind.MULTI -> {
                val multi = q.kind == AskKind.MULTI
                q.options.forEach { o ->
                    OptionRow(o.label, o.description, o.recommended, o.id in a.selected, multi) { onChange(AskUserTool.toggle(q, a, o.id)) }
                }
                if (q.allowOther) {
                    OptionRow(stringResource(R.string.ask_other), null, false, a.other != null, multi) { onChange(AskUserTool.toggleOther(q, a)) }
                    if (a.other != null) AnswerField(a.other, stringResource(R.string.ask_other_hint), Modifier.padding(start = 32.dp, top = Sp.xs)) { onChange(a.copy(other = it)) }
                }
            }
            AskKind.TEXT -> AnswerField(a.text, stringResource(R.string.ask_text_hint)) { onChange(a.copy(text = it)) }
            AskKind.CONFIRM -> Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                ConfirmChip(stringResource(R.string.ask_yes), a.selected.firstOrNull() == AskUserTool.YES) { onChange(AskUserTool.toggle(q, a, AskUserTool.YES)) }
                ConfirmChip(stringResource(R.string.ask_no), a.selected.firstOrNull() == AskUserTool.NO) { onChange(AskUserTool.toggle(q, a, AskUserTool.NO)) }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, description: String?, recommended: Boolean, selected: Boolean, multi: Boolean, onClick: () -> Unit) {
    val c = Saqr.colors
    val sel = if (multi) Modifier.toggleable(selected, role = Role.Checkbox) { onClick() } else Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).then(sel).heightIn(min = 44.dp).padding(horizontal = 2.dp, vertical = Sp.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 2.dp)) { if (multi) CheckMark(selected) else RadioDot(selected) }
        Spacer(Modifier.width(Sp.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = Saqr.type.bodySmall.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content), color = c.text1,
                    modifier = Modifier.weight(1f, fill = false))
                if (recommended) {
                    Spacer(Modifier.width(Sp.sm))
                    Text(stringResource(R.string.ask_recommended), style = Saqr.type.micro, color = c.accentText, maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(c.accentSoft).padding(horizontal = Sp.sm, vertical = 1.dp))
                }
            }
            if (description != null) Text(description, style = Saqr.type.caption.copy(textDirection = TextDirection.Content), color = c.text2)
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val c = Saqr.colors
    Box(Modifier.size(20.dp).clip(CircleShape).border(1.6.dp, if (selected) c.accent else c.borderStrong, CircleShape), contentAlignment = Alignment.Center) {
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
    }
}

@Composable
private fun CheckMark(checked: Boolean) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.size(20.dp).clip(shape).background(if (checked) c.accent else c.surface1).border(1.6.dp, if (checked) c.accent else c.borderStrong, shape),
        contentAlignment = Alignment.Center,
    ) { if (checked) SIcon(Lucide.Check, size = 14.dp, tint = c.onAccent) }
}

@Composable
private fun ConfirmChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Saqr.colors
    Row(
        Modifier.pressable(onClick = onClick).clip(CircleShape)
            .background(if (selected) c.accentSoft else c.surface2)
            .border(1.dp, if (selected) c.accent else c.border, CircleShape)
            .padding(horizontal = Sp.lg, vertical = Sp.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) { SIcon(Lucide.Check, size = 14.dp, tint = c.accentText); Spacer(Modifier.width(6.dp)) }
        Text(text, style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = if (selected) c.accentText else c.text1)
    }
}

@Composable
private fun AnswerField(value: String, hint: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.sm)
    BasicTextField(
        value, onValue, modifier.fillMaxWidth().clip(shape).background(c.surface2).border(1.dp, c.border, shape).padding(horizontal = Sp.md, vertical = Sp.sm + 2.dp),
        textStyle = Saqr.type.bodySmall.copy(color = c.text1, textDirection = TextDirection.Content),
        cursorBrush = SolidColor(c.accent), maxLines = 5,
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, style = Saqr.type.bodySmall, color = c.text3); inner() } },
    )
}

@Composable
private fun AskUserSummary(item: ChatItem.AskUser) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.md)
    val st = item.status
    val yes = stringResource(R.string.ask_yes)
    val no = stringResource(R.string.ask_no)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface1).border(1.dp, c.border, shape).padding(horizontal = Sp.md, vertical = Sp.sm + 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SIcon(if (st == ChatItem.AskUser.Status.ANSWERED) Lucide.CircleCheck else Lucide.Sparkles, size = 16.dp,
                tint = when (st) { ChatItem.AskUser.Status.ANSWERED -> c.success; ChatItem.AskUser.Status.CANCELLED -> c.text3; else -> c.accentText })
            Spacer(Modifier.width(Sp.sm))
            Box(Modifier.weight(1f)) {
                Text(item.request.title ?: stringResource(R.string.ask_default_title), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content),
                    color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Sp.sm))
            Text(stringResource(when (st) {
                ChatItem.AskUser.Status.ANSWERED -> R.string.ask_answered
                ChatItem.AskUser.Status.SKIPPED -> R.string.ask_skipped
                else -> R.string.ask_dismissed
            }), style = Saqr.type.caption, color = c.text3, maxLines = 1)
        }
        if (st == ChatItem.AskUser.Status.ANSWERED) {
            Spacer(Modifier.height(Sp.xs))
            item.request.questions.forEach { q ->
                Row(Modifier.padding(start = 24.dp, top = 2.dp)) {
                    Text(q.prompt, style = Saqr.type.caption.copy(textDirection = TextDirection.Content), color = c.text2,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(Sp.sm))
                    Text(AskUserTool.display(q, item.answers[q.id], yes, no), style = Saqr.type.caption.copy(fontWeight = FontWeight.SemiBold, textDirection = TextDirection.Content),
                        color = c.text1, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }
    }
}

/** Suggestion chips shown above the composer when the last reply ends with a numbered list. */
@Composable
fun QuickReplyChips(replies: List<String>, onPick: (String) -> Unit) = ReplyChips(replies.map { it to it }, onPick)

/** Chips showing a short label and inserting [Pair.second] when tapped. */
@Composable
fun ReplyChips(chips: List<Pair<String, String>>, onPick: (String) -> Unit) {
    val c = Saqr.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Sp.md, vertical = Sp.xs),
        horizontalArrangement = Arrangement.spacedBy(Sp.sm),
    ) {
        chips.forEach { (label, text) ->
            Box(Modifier.minimumInteractiveComponentSize().pressable { onPick(text) }, contentAlignment = Alignment.Center) {
                Text(
                    label, style = Saqr.type.label.copy(textDirection = TextDirection.Content), color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clip(CircleShape).background(c.surface2)
                        .border(1.dp, c.accent.copy(alpha = 0.45f), CircleShape).padding(horizontal = Sp.md, vertical = 7.dp),
                )
            }
        }
    }
}
