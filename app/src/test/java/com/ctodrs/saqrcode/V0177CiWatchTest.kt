package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.watch.CiWatch
import org.junit.Assert.*
import org.junit.Test

class V0177CiWatchTest {
    private fun run(id: Long, concl: String, done: Boolean = true) = CiWatch.Run(id, done, concl, "main", "abc1234")

    @Test fun repoValidation() {
        assertTrue(CiWatch.validRepo("CTO-DRS/saqr-code-public"))
        assertFalse(CiWatch.validRepo("no-slash"))
        assertFalse(CiWatch.validRepo("a/b/c"))
        assertFalse(CiWatch.validRepo("a/b; rm"))
    }

    @Test fun parsesLatestRun() {
        val r = CiWatch.parseLatest("""{"workflow_runs":[{"id":5,"status":"completed","conclusion":"failure","head_branch":"main","head_sha":"abcdef123456"}]}""")!!
        assertEquals(5L, r.id); assertTrue(r.completed); assertEquals("abcdef1", r.sha)
        assertNull(CiWatch.parseLatest("""{"workflow_runs":[]}"""))
        assertNull(CiWatch.parseLatest("nope"))
    }

    @Test fun quietOnFirstPassAndRepeats() {
        val (m, s) = CiWatch.decide("o/r", "", run(1, "success"), false)
        assertNull(m); assertEquals("1:ok", s)
        assertNull(CiWatch.decide("o/r", s, run(1, "success"), false).first)
    }

    @Test fun speaksOnFailureThenFixed() {
        val (m, s) = CiWatch.decide("o/r", "1:ok", run(2, "failure"), false)
        assertEquals("CI failed", m!!.title); assertEquals("2:fail", s)
        assertNull(CiWatch.decide("o/r", s, run(2, "failure"), false).first)
        val (m2, s2) = CiWatch.decide("o/r", s, run(3, "success"), true)
        assertEquals("عاد البناء للنجاح", m2!!.title); assertEquals("3:ok", s2)
    }

    @Test fun ignoresRunsStillInProgress() {
        val (m, s) = CiWatch.decide("o/r", "1:ok", run(2, "", false), false)
        assertNull(m); assertEquals("1:ok", s)
    }
}
