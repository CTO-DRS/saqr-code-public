package com.ctodrs.saqrcode.ui.chat

import com.ctodrs.saqrcode.agent.ChatItem

/** Majlis: in-conversation search and transcript export. Pure, so both are unit-testable. */
object Transcript {
    /** The searchable text of an item (what the user can read in the bubble), or null when it has none. */
    fun textOf(item: ChatItem): String? = when (item) {
        is ChatItem.User -> item.text
        is ChatItem.Assistant -> item.text.ifBlank { null }
        else -> null
    }

    /** Indexes (into [items]) of messages containing [query], case-insensitive; Arabic diacritics and tatweel are ignored. */
    fun search(items: List<ChatItem>, query: String): List<Int> {
        val q = normalize(query)
        if (q.length < 2) return emptyList()
        return items.indices.filter { i -> textOf(items[i])?.let { normalize(it).contains(q) } == true }
    }

    /** Lowercase, drop Arabic tashkeel (U+064B..U+0652, U+0670) and tatweel (U+0640), unify alef forms and ya/alef-maqsura. */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.trim().lowercase()) {
            when (ch) {
                in '\u064B'..'\u0652', '\u0670', '\u0640' -> {}
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ى' -> sb.append('ي')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Markdown transcript of the conversation: user and assistant messages, with tool calls as a short list and files changed. */
    fun toMarkdown(items: List<ChatItem>, title: String, userLabel: String = "User", assistantLabel: String = "Saqr"): String {
        val sb = StringBuilder("# ").append(title.ifBlank { "Saqr chat" }).append("\n")
        var toolLines = ArrayList<String>()
        fun flushTools() {
            if (toolLines.isNotEmpty()) { sb.append("\n").append(toolLines.joinToString("\n") { "- `$it`" }).append("\n"); toolLines = ArrayList() }
        }
        for (item in items) when (item) {
            is ChatItem.User -> if (item.text.isNotBlank()) { flushTools(); sb.append("\n## ").append(userLabel).append("\n\n").append(item.text.trim()).append("\n") }
            is ChatItem.Assistant -> if (item.text.isNotBlank()) { flushTools(); sb.append("\n## ").append(assistantLabel).append("\n\n").append(item.text.trim()).append("\n") }
            is ChatItem.Tool -> toolLines += (item.name + (item.summary.takeIf { it.isNotBlank() }?.let { ": " + it.lineSequence().first().take(120) } ?: ""))
            is ChatItem.Approval -> if (item.decision == true && item.action == null) item.changes.forEach { toolLines += "edit: ${it.path}" }
            else -> {}
        }
        flushTools()
        return sb.toString()
    }
}
