package com.ctodrs.saqrcode.data

import android.content.Context
import org.json.JSONObject

/**
 * v0.12 «الشاشة الرئيسية الحية» · Last on-device build result per project, so the home cards can
 * show a live status badge (success/failed + when) without touching the build engine.
 *
 * Kept in its own plain prefs file; the codec is pure and unit-tested.
 */
data class BuildRecord(val ok: Boolean, val time: Long, val apk: String)

object BuildHistoryCodec {

    /** Map of project name → record, encoded as a JSON object. */
    fun encode(history: Map<String, BuildRecord>): String = JSONObject().apply {
        history.forEach { (name, r) ->
            put(name, JSONObject().put("ok", r.ok).put("time", r.time).put("apk", r.apk))
        }
    }.toString()

    fun decode(json: String?): Map<String, BuildRecord> = runCatching {
        val o = JSONObject(json ?: return emptyMap())
        val out = LinkedHashMap<String, BuildRecord>()
        o.keys().forEach { name ->
            val r = o.optJSONObject(name) ?: return@forEach
            out[name] = BuildRecord(r.optBoolean("ok", false), r.optLong("time", 0L), r.optString("apk"))
        }
        out
    }.getOrDefault(emptyMap())
}

class BuildHistory(context: Context) {
    private val prefs = context.getSharedPreferences("saqr_builds", Context.MODE_PRIVATE)

    private fun all(): Map<String, BuildRecord> = BuildHistoryCodec.decode(prefs.getString("history", null))

    /** The latest build record for [project], or null when it has never been built here. */
    fun last(project: String): BuildRecord? = all()[project]

    fun record(project: String, ok: Boolean, apk: String) {
        val cur = all().toMutableMap()
        cur[project] = BuildRecord(ok, System.currentTimeMillis(), apk)
        prefs.edit().putString("history", BuildHistoryCodec.encode(cur)).apply()
    }

    /** Drops the record when a project is deleted. */
    fun clear(project: String) {
        val cur = all().toMutableMap()
        if (cur.remove(project) != null) prefs.edit().putString("history", BuildHistoryCodec.encode(cur)).apply()
    }

    /** v0.13.3 Saqr Coach: any build attempt exists (ok or failed) — completes the «try the build» step. */
    fun anyAttempt(): Boolean = all().isNotEmpty()

    /** v0.13.3 Saqr Coach: at least one build succeeded anywhere — the path's final gate. */
    fun anySuccess(): Boolean = all().values.any { it.ok }
}
