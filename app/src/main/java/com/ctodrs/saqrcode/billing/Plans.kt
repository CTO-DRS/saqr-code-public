package com.ctodrs.saqrcode.billing

/**
 * v0.11 · خطط صقر كود · Saqr Code plans and pricing.
 *
 * Prices are in Saudi Riyal stored as **halalas** (1 SAR = 100 halalas) — the same unit Moyasar
 * and Play Billing report. Free keeps every feature shipped so far; Pro removes the counters and
 * unlocks team agents plus signed release builds. Pure and unit-tested in V011FeaturesTest.
 */
enum class PlanTier { FREE, PRO, STUDENT, LIFETIME }

/** One purchasable plan. [priceHalalas] is the SAR amount × 100. */
data class Plan(
    val id: String,
    val tier: PlanTier,
    val titleKey: String,
    val priceHalalas: Int,
    val monthly: Boolean,
    val playProductId: String,
) {
    val priceSar: Double get() = priceHalalas / 100.0
}

/** What each tier allows. `-1` = unlimited. */
data class PlanLimits(
    val maxProjects: Int,
    val maxMcpServers: Int,
    val maxGitHosts: Int,
    val teamAgents: Boolean,
    val signedRelease: Boolean,
) {
    val unlimitedProjects: Boolean get() = maxProjects < 0
    val unlimitedMcp: Boolean get() = maxMcpServers < 0
    val unlimitedHosts: Boolean get() = maxGitHosts < 0

    companion object { const val UNLIMITED = -1 }
}

object Plans {

    val PRO_MONTHLY = Plan("pro_monthly", PlanTier.PRO, "plan_pro", 1500, true, "saqr_pro_monthly")
    val STUDENT_MONTHLY = Plan("student_monthly", PlanTier.STUDENT, "plan_student", 500, true, "saqr_student_monthly")
    val LIFETIME = Plan("lifetime", PlanTier.LIFETIME, "plan_lifetime", 14900, false, "saqr_lifetime")

    val CATALOG: List<Plan> = listOf(PRO_MONTHLY, STUDENT_MONTHLY, LIFETIME)

    fun byId(id: String): Plan? = CATALOG.firstOrNull { it.id == id }

    /** Play Billing product id → plan, for purchase → entitlement mapping. */
    fun byPlayProduct(productId: String?): Plan? = CATALOG.firstOrNull { it.playProductId == productId }

    /** `1500` → `15.00` — the amount part, currency symbol comes from string resources. */
    fun amountText(plan: Plan): String = "%,.2f".format(plan.priceSar)

    /** Free tier limits: generous but bounded. Pro/student/lifetime lift them. */
    fun freeLimits(): PlanLimits = PlanLimits(
        maxProjects = 3, maxMcpServers = 1, maxGitHosts = 1,
        teamAgents = false, signedRelease = false,
    )

    fun proLimits(): PlanLimits = PlanLimits(
        maxProjects = PlanLimits.UNLIMITED, maxMcpServers = 8, maxGitHosts = 8,
        teamAgents = true, signedRelease = true,
    )

    /** Student pays less but gets the same features as Pro. */
    fun limitsFor(tier: PlanTier): PlanLimits = when (tier) {
        PlanTier.FREE -> freeLimits()
        PlanTier.PRO, PlanTier.STUDENT, PlanTier.LIFETIME -> proLimits()
    }

    /** The plan a tier renews through; lifetime maps to itself (no renewal). */
    fun planForTier(tier: PlanTier): Plan? = when (tier) {
        PlanTier.PRO -> PRO_MONTHLY
        PlanTier.STUDENT -> STUDENT_MONTHLY
        PlanTier.LIFETIME -> LIFETIME
        PlanTier.FREE -> null
    }
}
