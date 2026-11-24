package com.ctodrs.saqrcode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.SlashCommand
import com.ctodrs.saqrcode.agent.SlashCommands
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

/**
 * v0.12 «لوحة الأوامر العربية» · Command palette: one search box from any pane that reaches
 * files, slash commands, settings destinations and the sessions sheet. The matching is
 * Arabic-aware (alef/yaa forms, diacritics, tatweel) through [SlashCommands.normalize].
 */

enum class PaletteKind { COMMAND, SETTING, FILE, SESSION }

data class PaletteItem(val kind: PaletteKind, val title: String, val subtitle: String = "", val key: String = "")

object PaletteLogic {

    /** Settings destinations reachable from the palette (Arabic title + English alias keywords). */
    val settingsEntries: List<PaletteItem> = listOf(
        PaletteItem(PaletteKind.SETTING, "المزوّدون", "providers api key مزود مزودين"),
        PaletteItem(PaletteKind.SETTING, "اللغة", "language عربي english لغة"),
        PaletteItem(PaletteKind.SETTING, "الثيم والاستوديو", "theme studio ثيم مظهر استوديو"),
        PaletteItem(PaletteKind.SETTING, "فريق الوكلاء", "team agent planner reviewer فريق مراجع مخطط"),
        PaletteItem(PaletteKind.SETTING, "الميزانية والأسعار", "budget prices cost ميزانية اسعار تكلفة"),
        PaletteItem(PaletteKind.SETTING, "أوامر الشرطة المخصصة", "custom slash commands أوامر"),
        PaletteItem(PaletteKind.SETTING, "خوادم MCP", "mcp servers stdio خوادم"),
        PaletteItem(PaletteKind.SETTING, "البناء على الجهاز", "on-device build aapt2 بناء"),
        PaletteItem(PaletteKind.SETTING, "الاشتراك والترخيص", "billing plan subscription اشتراك ترخيص"),
        PaletteItem(PaletteKind.SETTING, "الحسابات", "accounts github gitlab حسابات"),
    )

    /** The palette's source items: files, slash commands, settings destinations, sessions entry. */
    fun build(files: List<String>, commands: List<SlashCommand>, sessionsTitle: String = "الجلسات"): List<PaletteItem> =
        settingsEntries +
            commands.map { c ->
                PaletteItem(PaletteKind.COMMAND, "/${c.name}", (c.aliases.joinToString(" ") { "/$it" } + " " + c.template.take(80)).trim(), c.name)
            } +
            files.map { PaletteItem(PaletteKind.FILE, it, "", it) } +
            listOf(PaletteItem(PaletteKind.SESSION, sessionsTitle, "sessions جلسات محادثة"))

    /** Arabic-aware ranked search; empty query returns the default-ordered top [limit]. */
    fun search(query: String, items: List<PaletteItem>, limit: Int = 14): List<PaletteItem> {
        if (query.isBlank()) return items.take(limit)
        val tokens = SlashCommands.normalize(query).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return items.take(limit)
        return items.mapNotNull { item ->
            val hay = SlashCommands.normalize("${item.title} ${item.subtitle}")
            var score = 0
            for (t in tokens) {
                val i = hay.indexOf(t)
                if (i < 0) return@mapNotNull null
                score -= i
                if (i == 0) score += 8
                if (SlashCommands.normalize(item.title) == t) score += 12
            }
            score -= item.title.length / 8
            score += when (item.kind) { PaletteKind.COMMAND -> 3; PaletteKind.SETTING -> 2; PaletteKind.SESSION -> 1; PaletteKind.FILE -> 0 }
            item to score
        }.sortedByDescending { it.second }.take(limit).map { it.first }
    }
}

private fun PaletteKind.icon(): ImageVector = when (this) {
    PaletteKind.FILE -> Lucide.FileCode
    PaletteKind.COMMAND -> Lucide.Command
    PaletteKind.SETTING -> Lucide.Settings
    PaletteKind.SESSION -> Lucide.MessagesSquare
}

@Composable
fun CommandPaletteDialog(visible: Boolean, onDismiss: () -> Unit, items: List<PaletteItem>, onSelect: (PaletteItem) -> Unit) {
    if (!visible) return
    val c = Saqr.colors
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val results = remember(query, items) { PaletteLogic.search(query, items) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(c.surface1)
                .border(1.dp, c.borderStrong, RoundedCornerShape(Radius.lg))
                .imePadding()
                .navigationBarsPadding()
                .padding(vertical = Sp.md),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Sp.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SIcon(Lucide.Search, size = 17.dp, tint = c.accentText)
                Spacer(Modifier.width(Sp.md))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = Saqr.type.body.copy(color = c.text1),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let(onSelect) }),
                    decorationBox = { inner ->
                        Box(Modifier.height(30.dp), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text(stringResource(R.string.palette_hint), style = Saqr.type.body, color = c.text3)
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                Spacer(Modifier.width(Sp.sm))
                Box(
                    Modifier.height(26.dp).clip(RoundedCornerShape(8.dp)).background(c.surface3)
                        .clickable(onClick = onDismiss).padding(horizontal = Sp.sm),
                    contentAlignment = Alignment.Center,
                ) { Text("Esc", style = Saqr.type.monoMicro, color = c.text3) }
            }
            Spacer(Modifier.height(Sp.sm))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            if (results.isEmpty()) {
                Text(
                    stringResource(R.string.palette_no_results),
                    style = Saqr.type.body, color = c.text3,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Sp.lg, vertical = Sp.xl),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = Sp.xs),
                    modifier = Modifier.heightIn(max = 380.dp),
                ) {
                    items(results) { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(item) }
                                .padding(horizontal = Sp.lg, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (item.kind == PaletteKind.COMMAND) c.accentSoft else c.surface2),
                                contentAlignment = Alignment.Center,
                            ) {
                                SIcon(item.kind.icon(), size = 15.dp, tint = if (item.kind == PaletteKind.COMMAND) c.accentText else c.text2)
                            }
                            Spacer(Modifier.width(Sp.md))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = if (item.kind == PaletteKind.FILE) Saqr.type.monoSmall else Saqr.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                                    color = c.text1,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.semantics { contentDescription = item.title },
                                )
                                if (item.subtitle.isNotBlank() && item.kind != PaletteKind.FILE) {
                                    Text(item.subtitle, style = Saqr.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            SIcon(Lucide.CornerDownLeft, size = 13.dp, tint = c.text3.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}
