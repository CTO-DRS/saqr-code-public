package com.ctodrs.saqrcode.ui.chat

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.delay

/** Feedback on an answer: 0 none, 1 good, -1 bad. */
object Feedback { const val NONE = 0; const val UP = 1; const val DOWN = -1 }

/** One 48dp icon button of the message action bar: role, label and (optional) selected state for TalkBack. */
@Composable
private fun BarButton(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color, selected: Boolean? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { if (selected != null) this.selected = selected },
        contentAlignment = Alignment.Center,
    ) { SIcon(icon, size = 18.dp, tint = tint, contentDescription = label) }
}

/**
 * Majlis per-message action bar: copy, good/bad answer, read aloud, share. Icons keep their order in RTL
 * (the row mirrors), so the first action sits at the reading start.
 */
@Composable
fun MessageActionBar(
    text: String, feedback: Int, onFeedback: (Int) -> Unit,
    speaking: Boolean, onSpeak: (() -> Unit)?, modifier: Modifier = Modifier,
    onRegenerate: (() -> Unit)? = null,
) {
    val c = Saqr.colors
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        BarButton(if (copied) Lucide.Check else Lucide.Copy, stringResource(if (copied) R.string.copied else R.string.copy), if (copied) c.success else c.text3) {
            clipboard.setText(AnnotatedString(text)); copied = true
        }
        BarButton(Lucide.ThumbsUp, stringResource(R.string.msg_good), if (feedback == Feedback.UP) c.accentText else c.text3, feedback == Feedback.UP) {
            onFeedback(if (feedback == Feedback.UP) Feedback.NONE else Feedback.UP)
        }
        BarButton(Lucide.ThumbsDown, stringResource(R.string.msg_bad), if (feedback == Feedback.DOWN) c.danger else c.text3, feedback == Feedback.DOWN) {
            onFeedback(if (feedback == Feedback.DOWN) Feedback.NONE else Feedback.DOWN)
        }
        if (onSpeak != null) {
            BarButton(if (speaking) Lucide.Square else Lucide.Volume2, stringResource(if (speaking) R.string.stop_reading else R.string.read_aloud),
                if (speaking) c.accentText else c.text3, speaking) { onSpeak() }
        }
        if (onRegenerate != null) BarButton(Lucide.RotateCcw, stringResource(R.string.msg_regenerate), c.text3) { onRegenerate() }
        BarButton(Lucide.Share2, stringResource(R.string.skills_share), c.text3) {
            runCatching {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

/** Majlis "المصادر" row: a label then one chip per source (icon + short label), scrolling sideways. */
@Composable
fun SourcesRow(sources: List<SourceRef>, modifier: Modifier = Modifier) {
    if (sources.isEmpty()) return
    val c = Saqr.colors
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Sp.xs)) {
        Text(stringResource(R.string.sources_title), style = Saqr.type.caption, color = c.text3, modifier = Modifier.padding(end = Sp.xs))
        sources.forEach { s ->
            val kindLabel = stringResource(kindLabelRes(s.kind))
            Row(
                Modifier.heightIn(min = 28.dp).clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape)
                    .padding(horizontal = Sp.sm).semantics(mergeDescendants = true) { contentDescription = kindLabel + ": " + s.label },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                SIcon(iconFor(s.kind), size = 13.dp, tint = c.text2)
                Text(s.label, style = Saqr.type.monoMicro, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
            }
        }
    }
}

private fun iconFor(k: SourceRef.Kind): ImageVector = when (k) {
    SourceRef.Kind.FILE -> Lucide.FileText
    SourceRef.Kind.TERMINAL -> Lucide.SquareTerminal
    SourceRef.Kind.WEB -> Lucide.Globe
    SourceRef.Kind.SEARCH -> Lucide.Search
    SourceRef.Kind.GIT -> Lucide.GitBranch
    SourceRef.Kind.MEMORY -> Lucide.Copy
    SourceRef.Kind.TESTS -> Lucide.Check
}

private fun kindLabelRes(k: SourceRef.Kind): Int = when (k) {
    SourceRef.Kind.FILE -> R.string.src_file
    SourceRef.Kind.TERMINAL -> R.string.src_terminal
    SourceRef.Kind.WEB -> R.string.src_web
    SourceRef.Kind.SEARCH -> R.string.src_search
    SourceRef.Kind.GIT -> R.string.src_git
    SourceRef.Kind.MEMORY -> R.string.src_memory
    SourceRef.Kind.TESTS -> R.string.src_tests
}
