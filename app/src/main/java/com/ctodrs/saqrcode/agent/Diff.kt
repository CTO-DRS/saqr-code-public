package com.ctodrs.saqrcode.agent

/** Small line-based LCS diff for previews (pure, unit tested). */
object Diff {
    enum class Kind { SAME, ADD, DEL }
    data class Line(val kind: Kind, val text: String)

    fun lines(old: String, new: String, maxCells: Long = 4_000_000L): List<Line> {
        val a = if (old.isEmpty()) emptyList() else old.split('\n')
        val b = if (new.isEmpty()) emptyList() else new.split('\n')
        // trim common prefix / suffix to keep the table small
        var pre = 0
        while (pre < a.size && pre < b.size && a[pre] == b[pre]) pre++
        var suf = 0
        while (suf < a.size - pre && suf < b.size - pre && a[a.size - 1 - suf] == b[b.size - 1 - suf]) suf++
        val am = a.subList(pre, a.size - suf)
        val bm = b.subList(pre, b.size - suf)
        val out = ArrayList<Line>()
        a.subList(0, pre).forEach { out += Line(Kind.SAME, it) }
        if (am.size.toLong() * bm.size > maxCells) {
            am.forEach { out += Line(Kind.DEL, it) }
            bm.forEach { out += Line(Kind.ADD, it) }
        } else {
            val n = am.size; val m = bm.size
            val dp = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0)
                dp[i][j] = if (am[i] == bm[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
            var i = 0; var j = 0
            while (i < n && j < m) {
                when {
                    am[i] == bm[j] -> { out += Line(Kind.SAME, am[i]); i++; j++ }
                    dp[i + 1][j] >= dp[i][j + 1] -> { out += Line(Kind.DEL, am[i]); i++ }
                    else -> { out += Line(Kind.ADD, bm[j]); j++ }
                }
            }
            while (i < n) out += Line(Kind.DEL, am[i++])
            while (j < m) out += Line(Kind.ADD, bm[j++])
        }
        a.subList(a.size - suf, a.size).forEach { out += Line(Kind.SAME, it) }
        return out
    }

    /** Collapse unchanged runs, keeping [context] lines around changes. */
    fun compact(lines: List<Line>, context: Int = 2): List<Line?> {
        val keep = BooleanArray(lines.size)
        lines.forEachIndexed { idx, l ->
            if (l.kind != Kind.SAME) for (k in maxOf(0, idx - context)..minOf(lines.size - 1, idx + context)) keep[k] = true
        }
        val out = ArrayList<Line?>()
        var skipped = false
        lines.forEachIndexed { idx, l ->
            if (keep[idx]) { out += l; skipped = false } else if (!skipped) { out += null; skipped = true }
        }
        return out
    }

    fun stats(lines: List<Line>): Pair<Int, Int> =
        lines.count { it.kind == Kind.ADD } to lines.count { it.kind == Kind.DEL }
}
