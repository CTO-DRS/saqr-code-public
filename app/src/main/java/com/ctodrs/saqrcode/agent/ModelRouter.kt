package com.ctodrs.saqrcode.agent

import kotlin.coroutines.cancellation.CancellationException

/*
 * v0.17 · الموجّه الذكي · Smart Router — an extension of Falcon Router (v0.13.2), not a
 * replacement. Falcon Router routes the light stages to the cheapest capable model of the
 * ACTIVE provider when the price table proves a saving; it is unchanged and is what runs when
 * the `smartRouter` setting is off (the default).
 *
 * With `smartRouter` on, every model choice goes through [SmartRouter.route]:
 *  - a capability table ([ModelCatalog]) for the models the app already knows (vision, tool
 *    calling, context window, relative speed) with prices from the user's price table;
 *  - only providers the user can actually call: a saved key, or a keyless local/custom endpoint
 *    with a model set (BYOK — no key is ever assumed);
 *  - by tier: CHEAP (planning, classification, compaction) → the cheapest capable priced model
 *    that is provably cheaper than the coder; STRONG (coding) → the user's coder model unless it
 *    lacks a needed capability (images, context size); INDEPENDENT_REVIEW → a capable model
 *    different from the coder, preferring another provider/family;
 *  - a failover chain (the user's Phase 2 fallback first, then capable alternatives) that
 *    [Failover] walks when a provider fails with a transient error;
 *  - every decision has a reason that is written to the audit log and shown as a compact notice.
 *
 * Pure logic, unit-tested in V017FeaturesTest.
 */

/** What a model can do. [speed] is relative (1 slow · 2 normal · 3 fast) and only breaks ties. */
data class ModelCaps(
    val model: String,
    val vision: Boolean,
    val tools: Boolean,
    val contextWindow: Int,
    val speed: Int,
    val tier: FalconRouter.Tier,
    /** USD per 1M input + output tokens from the price table; null = unpriced. */
    val unitPrice: Double?,
    /** The model is in [ModelCatalog]; unknown models get conservative defaults. */
    val known: Boolean,
)

object ModelCatalog {
    private data class Row(val key: String, val tools: Boolean, val context: Int, val speed: Int)

    /**
     * Models the app already ships (Providers.suggestedModels). Context windows and tool support
     * from the vendors' model pages (late 2025); vision comes from [Vision] (heuristic + the
     * user's per-model override) so there is one source of truth. deepseek-reasoner is marked
     * without tool calling — the conservative reading — so tool-using steps are never routed to it.
     */
    private val TABLE = listOf(
        Row("claude-opus-4-1", true, 200_000, 1),
        Row("claude-sonnet-4-5", true, 200_000, 2),
        Row("claude-haiku-4-5", true, 200_000, 3),
        Row("gpt-4.1", true, 1_047_576, 2),
        Row("gpt-4.1-mini", true, 1_047_576, 3),
        Row("gpt-4o", true, 128_000, 2),
        Row("gpt-4o-mini", true, 128_000, 3),
        Row("o4-mini", true, 200_000, 1),
        Row("gemini-2.5-pro", true, 1_048_576, 1),
        Row("gemini-2.5-flash", true, 1_048_576, 3),
        Row("deepseek-chat", true, 128_000, 2),
        Row("deepseek-reasoner", false, 128_000, 1),
        Row("qwen3-coder", true, 262_144, 2),
    )

    private fun row(model: String): Row? {
        val k = PriceTable.key(model)
        TABLE.firstOrNull { PriceTable.key(it.key) == k }?.let { return it }
        return TABLE.filter { k.startsWith(PriceTable.key(it.key) + "-") }.maxByOrNull { it.key.length }
    }

    fun knows(model: String) = row(model) != null

    /** Capabilities of [model]; an unknown model gets its provider's context window, tools on, no vision unless [Vision] says so. */
    fun caps(model: String, providerContext: Int, prices: List<ModelPrice>, visionOverrides: Map<String, String> = emptyMap()): ModelCaps {
        val r = row(model)
        val p = PriceTable.find(model, prices)?.takeIf { it.inputPerM > 0.0 || it.outputPerM > 0.0 }
        return ModelCaps(
            model = model,
            vision = Vision.supports(model, visionOverrides),
            tools = r?.tools ?: true,
            contextWindow = r?.context ?: providerContext,
            speed = r?.speed ?: 2,
            tier = FalconRouter.tierOf(model),
            unitPrice = p?.let { it.inputPerM + it.outputPerM },
            known = r != null,
        )
    }
}

/** A model on a provider. */
data class RouteTarget(val provider: String, val model: String) {
    val label get() = "$provider · $model"
    fun sameModel(o: RouteTarget?) = o != null && PriceTable.key(model) == PriceTable.key(o.model)
}

/** A provider the user can call right now (key saved, or keyless local endpoint) and the models it offers. */
data class AvailableProvider(val id: String, val models: List<String>, val contextWindow: Int)

data class RouteRequest(
    val tier: ModelTier,
    /** The user's coder model on the active provider. */
    val primary: RouteTarget,
    val needsVision: Boolean = false,
    val needsTools: Boolean = true,
    /** Estimated prompt size in tokens. */
    val contextTokens: Int = 0,
    /** INDEPENDENT_REVIEW: the model whose work is reviewed (the coder) — avoided when possible. */
    val avoid: RouteTarget? = null,
)

data class RouteDecision(
    val target: RouteTarget,
    /** Short English reason for the audit log / notice (the UI localizes the frame). */
    val reason: String,
    /** True when the target differs from the user's coder model. */
    val routed: Boolean,
    /** Alternatives to try on a transient failure, in order (excludes [target]). */
    val failover: List<RouteTarget>,
)

object SmartRouter {
    /** Context headroom: the prompt must fit with room for the answer. */
    const val CONTEXT_HEADROOM = 1.25
    const val MAX_FAILOVER = 3

    fun neededContext(tokens: Int): Int = (tokens * CONTEXT_HEADROOM).toInt()

    fun candidates(available: List<AvailableProvider>, prices: List<ModelPrice>, vision: Map<String, String>): List<Pair<RouteTarget, ModelCaps>> =
        available.flatMap { p ->
            p.models.filter { it.isNotBlank() }.distinct().map { m -> RouteTarget(p.id, m) to ModelCatalog.caps(m, p.contextWindow, prices, vision) }
        }

    fun capable(c: ModelCaps, r: RouteRequest): Boolean =
        (!r.needsVision || c.vision) && (!r.needsTools || c.tools) && c.contextWindow >= neededContext(r.contextTokens)

    private fun missing(c: ModelCaps?, r: RouteRequest): String = when {
        c == null -> "the coder model is not available"
        r.needsVision && !c.vision -> "images need a vision model"
        r.needsTools && !c.tools -> "this step needs tool calling"
        c.contextWindow < neededContext(r.contextTokens) -> "context ~${r.contextTokens / 1000}k tokens exceeds ${c.contextWindow / 1000}k"
        else -> ""
    }

    /**
     * Picks the model for [r]. Only [available] providers are considered (the primary is always
     * allowed — it is the user's own choice). Never invents capability: a model outside the
     * catalog keeps conservative defaults, and an unpriced model is never chosen to "save money".
     */
    fun route(
        r: RouteRequest, available: List<AvailableProvider>, prices: List<ModelPrice>,
        vision: Map<String, String> = emptyMap(), userFallback: FallbackTarget? = null,
    ): RouteDecision {
        val all = candidates(available, prices, vision)
        val primaryCaps = all.firstOrNull { it.first == r.primary }?.second
            ?: ModelCatalog.caps(r.primary.model, available.firstOrNull { it.id == r.primary.provider }?.contextWindow ?: 128_000, prices, vision)
        val ok = all.filter { capable(it.second, r) }
        val primaryOk = capable(primaryCaps, r)
        val sameProviderFirst = compareBy<Pair<RouteTarget, ModelCaps>> { if (it.first.provider == r.primary.provider) 0 else 1 }

        val (target, reason) = when (r.tier) {
            ModelTier.CHEAP -> {
                val base = primaryCaps.unitPrice
                // like Falcon Router: route for savings only when the coder's price proves it
                val cheaper = if (base == null) null else ok.filter { it.second.unitPrice != null && it.second.unitPrice!! < base }
                    .sortedWith(compareBy<Pair<RouteTarget, ModelCaps>> { it.second.unitPrice!! }.then(sameProviderFirst).thenByDescending { it.second.speed })
                    .firstOrNull()
                when {
                    cheaper != null && !cheaper.first.sameModel(r.primary) -> cheaper.first to "cheapest capable model for a light step"
                    primaryOk -> r.primary to (if (base == null) "coder model (unpriced, so no provable saving)" else "coder model is already the cheapest capable")
                    else -> bestStrong(ok, r)?.let { it to missing(primaryCaps, r) } ?: (r.primary to "no capable model available; using yours")
                }
            }
            ModelTier.STRONG -> when {
                primaryOk -> r.primary to "your coder model"
                else -> bestStrong(ok, r)?.let { it to missing(primaryCaps, r) } ?: (r.primary to "no capable model available; using yours")
            }
            ModelTier.INDEPENDENT_REVIEW -> {
                val avoid = r.avoid ?: r.primary
                val indep = ok.filter { !it.first.sameModel(avoid) && it.second.tier.rank >= FalconRouter.Tier.MID.rank }
                    .sortedWith(compareBy<Pair<RouteTarget, ModelCaps>> { if (it.first.provider != avoid.provider) 0 else 1 }
                        .thenBy { it.second.unitPrice ?: Double.MAX_VALUE }
                        .thenByDescending { it.second.tier.rank })
                    .firstOrNull()
                when {
                    indep != null -> indep.first to (if (indep.first.provider != avoid.provider) "independent reviewer on another provider" else "independent reviewer (different model from the coder)")
                    primaryOk -> r.primary to "no independent model available; the coder's model reviews"
                    else -> r.primary to "no capable model available; using yours"
                }
            }
        }
        return RouteDecision(target, reason, !target.sameModel(r.primary) || target.provider != r.primary.provider,
            failoverChain(target, r, all, userFallback, available))
    }

    /** Strongest capable model: highest tier, then the active provider, then cheaper, then faster. */
    private fun bestStrong(ok: List<Pair<RouteTarget, ModelCaps>>, r: RouteRequest): RouteTarget? =
        ok.sortedWith(compareByDescending<Pair<RouteTarget, ModelCaps>> { it.second.tier.rank }
            .thenBy { if (it.first.provider == r.primary.provider) 0 else 1 }
            .thenBy { it.second.unitPrice ?: Double.MAX_VALUE }
            .thenByDescending { it.second.speed })
            .firstOrNull()?.first

    /**
     * Where to go when [chosen] fails with a transient error: the user's own fallback first
     * (Phase 2), then capable models on OTHER providers (a provider outage takes all its models
     * down), closest tier first then cheaper, then other models of the same provider.
     */
    fun failoverChain(
        chosen: RouteTarget, r: RouteRequest, all: List<Pair<RouteTarget, ModelCaps>>,
        userFallback: FallbackTarget?, available: List<AvailableProvider>, max: Int = MAX_FAILOVER,
    ): List<RouteTarget> {
        val out = LinkedHashSet<RouteTarget>()
        if (userFallback != null && available.any { it.id == userFallback.provider }) {
            val t = RouteTarget(userFallback.provider, userFallback.model)
            if (t != chosen) out += t
        }
        val chosenTier = all.firstOrNull { it.first == chosen }?.second?.tier ?: FalconRouter.tierOf(chosen.model)
        all.filter { it.first != chosen && capable(it.second, r) }
            .sortedWith(compareBy<Pair<RouteTarget, ModelCaps>> { if (it.first.provider != chosen.provider) 0 else 1 }
                .thenBy { kotlin.math.abs(it.second.tier.rank - chosenTier.rank) }
                .thenBy { it.second.unitPrice ?: Double.MAX_VALUE })
            .forEach { out += it.first }
        return out.take(max)
    }

    /** One audit/notice line: "role → provider · model (reason)". */
    fun logLine(label: String, d: RouteDecision): String = "$label → ${d.target.label} (${d.reason})"
}

/**
 * Walks a failover chain: each target is tried once; a transient failure ([retryable]) moves on
 * to the next with [onSwitch] (the chat notice), anything else — a bad key, a 400, a cancel —
 * surfaces at once. Returns the result and the target that produced it.
 */
object Failover {
    suspend fun <T> run(
        chain: List<RouteTarget>,
        retryable: (Throwable) -> Boolean = LlmRetry::isRetryable,
        onSwitch: (from: RouteTarget, to: RouteTarget, error: Throwable) -> Unit = { _, _, _ -> },
        call: suspend (RouteTarget) -> T,
    ): Pair<T, RouteTarget> {
        require(chain.isNotEmpty()) { "empty failover chain" }
        var last: Throwable? = null
        for ((i, t) in chain.withIndex()) {
            try {
                return call(t) to t
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!retryable(e)) throw e
                last = e
                chain.getOrNull(i + 1)?.let { onSwitch(t, it, e) }
            }
        }
        throw last!!
    }
}

/** User-configurable daily / monthly spend caps (USD, priced spend from the usage ledger; 0 = off). */
object SpendCap {
    enum class Hit { NONE, DAILY, MONTHLY }

    /** [daySpent]/[monthSpent] null = nothing priced (the cap cannot apply). */
    fun check(daySpent: Double?, monthSpent: Double?, dailyCap: Double, monthlyCap: Double): Hit = when {
        dailyCap > 0.0 && daySpent != null && daySpent >= dailyCap -> Hit.DAILY
        monthlyCap > 0.0 && monthSpent != null && monthSpent >= monthlyCap -> Hit.MONTHLY
        else -> Hit.NONE
    }

    /** The notice text the chat localizes. */
    fun noticeText(h: Hit): String = when (h) {
        Hit.DAILY -> "daily_cap"
        Hit.MONTHLY -> "monthly_cap"
        Hit.NONE -> ""
    }
}

/**
 * v0.17 pre-run estimate with real per-model prices: each step's prompt starts at the current
 * context and grows as tool results accumulate; a range (half to double the typical steps)
 * instead of a single number, because step counts genuinely vary. Null when a model is unpriced.
 */
object CostEstimate {
    const val GROWTH_PER_STEP = 1_500L
    const val OUTPUT_PER_STEP = 700L
    const val MIN_CONTEXT = 3_000L

    data class Range(val low: Double, val high: Double)

    /** USD for [steps] steps on [model] starting at [contextTokens]. */
    fun steps(model: String, steps: Int, contextTokens: Long, prices: List<ModelPrice>): Double? {
        if (steps <= 0) return 0.0
        var sum = 0.0
        val start = contextTokens.coerceAtLeast(MIN_CONTEXT)
        for (i in 0 until steps) {
            sum += PriceTable.cost(model, Usage(start + i * GROWTH_PER_STEP, OUTPUT_PER_STEP), prices) ?: return null
        }
        return sum
    }

    /** [plan] = (model, typical steps) per role/subtask. */
    fun range(plan: List<Pair<String, Int>>, contextTokens: Long, prices: List<ModelPrice>): Range? {
        var low = 0.0
        var high = 0.0
        for ((m, n) in plan) {
            low += steps(m, (n + 1) / 2, contextTokens, prices) ?: return null
            high += steps(m, n * 2, contextTokens, prices) ?: return null
        }
        return Range(low, high)
    }
}
