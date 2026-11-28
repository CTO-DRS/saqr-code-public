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

    // ------------------------------------------------------------------ v0.15 LoopDetector v2

    /** Why the guard fired (v0.15); REPEAT is the original v0.7 triple repeat. */
    enum class Kind { REPEAT, CYCLE, REPEATED_FAILURE, NO_PROGRESS }

    /** v0.15: steps of only-failed tool calls in a row that count as "no progress". */
    const val NO_PROGRESS_STEPS = 6

    /** v0.15: how many identical failures of the same call trip the guard. */
    const val FAILURE_REPEATS = 3

    /** v0.15: failure prints kept by the session. */
    const val FAILURE_WINDOW = 12

    /**
     * v0.15: the period (2 or 3) when the newest non-empty fingerprints repeat a short cycle
     * twice in a row — A B A B or A B C A B C — that is not a plain triple repeat. Null otherwise.
     */
    fun cycle(fingerprints: List<String>): Int? {
        val fs = fingerprints.filter { it.isNotBlank() }
        for (p in 2..3) {
            if (fs.size < 2 * p) continue
            val tail = fs.takeLast(2 * p)
            val first = tail.subList(0, p)
            if (first == tail.subList(p, 2 * p) && first.toSet().size > 1) return p
        }
        return null
    }

    /** v0.15: one call's print (tool + canonical args), the unit of the repeated-failure check. */
    fun callPrint(c: ToolCall): String = fingerprint(listOf(c))

    /** v0.15: a failing result keyed by the call and the error text, so only *identical* failures add up. */
    fun failurePrint(c: ToolCall, result: String): String =
        callPrint(c) + "#" + result.trim().take(300).hashCode().toString(16)

    /** v0.15: true when one identical failing call appears [FAILURE_REPEATS] times in the window. */
    fun repeatedFailure(failures: List<String>): Boolean =
        failures.groupingBy { it }.eachCount().values.any { it >= FAILURE_REPEATS }

    /** v0.15: per step, true when at least one tool succeeded. Fires after [n] failing steps in a row. */
    fun noProgress(stepOk: List<Boolean>, n: Int = NO_PROGRESS_STEPS): Boolean =
        stepOk.size >= n && stepOk.takeLast(n).none { it }

    /**
     * v0.15: the same failure test the chat rows use — an error, a policy refusal, a non-zero
     * exit or a timeout. A user's rejection is not a failure (the user decided).
     */
    fun isFailure(result: String): Boolean =
        result.startsWith("Error") || result.startsWith("Blocked") || result.startsWith("No test runner") ||
            Regex("""^\[exit [1-9]""").containsMatchIn(result) || result.startsWith("[timed out]")

    /**
     * v0.15: which guard fires, checking the cheapest/most certain first. [v2] off = only
     * the original triple repeat (the v0.7 behaviour).
     */
    fun detect(prints: List<String>, failures: List<String>, stepOk: List<Boolean>, v2: Boolean): Kind? = when {
        tripleRepeat(prints) -> Kind.REPEAT
        !v2 -> null
        cycle(prints) != null -> Kind.CYCLE
        repeatedFailure(failures) -> Kind.REPEATED_FAILURE
        noProgress(stepOk) -> Kind.NO_PROGRESS
        else -> null
    }

    /** v0.15: the one-time nudge the model gets, per kind. */
    fun nudge(kind: Kind): String = when (kind) {
        Kind.REPEAT -> "[System] You are repeating the same tool calls with the same arguments. " +
            "Re-read the affected file, change your approach, or summarise what is blocking you and ask the user."
        Kind.CYCLE -> "[System] You are going in a circle: the same sequence of tool calls keeps coming back. " +
            "Stop alternating, re-read what the tools returned, and take a different approach or ask the user."
        Kind.REPEATED_FAILURE -> "[System] The same tool call has failed with the same error several times. " +
            "Do not retry it unchanged: read the error, fix the cause, or ask the user."
        Kind.NO_PROGRESS -> "[System] Your last several steps only produced failing tool calls. " +
            "Pause, summarise what is failing and why, then either try a clearly different approach or ask the user."
    }
}
