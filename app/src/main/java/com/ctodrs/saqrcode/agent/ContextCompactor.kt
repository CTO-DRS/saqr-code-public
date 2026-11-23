package com.ctodrs.saqrcode.agent

/**
 * v0.7 · ضغط السياق · Context compaction.
 *
 * When the estimated context grows past a threshold, the oldest part of the model's message
 * list is summarised into a single [Msg.User] note and replaced. The cut index is always the
 * start of a [Msg.User] message, and an assistant's tool results always sit directly after
 * that assistant's message — so a cut can never split a tool_use / tool_result pair.
 * Pure logic, unit-tested in V07FeaturesTest.
 */
object ContextCompactor {

    /** Estimated characters (not tokens) that trigger compaction. 150k chars ≈ 40k tokens. */
    const val THRESHOLD_CHARS = 150_000

    /** The tail that is always kept verbatim. */
    const val KEEP_TAIL = 10

    /** Never summarise fewer than this many messages. */
    const val MIN_SUMMARISED = 4

    fun needsCompact(totalChars: Long, messages: Int, threshold: Int = THRESHOLD_CHARS): Boolean =
        totalChars > threshold && messages > KEEP_TAIL + MIN_SUMMARISED

    /**
     * Oldest index [i] where a cut is valid: `history[i]` is a user message, the tail
     * keeps at least [keepTail] messages, and at least [minSummarised] messages are summarised.
     * Returns null when nothing can be cut.
     */
    fun cutIndex(history: List<Msg>, keepTail: Int = KEEP_TAIL, minSummarised: Int = MIN_SUMMARISED): Int? {
        val last = history.size - keepTail
        if (last < minSummarised) return null
        for (i in minSummarised..last) if (history[i] is Msg.User) return i
        return null
    }

    /** Plain-text rendering of the messages to be summarised (for the summariser prompt). */
    fun renderForSummary(messages: List<Msg>, maxChars: Int = 30_000): String {
        val sb = StringBuilder()
        for (m in messages) when (m) {
            is Msg.User -> sb.append("USER: ").append(m.text.take(1_500)).append('\n')
            is Msg.Assistant -> {
                if (m.text.isNotBlank()) sb.append("ASSISTANT: ").append(m.text.take(1_500)).append('\n')
                if (m.toolCalls.isNotEmpty()) sb.append("  tools: ").append(m.toolCalls.joinToString(", ") { it.name }).append('\n')
            }
            is Msg.ToolResult -> sb.append("  [").append(m.name).append("] ").append(m.content.take(300)).append('\n')
        }
        val s = sb.toString()
        return if (s.length > maxChars) s.take(maxChars / 2) + "\n…[middle omitted]…\n" + s.takeLast(maxChars / 2) else s
    }

    const val SUMMARY_SYSTEM = """You compress a coding-agent conversation into a compact brief for a new assistant that will continue the work with NO other memory of it. Write in English. Include, as short bullet lines:
- the user's goal(s) in their own words (translate if needed)
- key decisions and constraints the user gave
- files created/modified/deleted (with one-word purpose each)
- current state: what is done, what is in progress, what remains
- anything pending: unanswered questions, promised next steps
Be dense and factual; do not invent anything; maximum ~300 words. Output only the brief."""

    /** Marker prefix of the compacted note, so later steps know the context was compacted. */
    const val MARKER = "[COMPACTED EARLIER CONTEXT]"

    /** Applies a cut: history becomes [compact note] + tail. */
    fun apply(history: List<Msg>, cut: Int, summary: String): List<Msg> {
        val note = Msg.User(MARKER + "\n" + summary.trim())
        return listOf(note) + history.subList(cut, history.size)
    }
}
