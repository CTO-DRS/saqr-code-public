package com.ctodrs.saqrcode.agent

/**
 * Majlis smart suggestions: next-step chips after a finished turn, chosen from what the turn did
 * (failed step, changed files, long answer). Rule based on purpose: no extra model call, no cost, instant.
 */
object FollowUps {
    enum class Kind { FIX_ERROR, RUN_TESTS, REVIEW, COMMIT, SUMMARIZE, NEXT_STEPS }

    const val LONG_REPLY = 500

    /** Chips for the turn that ends at the last item; empty when the chat is mid-turn or nothing useful applies. */
    fun suggest(items: List<ChatItem>, running: Boolean): List<Kind> {
        if (running) return emptyList()
        val last = items.lastOrNull() as? ChatItem.Assistant ?: return emptyList()
        if (last.streaming || last.text.isBlank()) return emptyList()
        var start = items.lastIndex
        while (start > 0 && items[start - 1] !is ChatItem.User) start--
        val turn = items.subList(start, items.size)

        val failed = turn.any { it is ChatItem.Tool && !it.ok && it.result != null && it.name in RUNNERS }
        if (failed) return listOf(Kind.FIX_ERROR)

        fun changed(it: ChatItem) = (it is ChatItem.Tool && it.ok && it.result != null && it.name in WRITERS) ||
            (it is ChatItem.Approval && it.decision == true && it.action == null && it.changes.isNotEmpty())
        val lastChange = turn.indexOfLast { changed(it) }
        if (lastChange >= 0) {
            val testedAfter = turn.drop(lastChange + 1).any { it is ChatItem.Tool && it.ok && it.name in CHECKERS }
            val committed = turn.any { it is ChatItem.Tool && it.name == Tools.GIT_COMMIT && it.ok }
            return buildList {
                if (!testedAfter) add(Kind.RUN_TESTS)
                add(Kind.REVIEW)
                if (!committed) add(Kind.COMMIT)
            }
        }
        return if (last.text.length >= LONG_REPLY) listOf(Kind.SUMMARIZE, Kind.NEXT_STEPS) else emptyList()
    }

    private val RUNNERS = setOf(Tools.RUN, Tools.TESTS, Tools.LINT)
    private val CHECKERS = setOf(Tools.TESTS, Tools.LINT)
    private val WRITERS = setOf(Tools.WRITE, Tools.EDIT, Tools.MULTI_EDIT, Tools.DELETE)
}
