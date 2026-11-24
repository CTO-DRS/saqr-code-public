package com.ctodrs.saqrcode.billing

import org.json.JSONObject

/**
 * v0.11 · الاستحقاق · The user's entitlement: which plan they paid for, through which channel,
 * until when. Stored as JSON in plain prefs (no secrets — a license token's signature is the
 * trust anchor, Play purchases are re-verified by [PlayBilling] at every start).
 *
 * Resolution: an expired subscription falls back to FREE after a 3-day grace window, so a renewal
 * hiccup never yanks features mid-work. Pure and unit-tested in V011FeaturesTest.
 */
data class Entitlement(
    val tier: PlanTier,
    /** "play" | "license" — where the entitlement came from. */
    val source: String,
    /** Epoch ms when a monthly plan lapses; null = lifetime / never expires. */
    val expiresAt: Long?,
    val orderId: String,
    val email: String? = null,
)

object Entitlements {

    enum class State { FREE, ACTIVE, GRACE, EXPIRED }

    const val GRACE_DAYS = 3L
    /** A "monthly" subscription is trusted for 31 days from its last anchor. */
    const val MONTH_MS = 31L * 24 * 60 * 60 * 1000
    const val SOURCE_PLAY = "play"
    const val SOURCE_LICENSE = "license"

    /** State of [ent] at [now]: grace keeps features, expiry drops to FREE. */
    fun stateOf(ent: Entitlement?, now: Long): State {
        if (ent == null || ent.tier == PlanTier.FREE) return State.FREE
        val exp = ent.expiresAt ?: return State.ACTIVE
        return when {
            now <= exp -> State.ACTIVE
            now <= exp + GRACE_DAYS * 24 * 60 * 60 * 1000 -> State.GRACE
            else -> State.EXPIRED
        }
    }

    /** The tier in force at [now]: GRACE still grants the paid tier, EXPIRED falls back to FREE. */
    fun effectiveTier(ent: Entitlement?, now: Long): PlanTier {
        val st = stateOf(ent, now)
        if (st == State.EXPIRED || st == State.FREE) return PlanTier.FREE
        return ent!!.tier
    }

    /** Extends a monthly entitlement: from its expiry while active, else from [from] (re-subscribe). */
    fun renewMonthly(previous: Entitlement?, from: Long): Long {
        val base = previous?.expiresAt?.takeIf { it > from } ?: from
        return base + MONTH_MS
    }

    /** Keeps the better of two entitlements: higher tier wins, then longer expiry, then newer order. */
    fun merge(current: Entitlement?, incoming: Entitlement): Entitlement {
        val cur = current ?: return incoming
        if (cur.tier == incoming.tier) {
            val ce = cur.expiresAt ?: Long.MAX_VALUE
            val ie = incoming.expiresAt ?: Long.MAX_VALUE
            return if (ie >= ce) incoming else cur
        }
        val rank = { t: PlanTier ->
            when (t) { PlanTier.FREE -> 0; PlanTier.STUDENT -> 1; PlanTier.PRO -> 2; PlanTier.LIFETIME -> 3 }
        }
        return if (rank(incoming.tier) >= rank(cur.tier)) incoming else cur
    }

    // ------------------------------------------------------------ persistence

    fun encode(ent: Entitlement): String = JSONObject()
        .put("tier", ent.tier.name.lowercase())
        .put("source", ent.source)
        .put("exp", ent.expiresAt ?: JSONObject.NULL)
        .put("order", ent.orderId)
        .put("email", ent.email ?: JSONObject.NULL)
        .toString()

    fun decode(json: String?): Entitlement? = runCatching {
        val o = JSONObject(json ?: return null)
        val tier = runCatching { PlanTier.valueOf(o.getString("tier").uppercase()) }.getOrNull() ?: return null
        if (tier == PlanTier.FREE) return null
        val exp = if (o.isNull("exp")) null else o.optLong("exp", 0L).takeIf { it > 0 }
        Entitlement(
            tier = tier,
            source = o.optString("source", SOURCE_LICENSE).ifBlank { SOURCE_LICENSE },
            expiresAt = exp,
            orderId = o.optString("order"),
            email = o.optString("email").takeIf { it.isNotBlank() && !o.isNull("email") },
        )
    }.getOrNull()
}
