package com.ctodrs.saqrcode.editor

/** Pure find/replace logic used by the editor's find bar. */
object FindReplace {
    data class Options(val caseSensitive: Boolean = false, val regex: Boolean = false)
    data class Result(val matches: List<IntRange>, val error: String? = null)

    const val MAX_MATCHES = 10_000

    fun compile(query: String, o: Options): Regex {
        val opts = if (o.caseSensitive) setOf(RegexOption.MULTILINE) else setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        return Regex(if (o.regex) query else Regex.escape(query), opts)
    }

    fun find(text: String, query: String, o: Options): Result {
        if (query.isEmpty()) return Result(emptyList())
        val re = try { compile(query, o) } catch (e: Exception) { return Result(emptyList(), e.message ?: "invalid regex") }
        val out = ArrayList<IntRange>()
        for (m in re.findAll(text)) {
            if (m.range.isEmpty()) continue // zero-length matches (e.g. ^, \b) are not useful to navigate
            out += m.range
            if (out.size >= MAX_MATCHES) break
        }
        return Result(out)
    }

    /** Index of the first match starting at or after [offset], wrapping around. -1 if none. */
    fun nextIndex(matches: List<IntRange>, offset: Int): Int {
        if (matches.isEmpty()) return -1
        val i = matches.indexOfFirst { it.first >= offset }
        return if (i < 0) 0 else i
    }

    /** Index of the last match starting before [offset], wrapping around. -1 if none. */
    fun prevIndex(matches: List<IntRange>, offset: Int): Int {
        if (matches.isEmpty()) return -1
        val i = matches.indexOfLast { it.first < offset }
        return if (i < 0) matches.lastIndex else i
    }

    /** Replace one match. Returns new text and caret offset after the replacement. */
    fun replaceOne(text: String, match: IntRange, query: String, replacement: String, o: Options): Pair<String, Int> {
        var rep = replacement
        if (o.regex) runCatching {
            val m = compile(query, o).toPattern().matcher(text)
            if (m.find(match.first) && m.start() == match.first) {
                val sb = StringBuffer()
                m.appendReplacement(sb, replacement)
                rep = sb.substring(match.first)
            }
        }
        val newText = text.substring(0, match.first) + rep + text.substring(match.last + 1)
        return newText to (match.first + rep.length)
    }

    /** Replace every match (zero-length matches are skipped). Returns new text and the number of replacements. */
    fun replaceAll(text: String, query: String, replacement: String, o: Options): Pair<String, Int> {
        if (query.isEmpty()) return text to 0
        val re = try { compile(query, o) } catch (_: Exception) { return text to 0 }
        val rep = if (o.regex) replacement else java.util.regex.Matcher.quoteReplacement(replacement)
        val m = re.toPattern().matcher(text)
        val sb = StringBuffer()
        var count = 0
        try {
            while (m.find()) {
                if (m.end() == m.start()) continue
                m.appendReplacement(sb, rep); count++
            }
        } catch (_: Exception) { return text to 0 } // bad group reference in replacement
        m.appendTail(sb)
        return sb.toString() to count
    }
}
