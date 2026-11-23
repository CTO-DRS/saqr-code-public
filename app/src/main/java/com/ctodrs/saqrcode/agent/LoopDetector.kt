package com.ctodrs.saqrcode.agent

import org.json.JSONObject

/**
 * v0.7 · كاشف الحلقات المفرغة · Loop detector.
 *
 * Keeps a rolling window of per-step "fingerprints" (tool name + canonical argument hash)
 * and flags the classic failure mode where the model repeats the exact same tool calls
 * forever. The session nudges the model once; a second detection stops the turn so tokens
 * are not burned in circles. Pure logic, unit-tested in V07FeaturesTest.
 */
object LoopDetector {

    /**
     * Canonical fingerprint of one model step's tool calls: name + a hash of the arguments
     * with keys sorted, so `{"a":1,"b":2}` and `{"b":2,"a":1}` match. Empty for text-only steps.
     */
    fun fingerprint(calls: List<ToolCall>): String {
        if (calls.isEmpty()) return ""
        return calls.joinToString("|") { c ->
            val canon = runCatching {
                val o = JSONObject(c.argsJson)
                o.keys().asSequence().toSortedSet().joinToString(",") { k -> k + "=" + o.opt(k).toString() }
            }.getOrDefault(c.argsJson)
            c.name + ":" + canon.hashCode().toString(16)
        }
    }

    /** True when the last 3 non-empty fingerprints are identical. */
    fun tripleRepeat(fingerprints: List<String>): Boolean {
        val fs = fingerprints.filter { it.isNotBlank() }
        if (fs.size < 3) return false
        return fs.takeLast(3).toSet().size == 1
    }

    /** Rolling window size kept by the session. */
    const val WINDOW = 6
}
