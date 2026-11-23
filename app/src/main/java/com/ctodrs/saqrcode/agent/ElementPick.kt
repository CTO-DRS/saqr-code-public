package com.ctodrs.saqrcode.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.net.URLDecoder

/** One element on the path from the picked node up to the root, as the picker script reports it. */
data class ChainNode(val tag: String, val id: String = "", val classes: List<String> = emptyList(), val nth: Int = 1, val count: Int = 1)

/** What the preview's element picker sends back for the tapped element. */
data class PickedElement(
    val selector: String,
    val outerHtml: String,
    val text: String,
    val styles: Map<String, String>,
    val x: Double, val y: Double, val w: Double, val h: Double,
    /** Viewport width in CSS px, to map [x]/[w] onto the WebView bitmap. */
    val viewportWidth: Double,
    val url: String,
    /** Project-relative file the page came from, when it is a local file. */
    val file: String?,
    val tag: String,
) {
    companion object {
        const val MAX_HTML = 2000
        const val MAX_TEXT = 300
        val STYLE_KEYS = listOf("color", "background", "font-size", "margin", "padding", "display")

        fun parse(json: String, projectDir: File?): PickedElement? = runCatching {
            val o = JSONObject(json)
            val chainArr = o.optJSONArray("chain") ?: JSONArray()
            val chain = (0 until chainArr.length()).mapNotNull { i ->
                val n = chainArr.optJSONObject(i) ?: return@mapNotNull null
                val cls = n.optJSONArray("cls")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                ChainNode(n.optString("tag").lowercase(), n.optString("id"), cls, n.optInt("nth", 1), n.optInt("count", 1))
            }
            if (chain.isEmpty()) return null
            val st = o.optJSONObject("styles") ?: JSONObject()
            val r = o.optJSONObject("rect") ?: JSONObject()
            val url = o.optString("url")
            PickedElement(
                selector = Selector.build(chain),
                outerHtml = truncate(o.optString("html"), MAX_HTML),
                text = truncate(o.optString("text").trim(), MAX_TEXT),
                styles = STYLE_KEYS.associateWith { st.optString(it) }.filterValues { it.isNotBlank() },
                x = r.optDouble("x", 0.0), y = r.optDouble("y", 0.0), w = r.optDouble("w", 0.0), h = r.optDouble("h", 0.0),
                viewportWidth = o.optDouble("vw", 0.0),
                url = url, file = PickerSecurity.relativeFile(url, projectDir), tag = chain.first().tag,
            )
        }.getOrNull()

        fun truncate(s: String, max: Int) = if (s.length <= max) s else s.take(max - 1) + "…"
    }
}

/** Robust CSS selector: `#id` when there is one, else `tag.class:nth-of-type(n)` path, at most [MAX_DEPTH] levels. */
object Selector {
    const val MAX_DEPTH = 6
    private val IDENT = Regex("""^-?[A-Za-z_][A-Za-z0-9_-]*$""")

    fun idSelector(id: String) = if (IDENT.matches(id)) "#$id" else "[id=\"" + id.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]"

    /** [chain] goes from the picked element (index 0) up towards the root. */
    fun build(chain: List<ChainNode>): String {
        val parts = ArrayList<String>()
        for ((i, n) in chain.withIndex()) {
            if (i >= MAX_DEPTH) break
            if (n.id.isNotBlank()) { parts += idSelector(n.id); break }
            val sb = StringBuilder(n.tag.lowercase())
            n.classes.filter { IDENT.matches(it) && !it.startsWith("__saqr") }.take(2).forEach { sb.append('.').append(it) }
            if (n.count > 1) sb.append(":nth-of-type(").append(n.nth).append(')')
            parts += sb.toString()
            if (n.tag.equals("body", true)) break
        }
        return parts.asReversed().joinToString(" > ")
    }
}

/**
 * A tolerant mini HTML tree (tags, attributes, line numbers): enough to map a picked element's
 * selector back to the source line when it has no id. Not a validating parser.
 */
object HtmlChain {
    class Node(val tag: String, val attrs: Map<String, String>, val line: Int, val parent: Node?) {
        val children = ArrayList<Node>()
        val id get() = attrs["id"].orEmpty()
        val classes get() = attrs["class"].orEmpty().split(Regex("\\s+")).filter { it.isNotBlank() }
        val text = StringBuilder()
        override fun toString() = "<$tag${if (id.isNotEmpty()) "#$id" else ""}@$line>"
    }

    private val VOID = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr")
    private val RAW = setOf("script", "style", "textarea", "title")
    private val TAG = Regex("""<!--[\s\S]*?-->|<![^>]*>|</\s*([A-Za-z][A-Za-z0-9-]*)\s*>|<([A-Za-z][A-Za-z0-9-]*)((?:\s+[^\s=/>]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+))?)*)\s*(/?)>""")
    private val ATTR = Regex("""([^\s=/>]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?""")

    fun parse(html: String): Node {
        val lineStarts = ArrayList<Int>().apply { add(0); html.forEachIndexed { i, ch -> if (ch == '\n') add(i + 1) } }
        fun lineOf(pos: Int): Int { var lo = 0; var hi = lineStarts.size - 1; while (lo < hi) { val mid = (lo + hi + 1) / 2; if (lineStarts[mid] <= pos) lo = mid else hi = mid - 1 }; return lo + 1 }
        val root = Node("#root", emptyMap(), 1, null)
        var cur = root
        var pos = 0
        while (pos < html.length) {
            val m = TAG.find(html, pos) ?: break
            if (m.range.first > pos) cur.text.append(html, pos, m.range.first)
            pos = m.range.last + 1
            val close = m.groups[1]?.value
            val open = m.groups[2]?.value
            when {
                close != null -> {
                    val t = close.lowercase()
                    var n: Node? = cur
                    while (n != null && n !== root && n.tag != t) n = n.parent
                    if (n != null && n !== root) cur = n.parent ?: root
                }
                open != null -> {
                    val t = open.lowercase()
                    val attrs = LinkedHashMap<String, String>()
                    ATTR.findAll(m.groups[3]?.value.orEmpty()).forEach { a ->
                        attrs[a.groupValues[1].lowercase()] = a.groups[2]?.value ?: a.groups[3]?.value ?: a.groups[4]?.value ?: ""
                    }
                    val node = Node(t, attrs, lineOf(m.range.first), cur)
                    cur.children += node
                    val selfClose = m.groupValues[4] == "/"
                    if (t in RAW && !selfClose) {
                        val end = Regex("</\\s*$t\\s*>", RegexOption.IGNORE_CASE).find(html, pos)
                        node.text.append(html, pos, end?.range?.first ?: html.length)
                        pos = end?.range?.last?.plus(1) ?: html.length
                    } else if (t !in VOID && !selfClose) cur = node
                }
            }
        }
        if (pos < html.length) cur.text.append(html, pos, html.length)
        return root
    }

    fun walk(n: Node): Sequence<Node> = sequence { for (c in n.children) { yield(c); yieldAll(walk(c)) } }

    /** The chain the picker script would report for [node] (element first). */
    fun chainOf(node: Node): List<ChainNode> {
        val out = ArrayList<ChainNode>()
        var n: Node? = node
        while (n != null && n.tag != "#root" && n.tag != "html") {
            val siblings = n.parent?.children?.filter { it.tag == n!!.tag } ?: listOf(n)
            out += ChainNode(n.tag, n.id, n.classes, siblings.indexOf(n) + 1, siblings.size)
            n = n.parent
        }
        return out
    }

    private class Part(val tag: String?, val id: String?, val classes: List<String>, val nth: Int?)
    private fun parsePart(s: String): Part {
        if (s.startsWith("[id=\"")) return Part(null, s.removePrefix("[id=\"").removeSuffix("\"]").replace("\\\"", "\"").replace("\\\\", "\\"), emptyList(), null)
        if (s.startsWith("#")) return Part(null, s.substring(1), emptyList(), null)
        val nth = Regex(""":nth-of-type\((\d+)\)""").find(s)?.groupValues?.get(1)?.toInt()
        val base = s.substringBefore(":")
        val bits = base.split('.')
        return Part(bits.first().ifEmpty { null }, null, bits.drop(1), nth)
    }

    private fun matches(n: Node, p: Part): Boolean {
        if (p.id != null) return n.id == p.id
        if (p.tag != null && n.tag != p.tag) return false
        if (!n.classes.containsAll(p.classes)) return false
        if (p.nth != null) {
            val sib = n.parent?.children?.filter { it.tag == n.tag } ?: listOf(n)
            if (sib.indexOf(n) + 1 != p.nth) return false
        }
        return true
    }

    /** Resolves a selector made by [Selector.build] (`a > b > c`) against [root]. */
    fun select(root: Node, selector: String): Node? {
        val parts = selector.split(" > ").map { it.trim() }.filter { it.isNotEmpty() }.map(::parsePart)
        if (parts.isEmpty()) return null
        var level = walk(root).filter { matches(it, parts[0]) }.toList()
        for (p in parts.drop(1)) level = level.flatMap { it.children.filter { c -> matches(c, p) } }
        return level.firstOrNull()
    }
}

/** Best guess at where a picked element is written in the project's sources. */
data class SourceMatch(val path: String, val line: Int, val how: String) {
    override fun toString() = "$path:$line"
}

object SourceLocator {
    private val EXT = setOf("html", "htm", "js", "jsx", "ts", "tsx", "vue", "svelte", "php", "ejs", "hbs", "astro", "erb", "njk")
    private val SKIP = setOf("node_modules", ".git", "build", "dist", ".gradle", ".next", "out", "vendor")

    fun candidates(dir: File, pageFile: String?): List<File> {
        val all = dir.walkTopDown().maxDepth(6).onEnter { it == dir || it.name !in SKIP }
            .filter { it.isFile && it.extension.lowercase() in EXT && it.length() < 1_000_000 }.take(800).toList()
        val page = pageFile?.let { File(dir, it).canonicalFile }
        return all.sortedBy { if (page != null && it.canonicalFile == page) 0 else if (it.extension.startsWith("htm")) 1 else 2 }
    }

    private fun lineAt(text: String, index: Int) = text.substring(0, index).count { it == '\n' } + 1

    fun locate(dir: File, el: PickedElement, pageFile: String? = el.file): SourceMatch? {
        val files = candidates(dir, pageFile)
        val texts = files.associateWith { runCatching { it.readText() }.getOrDefault("") }
        fun rel(f: File) = f.relativeTo(dir).path
        fun find(how: String, re: Regex): SourceMatch? {
            for ((f, t) in texts) re.find(t)?.let { return SourceMatch(rel(f), lineAt(t, it.range.first), how) }
            return null
        }
        // 1) the element's id (also JSX id={"x"} / id={'x'})
        val id = Regex("""^<[^>]*?\sid\s*=\s*"([^"]+)"""").find(el.outerHtml)?.groupValues?.get(1)
            ?: el.selector.takeIf { it.startsWith("#") && !it.contains(' ') }?.substring(1)
        if (!id.isNullOrBlank()) find("id", Regex("""\bid\s*=\s*\{?\s*["']""" + Regex.escape(id) + """["']"""))?.let { return it }
        // 2) a distinctive snippet: the opening tag as rendered, the visible text, the class list
        val openTag = Regex("""^<[^>]+>""").find(el.outerHtml)?.value
        if (openTag != null && openTag.length >= 12) find("snippet", Regex(Regex.escape(openTag)))?.let { return it }
        val textLine = el.text.lineSequence().map { it.trim() }.firstOrNull { it.length >= 4 }?.take(60)
        if (textLine != null) find("text", Regex(Regex.escape(textLine)))?.let { return it }
        val cls = Regex("""^<[^>]*?\sclass\s*=\s*"([^"]+)"""").find(el.outerHtml)?.groupValues?.get(1)?.trim()
        if (!cls.isNullOrBlank()) find("class", Regex("""\b(class|className)\s*=\s*\{?\s*["']""" + Regex.escape(cls) + """["']"""))?.let { return it }
        // 3) walk the selector through the page's own HTML
        val page = files.firstOrNull { pageFile != null && it.extension.startsWith("htm") && rel(it) == pageFile } ?: return null
        val node = HtmlChain.select(HtmlChain.parse(texts[page].orEmpty()), el.selector) ?: return null
        return SourceMatch(rel(page), node.line, "selector")
    }
}

/** The structured block sent to the agent with the user's instruction. */
object ElementContext {
    fun build(el: PickedElement, match: SourceMatch?, instruction: String): String = buildString {
        appendLine("[Preview element — picked by the user]")
        appendLine("page: ${el.file ?: el.url}")
        appendLine("source (best match): ${match?.let { "$it (by ${it.how})" } ?: "not found — search the project for it"}")
        appendLine("selector: ${el.selector}")
        appendLine("box: ${el.w.toInt()}×${el.h.toInt()} at (${el.x.toInt()}, ${el.y.toInt()}) css px")
        if (el.styles.isNotEmpty()) appendLine("computed styles: " + el.styles.entries.joinToString("; ") { "${it.key}: ${it.value}" })
        if (el.text.isNotBlank()) appendLine("text: \"${el.text.replace("\n", " ")}\"")
        appendLine("outerHTML:")
        appendLine("```html")
        appendLine(el.outerHtml)
        appendLine("```")
        appendLine()
        appendLine("Instruction: ${instruction.trim()}")
        append("Change the source of THIS element as asked (read the file first), leave everything else as is, then say it is ready in the Preview.")
    }

    /** Short line for the chat bubble. */
    fun display(el: PickedElement, instruction: String) = "🎯 ${el.selector}\n${instruction.trim()}"
}

/** The picker bridge is only ever exposed to the project's own pages. */
object PickerSecurity {
    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]")

    fun isLocal(url: String?, projectDir: File?): Boolean {
        if (url.isNullOrBlank()) return false
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        return when (u.scheme?.lowercase()) {
            "file" -> {
                val dir = projectDir ?: return false
                val path = u.path ?: return false
                val f = runCatching { File(path).canonicalFile }.getOrNull() ?: return false
                val root = dir.canonicalFile
                f == root || f.path.startsWith(root.path + File.separator)
            }
            "http", "https" -> (u.host?.lowercase() ?: "") in LOCAL_HOSTS
            else -> false
        }
    }

    /** Project-relative path of a local file:// page (or the URL path for localhost pages). */
    fun relativeFile(url: String, projectDir: File?): String? {
        val u = runCatching { URI(url) }.getOrNull() ?: return null
        return when (u.scheme?.lowercase()) {
            "file" -> projectDir?.let { d ->
                if (!isLocal(url, d)) null
                else runCatching { File(URLDecoder.decode(u.rawPath, "UTF-8")).canonicalFile.relativeTo(d.canonicalFile).path }.getOrNull()
            }
            "http", "https" -> if (isLocal(url, projectDir)) u.path?.trimStart('/')?.ifEmpty { "index.html" } else null
            else -> null
        }
    }
}

/** The script injected into the preview WebView. Idempotent: re-running only toggles the state. */
object PickerJs {
    const val BRIDGE = "SaqrPicker"

    /** Installs the picker (once per page) and turns it on/off; [token] must accompany every bridge call. */
    fun toggle(on: Boolean, token: String) =
        "(function(){$SCRIPT;\nwindow.__saqrPicker && window.__saqrPicker.enable($on, '" + token.filter { it.isLetterOrDigit() || it == '-' } + "');})();"

    val SCRIPT = """
if (!window.__saqrPicker) (function(){
  var GOLD = '#E9B44C', on = false, cur = null, tok = '';
  var box = document.createElement('div'), tag = document.createElement('div');
  box.className = '__saqr_box'; tag.className = '__saqr_tag';
  box.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483646;border:2px solid '+GOLD+';background:rgba(233,180,76,.12);border-radius:3px;box-shadow:0 0 0 1px rgba(0,0,0,.35);display:none;transition:all .06s ease-out';
  tag.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483647;background:'+GOLD+';color:#1A1406;font:600 11px/1.6 ui-monospace,monospace;padding:0 6px;border-radius:4px;direction:ltr;white-space:nowrap;max-width:80vw;overflow:hidden;text-overflow:ellipsis;display:none';
  function mount(){ if (!box.isConnected && document.body) { document.body.appendChild(box); document.body.appendChild(tag); } }
  function own(el){ return el === box || el === tag; }
  function label(el){ var s = el.tagName.toLowerCase(); if (el.id) s += '#' + el.id; else if (el.classList.length) s += '.' + Array.prototype.slice.call(el.classList, 0, 2).join('.'); return s; }
  function show(el){
    if (!el || own(el) || el === document.documentElement) return;
    cur = el; mount();
    var r = el.getBoundingClientRect();
    box.style.display = tag.style.display = 'block';
    box.style.left = r.left + 'px'; box.style.top = r.top + 'px'; box.style.width = r.width + 'px'; box.style.height = r.height + 'px';
    tag.textContent = label(el) + '  ' + Math.round(r.width) + '×' + Math.round(r.height);
    var ty = r.top - 20; if (ty < 2) ty = r.bottom + 4;
    tag.style.left = Math.max(2, r.left) + 'px'; tag.style.top = ty + 'px';
  }
  function hide(){ box.style.display = tag.style.display = 'none'; cur = null; }
  function chain(el){
    var out = [], n = el;
    while (n && n.nodeType === 1 && n !== document.documentElement && out.length < 6) {
      var t = n.tagName.toLowerCase(), p = n.parentElement, nth = 1, count = 1;
      if (p) { var sib = Array.prototype.filter.call(p.children, function(c){ return c.tagName === n.tagName; }); count = sib.length; nth = sib.indexOf(n) + 1; }
      out.push({ tag: t, id: n.id || '', cls: Array.prototype.slice.call(n.classList), nth: nth, count: count });
      if (n.id || t === 'body') break;
      n = p;
    }
    return out;
  }
  function payload(el){
    var cs = getComputedStyle(el), r = el.getBoundingClientRect(), html = el.outerHTML || '', text = (el.innerText || '');
    return JSON.stringify({
      chain: chain(el), html: html.length > 2000 ? html.slice(0, 1999) + '…' : html, text: text.length > 300 ? text.slice(0, 299) + '…' : text,
      styles: { color: cs.color, background: cs.backgroundColor + (cs.backgroundImage && cs.backgroundImage !== 'none' ? ' ' + cs.backgroundImage : ''),
        'font-size': cs.fontSize, margin: cs.margin, padding: cs.padding, display: cs.display },
      rect: { x: r.left, y: r.top, w: r.width, h: r.height }, vw: window.innerWidth, url: location.href
    });
  }
  function point(e){ return (e.changedTouches && e.changedTouches[0]) || (e.touches && e.touches[0]) || e; }
  function target(e){
    var p = point(e), el = document.elementFromPoint(p.clientX, p.clientY);
    return el && !own(el) ? el : null;
  }
  function pick(el){ if (!el) return; show(el); try { window.$BRIDGE && window.$BRIDGE.onPick(tok, payload(el)); } catch (err) {} }
  var sx = 0, sy = 0, moved = false;
  function onStart(e){ if (!on) return; var p = point(e); sx = p.clientX; sy = p.clientY; moved = false; onMove(e); }
  function onMove(e){ if (!on) return; var p = point(e); if (Math.abs(p.clientX - sx) + Math.abs(p.clientY - sy) > 12) moved = true; var el = target(e); if (el) show(el); }
  function block(e){ if (!on) return; e.preventDefault(); e.stopPropagation(); if (e.stopImmediatePropagation) e.stopImmediatePropagation(); }
  // Touch: pick on touchend unless the finger scrolled (preventing touchend also cancels the synthetic click).
  function onTouchEnd(e){ if (!on || moved) return; block(e); pick(target(e) || cur); }
  function onClick(e){ if (!on) return; block(e); pick(target(e) || cur); }
  document.addEventListener('touchstart', onStart, { capture: true, passive: true });
  ['mousemove','pointermove','touchmove'].forEach(function(t){ document.addEventListener(t, onMove, { capture: true, passive: true }); });
  ['mousedown','mouseup','submit','dblclick','contextmenu','auxclick'].forEach(function(t){ document.addEventListener(t, block, true); });
  document.addEventListener('touchend', onTouchEnd, { capture: true, passive: false });
  document.addEventListener('click', onClick, true);
  window.addEventListener('scroll', function(){ if (on && cur) show(cur); }, true);
  window.__saqrPicker = {
    enable: function(v, t){ on = !!v; if (t) tok = t; if (!on) hide(); document.documentElement.style.cursor = on ? 'crosshair' : ''; },
    box: function(v){ if (!cur) return; box.style.visibility = tag.style.visibility = v ? 'visible' : 'hidden'; }
  };
})()
""".trimIndent()
}
