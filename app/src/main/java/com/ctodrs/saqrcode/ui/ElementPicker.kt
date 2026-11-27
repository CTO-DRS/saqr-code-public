package com.ctodrs.saqrcode.ui

import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.SaqrApp
import com.ctodrs.saqrcode.agent.ElementContext
import com.ctodrs.saqrcode.agent.ImageMath
import com.ctodrs.saqrcode.agent.ImagePart
import com.ctodrs.saqrcode.agent.PickedElement
import com.ctodrs.saqrcode.agent.PickerJs
import com.ctodrs.saqrcode.agent.PickerSecurity
import com.ctodrs.saqrcode.agent.SourceLocator
import com.ctodrs.saqrcode.agent.SourceMatch
import com.ctodrs.saqrcode.ui.chat.ImageCodec
import com.ctodrs.saqrcode.ui.chat.SaqrSheet
import com.ctodrs.saqrcode.ui.components.EmptyState
import com.ctodrs.saqrcode.ui.components.PrimaryButton
import com.ctodrs.saqrcode.ui.components.SaqrField
import com.ctodrs.saqrcode.ui.icons.FileTypeIcon
import com.ctodrs.saqrcode.ui.icons.Lucide
import com.ctodrs.saqrcode.ui.icons.SIcon
import com.ctodrs.saqrcode.ui.theme.Radius
import com.ctodrs.saqrcode.ui.theme.Saqr
import com.ctodrs.saqrcode.ui.theme.Sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A picked element plus what we learn about it afterwards (source line, screenshot crop). */
class PickResult(val el: PickedElement, val crop: ImagePart?) {
    var match by mutableStateOf<SourceMatch?>(null)
    var locating by mutableStateOf(true)
}

/**
 * JS → Kotlin bridge for the element picker. Exposed only while a project page is loaded, and
 * every call must carry the per-page [token] the injected script got: frames from other origins
 * can see the interface object but not the token.
 */
class PickerBridge(private val dir: File, private val onPicked: (PickedElement) -> Unit) {
    @Volatile var currentUrl: String? = null
    @Volatile var token: String = newToken()
    private val main = Handler(Looper.getMainLooper())

    fun newToken(): String = UUID.randomUUID().toString().also { token = it }

    @JavascriptInterface
    fun onPick(tok: String?, json: String?) {
        if (tok == null || tok != token || json == null || json.length > 64_000) return
        if (!PickerSecurity.isLocal(currentUrl, dir)) return
        val el = PickedElement.parse(json, dir) ?: return
        main.post { onPicked(el) }
    }
}

/** Crops the element's box out of the WebView's current frame (CSS px → view px). */
fun captureElement(wv: WebView, el: PickedElement): ImagePart? = runCatching {
    if (wv.width <= 0 || wv.height <= 0) return null
    val full = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(full)
    canvas.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
    wv.draw(canvas)
    val r = ImageMath.cropRect(el.x, el.y, el.w, el.h, el.viewportWidth, full.width, full.height) ?: return null
    ImageCodec.encode(Bitmap.createBitmap(full, r[0], r[1], r[2], r[3]))
}.getOrNull()

// ------------------------------------------------------------------ preview pane

/** v0.1.16: previewable HTML entry points — index.html first, then shallowest, then name. Skips node_modules/.git. */
fun previewCandidates(dir: File): List<File> {
    val all = dir.walkTopDown().maxDepth(3).onEnter { it.name !in setOf("node_modules", ".git") }
        .filter { it.isFile && it.extension.lowercase() in setOf("html", "htm") }.toList()
    return all.sortedWith(compareBy(
        { it.name.lowercase() != "index.html" },
        { it.relativeTo(dir).path.count { ch -> ch == File.separatorChar || ch == '/' } },
        { it.relativeTo(dir).path.lowercase() },
    ))
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun PreviewPane(ws: Workspace) {
    val candidates = remember(ws.fsVersion) { previewCandidates(ws.dir) }
    var entryIdx by remember(ws.dir) { mutableIntStateOf(0) }
    val entry = candidates.getOrNull(entryIdx) ?: candidates.firstOrNull()
    var reload by remember { mutableIntStateOf(0) }
    var autoReload by rememberSaveable { mutableStateOf(true) }
    var picking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PickResult?>(null) }
    var filePicker by remember { mutableStateOf(false) }
    val webRef = remember { arrayOfNulls<WebView>(1) }
    val scrollSaved = remember { arrayOf("0") }
    val pickingNow by rememberUpdatedState(picking)
    val autoNow by rememberUpdatedState(autoReload)
    val store = SaqrApp.instance.settings
    val c = Saqr.colors
    if (entry == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(stringResource(R.string.preview_empty_title), stringResource(R.string.preview_empty_body), icon = Lucide.Globe)
        }
        return
    }
    // v0.1.16 live preview: a debounced reload follows workspace changes (agent edits, git ops)
    var fsSeen by remember { mutableIntStateOf(ws.fsVersion) }
    LaunchedEffect(ws.fsVersion) {
        if (ws.fsVersion != fsSeen) {
            fsSeen = ws.fsVersion
            if (autoNow) { kotlinx.coroutines.delay(700); reload++ }
        }
    }
    fun triggerReload() {
        webRef[0]?.evaluateJavascript("(window.scrollY||0).toString()") { v -> scrollSaved[0] = v?.trim('"') ?: "0" }
        reload++
    }
    val bridge = remember(ws.dir) {
        PickerBridge(ws.dir) { el ->
            val wv = webRef[0]
            if (wv == null) { result = PickResult(el, null); return@PickerBridge }
            // hide the gold overlay for the crop, then bring it back
            wv.evaluateJavascript("window.__saqrPicker&&__saqrPicker.box(false)") {
                wv.postDelayed({
                    val crop = captureElement(wv, el)
                    wv.evaluateJavascript("window.__saqrPicker&&__saqrPicker.box(true)", null)
                    result = PickResult(el, crop)
                }, 120)
            }
        }
    }
    fun inject(wv: WebView) {
        if (PickerSecurity.isLocal(bridge.currentUrl, ws.dir)) wv.evaluateJavascript(PickerJs.toggle(pickingNow, bridge.token), null)
    }
    LaunchedEffect(picking) { webRef[0]?.let { inject(it) } }
    result?.let { r ->
        LaunchedEffect(r) {
            r.match = withContext(Dispatchers.IO) { runCatching { SourceLocator.locate(ws.dir, r.el) }.getOrNull() }
            r.locating = false
        }
    }
    DisposableEffect(Unit) { onDispose { webRef[0] = null } }

    Column(Modifier.fillMaxSize().padding(horizontal = Sp.md).padding(bottom = Sp.sm).navigationBarsPadding()) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().padding(bottom = Sp.sm).clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape)
                .padding(start = Sp.md, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SIcon(Lucide.Globe, size = 15.dp, tint = c.text3)
                Spacer(Modifier.width(Sp.sm))
                Text(entry.relativeTo(ws.dir).path, style = Saqr.type.monoSmall, color = c.text2,
                    modifier = Modifier.weight(1f).clickable(enabled = candidates.size > 1, onClickLabel = stringResource(R.string.preview_file_pick)) { filePicker = true },
                    maxLines = 1)
                // v0.1.16: live auto-refresh toggle
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(if (autoReload) c.accent else Color.Transparent)
                        .clickable(onClickLabel = stringResource(R.string.preview_auto)) { autoReload = !autoReload },
                    contentAlignment = Alignment.Center,
                ) { SIcon(Lucide.Zap, size = 16.dp, tint = if (autoReload) c.onAccent else c.text1, contentDescription = stringResource(R.string.preview_auto)) }
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(if (picking) c.accent else Color.Transparent)
                        .clickable(onClickLabel = stringResource(R.string.pick_element)) { picking = !picking },
                    contentAlignment = Alignment.Center,
                ) { SIcon(Lucide.Crosshair, size = 16.dp, tint = if (picking) c.onAccent else c.text1, contentDescription = stringResource(R.string.pick_element)) }
                Spacer(Modifier.width(2.dp))
                val reloadLabel = stringResource(R.string.reload_preview)
                Box(Modifier.minimumInteractiveComponentSize().size(32.dp).clip(CircleShape).clickable(onClickLabel = reloadLabel, role = Role.Button) { triggerReload() }, contentAlignment = Alignment.Center) { SIcon(Lucide.RefreshCw, size = 15.dp, contentDescription = reloadLabel) }
            }
        }
        if (picking) PickHint()
        val shape = RoundedCornerShape(Radius.lg)
        Box(Modifier.weight(1f).fillMaxWidth().clip(shape).border(if (picking) 2.dp else 1.dp, if (picking) c.accent else c.border, shape).background(Color.White)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = true
                        settings.domStorageEnabled = true
                        addJavascriptInterface(bridge, PickerJs.BRIDGE)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                bridge.currentUrl = url; bridge.newToken()
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                bridge.currentUrl = url
                                inject(view) // re-inject after every (re)load
                                // v0.1.16: a live reload keeps the scroll position
                                val y = scrollSaved[0].toDoubleOrNull() ?: 0.0
                                if (y > 0) view.evaluateJavascript("window.scrollTo(0, ${y.toLong()})", null)
                            }
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val u = request.url.toString()
                                if (!request.isForMainFrame || PickerSecurity.isLocal(u, ws.dir)) return false
                                // the bridge never sees other sites: open them in the browser instead
                                runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                return true
                            }
                        }
                        webRef[0] = this
                    }
                },
                update = { wv -> wv.tag.let { if (it != reload) { wv.tag = reload; wv.loadUrl("file://" + entry.absolutePath) } } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (filePicker) {
        SaqrSheet(onDismiss = { filePicker = false }) {
            Row(Modifier.padding(bottom = Sp.md), verticalAlignment = Alignment.CenterVertically) {
                SIcon(Lucide.Globe, size = 20.dp, tint = c.accentText)
                Spacer(Modifier.width(Sp.sm))
                Text(stringResource(R.string.preview_file_title), style = Saqr.type.title2, color = c.text1)
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                candidates.forEachIndexed { i, f ->
                    val rel = f.relativeTo(ws.dir).path
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .clickable { entryIdx = i; filePicker = false; triggerReload() }
                            .padding(horizontal = Sp.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(rel, style = Saqr.type.monoSmall, color = if (i == entryIdx) c.accentText else c.text1, modifier = Modifier.weight(1f))
                        if (i == entryIdx) SIcon(Lucide.Check, size = 16.dp, tint = c.accentText)
                    }
                }
            }
        }
    }

    result?.let { r ->
        val model = store.config(store.activeProvider).model
        val vision = store.supportsVision(model)
        ElementEditSheet(
            r, attachShot = vision, busy = ws.agent.running,
            onDismiss = { result = null },
            onSubmit = { text ->
                val ctx = ElementContext.build(r.el, r.match, text)
                ws.agent.send(ctx, display = ElementContext.display(r.el, text), images = listOfNotNull(r.crop.takeIf { vision }))
                result = null; picking = false
                ws.tab = Pane.AGENT
            },
        )
    }
}

@Composable
private fun PickHint() {
    val c = Saqr.colors
    Row(
        Modifier.fillMaxWidth().padding(bottom = Sp.sm).clip(RoundedCornerShape(Radius.sm)).background(c.accentSoft)
            .border(1.dp, c.accent.copy(alpha = 0.35f), RoundedCornerShape(Radius.sm)).padding(horizontal = Sp.md, vertical = Sp.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIcon(Lucide.Crosshair, size = 15.dp, tint = c.accentText)
        Spacer(Modifier.width(Sp.sm))
        Text(stringResource(R.string.pick_hint), style = Saqr.type.caption, color = c.text1)
    }
}

// ------------------------------------------------------------------ sheet

@Composable
fun ElementEditSheet(r: PickResult, attachShot: Boolean, busy: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember(r) { mutableStateOf("") }
    SaqrSheet(onDismiss) {
        ElementEditContent(r.el, r.match, r.locating, r.crop, attachShot, busy, text, { text = it }, { onSubmit(text) })
    }
}

/** The element summary + "What should change?" form. Stateless so it can be screenshot-tested. */
@Composable
fun ElementEditContent(
    el: PickedElement, match: SourceMatch?, locating: Boolean, crop: ImagePart?, attachShot: Boolean, busy: Boolean,
    text: String, onText: (String) -> Unit, onSubmit: () -> Unit,
) {
    val c = Saqr.colors
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Sp.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                SIcon(Lucide.Crosshair, size = 18.dp, tint = c.accentText)
            }
            Spacer(Modifier.width(Sp.md))
            Text(stringResource(R.string.pick_sheet_title), style = Saqr.type.title2, color = c.text1, modifier = Modifier.weight(1f))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text("<${el.tag}>", style = Saqr.type.monoSmall.copy(fontWeight = FontWeight.SemiBold), color = c.accentText,
                    modifier = Modifier.clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }

        // ---- summary card
        val shape = RoundedCornerShape(Radius.md)
        Column(Modifier.fillMaxWidth().clip(shape).background(c.surface2).border(1.dp, c.border, shape).padding(Sp.md),
            verticalArrangement = Arrangement.spacedBy(Sp.sm)) {
            Row(verticalAlignment = Alignment.Top) {
                if (crop != null) {
                    com.ctodrs.saqrcode.ui.chat.ImagePreview(crop, 110.dp, 76.dp)
                    Spacer(Modifier.width(Sp.md))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(el.selector, style = Saqr.type.monoSmall.copy(fontWeight = FontWeight.SemiBold), color = c.text1, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (match != null) {
                            FileTypeIcon(match.path.substringAfterLast('/'), 15.dp)
                            Spacer(Modifier.width(6.dp))
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(match.toString(), style = Saqr.type.monoSmall, color = c.accentText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            SIcon(Lucide.Search, size = 14.dp, tint = c.text3)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (locating) R.string.pick_locating else R.string.pick_source_none), style = Saqr.type.caption, color = c.text3, maxLines = 1)
                        }
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text("${el.w.toInt()} × ${el.h.toInt()}", style = Saqr.type.monoMicro, color = c.text3)
                    }
                }
            }
            if (el.text.isNotBlank()) {
                Text("“" + el.text.replace('\n', ' ').take(140) + "”", style = Saqr.type.bodySmall.copy(textDirection = TextDirection.Content),
                    color = c.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            StyleChips(el.styles)
        }

        // ---- instruction
        SaqrField(text, onText, label = stringResource(R.string.pick_what_change), placeholder = stringResource(R.string.pick_placeholder), singleLine = false)
        QuickChips { onText((text.trimEnd() + " " + it).trimStart()) }
        if (!attachShot) Note(Lucide.Info, stringResource(R.string.pick_no_vision), c.text3)
        if (busy) Note(Lucide.TriangleAlert, stringResource(R.string.pick_busy), c.warning)
        PrimaryButton(stringResource(R.string.pick_send), onSubmit, Modifier.fillMaxWidth(), icon = Lucide.ArrowUp, enabled = text.isNotBlank() && !busy)
        Spacer(Modifier.height(Sp.xs))
    }
}

@Composable
private fun Note(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SIcon(icon, size = 14.dp, tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(text, style = Saqr.type.caption, color = tint)
    }
}

@Composable
private fun QuickChips(onPick: (String) -> Unit) {
    val c = Saqr.colors
    val chips = listOf(
        R.string.chip_color to R.string.chip_color_prompt, R.string.chip_size to R.string.chip_size_prompt,
        R.string.chip_text to R.string.chip_text_prompt, R.string.chip_delete to R.string.chip_delete_prompt,
        R.string.chip_move to R.string.chip_move_prompt,
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Sp.sm)) {
        chips.forEach { (label, prompt) ->
            val p = stringResource(prompt)
            Text(stringResource(label), style = Saqr.type.label, color = c.text1,
                modifier = Modifier.heightIn(min = 36.dp).clip(CircleShape).background(c.surface2).border(1.dp, c.border, CircleShape)
                    .clickable { onPick(p) }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

/** color / background as swatches, the rest as small mono chips. */
@Composable
private fun StyleChips(styles: Map<String, String>) {
    val c = Saqr.colors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("color", "background", "font-size", "display").forEach { k ->
                val v = styles[k] ?: return@forEach
                Row(Modifier.clip(RoundedCornerShape(Radius.xs)).background(c.surface3.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    cssColor(v)?.let { col ->
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(col).border(1.dp, c.borderStrong, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(5.dp))
                    }
                    Text("$k: " + (if (cssColor(v) != null) cssHex(v) else v.take(24)), style = Saqr.type.monoMicro, color = c.text2, maxLines = 1)
                }
            }
        }
    }
}

private val RGB = Regex("""rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)(?:[,\s/]+([\d.]+))?\s*\)""")

/** `rgb(…)` / `rgba(…)` from getComputedStyle → Color; null when transparent or not a color. */
fun cssColor(v: String): Color? {
    val m = RGB.find(v) ?: return null
    val a = m.groupValues[4].toFloatOrNull() ?: 1f
    if (a == 0f) return null
    return Color(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), (a * 255).toInt().coerceIn(0, 255))
}

fun cssHex(v: String): String {
    val m = RGB.find(v) ?: return v
    return "#%02X%02X%02X".format(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
}
