package com.ctodrs.saqrcode.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.IdentityHashMap

/*
 * v0.6 persistent chat history. One JSON file per session under filesDir/chats/<project>/<id>.json,
 * attached images as files in filesDir/chats/<project>/img/, and a small index.json with the
 * metadata the Sessions list needs. Everything here is plain JVM so it is unit-tested.
 */

/** Saves attachment pixels as files so the session JSON stays small. */
interface ImageRepo {
    /** Stores [part] (idempotent) and returns its file name. */
    fun put(part: ImagePart): String
    fun get(name: String, mediaType: String): ImagePart?
}

class DirImageRepo(private val dir: File) : ImageRepo {
    private val names = IdentityHashMap<ImagePart, String>()

    @Synchronized
    override fun put(part: ImagePart): String {
        names[part]?.let { return it }
        val ext = when (part.mediaType) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
        val name = sha1(part.base64).take(24) + "." + ext
        val f = File(dir, name)
        if (!f.isFile) {
            dir.mkdirs()
            val tmp = File(dir, "$name.tmp")
            tmp.writeBytes(Base64.getDecoder().decode(part.base64))
            tmp.renameTo(f)
        }
        names[part] = name
        return name
    }

    override fun get(name: String, mediaType: String): ImagePart? {
        val f = File(dir, File(name).name) // never leave the img dir
        if (!f.isFile) return null
        return ImagePart(Base64.getEncoder().encodeToString(f.readBytes()), mediaType).also { synchronized(this) { names[it] = f.name } }
    }

    fun files(): List<File> = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: emptyList()

    companion object {
        fun sha1(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

data class SessionMeta(
    val id: String,
    val title: String,
    val autoTitle: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: Int,
    val preview: String = "",
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("title", title).put("autoTitle", autoTitle)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("messages", messages).put("preview", preview)

    companion object {
        fun fromJson(o: JSONObject) = SessionMeta(
            o.getString("id"), o.optString("title"), o.optBoolean("autoTitle", true),
            o.optLong("createdAt"), o.optLong("updatedAt"), o.optInt("messages"), o.optString("preview"),
        )
    }
}

/** Everything a session restores: the chat, the plan, the touched files, usage and whether a turn was running. */
class SessionData(
    val meta: SessionMeta,
    val items: List<ChatItem>,
    val plan: List<PlanItem> = emptyList(),
    val touched: List<String> = emptyList(),
    val usage: Map<String, Usage> = emptyMap(),
    /** A turn was running when this was written (the process may have died mid-turn). */
    val active: Boolean = false,
)

object SessionCodec {
    const val VERSION = 1
    /** File contents kept in approval diffs; anything longer is cut (the diff becomes approximate). */
    const val MAX_DIFF_CHARS = 256_000
    /** Tool results kept on disk (the model only ever sees trimmed old outputs anyway). */
    const val MAX_RESULT_CHARS = 40_000

    private fun cut(s: String?, max: Int): String? = if (s == null || s.length <= max) s else s.take(max) + "\n…[truncated]"

    fun encode(data: SessionData, images: ImageRepo): JSONObject {
        val items = JSONArray()
        data.items.forEach { items.put(encodeItem(it, images)) }
        return JSONObject()
            .put("v", VERSION)
            .put("meta", data.meta.toJson())
            .put("active", data.active)
            .put("items", items)
            .put("plan", JSONArray().apply { data.plan.forEach { put(JSONObject().put("title", it.title).put("status", it.status)) } })
            .put("touched", JSONArray(data.touched))
            .put("usage", JSONObject().apply {
                data.usage.forEach { (m, u) -> put(m, JSONObject().put("in", u.input).put("out", u.output).put("est", u.estimated)) }
            })
    }

    fun decode(o: JSONObject, images: ImageRepo): SessionData {
        val arr = o.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { runCatching { decodeItem(it, images) }.getOrNull() } }
        val plan = o.optJSONArray("plan")?.let { p -> (0 until p.length()).mapNotNull { p.optJSONObject(it) }.map { PlanItem(it.optString("title"), it.optString("status", "pending")) } }.orEmpty()
        val touched = o.optJSONArray("touched")?.let { t -> (0 until t.length()).map { t.optString(it) } }.orEmpty()
        val usage = o.optJSONObject("usage")?.let { u ->
            u.keys().asSequence().associateWith { k -> u.getJSONObject(k).let { Usage(it.optLong("in"), it.optLong("out"), it.optBoolean("est")) } }
        }.orEmpty()
        return SessionData(SessionMeta.fromJson(o.getJSONObject("meta")), items, plan, touched, usage, o.optBoolean("active"))
    }

    private fun callsJson(calls: List<ToolCall>) = JSONArray().apply {
        calls.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("args", it.argsJson)) }
    }

    private fun encodeItem(item: ChatItem, images: ImageRepo): JSONObject {
        val o = JSONObject().put("at", item.createdAt)
        when (item) {
            is ChatItem.User -> {
                o.put("t", "user").put("text", item.text)
                if (item.prompt != item.text) o.put("prompt", item.prompt)
                if (item.images.isNotEmpty()) o.put("images", JSONArray().apply {
                    item.images.forEach { put(JSONObject().put("file", images.put(it)).put("type", it.mediaType)) }
                })
                item.checkpointId?.let { o.put("checkpoint", it) }
            }
            is ChatItem.Assistant -> {
                o.put("t", "assistant").put("text", item.text)
                if (item.calls.isNotEmpty()) o.put("calls", callsJson(item.calls))
                if (item.results.isNotEmpty()) o.put("results", JSONObject().apply { item.results.forEach { (k, v) -> put(k, cut(v, MAX_RESULT_CHARS)) } })
            }
            is ChatItem.Tool -> {
                o.put("t", "tool").put("name", item.name).put("summary", item.summary).put("ok", item.ok).put("ms", item.durationMs)
                if (item.callId.isNotEmpty()) o.put("call", item.callId)
                item.result?.let { o.put("result", cut(it, MAX_RESULT_CHARS)) }
            }
            is ChatItem.Approval -> {
                o.put("t", "approval").put("ms", item.durationMs).put("kind", item.actionKind.name)
                item.action?.let { o.put("action", it) }
                item.decision?.let { o.put("decision", it) }
                o.put("changes", JSONArray().apply {
                    item.changes.forEach { ch ->
                        put(JSONObject().put("path", ch.path).put("before", cut(ch.before, MAX_DIFF_CHARS) ?: JSONObject.NULL)
                            .put("after", cut(ch.after, MAX_DIFF_CHARS) ?: JSONObject.NULL))
                    }
                })
            }
            is ChatItem.AskUser -> {
                o.put("t", "ask").put("args", item.argsJson).put("status", item.status.name).put("ms", item.durationMs)
                item.callId?.let { o.put("call", it) }
                o.put("answers", JSONObject().apply {
                    item.answers.forEach { (q, a) ->
                        put(q, JSONObject().put("sel", JSONArray(a.selected)).put("text", a.text).apply { a.other?.let { put("other", it) } })
                    }
                })
            }
            is ChatItem.Notice -> o.put("t", "notice").put("text", item.text).put("kind", item.kind.name).put("count", item.count)
        }
        return o
    }

    private fun str(o: JSONObject, k: String): String? = if (o.isNull(k)) null else o.optString(k)

    private fun decodeItem(o: JSONObject, images: ImageRepo): ChatItem? {
        val item: ChatItem = when (o.optString("t")) {
            "user" -> {
                val imgs = o.optJSONArray("images")?.let { a ->
                    (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { images.get(it.optString("file"), it.optString("type", "image/jpeg")) }
                }.orEmpty()
                val text = o.optString("text")
                ChatItem.User(text, imgs, prompt = if (o.has("prompt")) o.optString("prompt") else text).apply {
                    checkpointId = str(o, "checkpoint")
                }
            }
            "assistant" -> ChatItem.Assistant().apply {
                text = o.optString("text"); streaming = false
                calls = o.optJSONArray("calls")?.let { a ->
                    (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { ToolCall(it.optString("id"), it.optString("name"), it.optString("args", "{}")) }
                }.orEmpty()
                o.optJSONObject("results")?.let { r -> r.keys().forEach { k -> results[k] = r.optString(k) } }
            }
            "tool" -> ChatItem.Tool(o.optString("name"), o.optString("summary"), o.optString("call")).apply {
                ok = o.optBoolean("ok", true); durationMs = o.optLong("ms", -1)
                result = str(o, "result")
            }
            "approval" -> {
                val ch = o.optJSONArray("changes")?.let { a ->
                    (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { FileChange(it.optString("path"), str(it, "before"), str(it, "after")) }
                }.orEmpty()
                val kind = runCatching { ChatItem.Approval.ActionKind.valueOf(o.optString("kind")) }.getOrDefault(ChatItem.Approval.ActionKind.PUSH)
                ChatItem.Approval(ch, str(o, "action"), kind).apply {
                    if (o.has("decision") && !o.isNull("decision")) { decision = o.getBoolean("decision"); deferred.complete(decision!!) }
                    durationMs = o.optLong("ms", -1)
                }
            }
            "ask" -> {
                val args = o.optString("args")
                val req = runCatching { AskUserTool.parse(JSONObject(args)) }.getOrNull() ?: return null
                val status = runCatching { ChatItem.AskUser.Status.valueOf(o.optString("status")) }.getOrDefault(ChatItem.AskUser.Status.CANCELLED)
                ChatItem.AskUser(req, args, restored = status == ChatItem.AskUser.Status.PENDING, callId = str(o, "call")).apply {
                    val ans = o.optJSONObject("answers")
                    if (ans != null) {
                        val m = LinkedHashMap(answers)
                        ans.keys().forEach { q ->
                            val a = ans.getJSONObject(q)
                            val sel = a.optJSONArray("sel")?.let { s -> (0 until s.length()).map { s.optString(it) } }.orEmpty()
                            m[q] = AskAnswer(sel, if (a.has("other")) a.optString("other") else null, a.optString("text"))
                        }
                        answers = m
                    }
                    this.status = status
                    durationMs = o.optLong("ms", -1)
                }
            }
            "notice" -> {
                val kind = runCatching { ChatItem.Notice.Kind.valueOf(o.optString("kind")) }.getOrDefault(ChatItem.Notice.Kind.INFO)
                ChatItem.Notice(o.optString("text"), kind == ChatItem.Notice.Kind.ERROR, kind, o.optInt("count", -1))
            }
            else -> return null
        }
        item.createdAt = o.optLong("at", item.createdAt)
        return item
    }

    /** Plain text of a session file (user + assistant messages and the title) for search, without decoding images. */
    fun searchText(o: JSONObject): String = buildString {
        o.optJSONObject("meta")?.let { append(it.optString("title")).append('\n') }
        val arr = o.optJSONArray("items") ?: return@buildString
        for (i in 0 until arr.length()) {
            val it = arr.optJSONObject(i) ?: continue
            when (it.optString("t")) { "user", "assistant" -> append(it.optString("text")).append('\n') }
        }
    }

    /** Image file names a session file refers to. */
    fun imageFiles(o: JSONObject): Set<String> {
        val out = HashSet<String>()
        val arr = o.optJSONArray("items") ?: return out
        for (i in 0 until arr.length()) {
            val imgs = arr.optJSONObject(i)?.optJSONArray("images") ?: continue
            for (j in 0 until imgs.length()) imgs.optJSONObject(j)?.optString("file")?.let { out += it }
        }
        return out
    }
}

/** Rebuilds the model's message list from chat items (assistant items carry their tool calls and results). */
object HistoryRebuilder {
    const val INTERRUPTED = "Error: interrupted — the app was closed or the task was stopped before this finished. Re-run it if it is still needed."
    const val ASK_DISMISSED = "{\"skipped\":true,\"note\":\"The user did not answer these questions and sent a new message instead.\"}"

    fun rebuild(items: List<ChatItem>): List<Msg> {
        val out = ArrayList<Msg>()
        for (item in items) when (item) {
            is ChatItem.User -> out += Msg.User(item.prompt, item.images)
            is ChatItem.Assistant -> {
                if (item.text.isBlank() && item.calls.isEmpty()) continue
                out += Msg.Assistant(item.text, item.calls)
                item.calls.forEach { c -> out += Msg.ToolResult(c.id, c.name, item.results[c.id] ?: INTERRUPTED) }
            }
            else -> Unit
        }
        return out
    }

    /** The assistant item that issued [callId], if any. */
    fun owner(items: List<ChatItem>, callId: String?): ChatItem.Assistant? =
        if (callId.isNullOrEmpty()) null else items.asReversed().firstOrNull { it is ChatItem.Assistant && it.calls.any { c -> c.id == callId } } as ChatItem.Assistant?
}

/**
 * Making a restored session consistent: unfinished rows are closed, undecided approvals count as
 * not approved, and (unless [keepAskPending]) open questions are dismissed. Returns true when the
 * chat ended inside a turn, i.e. it can be resumed.
 */
object SessionRepair {
    fun repair(items: List<ChatItem>, keepAskPending: Boolean = true): Boolean {
        var changed = false
        items.forEach { item ->
            when (item) {
                is ChatItem.Assistant -> if (item.streaming) { item.streaming = false; changed = true }
                is ChatItem.Tool -> if (item.result == null) { item.result = "(interrupted)"; item.ok = false; changed = true }
                is ChatItem.Approval -> if (item.decision == null) { item.decision = false; item.deferred.complete(false); changed = true }
                is ChatItem.AskUser -> if (item.status == ChatItem.AskUser.Status.PENDING && !keepAskPending) {
                    item.status = ChatItem.AskUser.Status.CANCELLED; changed = true
                }
                else -> Unit
            }
        }
        return changed
    }

    /** The open `ask_user` card a restored session is waiting on, if any. */
    fun pendingAsk(items: List<ChatItem>): ChatItem.AskUser? =
        items.lastOrNull { it is ChatItem.AskUser && it.status == ChatItem.AskUser.Status.PENDING } as ChatItem.AskUser?
}

object SessionTitles {
    const val MAX = 48

    /** "سوّ لي **تطبيق** ملاحظات…" → a one-line title from the first user message. */
    fun auto(firstMessage: String): String {
        var s = firstMessage.replace(Regex("""```[\s\S]*?(```|$)"""), " ")
        s = s.replace(Regex("""!?\[([^\]]*)]\([^)]*\)"""), "$1")
        s = s.replace(Regex("""[*_`#>~|]+"""), "").replace(Regex("""\s+"""), " ").trim()
        if (s.length <= MAX) return s
        val cut = s.take(MAX)
        val sp = cut.lastIndexOf(' ')
        return (if (sp >= MAX / 2) cut.take(sp) else cut).trimEnd(',', '،', '.', ':', ' ') + "…"
    }
}

object SessionSearch {
    private val TASHKEEL = Regex("[\u064B-\u065F\u0670\u0640]")

    /** Case-, diacritic- and alef/yaa/taa-marbuta-insensitive form for matching Arabic and Latin text. */
    fun normalize(s: String): String = TASHKEEL.replace(s.lowercase(), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه').replace('ؤ', 'و').replace('ئ', 'ي')
        .replace(Regex("\\s+"), " ")

    fun matches(text: String, query: String): Boolean {
        val q = normalize(query).trim()
        if (q.isEmpty()) return true
        val t = normalize(text)
        return q.split(' ').filter { it.isNotEmpty() }.all { t.contains(it) }
    }
}

/**
 * The per-project session directory: <id>.json files, img/ and index.json.
 * Thread-safe; meant to be called off the main thread.
 */
class SessionStore(val dir: File) {
    val images = DirImageRepo(File(dir, "img"))
    private val indexFile = File(dir, "index.json")
    private var cache: MutableList<SessionMeta>? = null
    private var lastId: String? = null

    @Synchronized
    private fun index(): MutableList<SessionMeta> {
        cache?.let { return it }
        val list = ArrayList<SessionMeta>()
        val ok = runCatching {
            if (indexFile.isFile) {
                val o = JSONObject(indexFile.readText())
                lastId = o.optString("last").takeIf { it.isNotEmpty() }
                val a = o.getJSONArray("sessions")
                for (i in 0 until a.length()) list += SessionMeta.fromJson(a.getJSONObject(i))
                true
            } else false
        }.getOrDefault(false)
        if (!ok) { // missing or corrupt: rebuild from the session files
            list.clear()
            sessionFiles().forEach { f -> runCatching { list += SessionMeta.fromJson(JSONObject(f.readText()).getJSONObject("meta")) } }
            lastId = list.maxByOrNull { it.updatedAt }?.id
        }
        val existing = sessionFiles().map { it.nameWithoutExtension }.toSet()
        list.retainAll { it.id in existing }
        list.sortByDescending { it.updatedAt }
        cache = list
        return list
    }

    private fun sessionFiles() = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".json") && it.name != "index.json" } ?: emptyList()

    private fun file(id: String) = File(dir, safeId(id) + ".json")

    @Synchronized
    private fun writeIndex() {
        val list = index()
        dir.mkdirs()
        val o = JSONObject().put("last", lastId ?: "").put("sessions", JSONArray().apply { list.forEach { put(it.toJson()) } })
        atomicWrite(indexFile, o.toString())
    }

    @Synchronized fun list(): List<SessionMeta> = index().toList()

    @Synchronized fun last(): String? { index(); return lastId?.takeIf { id -> index().any { it.id == id } } ?: index().firstOrNull()?.id }

    @Synchronized fun setLast(id: String?) { index(); lastId = id; writeIndex() }

    @Synchronized
    fun loadJson(id: String): JSONObject? = runCatching { file(id).takeIf { it.isFile }?.let { JSONObject(it.readText()) } }.getOrNull()

    fun load(id: String): SessionData? = loadJson(id)?.let { runCatching { SessionCodec.decode(it, images) }.getOrNull() }

    /** Writes an encoded session ([SessionCodec.encode]) and updates the index; prunes beyond [MAX_SESSIONS]. */
    @Synchronized
    fun save(json: JSONObject, makeLast: Boolean = true): List<String> {
        val meta = SessionMeta.fromJson(json.getJSONObject("meta"))
        dir.mkdirs()
        atomicWrite(file(meta.id), json.toString())
        val list = index()
        list.removeAll { it.id == meta.id }
        list.add(0, meta)
        list.sortByDescending { it.updatedAt }
        if (makeLast) lastId = meta.id
        val pruned = ArrayList<String>()
        while (list.size > MAX_SESSIONS) {
            val victim = list.filter { it.id != meta.id }.minByOrNull { it.updatedAt } ?: break
            list.remove(victim); file(victim.id).delete(); pruned += victim.id
        }
        writeIndex()
        if (pruned.isNotEmpty()) gcImages()
        return pruned
    }

    @Synchronized
    fun delete(id: String) {
        file(id).delete()
        index().removeAll { it.id == id }
        if (lastId == id) lastId = index().firstOrNull()?.id
        writeIndex()
        gcImages()
    }

    /** Renames a session; a blank title goes back to the automatic one. */
    @Synchronized
    fun rename(id: String, title: String) {
        val t = title.replace(Regex("\\s+"), " ").trim().take(120)
        val o = loadJson(id) ?: return
        val meta = o.getJSONObject("meta").put("title", t).put("autoTitle", t.isEmpty())
        o.put("meta", meta)
        atomicWrite(file(id), o.toString())
        val list = index()
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list[i] = SessionMeta.fromJson(meta)
        writeIndex()
    }

    /** Sessions whose title or messages contain every word of [query] (newest first). */
    fun search(query: String): List<SessionMeta> {
        if (query.isBlank()) return list()
        return list().filter { m ->
            SessionSearch.matches(m.title + "\n" + m.preview, query) || (loadJson(m.id)?.let { SessionSearch.matches(SessionCodec.searchText(it), query) } ?: false)
        }
    }

    fun sizeBytes(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun overWarnSize() = sizeBytes() > WARN_BYTES

    @Synchronized
    private fun gcImages() {
        val used = HashSet<String>()
        sessionFiles().forEach { f -> runCatching { used += SessionCodec.imageFiles(JSONObject(f.readText())) } }
        images.files().filter { it.name !in used }.forEach { it.delete() }
    }

    companion object {
        const val MAX_SESSIONS = 200
        const val WARN_BYTES = 50L * 1024 * 1024

        fun newId(now: Long = System.currentTimeMillis()): String =
            "s" + now.toString(36) + "-" + (1..4).map { "abcdefghijkmnpqrstuvwxyz23456789".random() }.joinToString("")

        fun safeId(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64)

        fun atomicWrite(f: File, text: String) {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }
}
