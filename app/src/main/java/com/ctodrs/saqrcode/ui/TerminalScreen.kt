package com.ctodrs.saqrcode.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.terminal.LinuxEnv
import com.ctodrs.saqrcode.terminal.TermPalette
import com.ctodrs.saqrcode.terminal.TermRender
import com.ctodrs.saqrcode.terminal.TermRun
import com.termux.terminal.TerminalEmulator
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.SaqrCard
import com.ctodrs.saqrcode.ui.components.Spinner
import com.ctodrs.saqrcode.ui.components.pressable
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.JetBrainsMono
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.launch

/** Tab chip data for the terminal header. */
data class TermTabUi(val id: Int, val title: String, val finished: Boolean)

@Composable
fun TerminalPane(ws: Workspace) {
    val app = SaqrApp.instance
    val linux = app.linux
    val state by linux.state.collectAsState()
    val progress by linux.progress.collectAsState()
    val settings = app.settings
    val version by settings.version.collectAsState()
    version
    val scope = rememberCoroutineScope()
    val mgr = ws.terminals
    LaunchedEffect(Unit) { mgr.ensureOne() }
    val tab = mgr.current
    val ctx = LocalContext.current
    var size by remember { mutableStateOf(80 to 24) }
    val linuxReady = state == LinuxEnv.State.READY && linux.prootAvailable
    TerminalContent(
        emulator = tab?.emulator, version = tab?.version ?: 0,
        tabs = mgr.tabs.map { TermTabUi(it.id, it.title, it.finished) }, active = mgr.active,
        linuxReady = linuxReady, installing = state == LinuxEnv.State.INSTALLING, error = state == LinuxEnv.State.ERROR, progress = progress,
        finished = tab?.finished == true, fontSize = settings.terminalFontSize,
        onInstall = { scope.launch { if (linux.install().isSuccess) mgr.restartAll(size.first, size.second) } },
        onSize = { c, r -> size = c to r; tab?.resize(c, r) },
        onWrite = { tab?.write(it) },
        onPaste = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { tab?.paste(it) }
        },
        onCopyAll = {
            val text = tab?.emulator?.screen?.transcriptText.orEmpty()
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("terminal", text))
        },
        onRestart = { tab?.restart(size.first, size.second) },
        onSelectTab = { mgr.active = it },
        onNewTab = { mgr.newTab().resize(size.first, size.second) },
        onCloseTab = { mgr.closeTab(it); if (mgr.tabs.isEmpty()) mgr.newTab().resize(size.first, size.second) },
        onFont = { settings.terminalFontSize = it },
    )
}

private val EXTRA_KEYS = listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→", "^C", "^D", "^Z", "^L", "HOME", "END", "PGUP", "PGDN", "|", "-", "/", "~", "$")

@Composable
fun TerminalContent(
    emulator: TerminalEmulator?, version: Int, tabs: List<TermTabUi>, active: Int,
    linuxReady: Boolean, installing: Boolean, error: Boolean, progress: String, finished: Boolean, fontSize: Int,
    onInstall: () -> Unit, onSize: (Int, Int) -> Unit, onWrite: (String) -> Unit, onPaste: () -> Unit, onCopyAll: () -> Unit,
    onRestart: () -> Unit, onSelectTab: (Int) -> Unit, onNewTab: () -> Unit, onCloseTab: (Int) -> Unit, onFont: (Int) -> Unit,
    showKeyboardRow: Boolean = true,
) {
    val c = Saqr.colors
    val palette = TermPalette.Saqr
    var ctrl by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var direct by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    fun send(s: String) {
        var out = s
        if (ctrl && s.length == 1) { TermRender.ctrl(s[0])?.let { out = it }; ctrl = false }
        if (alt) { out = "\u001b" + out; alt = false }
        onWrite(out)
    }
    fun flushLine() { if (line.isNotEmpty()) { onWrite(line); line = "" } }
    fun key(k: String) {
        val app = emulator?.isCursorKeysApplicationMode == true
        when (k) {
            "CTRL" -> ctrl = !ctrl
            "ALT" -> alt = !alt
            "↑" -> { flushLine(); send(TermRender.key("UP", app)) }
            "↓" -> { flushLine(); send(TermRender.key("DOWN", app)) }
            "←" -> { flushLine(); send(TermRender.key("LEFT", app)) }
            "→" -> { flushLine(); send(TermRender.key("RIGHT", app)) }
            "^C", "^D", "^Z", "^L" -> { line = ""; onWrite(TermRender.ctrl(k[1])!!) }
            "ESC", "TAB", "HOME", "END", "PGUP", "PGDN" -> { flushLine(); send(TermRender.key(k, app)) }
            else -> if (direct) send(k) else line += k
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Sp.md).padding(bottom = Sp.sm)) {
        if (!linuxReady) {
            SaqrCard(Modifier.fillMaxWidth().padding(bottom = Sp.sm), padding = PaddingValues(Sp.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(c.info.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        SIcon(Lucide.Box, size = 19.dp, tint = c.info)
                    }
                    Spacer(Modifier.width(Sp.md))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.terminal_setup), style = Saqr.type.label.copy(fontWeight = FontWeight.SemiBold), color = c.text1)
                        Text(if (progress.isNotEmpty()) progress else stringResource(R.string.terminal_fallback), style = Saqr.type.caption,
                            color = if (error) c.danger else c.text2, maxLines = 2)
                    }
                    Spacer(Modifier.width(Sp.sm))
                    if (installing) Spinner(22.dp) else PrimaryButton(stringResource(R.string.install), onInstall, icon = Lucide.Zap)
                }
            }
        }
        val shape = RoundedCornerShape(Radius.lg)
        val chromeText = Color(0xFF8A919B)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.weight(1f).fillMaxWidth().clip(shape).background(Color(palette.bg)).border(1.dp, if (c.isDark) c.border else Color(0xFF23272E), shape)) {
                // ---- chrome: session tabs + tools
                Row(Modifier.fillMaxWidth().background(Color(0xFF101216)).padding(start = Sp.sm, end = 4.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        tabs.forEachIndexed { i, t ->
                            val sel = i == active
                            Row(
                                Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(if (sel) Color(0xFF1C2027) else Color.Transparent)
                                    .border(1.dp, if (sel) Color(0xFF2C313A) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { onSelectTab(i) }.padding(start = 8.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(if (t.finished) Color(0xFFF2555A) else if (sel) c.accent else Color(0xFF46C98B)))
                                Spacer(Modifier.width(6.dp))
                                Text(t.title.ifBlank { (if (linuxReady) "alpine" else "sh") + " " + t.id }, style = Saqr.type.monoMicro,
                                    color = if (sel) Color(0xFFEDEEF0) else chromeText, maxLines = 1)
                                Box(Modifier.size(22.dp).clip(CircleShape).clickable { onCloseTab(i) }, contentAlignment = Alignment.Center) {
                                    SIcon(Lucide.X, size = 11.dp, tint = chromeText, contentDescription = stringResource(R.string.close))
                                }
                            }
                        }
                        Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onNewTab), contentAlignment = Alignment.Center) {
                            SIcon(Lucide.Plus, size = 15.dp, tint = chromeText, contentDescription = stringResource(R.string.term_new_tab))
                        }
                    }
                    ChromeIcon(Lucide.Minus, stringResource(R.string.term_smaller)) { onFont(fontSize - 1) }
                    ChromeIcon(Lucide.Plus, stringResource(R.string.term_bigger)) { onFont(fontSize + 1) }
                    ChromeIcon(Lucide.Copy, stringResource(R.string.copy), onCopyAll)
                    ChromeIcon(Lucide.ClipboardPaste, stringResource(R.string.term_paste), onPaste)
                    ChromeIcon(Lucide.RotateCcw, stringResource(R.string.restart_shell), onRestart)
                }
                // ---- screen
                val style = Saqr.type.monoSmall.copy(fontFamily = JetBrainsMono, fontSize = fontSize.sp, lineHeight = (fontSize * 1.3f).sp, color = Color(palette.fg))
                val measurer = rememberTextMeasurer()
                val cell = remember(fontSize) { measurer.measure(AnnotatedString("W".repeat(10)), style).size }
                val density = LocalDensity.current
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clickable(indication = null, interactionSource = null) { runCatching { focus.requestFocus() } }
                    .padding(horizontal = 8.dp, vertical = 6.dp)) {
                    val wPx = with(density) { maxWidth.toPx() }
                    val hPx = with(density) { maxHeight.toPx() }
                    val cols = (wPx / (cell.width / 10f)).toInt().coerceIn(20, 300)
                    val rows = (hPx / cell.height).toInt().coerceIn(4, 200)
                    LaunchedEffect(cols, rows) { onSize(cols, rows) }
                    val lines = remember(emulator, version) { emulator?.let { TermRender.lines(it, palette) }.orEmpty() }
                    val list = rememberLazyListState()
                    LaunchedEffect(lines.size, version) { if (lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }
                    SelectionContainer {
                        LazyColumn(Modifier.fillMaxSize(), state = list) {
                            items(lines.size) { i -> Text(runsToAnnotated(lines[i]), style = style, softWrap = false, maxLines = 1) }
                        }
                    }
                    if (finished) Text("[process exited — ↻]", Modifier.align(Alignment.BottomEnd), style = Saqr.type.monoMicro, color = Color(0xFFF2555A))
                }
                // ---- extra keys
                if (showKeyboardRow) Row(Modifier.fillMaxWidth().background(Color(0xFF0C0E11)).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    EXTRA_KEYS.forEach { k -> ExtraKey(k, active = (k == "CTRL" && ctrl) || (k == "ALT" && alt)) { key(k) } }
                }
                // ---- input
                Row(Modifier.fillMaxWidth().background(Color(0xFF0C0E11)).padding(start = Sp.md, end = 6.dp, top = 4.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(if (direct) c.accentSoft else Color.Transparent).clickable { flushLine(); direct = !direct },
                        contentAlignment = Alignment.Center) {
                        SIcon(Lucide.Keyboard, size = 16.dp, tint = if (direct) c.accent else chromeText, contentDescription = stringResource(R.string.term_direct))
                    }
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        line,
                        { v ->
                            when {
                                direct -> { // every keystroke goes straight to the PTY
                                    if (v.length > line.length && v.startsWith(line)) v.substring(line.length).forEach { ch -> if (ch == '\n') send("\r") else send(ch.toString()) }
                                    else if (v.length < line.length) repeat(line.length - v.length) { onWrite("\u007f") }
                                    line = if (v.length > 64) "" else v
                                }
                                ctrl && v.length == line.length + 1 -> send(v.last().toString())
                                else -> line = v
                            }
                        },
                        Modifier.weight(1f).focusRequester(focus), singleLine = true,
                        textStyle = Saqr.type.mono.copy(color = Color(0xFFEDEEF0)), cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None,
                            keyboardType = if (direct) KeyboardType.Password else KeyboardType.Ascii, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (direct) { onWrite("\r"); line = "" } else { onWrite(line + "\r"); line = "" } }),
                        decorationBox = { inner ->
                            Box {
                                if (line.isEmpty()) Text(stringResource(if (direct) R.string.term_direct_hint else R.string.command_hint),
                                    style = Saqr.type.bodySmall, color = Color(0xFF59606A))
                                inner()
                            }
                        },
                    )
                    Box(Modifier.size(36.dp).pressable { if (direct) onWrite("\r") else { onWrite(line + "\r"); line = "" } }.clip(CircleShape)
                        .background(if (line.isNotBlank()) c.accent else Color(0xFF1B1F25)), contentAlignment = Alignment.Center) {
                        SIcon(Lucide.CornerDownLeft, size = 16.dp, tint = if (line.isNotBlank()) c.onAccent else Color(0xFF6D737D), contentDescription = stringResource(R.string.run))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChromeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        SIcon(icon, size = 14.dp, tint = Color(0xFF8A919B), contentDescription = label)
    }
}

@Composable
private fun ExtraKey(label: String, active: Boolean, onClick: () -> Unit) {
    val c = Saqr.colors
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(if (active) c.accent else Color(0xFF15181D))
            .border(1.dp, if (active) c.accent else Color(0xFF262A31), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Saqr.type.monoMicro, color = if (active) c.onAccent else Color(0xFFB5BCC6)) }
}

fun runsToAnnotated(runs: List<TermRun>): AnnotatedString = buildAnnotatedString {
    runs.forEach { r ->
        val deco = when {
            r.underline && r.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            r.underline -> TextDecoration.Underline
            r.strike -> TextDecoration.LineThrough
            else -> null
        }
        val fg = Color(r.fg).let { if (r.dim) it.copy(alpha = 0.6f) else it }
        withStyle(SpanStyle(
            color = fg, background = r.bg?.let { Color(it) } ?: Color.Unspecified,
            fontWeight = if (r.bold) FontWeight.Bold else null, fontStyle = if (r.italic) FontStyle.Italic else null, textDecoration = deco,
        )) { append(r.text) }
    }
}
