package com.ctodrs.saqrcode.agent

import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/*
 * v0.13.2 · موجّه النماذج · Falcon Router
 *
 * Route the light agent stages (planner, reviewer, context compaction) to the cheapest
 * capable model of the active provider, while the coder — the stage that needs the real
 * capability — stays on the user's chosen model. An explicit per-stage pick by the user
 * (plannerModel / reviewModel) always wins over the router.
 *
 * Savings are measured honestly: every routed call is priced at its model (actual) and at
 * the coder model's price (baseline), and the pair is persisted daily so the «≥25%» promise
 * from the roadmap is a published number, not a slogan. Routing happens only when the price
 * table can prove it — a model without a price row is never routed.
 */

object FalconRouter {

    /** Agent stages that can be routed. The coder stage is never routed. */
    enum class Stage(val required: Tier) {
        PLANNER(Tier.LIGHT),   // turns a request into a short JSON task plan — no code
        REVIEWER(Tier.MID),    // reads a diff and catches real bugs — needs some muscle
        COMPACTOR(Tier.LIGHT), // summarises the oldest context
    }

    /** Capability tiers by model family; a stage requires [Tier.rank] ≥ its requirement. */
    enum class Tier(val rank: Int) { LIGHT(0), MID(1), FULL(2) }

    // Family hints checked as whole tokens of the normalized key (separators: anything
    // non-alphanumeric). Whole-token matching keeps "gemini" from matching "mini".
    private val LIGHT_HINTS = setOf("haiku", "mini", "flash", "nano", "lite", "8b", "small")
    private val FULL_HINTS = setOf("opus", "ultra", "pro")

    /** [Tier] of a model from its name; unknown families are MID (the provider's main model). */
    fun tierOf(model: String): Tier {
        val tokens = PriceTable.key(model).split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        return when {
            tokens.any { it in LIGHT_HINTS } -> Tier.LIGHT
            tokens.any { it in FULL_HINTS } -> Tier.FULL
            else -> Tier.MID
        }
    }

    /**
     * The cheapest capable model for [stage] out of [candidates], or null when routing
     * would not provably save money: the coder has no price row, no candidate is both
     * capable (tier ≥ the stage requirement) and cheaper than the coder, or the coder is
     * already the cheapest capable model. Never returns the coder model itself.
     */
    fun pick(stage: Stage, coderModel: String, candidates: List<String>, prices: List<ModelPrice>): String? {
        val coderCost = unitCost(coderModel, prices) ?: return null
        val coderKey = PriceTable.key(coderModel)
        return candidates.asSequence()
            // compare and gate on the normalized key, but return the original id — the API
            // needs the vendor prefix on multi-model providers (e.g. openrouter)
            .filter { it.isNotBlank() && PriceTable.key(it) != coderKey }
            .filter { tierOf(it).rank >= stage.required.rank }
            .mapNotNull { m -> unitCost(m, prices)?.let { m to it } }
            .filter { (_, cost) -> cost < coderCost }
            .minByOrNull { it.second }
            ?.first
    }

    /** Blended cost per 1M tokens (input + output), or null when the model is unpriced. */
    private fun unitCost(model: String, prices: List<ModelPrice>): Double? {
        val p = PriceTable.find(model, prices) ?: return null
        if (p.inputPerM <= 0.0 && p.outputPerM <= 0.0) return null
        return p.inputPerM + p.outputPerM
    }

    /** One routed call's usage for the meter. */
    data class RoutedCall(val model: String, val usage: Usage)

    /**
     * The meter: prices [calls] at their models (actual) and at [coderModel]'s price
     * (baseline). Returns (actual, baseline) or null when any side is unpriced — the
     * published number never guesses.
     */
    fun meter(calls: List<RoutedCall>, coderModel: String, prices: List<ModelPrice>): Pair<Double, Double>? {
        if (calls.isEmpty()) return null
        val coderP = PriceTable.find(coderModel, prices) ?: return null
        if (coderP.inputPerM <= 0.0 && coderP.outputPerM <= 0.0) return null
        var actual = 0.0
        var baseline = 0.0
        for (c in calls) {
            actual += PriceTable.cost(c.model, c.usage, prices) ?: return null
            baseline += c.usage.input / 1e6 * coderP.inputPerM + c.usage.output / 1e6 * coderP.outputPerM
        }
        return actual to baseline
    }

    /** Savings share of the baseline (0.25 = 25%); null when the baseline is zero. */
    fun savingsPct(actual: Double, baseline: Double): Double? =
        if (baseline <= 0.0) null else (baseline - actual) / baseline
}

/**
 * Persistent daily savings (`usage/falcon.json`, next to `usage.json`): day →
 * [actualUsd, baselineUsd]. Costs are computed once at call time with the price table of
 * that moment and kept as dollars, so the published «saved X% / Y$ this month» survives
 * restarts without re-reading token counts.
 */
class SavingsLedger(private val file: File) {

    private fun load(): MutableMap<String, DoubleArray> {
        val out = linkedMapOf<String, DoubleArray>()
        runCatching {
            val days = JSONObject(file.readText()).getJSONObject("days")
            days.keys().forEach { d ->
                val a = days.getJSONArray(d)
                if (a.length() >= 2) out[d] = doubleArrayOf(a.optDouble(0, 0.0), a.optDouble(1, 0.0))
            }
        }
        return out
    }

    private fun save(data: Map<String, DoubleArray>) {
        val days = JSONObject()
        data.entries.sortedByDescending { it.key }.take(KEEP_DAYS).forEach { (d, v) ->
            days.put(d, org.json.JSONArray().put(v[0]).put(v[1]))
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSONObject().put("days", days).toString())
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    /** Adds one routed call's (actual, baseline) dollars to [day]. */
    fun record(actualUsd: Double, baselineUsd: Double, day: LocalDate = LocalDate.now()) = synchronized(LOCK) {
        if (actualUsd >= 0.0 && baselineUsd > 0.0) {
            val data = load()
            val v = data.getOrPut(day.toString()) { doubleArrayOf(0.0, 0.0) }
            v[0] += actualUsd
            v[1] += baselineUsd
            save(data)
        }
    }

    /** (actual, baseline) dollars for [day]; zeros when nothing was recorded. */
    fun day(day: LocalDate = LocalDate.now()): Pair<Double, Double> = synchronized(LOCK) {
        val v = load()[day.toString()] ?: return zeros()
        v[0] to v[1]
    }

    /** (actual, baseline) dollars over the calendar month of [day]. */
    fun month(day: LocalDate = LocalDate.now()): Pair<Double, Double> = synchronized(LOCK) {
        val prefix = day.toString().take(7)
        var a = 0.0
        var b = 0.0
        load().filterKeys { it.startsWith(prefix) }.values.forEach { v -> a += v[0]; b += v[1] }
        a to b
    }

    private fun zeros() = 0.0 to 0.0

    companion object {
        private val LOCK = Any()
        const val KEEP_DAYS = 400
    }
}
