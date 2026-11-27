package com.ctodrs.saqrcode.git

/**
 * v0.1.22: merge-conflict hunks, parsed from the `<<<<<<<` / `=======` / `>>>>>>>` markers git leaves
 * in a file (diff3 `|||||||` base sections are understood and dropped). Pure so it is unit-tested.
 */
object ConflictResolver {
    sealed class Segment {
        data class Text(val lines: List<String>) : Segment()
        data class Hunk(val ours: List<String>, val theirs: List<String>, val oursLabel: String, val theirsLabel: String) : Segment()
    }

    enum class Choice { OURS, THEIRS, BOTH, BOTH_THEIRS_FIRST }

    private fun isStart(l: String) = l.startsWith("<<<<<<<") && (l.length == 7 || l[7] == ' ')
    private fun isBase(l: String) = l.startsWith("|||||||") && (l.length == 7 || l[7] == ' ')
    private fun isMid(l: String) = l == "======="
    private fun isEnd(l: String) = l.startsWith(">>>>>>>") && (l.length == 7 || l[7] == ' ')

    /** Splits [text] into plain runs and conflict hunks. Unterminated markers stay as plain text. */
    fun parse(text: String): List<Segment> {
        val lines = text.split("\n")
        val out = ArrayList<Segment>()
        var plain = ArrayList<String>()
        var i = 0
        while (i < lines.size) {
            val l = lines[i]
            if (isStart(l)) {
                var j = i + 1
                val ours = ArrayList<String>(); val theirs = ArrayList<String>()
                var inBase = false; var seenMid = false; var closed = false; var theirsLabel = ""
                while (j < lines.size) {
                    val m = lines[j]
                    when {
                        !seenMid && isBase(m) -> inBase = true
                        !seenMid && isMid(m) -> { seenMid = true; inBase = false }
                        seenMid && isEnd(m) -> { theirsLabel = m.removePrefix(">>>>>>>").trim(); closed = true }
                        isStart(m) && !closed -> break // nested/garbled: give up on this hunk
                        inBase -> {}
                        seenMid -> theirs += m
                        else -> ours += m
                    }
                    j++
                    if (closed) break
                }
                if (closed) {
                    if (plain.isNotEmpty()) { out += Segment.Text(plain); plain = ArrayList() }
                    out += Segment.Hunk(ours, theirs, l.removePrefix("<<<<<<<").trim(), theirsLabel)
                    i = j
                    continue
                }
            }
            plain += l
            i++
        }
        if (plain.isNotEmpty()) out += Segment.Text(plain)
        return out
    }

    fun hunks(segments: List<Segment>): List<Segment.Hunk> = segments.filterIsInstance<Segment.Hunk>()

    fun hasConflicts(text: String) = parse(text).any { it is Segment.Hunk }

    /** Rebuilds the file applying one [Choice] per hunk, in order. Null when a choice is missing. */
    fun resolve(segments: List<Segment>, choices: List<Choice?>): String? {
        val out = ArrayList<String>()
        var h = 0
        for (s in segments) when (s) {
            is Segment.Text -> out += s.lines
            is Segment.Hunk -> {
                val c = choices.getOrNull(h++) ?: return null
                when (c) {
                    Choice.OURS -> out += s.ours
                    Choice.THEIRS -> out += s.theirs
                    Choice.BOTH -> { out += s.ours; out += s.theirs }
                    Choice.BOTH_THEIRS_FIRST -> { out += s.theirs; out += s.ours }
                }
            }
        }
        return out.joinToString("\n")
    }

    fun resolveAll(text: String, choice: Choice): String {
        val seg = parse(text)
        return resolve(seg, List(hunks(seg).size) { choice })!!
    }
}
