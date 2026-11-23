package com.ctodrs.saqrcode.agent

/*
 * v0.6 voice: the pure logic behind the mic button (which recognizer language to use) and
 * "Read aloud" (what text a TTS engine should get). Android wiring lives in ui/chat/Voice.kt.
 */

object VoiceLocale {
    const val AR = "ar-SA"
    const val EN = "en-US"
    /** Settings value: follow the app language. */
    const val FOLLOW = ""

    /**
     * The recognizer language: an explicit [override] (ar-SA / en-US) wins, otherwise the app
     * language ([appLang], e.g. "ar", "ar-SA", "en-GB", or "" for system) decides.
     */
    fun pick(appLang: String, override: String = FOLLOW, systemLang: String = "en"): String {
        normalize(override)?.let { return it }
        val lang = appLang.ifBlank { systemLang }
        return if (lang.trim().lowercase().startsWith("ar")) AR else EN
    }

    /** Long-press on the mic flips between Arabic and English. */
    fun toggle(current: String): String = if (current.lowercase().startsWith("ar")) EN else AR

    fun normalize(tag: String?): String? = when {
        tag.isNullOrBlank() -> null
        tag.trim().lowercase().startsWith("ar") -> AR
        tag.trim().lowercase().startsWith("en") -> EN
        else -> null
    }

    /** Joins what was typed before dictation with the recognized text ([partial] replaces the previous partial). */
    fun merge(prefix: String, partial: String): String {
        val p = partial.trim()
        if (p.isEmpty()) return prefix
        if (prefix.isBlank()) return p
        return if (prefix.endsWith(" ") || prefix.endsWith("\n")) prefix + p else "$prefix $p"
    }
}

object SpeechText {
    /** TextToSpeech rejects inputs longer than getMaxSpeechInputLength() (4000). */
    const val MAX_CHUNK = 3800

    private val FENCE = Regex("""```[\s\S]*?(```|$)|~~~[\s\S]*?(~~~|$)""")
    private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val URL = Regex("""https?://\S+""")
    private val INLINE_CODE = Regex("""`([^`\n]*)`""")
    private val HTML = Regex("""</?[A-Za-z][^>]*>""")
    private val HEADING = Regex("""(?m)^\s{0,3}#{1,6}\s*""")
    private val QUOTE = Regex("""(?m)^\s*>+\s?""")
    private val BULLET = Regex("""(?m)^\s*(?:[-*+•]|\d+[.)]|[٠-٩]+[.)])\s+""")
    private val RULE = Regex("""(?m)^\s*([-*_]\s*){3,}$""")
    private val TABLE_SEP = Regex("""(?m)^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")
    private val EMPHASIS = Regex("""(?<![\w*])(\*\*|__|\*|_|~~)(?=\S)(.+?)(?<=\S)\1(?![\w*])""")

    /**
     * Plain text for TTS: fenced code blocks removed, inline code kept as words,
     * links reduced to their text, markdown markers, HTML and bare URLs dropped.
     */
    fun clean(markdown: String): String {
        var s = markdown.replace("\r\n", "\n")
        s = FENCE.replace(s, "\n")
        s = IMAGE.replace(s, "$1")
        s = LINK.replace(s, "$1")
        s = URL.replace(s, "")
        s = INLINE_CODE.replace(s, "$1")
        s = HTML.replace(s, "")
        s = TABLE_SEP.replace(s, "")
        s = RULE.replace(s, "")
        s = HEADING.replace(s, "")
        s = QUOTE.replace(s, "")
        s = BULLET.replace(s, "")
        repeat(2) { s = EMPHASIS.replace(s) { it.groupValues[2] } }
        s = s.replace('|', ' ').replace(Regex("""[#*`~]+"""), "")
        s = s.replace(Regex("""[ \t]+"""), " ").replace(Regex("""\n\s*\n+"""), "\n").trim()
        return s
    }

    /** "ar" when Arabic letters make up at least a third of the letters, else "en". */
    fun language(text: String): String {
        var ar = 0; var latin = 0
        for (ch in text) {
            if (ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' || ch in '\uFB50'..'\uFDFF' || ch in '\uFE70'..'\uFEFF') ar++
            else if (ch.isLetter()) latin++
        }
        return if (ar > 0 && ar * 2 >= latin) "ar" else "en"
    }

    /** Splits [text] into TTS-sized pieces at sentence / line / word boundaries. */
    fun chunks(text: String, max: Int = MAX_CHUNK): List<String> {
        val out = ArrayList<String>()
        var rest = text.trim()
        while (rest.length > max) {
            val window = rest.take(max)
            val cut = listOf(window.lastIndexOf('\n'), window.lastIndexOf(". "), window.lastIndexOf("。"), window.lastIndexOf("؟ "),
                window.lastIndexOf("? "), window.lastIndexOf("! "), window.lastIndexOf("، ")).maxOrNull()?.takeIf { it > max / 3 }
                ?: window.lastIndexOf(' ').takeIf { it > max / 3 } ?: max
            out += rest.take(cut + 1).trim()
            rest = rest.drop(cut + 1).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out.filter { it.isNotBlank() }
    }
}
