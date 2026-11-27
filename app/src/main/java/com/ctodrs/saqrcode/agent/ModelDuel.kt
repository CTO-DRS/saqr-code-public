package com.ctodrs.saqrcode.agent

/*
 * v0.1.14 «قارن»: compare two model answers side by side, as pure logic so it is
 * unit-testable. The runner (ui/IdeScreen.kt) fires two plain completions with no tools;
 * here live the side model, cost math and the label line. A duel never touches files.
 */

object ModelDuel {
    /** One side of the comparison: which model answered, what it said, and what it cost. */
    data class Side(
        val provider: String,
        val model: String,
        val text: String = "",
        val elapsedMs: Long = 0,
        val usage: Usage? = null,
        /** Set instead of [text] when the call failed. */
        val error: String? = null,
    ) {
        val running get() = text.isEmpty() && error == null
    }

    /** Display label: "openai/gpt-4o-mini". */
    fun label(side: Side) = "${side.provider}/${side.model}"

    /** Estimated USD for one side, or null when the model has no price row. */
    fun costOf(side: Side, prices: List<ModelPrice>): Double? {
        val u = side.usage ?: return null
        val row = PriceTable.find(side.model, prices) ?: return null
        return (u.input * row.inputPerM + u.output * row.outputPerM) / 1_000_000.0
    }

    /** "1.8s · 1.2k tokens · $0.003" — unknown parts drop out. Western digits (UI wraps with ltr()). */
    fun statsOf(side: Side, prices: List<ModelPrice>): String {
        val parts = ArrayList<String>()
        if (side.elapsedMs > 0) parts += "%.1fs".format(side.elapsedMs / 1000.0)
        side.usage?.let { u ->
            parts += (if (u.estimated) "~" else "") + formatTokens(u.total) + " tokens"
        }
        costOf(side, prices)?.let { parts += formatUsd(it) }
        return parts.joinToString(" · ")
    }

    private fun formatTokens(t: Long): String = when {
        t >= 1_000_000 -> "%.1fM".format(t / 1_000_000.0)
        t >= 1_000 -> "%.1fk".format(t / 1_000.0)
        else -> t.toString()
    }

    private fun formatUsd(v: Double): String = when {
        v >= 1.0 -> "$%.2f".format(v)
        v >= 0.01 -> "$%.3f".format(v)
        else -> "$%.4f".format(v)
    }
}
