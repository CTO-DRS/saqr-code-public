package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ModelDuel
import com.ctodrs.saqrcode.agent.ModelPrice
import com.ctodrs.saqrcode.agent.PriceTable
import com.ctodrs.saqrcode.agent.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.14: duel cost math, stats line and labels. */
class V0115ModelDuelTest {

    private val prices = PriceTable.merge(emptyList())

    @Test
    fun `cost uses the price table per million tokens`() {
        val side = ModelDuel.Side("openai", "gpt-4o-mini", usage = Usage(1000, 500))
        // gpt-4o-mini: $0.15 in / $0.60 out per 1M → 1000*0.15 + 500*0.60 = 450 micro-USD
        val cost = ModelDuel.costOf(side, prices)!!
        assertEquals(0.00045, cost, 0.0000001)
    }

    @Test
    fun `versioned model names match the prefix row`() {
        val side = ModelDuel.Side("anthropic", "claude-sonnet-4-5-20250929", usage = Usage(1_000_000, 0))
        assertEquals(3.0, ModelDuel.costOf(side, prices)!!, 0.0001)
    }

    @Test
    fun `unknown price or missing usage drops out of the stats line`() {
        val noPrice = ModelDuel.Side("local", "my-model", elapsedMs = 1200, usage = Usage(100, 50))
        val stats = ModelDuel.statsOf(noPrice, prices)
        assertTrue(stats.contains("1.2s"))
        assertTrue(stats.contains("150 tokens"))
        assertTrue(!stats.contains("$"))
        val noUsage = ModelDuel.Side("openai", "gpt-4o", elapsedMs = 800)
        assertEquals("0.8s", ModelDuel.statsOf(noUsage, prices))
    }

    @Test
    fun `label and running state`() {
        val running = ModelDuel.Side("google", "gemini-2.5-flash")
        assertEquals("google/gemini-2.5-flash", ModelDuel.label(running))
        assertTrue(running.running)
        assertTrue(!running.copy(text = "answer").running)
        assertTrue(!running.copy(error = "boom").running)
    }

    @Test
    fun `estimated usage is marked with a tilde`() {
        val side = ModelDuel.Side("local", "m", elapsedMs = 500, usage = Usage(2000, 1000, estimated = true))
        assertTrue(ModelDuel.statsOf(side, prices).contains("~3.0k tokens"))
    }
}
