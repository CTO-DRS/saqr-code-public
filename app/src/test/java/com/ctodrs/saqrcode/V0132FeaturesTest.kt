package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.FalconRouter
import com.ctodrs.saqrcode.agent.ModelPrice
import com.ctodrs.saqrcode.agent.PriceTable
import com.ctodrs.saqrcode.agent.SavingsLedger
import com.ctodrs.saqrcode.agent.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

/** v0.13 «عقل الصقر» · Falcon Router (موجّه النماذج): capability tiering, cheapest-capable
 *  routing per stage, the honest savings meter (actual vs the coder-price baseline), and the
 *  persistent daily savings ledger. */
class V0132FeaturesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val prices = PriceTable.seeds

    // ------------------------------------------------------------ tiering

    @Test
    fun `cheap families are LIGHT`() {
        for (m in listOf("claude-haiku-4-5", "gpt-4.1-mini", "gemini-2.5-flash", "gpt-4o-mini", "o4-mini"))
            assertEquals(m, FalconRouter.Tier.LIGHT, FalconRouter.tierOf(m))
    }

    @Test
    fun `heavy families are FULL and unknown are MID`() {
        assertEquals(FalconRouter.Tier.FULL, FalconRouter.tierOf("claude-opus-4-1"))
        assertEquals(FalconRouter.Tier.FULL, FalconRouter.tierOf("gemini-2.5-pro"))
        assertEquals(FalconRouter.Tier.MID, FalconRouter.tierOf("claude-sonnet-4-5"))
        assertEquals(FalconRouter.Tier.MID, FalconRouter.tierOf("deepseek-chat"))
        assertEquals(FalconRouter.Tier.MID, FalconRouter.tierOf("my-private-model"))
        // vendor prefixes do not hide the family
        assertEquals(FalconRouter.Tier.LIGHT, FalconRouter.tierOf("anthropic/claude-haiku-4-5"))
    }

    @Test
    fun `stage requirements gate capability`() {
        assertEquals(FalconRouter.Tier.LIGHT, FalconRouter.Stage.PLANNER.required)
        assertEquals(FalconRouter.Tier.MID, FalconRouter.Stage.REVIEWER.required)
        assertEquals(FalconRouter.Tier.LIGHT, FalconRouter.Stage.COMPACTOR.required)
    }

    // ------------------------------------------------------------ routing

    @Test
    fun `planner and compactor route to the cheapest capable model`() {
        val anthropic = listOf("claude-sonnet-4-5", "claude-opus-4-1", "claude-haiku-4-5")
        assertEquals("claude-haiku-4-5", FalconRouter.pick(FalconRouter.Stage.PLANNER, "claude-opus-4-1", anthropic, prices))
        assertEquals("claude-haiku-4-5", FalconRouter.pick(FalconRouter.Stage.COMPACTOR, "claude-opus-4-1", anthropic, prices))
        // the reviewer needs a MID model — haiku is too light for it
        assertEquals("claude-sonnet-4-5", FalconRouter.pick(FalconRouter.Stage.REVIEWER, "claude-opus-4-1", anthropic, prices))
    }

    @Test
    fun `no route when the coder is already the cheapest capable model`() {
        val openai = listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4o", "o4-mini")
        // coder is the mini — nothing cheaper exists on the provider
        assertNull(FalconRouter.pick(FalconRouter.Stage.PLANNER, "gpt-4.1-mini", openai, prices))
        assertNull(FalconRouter.pick(FalconRouter.Stage.COMPACTOR, "gpt-4.1-mini", openai, prices))
        // coder is a LIGHT flash and the only cheap Gemini
        assertNull(FalconRouter.pick(FalconRouter.Stage.PLANNER, "gemini-2.5-flash", listOf("gemini-2.5-flash", "gemini-2.5-pro"), prices))
    }

    @Test
    fun `reviewer never routes to a LIGHT model even when it is cheapest`() {
        val openai = listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4o-mini")
        // coder gpt-4.1 (MID): the minis fail the capability bar and gpt-4o is pricier
        assertNull(FalconRouter.pick(FalconRouter.Stage.REVIEWER, "gpt-4.1", openai, prices))
    }

    @Test
    fun `unpriced coder or candidates are never routed`() {
        assertNull(FalconRouter.pick(FalconRouter.Stage.PLANNER, "my-private-model", listOf("claude-haiku-4-5"), prices))
        // a candidate without a price row is skipped, not guessed
        assertNull(FalconRouter.pick(FalconRouter.Stage.PLANNER, "claude-sonnet-4-5", listOf("local-free-model"), prices))
        assertNull(FalconRouter.pick(FalconRouter.Stage.PLANNER, "claude-sonnet-4-5", listOf("claude-haiku-4-5"), listOf()))
    }

    @Test
    fun `candidates are matched after vendor normalization and returned in original form`() {
        // openrouter-style ids route by family, but the original id comes back for the API call
        val openrouter = listOf("anthropic/claude-sonnet-4.5", "deepseek/deepseek-chat", "google/gemini-2.5-flash", "qwen/qwen3-coder")
        assertEquals("deepseek/deepseek-chat", FalconRouter.pick(FalconRouter.Stage.PLANNER, "claude-opus-4-1", openrouter, prices))
        // the unpriced qwen candidate is skipped, not guessed
        assertTrue(FalconRouter.pick(FalconRouter.Stage.PLANNER, "claude-opus-4-1", openrouter, prices) != "qwen/qwen3-coder")
    }

    @Test
    fun `dated model ids inherit the family price`() {
        // claude-sonnet-4-5-20250929 is matched by the longest prefix row
        val p = PriceTable.find("claude-sonnet-4-5-20250929", prices)
        assertEquals(3.0, p?.inputPerM ?: 0.0, 1e-9)
        assertEquals(FalconRouter.Tier.MID, FalconRouter.tierOf("claude-sonnet-4-5-20250929"))
    }

    // ------------------------------------------------------------ savings meter

    @Test
    fun `meter prices routed calls at their model and at the coder price`() {
        val calls = listOf(
            FalconRouter.RoutedCall("claude-haiku-4-5", Usage(input = 1_000_000, output = 200_000)),
        )
        val (actual, baseline) = FalconRouter.meter(calls, "claude-opus-4-1", prices)!!
        // actual: 1M × $1 + 0.2M × $5 = $2
        assertEquals(2.0, actual, 1e-9)
        // baseline (opus): 1M × $15 + 0.2M × $75 = $30
        assertEquals(30.0, baseline, 1e-9)
        assertEquals(0.9333, FalconRouter.savingsPct(actual, baseline)!!, 0.0005)
    }

    @Test
    fun `meter refuses to guess when any price is missing`() {
        assertNull(FalconRouter.meter(emptyList(), "claude-opus-4-1", prices))
        assertNull(FalconRouter.meter(listOf(FalconRouter.RoutedCall("claude-haiku-4-5", Usage(1, 1))), "unpriced-coder", prices))
        assertNull(FalconRouter.meter(listOf(FalconRouter.RoutedCall("unpriced-stage", Usage(1, 1))), "claude-opus-4-1", prices))
        assertNull(FalconRouter.savingsPct(1.0, 0.0))
    }

    // ------------------------------------------------------------ ledger

    @Test
    fun `savings ledger records and aggregates by day and month`() {
        val f = File(tmp.root, "usage/falcon.json")
        val led = SavingsLedger(f)
        val day = LocalDate.of(2026, 11, 25)
        led.record(2.0, 30.0, day)
        led.record(1.0, 10.0, day)
        led.record(0.5, 5.0, day.plusDays(1))
        assertEquals(3.0 to 40.0, led.day(day))
        val (actual, baseline) = led.month(day)
        assertEquals(3.5, actual, 1e-9)
        assertEquals(45.0, baseline, 1e-9)
    }

    @Test
    fun `savings ledger survives restarts and ignores junk`() {
        val f = File(tmp.root, "usage/falcon.json")
        val led = SavingsLedger(f)
        val day = LocalDate.of(2026, 11, 25)
        led.record(2.0, 30.0, day)
        led.record(-1.0, 0.0, day) // junk: negative actual / zero baseline never lands
        assertEquals(2.0 to 30.0, SavingsLedger(f).day(day))
        // corrupt file recovers as empty instead of crashing
        f.writeText("{not json")
        assertEquals(0.0 to 0.0, SavingsLedger(f).day(day))
    }
}
