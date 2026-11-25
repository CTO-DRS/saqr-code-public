package com.ctodrs.saqrcode.agent

/*
 * v0.13.4 «صوت بلا يدين»: the «يا صقر» wake phrase, as pure logic so it is unit-testable.
 * Recognizers spell the same two words many ways (harakat, alef forms, fused words, latin),
 * so detection runs on the Arabic-aware normalizer (MemoryBank.normalize) instead of exact
 * matching, and compares word pairs rather than substrings to avoid false positives.
 */

object WakePhrase {
    /** Normalized «يا صقر» when fused into one word by the recognizer. */
    private const val FUSED_AR = "ياصقر"

    /** Greeting words accepted before the name, normalized latin, lowercased. */
    private val GREETINGS = setOf("ya", "hey", "hi", "ok", "yo", "هي", "هاي", "ياو")

    /** Name spellings after a greeting, matched by prefix (saqr / saqer / saqre…). */
    private val NAMES = listOf("saqr", "saqer", "saqar", "sagr")

    /** Whether a word is the wake name, Arabic or latin, normalized. */
    private fun isName(norm: String): Boolean {
        if (norm == FUSED_AR || norm == "صقر" || norm.startsWith("صقر")) return true
        return NAMES.any { norm.startsWith(it) }
    }

    private fun isGreeting(norm: String): Boolean = norm in GREETINGS

    /**
     * The text that follows the wake phrase, or `null` when the phrase is not present.
     * Returns `""` when the phrase was heard but no command followed it yet.
     */
    fun after(text: String): String? {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        val norm = words.map { MemoryBank.normalize(it) }
        for (i in norm.indices) {
            val w = norm[i]
            // fused «ياصقر» — one word; the rest starts right after it
            if (w == FUSED_AR) return words.drop(i + 1).joinToString(" ").trim()
            // «يا صقر» or a latin pair «ya/hey/hi/ok saqr» — the wake spans two words
            if ((w == "يا" || isGreeting(w)) && i + 1 < norm.size && isName(norm[i + 1])) {
                return words.drop(i + 2).joinToString(" ").trim()
            }
        }
        return null
    }

    /** Whether the wake phrase occurs in [text]. */
    fun heardIn(text: String): Boolean = after(text) != null
}
