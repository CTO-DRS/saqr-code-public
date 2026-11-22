package com.ctodrs.saqrcode.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Restore points: before the agent applies a batch of changes we save the previous
 * content (or absence) of every file it will touch. Restoring a point reverts it and
 * every newer point, newest first, so the project returns to that moment.
 */
class Snapshots(private val projectDir: File, private val store: File) {
    data class Point(val id: String, val time: Long, val label: String, val files: List<String>)

    init { store.mkdirs() }

    fun create(label: String, relPaths: Collection<String>): Point {
        val id = System.currentTimeMillis().toString() + "_" + (100..999).random()
        val dir = File(store, id).apply { mkdirs() }
        val files = JSONArray()
        relPaths.distinct().forEach { rel ->
            val src = File(projectDir, rel)
            val existed = src.isFile
            if (existed) src.copyTo(File(dir, "data/$rel"), overwrite = true)
            files.put(JSONObject().put("path", rel).put("existed", existed))
        }
        val meta = JSONObject().put("time", System.currentTimeMillis()).put("label", label).put("files", files)
        File(dir, "meta.json").writeText(meta.toString())
        return Point(id, meta.getLong("time"), label, relPaths.distinct())
    }

    fun list(): List<Point> = (store.listFiles() ?: emptyArray()).filter { it.isDirectory }.mapNotNull { d ->
        runCatching {
            val m = JSONObject(File(d, "meta.json").readText())
            val arr = m.getJSONArray("files")
            Point(d.name, m.getLong("time"), m.optString("label"), (0 until arr.length()).map { arr.getJSONObject(it).getString("path") })
        }.getOrNull()
    }.sortedByDescending { it.time }

    fun restore(point: Point) {
        val newerFirst = list().filter { it.time >= point.time }
        for (p in newerFirst) {
            val dir = File(store, p.id)
            val m = JSONObject(File(dir, "meta.json").readText())
            val arr = m.getJSONArray("files")
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                val rel = f.getString("path")
                val target = File(projectDir, rel)
                if (f.getBoolean("existed")) {
                    target.parentFile?.mkdirs()
                    File(dir, "data/$rel").copyTo(target, overwrite = true)
                } else target.delete()
            }
            dir.deleteRecursively()
        }
    }
}
