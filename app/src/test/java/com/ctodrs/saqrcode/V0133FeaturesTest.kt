package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.CoachProgressCodec
import com.ctodrs.saqrcode.agent.CoachSnapshot
import com.ctodrs.saqrcode.agent.SaqrCoach
import com.ctodrs.saqrcode.agent.StarterPrompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.13 «عقل الصقر» · Saqr Coach (مدرّب صقر): the five-step path to a first successful
 *  build, driven by real signals only — plus the progress ledger and the Arabic starter prompts. */
class V0133FeaturesTest {

    private val zero = CoachSnapshot()

    // ------------------------------------------------------------ the path

    @Test
    fun `empty snapshot leaves every step pending and focuses the key step`() {
        val s = SaqrCoach.steps(zero)
        assertEquals(5, s.size)
        assertTrue(s.none { it.done })
        assertEquals(SaqrCoach.STEP_KEY, SaqrCoach.current(zero))
        assertEquals(0, SaqrCoach.progress(zero))
        assertFalse(SaqrCoach.complete(zero))
    }

    @Test
    fun `key then project then message advance the focus in order`() {
        val one = zero.copy(hasKey = true)
        assertEquals(SaqrCoach.STEP_PROJECT, SaqrCoach.current(one))
        assertEquals(1, SaqrCoach.progress(one))
        val two = one.copy(hasProject = true)
        assertEquals(SaqrCoach.STEP_MESSAGE, SaqrCoach.current(two))
        val three = two.copy(hasFirstMessage = true)
        assertEquals(SaqrCoach.STEP_BUILD, SaqrCoach.current(three))
        assertEquals(3, SaqrCoach.progress(three))
        assertFalse(SaqrCoach.complete(three))
    }

    @Test
    fun `a failed attempt completes the build step but not the path`() {
        val snap = CoachSnapshot(hasKey = true, hasProject = true, hasFirstMessage = true, hasBuildAttempt = true)
        assertTrue(SaqrCoach.steps(snap).first { it.id == SaqrCoach.STEP_BUILD }.done)
        assertEquals(SaqrCoach.STEP_FIRST_BUILD, SaqrCoach.current(snap))
        assertEquals(4, SaqrCoach.progress(snap))
        assertFalse(SaqrCoach.complete(snap))
    }

    @Test
    fun `first successful build completes the path`() {
        val snap = CoachSnapshot(
            hasKey = true, hasProject = true, hasFirstMessage = true,
            hasBuildAttempt = true, hasSuccessfulBuild = true,
        )
        assertNull(SaqrCoach.current(snap))
        assertEquals(5, SaqrCoach.progress(snap))
        assertTrue(SaqrCoach.complete(snap))
        // success implies an attempt — the build step cannot lag behind the final one
        assertTrue(SaqrCoach.steps(snap).first { it.id == SaqrCoach.STEP_BUILD }.done)
    }

    @Test
    fun `the path never skips ahead even when later signals exist`() {
        // a build attempt recorded before a key: the coach still focuses the key first
        val snap = zero.copy(hasBuildAttempt = true, hasSuccessfulBuild = true)
        assertEquals(SaqrCoach.STEP_KEY, SaqrCoach.current(snap))
        // message exists but the key and project do not: focus stays on the key step,
        // and with the key linked it stays on the project — never the message step
        assertEquals(SaqrCoach.STEP_KEY, SaqrCoach.current(zero.copy(hasFirstMessage = true)))
        assertEquals(SaqrCoach.STEP_PROJECT, SaqrCoach.current(zero.copy(hasKey = true, hasFirstMessage = true)))
    }

    // ------------------------------------------------------------ visibility & ledger

    @Test
    fun `card visibility follows dismissal only`() {
        assertTrue(SaqrCoach.visible(dismissed = false))
        assertFalse(SaqrCoach.visible(dismissed = true))
        // dismissal hides the card even when the path is complete
        assertTrue(SaqrCoach.complete(CoachSnapshot(hasSuccessfulBuild = true)))
        assertFalse(SaqrCoach.visible(dismissed = true))
    }

    @Test
    fun `progress codec round-trips and survives garbage`() {
        val p = mapOf(SaqrCoach.STEP_KEY to 1000L, SaqrCoach.STEP_PROJECT to 2000L)
        assertEquals(p, CoachProgressCodec.decode(CoachProgressCodec.encode(p)))
        assertTrue(CoachProgressCodec.decode(null).isEmpty())
        assertTrue(CoachProgressCodec.decode("not json").isEmpty())
        assertTrue(CoachProgressCodec.decode("{\"key\":0}").isEmpty()) // zero timestamps are not real completions
    }

    @Test
    fun `merge stamps only new completions and keeps the first times`() {
        val cur = mapOf(SaqrCoach.STEP_KEY to 111L)
        val merged = CoachProgressCodec.merge(cur, listOf(SaqrCoach.STEP_KEY, SaqrCoach.STEP_PROJECT), 999L)
        assertEquals(111L, merged[SaqrCoach.STEP_KEY]) // first-recorded time wins
        assertEquals(999L, merged[SaqrCoach.STEP_PROJECT])
        assertEquals(2, merged.size)
    }

    // ------------------------------------------------------------ starter prompts

    @Test
    fun `every template family has Arabic starter prompts`() {
        for (family in listOf("WEB", "PYTHON", "NODE", "COMPOSE", "IME", "LITE_WEB", "EMPTY")) {
            val prompts = StarterPrompts.byTemplate[family]!!
            assertTrue(family, prompts.isNotEmpty())
            prompts.forEach { p ->
                assertTrue(family, p.isNotBlank())
                // Arabic-first: each template prompt carries Arabic letters
                assertTrue(family, p.any { it in '\u0600'..'\u06FF' })
            }
        }
        assertTrue(StarterPrompts.universal.isNotEmpty())
        // universal prompts must not duplicate a template one
        StarterPrompts.byTemplate.values.forEach { list -> assertTrue(list.distinct().size == list.size) }
    }

    @Test
    fun `forTemplate mixes family and universal prompts, deduped and capped`() {
        val prompts = StarterPrompts.forTemplate("COMPOSE", limit = 4)
        assertEquals(4, prompts.size)
        assertTrue(prompts.distinct().size == prompts.size)
        // family examples come first so the user sees the relevant ones
        assertEquals(StarterPrompts.byTemplate["COMPOSE"]!![0], prompts[0])
        // the limit is respected at 1
        assertEquals(1, StarterPrompts.forTemplate("WEB", limit = 1).size)
        // unknown family falls back to universal prompts only
        val fallback = StarterPrompts.forTemplate("NOPE", limit = 3)
        assertEquals(3, fallback.size)
        assertEquals(StarterPrompts.universal[0], fallback[0])
    }

    // ------------------------------------------------------------ template guessing

    @Test
    fun `guessTemplate maps marker files to prompt families`() {
        assertEquals("COMPOSE", SaqrCoach.guessTemplate(listOf("MainActivity.kt", "res/values/strings.xml")))
        assertEquals("IME", SaqrCoach.guessTemplate(listOf("ArabicImeService.kt")))
        assertEquals("PYTHON", SaqrCoach.guessTemplate(listOf("main.py", "README.md")))
        assertEquals("NODE", SaqrCoach.guessTemplate(listOf("index.js", "style.css")))
        assertEquals("WEB", SaqrCoach.guessTemplate(listOf("index.html")))
        assertEquals("WEB", SaqrCoach.guessTemplate(emptyList()))
    }
}
