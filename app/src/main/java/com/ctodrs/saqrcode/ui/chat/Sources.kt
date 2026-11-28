package com.ctodrs.saqrcode.ui.chat

import com.ctodrs.saqrcode.agent.ChatItem
import com.ctodrs.saqrcode.agent.Tools

/** Majlis sources row: what an answer was built from (files, commands, web, git, memory), taken from the tool calls of its turn. */
data class SourceRef(val kind: Kind, val label: String) {
    enum class Kind { FILE, TERMINAL, WEB, SEARCH, GIT, MEMORY, TESTS }
}

object Sources {
    const val MAX = 8

    /** Sources of the turn that ends at [endIndex]: every tool call and file change since the previous user message. */
    fun forTurn(items: List<ChatItem>, endIndex: Int): List<SourceRef> {
        if (endIndex !in items.indices) return emptyList()
        var start = endIndex
        while (start > 0 && items[start - 1] !is ChatItem.User) start--
        val out = LinkedHashSet<SourceRef>()
        for (i in start..endIndex) {
            when (val it = items[i]) {
                is ChatItem.Tool -> kindOf(it.name)?.let { k -> out += SourceRef(k, it.summary.trim().ifBlank { it.name }.take(60)) }
                is ChatItem.Approval -> if (it.decision == true && it.action == null) it.changes.forEach { c -> out += SourceRef(SourceRef.Kind.FILE, c.path) }
                else -> {}
            }
        }
        return out.take(MAX)
    }

    fun kindOf(tool: String): SourceRef.Kind? = when (tool) {
        Tools.READ, Tools.LIST, Tools.WRITE, Tools.EDIT, Tools.MULTI_EDIT, Tools.MOVE, Tools.DELETE -> SourceRef.Kind.FILE
        Tools.RUN -> SourceRef.Kind.TERMINAL
        Tools.SEARCH, Tools.PROJECT_SEARCH, Tools.WEB_SEARCH -> if (tool == Tools.WEB_SEARCH) SourceRef.Kind.WEB else SourceRef.Kind.SEARCH
        Tools.WEB_FETCH -> SourceRef.Kind.WEB
        Tools.GIT_STATUS, Tools.GIT_DIFF, Tools.GIT_LOG, Tools.GIT_COMMIT, Tools.GIT_BRANCH, Tools.GIT_PULL, Tools.GIT_PUSH -> SourceRef.Kind.GIT
        Tools.MEMORY_SAVE, Tools.MEMORY_FORGET -> SourceRef.Kind.MEMORY
        Tools.LINT, Tools.TESTS -> SourceRef.Kind.TESTS
        else -> null // plan / ask_user / unknown: not a source
    }
}
