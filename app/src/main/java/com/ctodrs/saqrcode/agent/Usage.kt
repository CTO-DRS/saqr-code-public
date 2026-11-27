package com.ctodrs.saqrcode.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import kotlin.math.ceil

/** Token usage of one or more model calls. [estimated] when any part was guessed from text length. */
data class Usage(val input: Long = 0, val output: Long = 0, val estimated: Boolean = false) {
    val total get() = input + output
    operator fun plus(o: Usage) = Usage(input + o.input, output + o.output, estimated || o.estimated)
    companion object { val ZERO = Usage() }
}

/** Reads token counts from each provider's usage fields. */
object UsageParser {
    /** OpenAI-compatible: `usage.prompt_tokens` / `usage.completion_tokens` (also on the last stream chunk). */
    fun openAi(o: JSONObject): Usage? {
        val u = o.optJSONObject("usage") ?: return null
        if (!u.has("prompt_tokens") && !u.has("completion_tokens")) return null
        return Usage(u.optLong("prompt_tokens"), u.optLong("completion_tokens"))
    }

    /**
     * Anthropic: `message_start.message.usage` carries the input (plus cache reads/writes) and
     * `message_delta.usage.output_tokens` the cumulative output. Returns what this event knows.
     */
    fun anthropic(o: JSONObject): Usage? {
        val u = o.optJSONObject("usage") ?: o.optJSONObject("message")?.optJSONObject("usage") ?: return null
        if (!u.has("input_tokens") && !u.has("output_tokens")) return null
        val input = u.optLong("input_tokens") + u.optLong("cache_creation_input_tokens") + u.optLong("cache_read_input_tokens")
        return Usage(input, u.optLong("output_tokens"))
    }

    /** Gemini native: `usageMetadata.promptTokenCount` / `candidatesTokenCount` (+ `thoughtsTokenCount`). */
    fun gemini(o: JSONObject): Usage? {
        val u = o.optJSONObject("usageMetadata") ?: return null
        if (!u.has("promptTokenCount") && !u.has("candidatesTokenCount")) return null
        return Usage(u.optLong("promptTokenCount"), u.optLong("candidatesTokenCount") + u.optLong("thoughtsTokenCount"))
    }

    /** Whichever of the three formats [o] is in. */
    fun any(o: JSONObject): Usage? = gemini(o) ?: openAi(o) ?: anthropic(o)

    /** Rough count when the provider sent none: about 4 characters per token. */
    fun estimate(inputChars: Int, outputChars: Int) =
        Usage(ceil(inputChars / 4.0).toLong(), ceil(outputChars / 4.0).toLong(), estimated = true)
}

/** USD per one million tokens. */
data class ModelPrice(val model: String, val inputPerM: Double, val outputPerM: Double)

object PriceTable {
    /** Seed prices (USD / 1M tokens, list prices as of late 2025). Users can edit or add rows in Settings. */
    val seeds = listOf(
        ModelPrice("claude-opus-4-1", 15.0, 75.0),
        ModelPrice("claude-sonnet-4-5", 3.0, 15.0),
        ModelPrice("claude-haiku-4-5", 1.0, 5.0),
        ModelPrice("gpt-4.1", 2.0, 8.0),
        ModelPrice("gpt-4.1-mini", 0.4, 1.6),
        ModelPrice("gpt-4o", 2.5, 10.0),
        ModelPrice("gpt-4o-mini", 0.15, 0.6),
        ModelPrice("o4-mini", 1.1, 4.4),
        ModelPrice("gemini-2.5-pro", 1.25, 10.0),
        ModelPrice("gemini-2.5-flash", 0.3, 2.5),
        ModelPrice("deepseek-chat", 0.28, 0.42),
        ModelPrice("deepseek-reasoner", 0.28, 0.42),
    )

    /** "anthropic/claude-sonnet-4.5" → "claude-sonnet-4-5". */
    fun key(model: String) = model.trim().lowercase().substringAfterLast('/').removePrefix("models/").replace('.', '-')

    /** Seeds overridden / extended by the user's rows (matched by [key]). */
    fun merge(user: List<ModelPrice>): List<ModelPrice> {
        val map = LinkedHashMap<String, ModelPrice>()
        seeds.forEach { map[key(it.model)] = it }
        user.forEach { map[key(it.model)] = it }
        return map.values.toList()
    }

    /** Exact match first, then the longest row that is a prefix ("claude-sonnet-4-5-20250929"). */
    fun find(model: String, table: List<ModelPrice>): ModelPrice? {
        val k = key(model)
        table.firstOrNull { key(it.model) == k }?.let { return it }
        return table.filter { k.startsWith(key(it.model) + "-") }.maxByOrNull { it.model.length }
    }

    /** Cost in USD, or null when the model has no (non-zero) price. */
    fun cost(model: String, u: Usage, table: List<ModelPrice>): Double? {
        val p = find(model, table) ?: return null
        if (p.inputPerM == 0.0 && p.outputPerM == 0.0) return null
        return u.input / 1e6 * p.inputPerM + u.output / 1e6 * p.outputPerM
    }

    /** Sum over models; null when no model in [byModel] has a price. */
    fun cost(byModel: Map<String, Usage>, table: List<ModelPrice>): Double? {
        val costs = byModel.mapNotNull { (m, u) -> cost(m, u, table) }
        return if (costs.isEmpty()) null else costs.sum()
    }

    fun encode(rows: List<ModelPrice>): String = JSONArray().apply {
        rows.forEach { put(JSONObject().put("model", it.model).put("in", it.inputPerM).put("out", it.outputPerM)) }
    }.toString()

    fun decode(json: String?): List<ModelPrice> = runCatching {
        val arr = JSONArray(json ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val m = o.optString("model").trim()
            if (m.isEmpty()) null else ModelPrice(m, o.optDouble("in", 0.0).coerceAtLeast(0.0), o.optDouble("out", 0.0).coerceAtLeast(0.0))
        }
    }.getOrDefault(emptyList())
}

/**
 * Persistent per-day, per-model token totals for all projects (`usage.json`), used for the
 * "today" / "this month" figures and the monthly budget. Costs are computed when shown, so
 * editing a price applies to past usage too.
 */
class UsageLedger(private val file: File) {
    /** day ("2026-11-23") → model → usage */
    private fun load(): MutableMap<String, MutableMap<String, Usage>> {
        val out = sortedMapOf<String, MutableMap<String, Usage>>()
        runCatching {
            val days = JSONObject(file.readText()).getJSONObject("days")
            days.keys().forEach { d ->
                val models = days.getJSONObject(d)
                val m = LinkedHashMap<String, Usage>()
                models.keys().forEach { k ->
                    val a = models.getJSONArray(k)
                    m[k] = Usage(a.optLong(0), a.optLong(1), a.optInt(2) == 1)
                }
                out[d] = m
            }
        }
        return out
    }

    private fun save(data: Map<String, Map<String, Usage>>) {
        val days = JSONObject()
        data.entries.toList().takeLast(KEEP_DAYS).forEach { (d, models) ->
            val o = JSONObject()
            models.forEach { (k, u) -> o.put(k, JSONArray().put(u.input).put(u.output).put(if (u.estimated) 1 else 0)) }
            days.put(d, o)
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSONObject().put("days", days).toString())
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    fun record(model: String, u: Usage, day: LocalDate = LocalDate.now()) = synchronized(LOCK) {
        val data = load()
        val m = data.getOrPut(day.toString()) { LinkedHashMap() }
        m[model] = (m[model] ?: Usage.ZERO) + u
        save(data)
    }

    fun day(day: LocalDate = LocalDate.now()): Map<String, Usage> = synchronized(LOCK) { load()[day.toString()] ?: emptyMap() }

    fun month(day: LocalDate = LocalDate.now()): Map<String, Usage> = synchronized(LOCK) {
        val prefix = day.toString().take(7)
        val out = LinkedHashMap<String, Usage>()
        load().filterKeys { it.startsWith(prefix) }.values.forEach { m -> m.forEach { (k, u) -> out[k] = (out[k] ?: Usage.ZERO) + u } }
        out
    }

    companion object {
        private val LOCK = Any()
        const val KEEP_DAYS = 400
    }
}

/** Monthly budget thresholds: warn at 80%, require confirmation at 100%. */
object Budget {
    enum class Level { NONE, OK, WARN, OVER }
    const val WARN_AT = 0.8

    fun level(spent: Double, budget: Double?): Level = when {
        budget == null || budget <= 0.0 -> Level.NONE
        spent >= budget -> Level.OVER
        spent >= budget * WARN_AT -> Level.WARN
        else -> Level.OK
    }

    fun fraction(spent: Double, budget: Double?): Float =
        if (budget == null || budget <= 0.0) 0f else (spent / budget).toFloat().coerceIn(0f, 1f)

    /** Sending needs an explicit confirmation once the budget is used up. */
    fun needsConfirmation(spent: Double, budget: Double?) = level(spent, budget) == Level.OVER

    /** Which budget a send would trip, if any. */
    enum class Kind { MONTHLY, DAILY }

    /**
     * v0.1.15: the budget that blocks this send, or null when neither is used up.
     * Monthly wins when both are over so the bigger picture is answered first.
     */
    fun confirmation(monthSpent: Double, monthBudget: Double?, todaySpent: Double, dayBudget: Double?): Kind? = when {
        needsConfirmation(monthSpent, monthBudget) -> Kind.MONTHLY
        needsConfirmation(todaySpent, dayBudget) -> Kind.DAILY
        else -> null
    }
}
