package com.ctodrs.saqrcode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.Planner
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.SecondaryButton
import com.ctodrs.saqrcode.ui.components.pressable
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

/** v0.1.9 plan mode: the agent's plan as an editable list. Run starts the work with the edited plan; Cancel stops the turn. */
@Composable
fun PlanReviewCard(item: ChatItem.PlanReview, onDecide: (ChatItem.PlanReview, Boolean) -> Unit) {
    val c = Saqr.colors
    val shape = RoundedCornerShape(Radius.lg)
    val pending = item.status == ChatItem.PlanReview.Status.PENDING
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.surface1).border(1.dp, if (pending) c.accent.copy(alpha = 0.35f) else c.border, shape).padding(Sp.lg),
        verticalArrangement = Arrangement.spacedBy(Sp.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                SIcon(Lucide.ListChecks, size = 17.dp, tint = c.accentText)
            }
            Spacer(Modifier.width(Sp.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.plan_edit_title), style = Saqr.type.title3, color = c.text1)
                Text(stringResource(when (item.status) {
                    ChatItem.PlanReview.Status.PENDING -> R.string.plan_edit_sub
                    ChatItem.PlanReview.Status.APPROVED -> R.string.plan_edit_approved
                    ChatItem.PlanReview.Status.CANCELLED -> R.string.plan_edit_cancelled
                }), style = Saqr.type.caption, color = c.text2)
            }
        }
        item.tasks.forEachIndexed { i, t ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
                Text("${i + 1}", style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.accentText, modifier = Modifier.width(18.dp))
                if (pending) {
                    val shapeF = RoundedCornerShape(Radius.sm)
                    BasicTextField(
                        t, { item.tasks[i] = it },
                        Modifier.weight(1f).clip(shapeF).background(c.surface2).border(1.dp, c.border, shapeF).padding(horizontal = Sp.md, vertical = Sp.sm + 2.dp)
                            .semantics { contentDescription = "${i + 1}" },
                        textStyle = Saqr.type.bodySmall.copy(color = c.text1, textDirection = TextDirection.Content),
                        cursorBrush = SolidColor(c.accent), maxLines = 3,
                    )
                    PlanIconButton(Lucide.ArrowUp, stringResource(R.string.plan_move_up), i > 0) { val m = Planner.move(item.tasks.toList(), i, -1); item.tasks.clear(); item.tasks.addAll(m) }
                    PlanIconButton(Lucide.ArrowDown, stringResource(R.string.plan_move_down), i < item.tasks.lastIndex) { val m = Planner.move(item.tasks.toList(), i, 1); item.tasks.clear(); item.tasks.addAll(m) }
                    PlanIconButton(Lucide.Trash2, stringResource(R.string.plan_remove_step), true) { item.tasks.removeAt(i) }
                } else {
                    Text(t, style = Saqr.type.bodySmall.copy(textDirection = TextDirection.Content), color = c.text1, modifier = Modifier.weight(1f))
                }
            }
        }
        if (pending) {
            Row(
                Modifier.pressable { if (item.tasks.size < Planner.MAX_TEAM_TASKS) item.tasks.add("") }.heightIn(min = 48.dp)
                    .semantics { role = Role.Button }.padding(horizontal = Sp.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SIcon(Lucide.Plus, size = 16.dp, tint = c.accentText)
                Spacer(Modifier.width(Sp.sm))
                Text(stringResource(R.string.plan_add_step), style = Saqr.type.label, color = c.accentText)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
                PrimaryButton(stringResource(R.string.plan_run), { onDecide(item, true) }, Modifier.weight(1f), icon = Lucide.Check,
                    enabled = item.tasks.any { it.isNotBlank() })
                SecondaryButton(stringResource(R.string.plan_cancel), { onDecide(item, false) }, Modifier.weight(1f), icon = Lucide.X)
            }
        }
    }
}

@Composable
private fun PlanIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Saqr.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).pressable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { SIcon(icon, size = 16.dp, tint = if (enabled) c.text2 else c.text3.copy(alpha = 0.4f)) }
}
