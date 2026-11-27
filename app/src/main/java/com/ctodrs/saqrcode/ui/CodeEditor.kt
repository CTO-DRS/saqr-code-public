package com.ctodrs.saqrcode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.editor.CodePalette
import com.ctodrs.saqrcode.editor.FindReplace
import com.ctodrs.saqrcode.editor.HighlightTransformation
import com.ctodrs.saqrcode.editor.IncrementalHighlighter
import com.ctodrs.saqrcode.editor.SyntaxHighlighter
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp

/** Find-bar state kept per open file. */
private class FindState {
    var open by mutableStateOf(false)
    var showReplace by mutableStateOf(false)
    var query by mutableStateOf("")
    var replacement by mutableStateOf("")
    var caseSensitive by mutableStateOf(false)
    var regex by mutableStateOf(false)
    var current by mutableIntStateOf(0)
    var replacedCount by mutableIntStateOf(-1)
    val options get() = FindReplace.Options(caseSensitive, regex)
}

@Composable
fun CodeEditor(f: OpenFile, fontSize: Int = 13, wrap: Boolean = false) {
    val c = Saqr.colors
    val palette = if (c.isDark) CodePalette.Dark else CodePalette.Light
    val lang = remember(f.path) { SyntaxHighlighter.languageOf(f.path) }
    val highlighter = remember(f, lang) { IncrementalHighlighter(lang) }
    val find = remember(f) { FindState() }
    val findFocus = remember { FocusRequester() }
    val style = Saqr.type.mono.copy(color = c.text1, fontSize = fontSize.sp, lineHeight = (fontSize * 1.55f).sp)
    val text = f.value.text
    val lines = remember(text) { text.count { it == '\n' } + 1 }
    val caret = f.value.selection.start.coerceIn(0, text.length)
    val curLine = remember(caret, text) { var n = 1; for (i in 0 until caret) if (text[i] == '\n') n++; n }
    @Suppress("UNUSED_VARIABLE") val hv = f.historyVersion // recompose undo/redo enabled state

    val result = remember(text, find.open, find.query, find.caseSensitive, find.regex) {
        if (!find.open) FindReplace.Result(emptyList()) else FindReplace.find(text, find.query, find.options)
    }
    val matches = result.matches
    val curIdx = if (matches.isEmpty()) -1 else find.current.coerceIn(0, matches.lastIndex)
    val transformation = remember(highlighter, palette, matches, curIdx) { HighlightTransformation(highlighter, palette, matches, curIdx) }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    val padPx = with(density) { Sp.md.toPx() }

    fun select(i: Int) {
        if (i !in matches.indices) return
        find.current = i
        val r = matches[i]
        f.value = f.value.copy(selection = TextRange(r.first, r.last + 1))
    }
    fun next() { if (matches.isNotEmpty()) select(if (curIdx < 0) FindReplace.nextIndex(matches, caret) else (curIdx + 1) % matches.size) }
    fun prev() { if (matches.isNotEmpty()) select(if (curIdx <= 0) matches.lastIndex else curIdx - 1) }
    fun replaceOne() {
        val r = matches.getOrNull(curIdx) ?: return
        val (t, pos) = FindReplace.replaceOne(text, r, find.query, find.replacement, find.options)
        f.history.breakGroup(); f.edit(TextFieldValue(t, TextRange(pos))); f.history.breakGroup()
        find.replacedCount = -1
    }
    fun replaceAll() {
        val (t, n) = FindReplace.replaceAll(text, find.query, find.replacement, find.options)
        if (n > 0) { f.history.breakGroup(); f.edit(TextFieldValue(t, TextRange(caret.coerceAtMost(t.length)))); f.history.breakGroup() }
        find.replacedCount = n
    }
    fun openFind(replace: Boolean) {
        find.open = true; if (replace) find.showReplace = true
        val sel = f.value.selection
        if (!sel.collapsed && sel.length < 200) text.substring(sel.min, sel.max).takeIf { '\n' !in it }?.let { find.query = it }
    }

    // keep the current match in view
    LaunchedEffect(curIdx, matches) {
        val l = layout ?: return@LaunchedEffect
        val r = matches.getOrNull(curIdx) ?: return@LaunchedEffect
        if (r.first > l.layoutInput.text.length) return@LaunchedEffect
        val line = l.getLineForOffset(r.first)
        val top = l.getLineTop(line) + padPx
        val vp = vScroll.viewportSize.toFloat()
        if (top < vScroll.value || top > vScroll.value + vp - 2 * padPx) vScroll.animateScrollTo((top - vp / 3).toInt().coerceAtLeast(0))
        val x = l.getHorizontalPosition(r.first, true)
        val hvp = hScroll.viewportSize.toFloat()
        if (x < hScroll.value || x > hScroll.value + hvp - 4 * padPx) hScroll.animateScrollTo((x - hvp / 3).toInt().coerceAtLeast(0))
    }
    LaunchedEffect(find.open) { if (find.open) runCatching { findFocus.requestFocus() } }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            Modifier.fillMaxSize().onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val mod = e.isCtrlPressed || e.isMetaPressed
                when {
                    mod && e.key == Key.Z && !e.isShiftPressed -> { f.undo(); true }
                    mod && ((e.key == Key.Z && e.isShiftPressed) || e.key == Key.Y) -> { f.redo(); true }
                    mod && e.key == Key.F -> { openFind(false); true }
                    mod && e.key == Key.H -> { openFind(true); true }
                    e.key == Key.F3 && find.open -> { if (e.isShiftPressed) prev() else next(); true }
                    e.key == Key.Escape && find.open -> { find.open = false; true }
                    else -> false
                }
            },
        ) {
            // toolbar
            Row(Modifier.fillMaxWidth().background(c.surface1).padding(horizontal = Sp.sm, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextToolIcon("A−", stringResource(R.string.editor_font_down), enabled = fontSize > 12) { settingsEditorFont(fontSize - 1) }
                TextToolIcon("A+", stringResource(R.string.editor_font_up), enabled = fontSize < 20) { settingsEditorFont(fontSize + 1) }
                ToolIcon(Lucide.ArrowUpDown, stringResource(R.string.editor_wrap), active = wrap) { settingsEditorWrap(!wrap) }
                ToolIcon(Lucide.Undo2, stringResource(R.string.editor_undo), enabled = f.history.canUndo) { f.undo() }
                ToolIcon(Lucide.Redo2, stringResource(R.string.editor_redo), enabled = f.history.canRedo) { f.redo() }
                ToolIcon(Lucide.Search, stringResource(R.string.editor_find), active = find.open) { if (find.open) find.open = false else openFind(false) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            if (find.open) FindBar(find, result, curIdx, findFocus, ::next, ::prev, ::replaceOne, ::replaceAll)

            Row(
                Modifier.weight(1f).fillMaxWidth().background(c.codeBg).verticalScroll(vScroll).drawBehind {
                    val l = layout ?: return@drawBehind
                    if (caret > l.layoutInput.text.length) return@drawBehind
                    val line = l.getLineForOffset(caret)
                    val top = l.getLineTop(line) + padPx
                    drawRect(palette.currentLine, Offset(0f, top), Size(size.width, l.getLineBottom(line) - l.getLineTop(line)))
                },
            ) {
                val gutter = remember(lines, curLine, c) {
                    buildAnnotatedString {
                        for (i in 1..lines) {
                            if (i == curLine) withStyle(SpanStyle(color = c.accentText, fontWeight = FontWeight.SemiBold)) { append(i.toString()) }
                            else withStyle(SpanStyle(color = c.text3.copy(alpha = 0.7f))) { append(i.toString()) }
                            if (i < lines) append('\n')
                        }
                    }
                }
                Text(gutter, Modifier.padding(start = Sp.md, end = Sp.sm, top = Sp.md, bottom = Sp.md).widthIn(min = 26.dp), style = style.copy(textAlign = TextAlign.End))
                Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
                if (wrap) {
                    BasicTextField(
                        value = f.value, onValueChange = { f.edit(it) }, textStyle = style, cursorBrush = SolidColor(c.accent),
                        visualTransformation = transformation,
                        onTextLayout = { layout = it },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                        modifier = Modifier.weight(1f).padding(horizontal = Sp.md, vertical = Sp.md),
                    )
                } else Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                    BasicTextField(
                        value = f.value, onValueChange = { f.edit(it) }, textStyle = style, cursorBrush = SolidColor(c.accent),
                        visualTransformation = transformation,
                        onTextLayout = { layout = it },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                        modifier = Modifier.padding(horizontal = Sp.md, vertical = Sp.md).widthIn(min = 600.dp),
                    )
                }
            }
            // status bar
            Row(Modifier.fillMaxWidth().background(c.surface1).padding(horizontal = Sp.lg, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(lang.uppercase(), style = Saqr.type.monoMicro, color = c.accentText)
                Spacer(Modifier.width(Sp.md))
                Text("Ln $curLine · $lines lines", style = Saqr.type.monoMicro, color = c.text3)
                Spacer(Modifier.weight(1f))
                Text(if (f.dirty) "● " + stringResource(R.string.unsaved) else "UTF-8", style = Saqr.type.micro, color = if (f.dirty) c.accentText else c.text3)
            }
        }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    val c = Saqr.colors
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(if (active) c.accentSoft else c.surface1)
            .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { SIcon(icon, size = 16.dp, tint = when { active -> c.accentText; enabled -> c.text2; else -> c.text3.copy(alpha = 0.45f) }) }
}

@Composable
private fun TextToolIcon(text: String, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Saqr.colors
    Box(
        Modifier.size(width = 34.dp, height = 34.dp).clip(RoundedCornerShape(8.dp)).background(c.surface1)
            .clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Saqr.type.micro, color = if (enabled) c.text2 else c.text3.copy(alpha = 0.45f)) }
}

/** Persists the editor font size (v0.12); safe no-op when the app store is unavailable. */
private fun settingsEditorFont(size: Int) {
    runCatching { com.ctodrs.saqrcode.SaqrApp.instance.settings.editorFontSize = size.coerceIn(12, 20) }
}

/** Persists the wrap toggle (v0.12); safe no-op when the app store is unavailable. */
private fun settingsEditorWrap(wrap: Boolean) {
    runCatching { com.ctodrs.saqrcode.SaqrApp.instance.settings.editorWrap = wrap }
}

@Composable
private fun ToggleChip(text: String, label: String, on: Boolean, onClick: () -> Unit) {
    val c = Saqr.colors
    Box(
        Modifier.size(width = 32.dp, height = 28.dp).clip(RoundedCornerShape(6.dp)).background(if (on) c.accentSoft else c.surface3)
            .clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Saqr.type.monoMicro, color = if (on) c.accentText else c.text2) }
}

@Composable
private fun FindField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier, error: Boolean = false, onEnter: () -> Unit) {
    val c = Saqr.colors
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = Saqr.type.monoSmall.copy(color = c.text1), cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onEnter() }, onDone = { onEnter() }),
        modifier = modifier.onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { onEnter(); true } else false
        },
        decorationBox = { inner ->
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(c.surface3).border(1.dp, if (error) c.danger else c.border, RoundedCornerShape(8.dp))
                    .padding(horizontal = Sp.sm, vertical = 6.dp),
            ) {
                if (value.isEmpty()) Text(hint, style = Saqr.type.caption, color = c.text3)
                inner()
            }
        },
    )
}

@Composable
private fun FindBar(
    find: FindState, result: FindReplace.Result, curIdx: Int, focus: FocusRequester,
    next: () -> Unit, prev: () -> Unit, replaceOne: () -> Unit, replaceAll: () -> Unit,
) {
    val c = Saqr.colors
    val count = result.matches.size
    Column(Modifier.fillMaxWidth().background(c.surface2).padding(horizontal = Sp.sm, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolIcon(if (find.showReplace) Lucide.ChevronDown else Lucide.ChevronRight, stringResource(R.string.find_toggle_replace)) { find.showReplace = !find.showReplace }
            FindField(find.query, { find.query = it; find.current = 0; find.replacedCount = -1 }, stringResource(R.string.find_hint),
                Modifier.weight(1f).focusRequester(focus), error = result.error != null, onEnter = next)
            ToggleChip("Aa", stringResource(R.string.find_case), find.caseSensitive) { find.caseSensitive = !find.caseSensitive }
            ToggleChip(".*", stringResource(R.string.find_regex), find.regex) { find.regex = !find.regex }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val status = when {
                result.error != null -> stringResource(R.string.find_invalid_regex)
                find.replacedCount >= 0 -> stringResource(R.string.replaced_n, find.replacedCount)
                find.query.isEmpty() -> ""
                count == 0 -> stringResource(R.string.find_no_results)
                else -> stringResource(R.string.find_count, curIdx + 1, count) + if (count >= FindReplace.MAX_MATCHES) "+" else ""
            }
            Text(status, style = Saqr.type.micro, color = if (result.error != null || (count == 0 && find.query.isNotEmpty())) c.danger else c.text3,
                modifier = Modifier.weight(1f).padding(start = Sp.sm))
            ToolIcon(Lucide.ArrowUp, stringResource(R.string.find_prev), enabled = count > 0, onClick = prev)
            ToolIcon(Lucide.ArrowDown, stringResource(R.string.find_next), enabled = count > 0, onClick = next)
            ToolIcon(Lucide.X, stringResource(R.string.find_close)) { find.open = false }
        }
        if (find.showReplace) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FindField(find.replacement, { find.replacement = it }, stringResource(R.string.replace_hint), Modifier.weight(1f), onEnter = replaceOne)
            SmallTextButton(stringResource(R.string.replace_one), enabled = curIdx >= 0, onClick = replaceOne)
            SmallTextButton(stringResource(R.string.replace_all), enabled = count > 0, onClick = replaceAll)
        }
    }
}

@Composable
private fun SmallTextButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Saqr.colors
    Box(
        Modifier.heightIn(min = 30.dp).clip(RoundedCornerShape(8.dp)).background(c.surface3).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Sp.sm),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Saqr.type.micro, color = if (enabled) c.text1 else c.text3) }
}
