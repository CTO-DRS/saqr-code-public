package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.Budget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v0.1.15: daily budget levels, the send-confirmation matrix and per-run stats plumbing. */
class V0116BudgetDailyTest {

    @Test
    fun `daily level thresholds mirror the monthly ones`() {
        assertEquals(Budget.Level.NONE, Budget.level(5.0, null))
        assertEquals(Budget.Level.NONE, Budget.level(5.0, 0.0))
        assertEquals(Budget.Level.OK, Budget.level(0.50, 1.0))
        assertEquals(Budget.Level.WARN, Budget.level(0.80, 1.0))
        assertEquals(Budget.Level.OVER, Budget.level(1.0, 1.0))
        assertEquals(Budget.Level.OVER, Budget.level(1.01, 1.0))
    }

    @Test
    fun `confirmation is null while both budgets are under`() {
        assertNull(Budget.confirmation(5.0, 10.0, 1.0, 2.0))
        assertNull(Budget.confirmation(8.0, 10.0, 1.6, 2.0)) // both WARN, neither OVER
        assertNull(Budget.confirmation(99.0, null, 0.5, 2.0))
        assertNull(Budget.confirmation(1.0, 10.0, 99.0, null))
    }

    @Test
    fun `daily over alone asks for the daily confirmation`() {
        assertEquals(Budget.Kind.DAILY, Budget.confirmation(5.0, 10.0, 2.0, 2.0))
        assertEquals(Budget.Kind.DAILY, Budget.confirmation(5.0, 10.0, 2.5, 2.0))
    }

    @Test
    fun `monthly over wins when both are over`() {
        assertEquals(Budget.Kind.MONTHLY, Budget.confirmation(10.0, 10.0, 2.5, 2.0))
        assertEquals(Budget.Kind.MONTHLY, Budget.confirmation(12.0, 10.0, 1.0, 2.0))
        assertEquals(Budget.Kind.MONTHLY, Budget.confirmation(10.0, 10.0, 1.0, null))
    }

    @Test
    fun `daily fraction feeds the banner progress bar`() {
        assertEquals(0.5f, Budget.fraction(1.0, 2.0), 0.0001f)
        assertEquals(0f, Budget.fraction(1.0, null), 0.0001f)
        assertEquals(1f, Budget.fraction(3.0, 2.0), 0.0001f)
    }

    @Test
    fun `run stats default to zero before any run`() {
        val stats = com.ctodrs.saqrcode.agent.RunStats()
        assertEquals(0, stats.steps)
        assertEquals(0, stats.files)
        assertEquals(0, stats.commands)
        assertEquals(0L, stats.tokens)
        assertNull(stats.cost)
        assertEquals(false, stats.estimatedTokens)
    }
}
