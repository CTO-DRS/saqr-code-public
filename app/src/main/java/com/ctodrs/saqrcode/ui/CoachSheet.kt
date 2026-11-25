package com.ctodrs.saqrcode.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.CoachSnapshot
import com.ctodrs.saqrcode.agent.SaqrCoach
import com.ctodrs.saqrcode.ui.chat.SaqrSheet
import com.ctodrs.saqrcode.ui.components.GhostButton
import com.ctodrs.saqrcode.ui.components.SaqrCard
import com.ctodrs.saqrcode.ui.components.Tag
import com.ctodrs.saqrcode.ui.components.pressable
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

fun coachStepTitle(id: String): Int = when (id) {
    SaqrCoach.STEP_KEY -> R.string.coach_step_key
    SaqrCoach.STEP_PROJECT -> R.string.coach_step_project
    SaqrCoach.STEP_MESSAGE -> R.string.coach_step_message
    SaqrCoach.STEP_BUILD -> R.string.coach_step_build
    else -> R.string.coach_step_first_build
}

fun coachStepHow(id: String): Int = when (id) {
    SaqrCoach.STEP_KEY -> R.string.coach_step_key_how
    SaqrCoach.STEP_PROJECT -> R.string.coach_step_project_how
    SaqrCoach.STEP_MESSAGE -> R.string.coach_step_message_how
    SaqrCoach.STEP_BUILD -> R.string.coach_step_build_how
    else -> R.string.coach_step_first_build_how
}

private fun coachStepIcon(id: String): ImageVector = when (id) {
    SaqrCoach.STEP_KEY -> Lucide.KeyRound
    SaqrCoach.STEP_PROJECT -> Lucide.FilePlus
    SaqrCoach.STEP_MESSAGE -> Lucide.MessageSquarePlus
    SaqrCoach.STEP_BUILD -> Lucide.Hammer
    else -> Lucide.Rocket
}

/** v0.13.3 مدرّب صقر · The home learning-path card: progress n/5 plus the current step. */
@Composable
fun CoachCard(snap: CoachSnapshot, progress: Map<String, Long>, onClick: () -> Unit) {
    val c = Saqr.colors
    val done = SaqrCoach.progress(snap)
    val total = SaqrCoach.STEPS.size
    val shape = RoundedCornerShape(Radius.lg)
    val cur = SaqrCoach.current(snap)
    SaqrCard(Modifier.fillMaxWidth(), onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(Sp.lg)) {
        if (SaqrCoach.complete(snap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                    SIcon(Lucide.Rocket, size = 20.dp, tint = c.accentText)
                }
                Spacer(Modifier.width(Sp.md))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.coach_done_title), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                    progress[SaqrCoach.STEP_FIRST_BUILD]?.let { t ->
                        Text(
                            stringResource(R.string.coach_done_when, DateUtils.getRelativeTimeSpanString(t).toString()),
                            style = Saqr.type.caption, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                SIcon(Lucide.ChevronRight, size = 18.dp, tint = c.text3)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                    SIcon(Lucide.ListChecks, size = 20.dp, tint = c.accentText)
                }
                Spacer(Modifier.width(Sp.md))
                Text(stringResource(R.string.coach_card_title), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1, modifier = Modifier.weight(1f))
                Tag("$done/$total", color = c.accentText, dot = false)
            }
            Spacer(Modifier.height(Sp.md))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.surface2)) {
                Box(Modifier.fillMaxWidth(done.toFloat() / total).height(4.dp).clip(CircleShape).background(c.accent))
            }
            cur?.let { id ->
                Spacer(Modifier.height(Sp.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SIcon(coachStepIcon(id), size = 15.dp, tint = c.accentText)
                    Spacer(Modifier.width(Sp.sm))
                    Text(stringResource(coachStepTitle(id)), style = Saqr.type.bodySmall.copy(fontWeight = FontWeight.Medium), color = c.text1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(stringResource(coachStepHow(id)), style = Saqr.type.caption, color = c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** v0.13.3 مدرّب صقر · The full coach sheet: the five steps, how-to for the current one,
 *  and Arabic starter commands with a copy button. */
@Composable
fun CoachSheet(
    snap: CoachSnapshot,
    progress: Map<String, Long>,
    template: String,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
) {
    SaqrSheet(onDismiss) {
        CoachSheetContent(snap, progress, template, onHide)
    }
}

@Composable
fun CoachSheetContent(
    snap: CoachSnapshot,
    progress: Map<String, Long>,
    template: String,
    onHide: () -> Unit,
) {
    val c = Saqr.colors
    val done = SaqrCoach.progress(snap)
    val total = SaqrCoach.STEPS.size
    val cur = SaqrCoach.current(snap)
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(-1) }

    Row(Modifier.padding(bottom = Sp.sm), verticalAlignment = Alignment.CenterVertically) {
        SIcon(Lucide.ListChecks, size = 20.dp, tint = c.accentText)
        Spacer(Modifier.width(Sp.sm))
        Text(stringResource(R.string.coach_title), style = Saqr.type.title2, color = c.text1)
        Spacer(Modifier.width(Sp.sm))
        Tag("$done/$total", color = c.accentText, dot = false)
        Spacer(Modifier.weight(1f))
        SIcon(Lucide.Rocket, size = 18.dp, tint = if (SaqrCoach.complete(snap)) c.accentText else c.text3)
    }
    Text(stringResource(R.string.coach_sheet_hint), style = Saqr.type.caption, color = c.text2)
    Spacer(Modifier.height(Sp.md))

    if (SaqrCoach.complete(snap)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.accentSoft).border(1.dp, c.accent.copy(alpha = 0.4f), RoundedCornerShape(Radius.md)).padding(Sp.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            SIcon(Lucide.Rocket, size = 28.dp, tint = c.accentText)
            Spacer(Modifier.height(Sp.sm))
            Text(stringResource(R.string.coach_done_title), style = Saqr.type.title3.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
            Text(stringResource(R.string.coach_done_body), style = Saqr.type.bodySmall, color = c.text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            progress[SaqrCoach.STEP_FIRST_BUILD]?.let { t ->
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.coach_done_when, DateUtils.getRelativeTimeSpanString(t).toString()),
                    style = Saqr.type.micro, color = c.text3,
                )
            }
        }
        Spacer(Modifier.height(Sp.md))
    }

    Column(verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
        SaqrCoach.steps(snap).forEach { step ->
            val isCur = step.id == cur
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md))
                    .background(if (isCur) c.accentSoft else c.surface2)
                    .border(1.dp, if (isCur) c.accent.copy(alpha = 0.45f) else c.border, RoundedCornerShape(Radius.md))
                    .padding(Sp.md),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(if (step.done) c.success.copy(alpha = 0.14f) else c.surface1), contentAlignment = Alignment.Center) {
                    if (step.done) SIcon(Lucide.CircleCheck, size = 16.dp, tint = c.success)
                    else SIcon(coachStepIcon(step.id), size = 15.dp, tint = if (isCur) c.accentText else c.text3)
                }
                Spacer(Modifier.width(Sp.md))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(coachStepTitle(step.id)), style = Saqr.type.bodySmall.copy(fontWeight = if (isCur) FontWeight.SemiBold else FontWeight.Medium), color = c.text1)
                    Text(stringResource(coachStepHow(step.id)), style = Saqr.type.caption, color = c.text2, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    progress[step.id]?.let { t ->
                        Spacer(Modifier.height(2.dp))
                        Text(DateUtils.getRelativeTimeSpanString(t).toString(), style = Saqr.type.micro, color = c.text3)
                    }
                }
            }
        }
    }

    // Arabic starter commands for the current focus (or celebration-neutral when done).
    val prompts = remember(template) { com.ctodrs.saqrcode.agent.StarterPrompts.forTemplate(template) }
    Spacer(Modifier.height(Sp.md))
    Text(stringResource(R.string.coach_examples_label), style = Saqr.type.caption.copy(fontWeight = FontWeight.Medium), color = c.text2)
    Spacer(Modifier.height(Sp.sm))
    prompts.forEachIndexed { i, p ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(Radius.sm)).pressable { clipboard.setText(AnnotatedString(p)); copied = i }
                .padding(horizontal = Sp.md, vertical = Sp.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("«$p»", style = Saqr.type.caption, color = c.text1, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Sp.sm))
            SIcon(if (copied == i) Lucide.CircleCheck else Lucide.Copy, size = 15.dp, tint = if (copied == i) c.success else c.text3)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(if (copied == i) R.string.coach_copied else R.string.coach_copy), style = Saqr.type.micro, color = if (copied == i) c.success else c.text2)
        }
        Spacer(Modifier.height(Sp.xs))
    }

    Spacer(Modifier.height(Sp.sm))
    GhostButton(stringResource(R.string.coach_hide), onHide, tint = c.text2)
}
