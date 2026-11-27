package com.ctodrs.saqrcode.ui

/** Wave D: pure matching for the settings search box (unit-tested). One entry per settings section. */
object SettingsSearch {
    class Entry(val key: String, val title: String, val texts: List<String>)

    private val diacritics = Regex("[\u064B-\u065F\u0670\u0640]")

    /** Lowercase, strip Arabic diacritics and tatweel, unify alef/yeh/teh-marbuta, western digits. */
    fun normalize(s: String): String {
        val sb = StringBuilder()
        for (ch in diacritics.replace(s.lowercase(), "")) sb.append(when (ch) {
            '\u0623', '\u0625', '\u0622' -> '\u0627'
            '\u0649' -> '\u064A'
            '\u0629' -> '\u0647'
            in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
            else -> ch
        })
        return sb.toString()
    }

    /** Keys of the sections whose title or rows contain every query word; title matches first, then list order. */
    fun match(entries: List<Entry>, query: String): List<String> {
        val words = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val hits = entries.mapIndexedNotNull { i, e ->
            val title = normalize(e.title)
            val all = normalize(e.title + " " + e.texts.joinToString(" "))
            if (words.all { it in all }) Triple(i, e.key, words.all { it in title }) else null
        }
        return hits.sortedWith(compareBy({ !it.third }, { it.first })).map { it.second }
    }
}
