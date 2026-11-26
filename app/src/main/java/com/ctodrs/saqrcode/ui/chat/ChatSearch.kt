package com.ctodrs.saqrcode.ui.chat

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

@Composable
private fun SearchBtn(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Saqr.colors
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        SIcon(icon, size = 18.dp, tint = if (enabled) c.text2 else c.text3.copy(alpha = 0.4f), contentDescription = label)
    }
}

/**
 * Majlis: search icon + export icon pinned to the top end of the chat. Search opens a bar with a match counter and
 * previous/next; jumping scrolls the list to the matching message. Export shares the transcript as Markdown.
 */
@Composable
fun ChatSearchOverlay(shown: List<ChatItem>, listState: LazyListState, title: String, modifier: Modifier = Modifier) {
    val c = Saqr.colors
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var at by remember { mutableIntStateOf(0) }
    val hits = remember(shown.size, query, shown.lastOrNull()) { Transcript.search(shown, query) }
    LaunchedEffect(hits, at) { if (hits.isNotEmpty()) listState.animateScrollToItem(hits[at.coerceIn(0, hits.lastIndex)]) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(open) { if (open) runCatching { focus.requestFocus() } }
    val userLabel = stringResource(R.string.msg_you); val me = stringResource(R.string.app_name)
    Row(modifier.fillMaxWidth().padding(horizontal = Sp.sm), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (open) {
            Row(
                Modifier.weight(1f).clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape).padding(start = Sp.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    query, { query = it; at = 0 }, Modifier.weight(1f).focusRequester(focus), singleLine = true,
                    textStyle = Saqr.type.body.copy(color = c.text1, textDirection = TextDirection.Content), cursorBrush = SolidColor(c.accent),
                    decorationBox = { inner ->
                        Box { if (query.isEmpty()) Text(stringResource(R.string.chat_search_hint), style = Saqr.type.body, color = c.text3); inner() }
                    },
                )
                if (query.length >= 2) Text(if (hits.isEmpty()) stringResource(R.string.chat_search_none) else "${at.coerceIn(0, hits.lastIndex) + 1}/${hits.size}",
                    style = Saqr.type.monoMicro, color = c.text3, modifier = Modifier.padding(horizontal = Sp.xs))
                SearchBtn(Lucide.ChevronDown, stringResource(R.string.chat_search_next), hits.size > 1) { at = (at + 1) % hits.size }
                SearchBtn(Lucide.X, stringResource(R.string.close)) { open = false; query = ""; at = 0 }
            }
        } else {
            Row(Modifier.clip(CircleShape).background(c.surface2.copy(alpha = 0.92f)).border(1.dp, c.border, CircleShape)) {
                SearchBtn(Lucide.Search, stringResource(R.string.chat_search)) { open = true }
                SearchBtn(Lucide.Share2, stringResource(R.string.chat_export)) {
                    val md = Transcript.toMarkdown(shown, title, userLabel, me)
                    runCatching {
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/markdown").putExtra(Intent.EXTRA_TEXT, md)
                            .putExtra(Intent.EXTRA_SUBJECT, title), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
    }
}
