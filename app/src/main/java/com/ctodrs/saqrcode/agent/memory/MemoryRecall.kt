package com.ctodrs.saqrcode.agent.memory

import com.ctodrs.saqrcode.agent.MemoryBank
import com.ctodrs.saqrcode.agent.SlashCommands
import kotlin.math.ln

/**
 * v0.17 «محرك الذاكرة» · relevance retrieval.
 *
 * A tiny on-device BM25 index over memory entries — no embedding library, no ML runtime, a few
 * KB of heap even for a full 200-entry bank. Text is normalized the way the rest of Saqr
 * matches Arabic ([SlashCommands.normalize] then [MemoryBank.normalize]: alef forms, yaa,
 * taa marbouta, diacritics, tatweel), split into tokens and lightly stemmed (Arabic article /
 * conjunction prefixes and common plural/pronoun suffixes; English plural/-ing/-ed), so
 * «الشبكات» meets «شبكة» and "tests" meets "test".
 *
 * Pure Kotlin, unit-tested in V017FeaturesTest.
 */
object MemoryRecall {

    const val DEFAULT_K = 8
    const val MIN_K = 1
    const val MAX_K = 30
    /** Standing user preferences ride along with any query (they apply to every answer). */
    const val PINNED_PREFERENCES = 4

    private const val K1 = 1.2
    private const val B = 0.75

    private val STOP = setOf(
        "the", "a", "an", "and", "or", "to", "of", "in", "on", "for", "is", "are", "be", "it", "this", "that", "with", "as", "at", "by",
        "we", "i", "you", "me", "my", "do", "use", "please",
        "في", "من", "علي", "الي", "عن", "ان", "او", "و", "ثم", "هذا", "هذه", "ذلك", "التي", "الذي", "مع", "كل", "لا", "ما", "هل", "قد",
    )
    private val AR_PREFIXES = listOf("وال", "بال", "كال", "فال", "لل", "ال")
    private val AR_SUFFIXES = listOf("ات", "ون", "ين", "ها", "هم", "كم", "نا", "ه", "ي")

    private fun isArabic(t: String) = t.any { it in '\u0600'..'\u06FF' }

    /** Light stemming — deliberately conservative: a stem never gets shorter than 3 letters. */
    fun stem(t: String): String {
        var s = t
        if (isArabic(s)) {
            for (p in AR_PREFIXES) if (s.startsWith(p) && s.length - p.length >= 3) { s = s.substring(p.length); break }
            for (x in AR_SUFFIXES) if (s.endsWith(x) && s.length - x.length >= 3) { s = s.dropLast(x.length); break }
            return s
        }
        return when {
            s.length > 5 && s.endsWith("ing") -> s.dropLast(3)
            s.length > 4 && s.endsWith("ies") -> s.dropLast(3) + "y"
            s.length > 4 && s.endsWith("ed") -> s.dropLast(2)
            s.length > 3 && s.endsWith("s") && !s.endsWith("ss") -> s.dropLast(1)
            else -> s
        }
    }

    /** Normalized, stemmed tokens of [s] (stop words and 1-letter tokens dropped). */
    fun tokens(s: String): List<String> {
        val out = ArrayList<String>()
        for (raw in s.split(Regex("[^\\p{L}\\p{N}\\u064B-\\u065F\\u0670\\u0640]+"))) {
            if (raw.isEmpty()) continue
            // camelCase / snake parts count on their own too ("NetworkModule" → network, module)
            val parts = raw.split(Regex("(?<=[a-z0-9])(?=[A-Z])")).ifEmpty { listOf(raw) }
            for (p in parts) {
                val n = MemoryBank.normalize(SlashCommands.normalize(p))
                if (n.length < 2 || n in STOP) continue
                out += stem(n)
            }
        }
        return out
    }

    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        return inter.toDouble() / (a.size + b.size - inter)
    }

    /** The text an entry is indexed by: key, value, type and referenced files. */
    fun docText(e: MemoryBank.Entry): String = buildString {
        append(e.key).append(' ').append(e.value).append(' ').append(e.type.id)
        e.files.forEach { append(' ').append(it.replace('/', ' ').replace('.', ' ')) }
    }

    /** A BM25 index over [docs]; rebuilt cheaply whenever the bank's version changes. */
    class Index(val docs: List<MemoryBank.Entry>) {
        private val tf: List<Map<String, Int>> = docs.map { d -> tokens(docText(d)).groupingBy { it }.eachCount() }
        private val len: List<Int> = tf.map { it.values.sum() }
        private val avg: Double = if (len.isEmpty()) 0.0 else len.average().coerceAtLeast(1.0)
        private val df: Map<String, Int> = HashMap<String, Int>().also { m -> tf.forEach { d -> d.keys.forEach { m[it] = (m[it] ?: 0) + 1 } } }

        fun idf(term: String): Double {
            val n = docs.size.toDouble()
            val f = (df[term] ?: 0).toDouble()
            return ln(1.0 + (n - f + 0.5) / (f + 0.5))
        }

        /** BM25 score of every doc for [query] (same order as [docs]). */
        fun scores(query: String): DoubleArray {
            val q = tokens(query).distinct()
            val out = DoubleArray(docs.size)
            if (q.isEmpty()) return out
            for (i in docs.indices) {
                var s = 0.0
                val d = tf[i]
                for (t in q) {
                    val f = d[t] ?: continue
                    s += idf(t) * (f * (K1 + 1)) / (f + K1 * (1 - B + B * len[i] / avg))
                }
                // a stale file reference makes a memory less trustworthy, not useless
                if (docs[i].stale) s *= 0.5
                out[i] = s
            }
            return out
        }

        /** Docs with a positive score, best first (ties: newest first). */
        fun rank(query: String): List<Pair<MemoryBank.Entry, Double>> {
            val s = scores(query)
            return docs.indices.filter { s[it] > 0.0 }
                .sortedWith(compareByDescending<Int> { s[it] }.thenByDescending { docs[it].updatedAt })
                .map { docs[it] to s[it] }
        }
    }

    /**
     * The entries to inject for [query]: up to [PINNED_PREFERENCES] newest user preferences
     * (standing rules for every answer), then the top-[k] relevant entries. When the query
     * matches fewer than [k], the rest is filled only with standing project rules (decisions,
     * architecture, conventions), newest first — never with unrelated notes or lessons.
     */
    fun select(entries: List<MemoryBank.Entry>, query: String, k: Int = DEFAULT_K, index: Index? = null): List<MemoryBank.Entry> {
        val kk = k.coerceIn(MIN_K, MAX_K)
        val prefs = entries.filter { it.kind == MemoryBank.Kind.PREFERENCE }.sortedByDescending { it.updatedAt }.take(PINNED_PREFERENCES)
        val rest = entries.filter { it !in prefs }
        val idx = index?.takeIf { it.docs == rest } ?: Index(rest)
        val ranked = idx.rank(query).map { it.first }.take(kk)
        val out = LinkedHashSet<MemoryBank.Entry>()
        out += prefs
        out += ranked
        if (ranked.size < kk) {
            val standing = setOf(MemoryBank.Type.DECISION, MemoryBank.Type.ARCHITECTURE, MemoryBank.Type.CONVENTION)
            rest.filter { it.type in standing && it !in out }.sortedByDescending { it.updatedAt }.take(kk - ranked.size).forEach { out += it }
        }
        return out.toList()
    }
}
