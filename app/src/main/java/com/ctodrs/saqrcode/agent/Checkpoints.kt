package com.ctodrs.saqrcode.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Checkpoints ("time travel"): before the agent changes files during a user turn, the previous
 * content (or absence) of each affected file is copied under `<store>/<id>/` with a small
 * `manifest.json`. One checkpoint per user turn; later batches of the same turn add the files
 * they touch for the first time. Restoring a checkpoint reverts it and every newer one, newest
 * first, so the project returns to the moment before that user message. Independent of git.
 *
 * Files larger than [maxFileBytes] or that look binary are not copied; they are recorded as
 * skipped and left untouched by a restore.
 */
class Checkpoints(
    private val projectDir: File,
    private val store: File,
    private val maxCount: Int = MAX_COUNT,
    private val maxBytes: Long = MAX_BYTES,
    private val maxFileBytes: Long = MAX_FILE_BYTES,
) {
    enum class State { SAVED, ABSENT, SKIPPED }

    data class Entry(val path: String, val state: State, val reason: String? = null)

    data class Point(val id: String, val time: Long, val turn: String, val prompt: String, val files: List<Entry>)

    /** What a restore will do / did: files rewritten, files deleted, files left as they are. */
    data class Plan(val restored: List<String>, val deleted: List<String>, val skipped: List<String>) {
        val isEmpty get() = restored.isEmpty() && deleted.isEmpty() && skipped.isEmpty()
        val changedCount get() = restored.size + deleted.size
    }

    init { store.mkdirs() }

    private var lastTime = list().maxOfOrNull { it.time } ?: 0L

    /** Strictly increasing timestamps so ordering holds even within one millisecond. */
    @Synchronized private fun nextTime(): Long {
        val now = System.currentTimeMillis()
        lastTime = if (now > lastTime) now else lastTime + 1
        return lastTime
    }

    /**
     * Record the current state of [relPaths] for the user turn [turn]. When the newest checkpoint
     * already belongs to this turn, only files it has not recorded yet are added (the earliest
     * content is the one a restore must bring back). Returns the checkpoint.
     */
    @Synchronized
    fun record(turn: String, prompt: String, relPaths: Collection<String>): Point {
        val newest = list().firstOrNull()
        val point = if (newest != null && newest.turn == turn) {
            val known = newest.files.map { it.path }.toSet()
            val add = relPaths.distinct().filter { it !in known }
            if (add.isEmpty()) return newest
            val dir = File(store, newest.id)
            val entries = newest.files + add.map { save(dir, it) }
            writeManifest(dir, newest.copy(files = entries))
            newest.copy(files = entries)
        } else {
            val time = nextTime()
            val id = time.toString()
            val dir = File(store, id).apply { mkdirs() }
            val p = Point(id, time, turn, prompt.trim().replace(Regex("\\s+"), " ").take(PROMPT_EXCERPT),
                relPaths.distinct().map { save(dir, it) })
            writeManifest(dir, p)
            p
        }
        prune(keep = point.id)
        return point
    }

    private fun save(dir: File, rel: String): Entry {
        val src = File(projectDir, rel)
        if (!src.isFile) return Entry(rel, State.ABSENT)
        if (src.length() > maxFileBytes) return Entry(rel, State.SKIPPED, "too_large")
        if (isBinary(src)) return Entry(rel, State.SKIPPED, "binary")
        val dst = File(dir, "data/$rel")
        dst.parentFile?.mkdirs()
        src.copyTo(dst, overwrite = true)
        return Entry(rel, State.SAVED)
    }

    private fun writeManifest(dir: File, p: Point) {
        val files = JSONArray()
        p.files.forEach { e ->
            files.put(JSONObject().put("path", e.path).put("state", e.state.name.lowercase()).apply { e.reason?.let { put("reason", it) } })
        }
        val m = JSONObject().put("id", p.id).put("timestamp", p.time).put("turn", p.turn).put("prompt", p.prompt).put("files", files)
        File(dir, MANIFEST).writeText(m.toString(1))
    }

    private fun read(dir: File): Point? = runCatching {
        val m = JSONObject(File(dir, MANIFEST).readText())
        val arr = m.getJSONArray("files")
        Point(
            m.optString("id", dir.name), m.getLong("timestamp"), m.optString("turn"), m.optString("prompt"),
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Entry(o.getString("path"), State.valueOf(o.getString("state").uppercase()), o.optString("reason").ifEmpty { null })
            },
        )
    }.getOrNull()

    /** Newest first. */
    fun list(): List<Point> = (store.listFiles() ?: emptyArray()).filter { it.isDirectory }.mapNotNull { read(it) }
        .sortedWith(compareByDescending<Point> { it.time }.thenByDescending { it.id })

    fun get(id: String): Point? = list().firstOrNull { it.id == id }

    /** Checkpoints a restore of [point] reverts: it and everything newer, newest first. */
    private fun chain(point: Point) = list().filter { it.time >= point.time }

    /** The files a restore of [point] would rewrite, delete or leave alone. */
    fun preview(point: Point): Plan {
        // the oldest record of each path wins: that is the state before [point]'s turn
        val finalState = LinkedHashMap<String, Entry>()
        chain(point).asReversed().forEach { p -> p.files.forEach { e -> finalState.putIfAbsent(e.path, e) } }
        val restored = ArrayList<String>(); val deleted = ArrayList<String>(); val skipped = ArrayList<String>()
        finalState.values.forEach { e ->
            when (e.state) {
                State.SAVED -> restored += e.path
                State.ABSENT -> if (File(projectDir, e.path).exists()) deleted += e.path
                State.SKIPPED -> skipped += e.path
            }
        }
        return Plan(restored.sorted(), deleted.sorted(), skipped.sorted())
    }

    /** Revert [point] and every newer checkpoint, then drop them. */
    @Synchronized
    fun restore(point: Point): Plan {
        val plan = preview(point)
        for (p in chain(point)) {
            val dir = File(store, p.id)
            for (e in p.files) {
                val target = File(projectDir, e.path)
                when (e.state) {
                    State.SAVED -> { target.parentFile?.mkdirs(); File(dir, "data/${e.path}").copyTo(target, overwrite = true) }
                    State.ABSENT -> { target.delete(); pruneEmptyParents(target.parentFile) }
                    State.SKIPPED -> Unit
                }
            }
            dir.deleteRecursively()
        }
        return plan
    }

    private fun pruneEmptyParents(dir: File?) {
        var d = dir
        val root = projectDir.canonicalFile
        while (d != null && d.canonicalFile != root && d.isDirectory && (d.list()?.isEmpty() == true)) {
            d.delete(); d = d.parentFile
        }
    }

    fun clear() { store.listFiles()?.forEach { it.deleteRecursively() } }

    fun sizeBytes(): Long = store.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** Drop the oldest checkpoints beyond [maxCount] or [maxBytes] (never [keep]). */
    private fun prune(keep: String) {
        val all = list().toMutableList() // newest first
        var size = all.sumOf { dirSize(File(store, it.id)) }
        while (all.size > 1 && (all.size > maxCount || size > maxBytes)) {
            val oldest = all.last()
            if (oldest.id == keep) break
            val d = File(store, oldest.id)
            size -= dirSize(d)
            d.deleteRecursively()
            all.removeAt(all.lastIndex)
        }
    }

    private fun dirSize(d: File) = d.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    companion object {
        const val MAX_COUNT = 50
        const val MAX_BYTES = 50L * 1024 * 1024
        const val MAX_FILE_BYTES = 2L * 1024 * 1024
        const val PROMPT_EXCERPT = 140
        const val MANIFEST = "manifest.json"

        /** A NUL byte in the first 8 KB marks a file as binary (same heuristic as git). */
        fun isBinary(f: File): Boolean = runCatching {
            f.inputStream().use { s ->
                val buf = ByteArray(8192)
                val n = s.read(buf)
                (0 until maxOf(n, 0)).any { buf[it] == 0.toByte() }
            }
        }.getOrDefault(true)
    }
}
