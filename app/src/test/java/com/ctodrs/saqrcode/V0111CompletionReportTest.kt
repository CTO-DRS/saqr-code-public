package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.NotifText
import com.ctodrs.saqrcode.agent.RunOutcome
import com.ctodrs.saqrcode.agent.RunStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.10: the completion report of a background run (pure; the notification only renders it). */
class V0111CompletionReportTest {

    @Test fun `english report line, all parts`() {
        val s = RunStats(activeMs = 192_000, steps = 14, files = 3, commands = 2, tokens = 12_400, cost = 0.031)
        assertEquals("‎3:12‎ · 14 steps · 3 files · 2 commands · 12.4k tokens · ‎$0.03‎", NotifText.report(s, "en"))
    }

    @Test fun `empty parts drop out, unknown price omitted, singulars`() {
        val s = RunStats(activeMs = 65_000, steps = 1, files = 1, commands = 1, tokens = 812, cost = null)
        assertEquals("‎1:05‎ · 1 step · 1 file · 1 command · 812 tokens", NotifText.report(s, "en"))
        // nothing but the clock
        assertEquals("‎0:07‎", NotifText.report(RunStats(activeMs = 7_000), "en"))
        // tiny non-zero cost
        assertTrue(NotifText.report(RunStats(activeMs = 1_000, cost = 0.004), "en").endsWith("‎<$0.01‎"))
    }

    @Test fun `arabic counts one two few many`() {
        assertEquals("خطوة", NotifText.arCount(1, "خطوة", "خطوتان", "خطوات"))
        assertEquals("خطوتان", NotifText.arCount(2, "خطوة", "خطوتان", "خطوات"))
        assertEquals("5 خطوات", NotifText.arCount(5, "خطوة", "خطوتان", "خطوات"))
        assertEquals("14 خطوة", NotifText.arCount(14, "خطوة", "خطوتان", "خطوات"))
        val s = RunStats(activeMs = 192_000, steps = 14, files = 3, commands = 2, tokens = 12_400, cost = 0.03)
        assertEquals("‎3:12‎ · 14 خطوة · 3 ملفات · أمران · 12.4 ألف رمز · ‎$0.03‎", NotifText.report(s, "ar"))
    }

    @Test fun `arabic token words and estimated marker`() {
        assertEquals("812 رمز", NotifText.arTokens(812))
        assertEquals("رمزان", NotifText.arTokens(2))
        assertEquals("12.4 ألف رمز", NotifText.arTokens(12_400))
        assertEquals("2.3 مليون رمز", NotifText.arTokens(2_300_000))
        assertTrue(NotifText.report(RunStats(activeMs = 1_000, tokens = 12_400, estimatedTokens = true), "en").contains("~12.4k tokens"))
    }

    @Test fun `finished body gains the report on its own line`() {
        val o = RunOutcome(RunOutcome.Kind.DONE, "All done.").withStats(RunStats(activeMs = 61_000, steps = 4, tokens = 900))
        val body = NotifText.finishedBody("demo", o, "en")
        assertTrue(body.startsWith("demo · All done.\n"))
        assertTrue(body.contains("4 steps"))
        // without stats the body stays one line, as before
        assertFalse(NotifText.finishedBody("demo", RunOutcome(RunOutcome.Kind.DONE, "All done."), "en").contains("\n"))
    }

    @Test fun `long summary still truncates, report survives`() {
        val o = RunOutcome(RunOutcome.Kind.DONE, "x".repeat(400)).withStats(RunStats(activeMs = 1_000, files = 2))
        val body = NotifText.finishedBody("demo", o, "en")
        val lines = body.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].length <= "demo · ".length + 160)
        assertEquals("‎0:01‎ · 2 files", lines[1])
    }
}
