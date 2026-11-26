package com.ctodrs.saqrcode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.PromptLibrary
import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

/** Searchable list of every prompt command (built-in and custom). Picking one fills the composer. */
@Composable
fun PromptLibrarySheet(custom: List<SlashCommand>, onPick: (SlashCommand) -> Unit, onDismiss: () -> Unit) {
    SaqrSheet(onDismiss) { PromptLibraryContent(custom, onPick) }
}

@Composable
fun PromptLibraryContent(custom: List<SlashCommand>, onPick: (SlashCommand) -> Unit) {
    val c = Saqr.colors
    var query by remember { mutableStateOf("") }
    val all = remember(custom) { PromptLibrary.prompts(custom) }
    val descriptions = all.associateWith { slashDescription(it) }
    val shown = PromptLibrary.filter(all, query) { descriptions[it].orEmpty() }
    Row(Modifier.padding(bottom = Sp.xs), verticalAlignment = Alignment.CenterVertically) {
        SIcon(Lucide.Sparkles, size = 20.dp, tint = c.accentText)
        Spacer(Modifier.width(Sp.sm))
        Text(stringResource(R.string.prompt_library), style = Saqr.type.title2, color = c.text1)
    }
    Text(stringResource(R.string.prompt_library_desc), style = Saqr.type.caption, color = c.text3, modifier = Modifier.padding(bottom = Sp.md))
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface2).border(1.dp, c.border, RoundedCornerShape(14.dp)).padding(horizontal = Sp.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(Lucide.Search, size = 18.dp, tint = c.text3)
        Spacer(Modifier.width(Sp.sm))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(stringResource(R.string.prompt_library_search), style = Saqr.type.body, color = c.text3)
            BasicTextField(query, { query = it }, singleLine = true, textStyle = Saqr.type.body.copy(color = c.text1, textDirection = TextDirection.Content),
                cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
        }
    }
    Spacer(Modifier.padding(top = Sp.sm))
    if (shown.isEmpty()) {
        Text(stringResource(R.string.prompt_library_none), style = Saqr.type.body, color = c.text3, modifier = Modifier.padding(vertical = Sp.lg))
    } else LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(Sp.xs)) {
        items(shown, key = { it.name }) { cmd ->
            Column(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = descriptions[cmd]) { onPick(cmd) }
                    .padding(horizontal = Sp.md, vertical = 10.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("/" + cmd.name, style = Saqr.type.mono.copy(fontWeight = FontWeight.SemiBold, textDirection = if (cmd.name.any { it in '\u0600'..'\u06FF' }) TextDirection.Content else TextDirection.Ltr), color = c.accentText)
                    if (cmd.aliases.isNotEmpty()) {
                        Spacer(Modifier.width(Sp.sm))
                        Text(cmd.aliases.joinToString(" ") { "/$it" }, style = Saqr.type.caption, color = c.text3)
                    }
                }
                Text(descriptions[cmd].orEmpty(), style = Saqr.type.caption.copy(textDirection = TextDirection.Content), color = c.text2,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
