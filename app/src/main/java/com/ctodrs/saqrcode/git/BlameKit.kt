package com.ctodrs.saqrcode.git

/** One line of a file and the commit that last changed it. */
data class BlameLine(val author: String, val timeSec: Long, val sha: String)

/** Consecutive lines from the same commit, 1-based [start]. */
data class BlameGroup(val start: Int, val count: Int, val author: String, val timeSec: Long, val sha: String) {
    val end: Int get() = start + count - 1
}

/** Wave E: pure helpers for git blame (grouping and a short relative age, English and Arabic). Unit-tested. */
object BlameKit {
    fun group(lines: List<BlameLine>): List<BlameGroup> {
        val out = ArrayList<BlameGroup>()
        lines.forEachIndexed { i, l ->
            val last = out.lastOrNull()
            if (last != null && last.sha == l.sha) out[out.size - 1] = last.copy(count = last.count + 1)
            else out += BlameGroup(i + 1, 1, l.author, l.timeSec, l.sha)
        }
        return out
    }

    /** "5 min ago" / "قبل 5 دقيقة"; western digits in both. A future time counts as now. */
    fun age(nowSec: Long, thenSec: Long, arabic: Boolean): String {
        val d = (nowSec - thenSec).coerceAtLeast(0)
        val (n, unit) = when {
            d < 60 -> return if (arabic) "الآن" else "just now"
            d < 3600 -> d / 60 to 0
            d < 86_400 -> d / 3600 to 1
            d < 30 * 86_400L -> d / 86_400 to 2
            d < 365 * 86_400L -> d / (30 * 86_400L) to 3
            else -> d / (365 * 86_400L) to 4
        }
        return if (arabic) "قبل $n " + listOf("دقيقة", "ساعة", "يومًا", "شهرًا", "سنة")[unit]
        else "$n " + listOf("min", "h", "d", "mo", "y")[unit] + " ago"
    }

    /** One compact row, e.g. "a1b2c3d · Rashid · 3 d ago · lines 4-9". */
    fun summary(g: BlameGroup, nowSec: Long, arabic: Boolean): String {
        val lines = if (g.count == 1) "${g.start}" else "${g.start}-${g.end}"
        return "${g.sha} · ${g.author} · ${age(nowSec, g.timeSec, arabic)} · " + (if (arabic) "السطر $lines" else "line $lines")
    }
}
