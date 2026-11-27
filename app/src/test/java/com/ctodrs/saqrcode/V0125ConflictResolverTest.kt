package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.git.ConflictResolver
import com.ctodrs.saqrcode.git.ConflictResolver.Choice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.22: conflict markers become hunks the user resolves one by one. */
class V0125ConflictResolverTest {
    private val two = """
        a
        <<<<<<< HEAD
        mine1
        =======
        theirs1
        >>>>>>> feature
        b
        <<<<<<< HEAD
        mine2
        mine2b
        =======
        theirs2
        >>>>>>> feature
        c
    """.trimIndent()

    @Test fun `parses hunks with labels`() {
        val seg = ConflictResolver.parse(two)
        val h = ConflictResolver.hunks(seg)
        assertEquals(2, h.size)
        assertEquals(listOf("mine1"), h[0].ours); assertEquals(listOf("theirs1"), h[0].theirs)
        assertEquals("HEAD", h[0].oursLabel); assertEquals("feature", h[0].theirsLabel)
        assertEquals(listOf("mine2", "mine2b"), h[1].ours)
    }

    @Test fun `choices apply per hunk`() {
        val seg = ConflictResolver.parse(two)
        assertEquals("a\nmine1\nb\ntheirs2\nc", ConflictResolver.resolve(seg, listOf(Choice.OURS, Choice.THEIRS)))
        assertEquals("a\nmine1\ntheirs1\nb\nmine2\nmine2b\ntheirs2\nc", ConflictResolver.resolve(seg, listOf(Choice.BOTH, Choice.BOTH)))
        assertEquals("a\ntheirs1\nmine1\nb\ntheirs2\nmine2\nmine2b\nc", ConflictResolver.resolve(seg, listOf(Choice.BOTH_THEIRS_FIRST, Choice.BOTH_THEIRS_FIRST)))
    }

    @Test fun `missing choice yields null and resolveAll fills every hunk`() {
        val seg = ConflictResolver.parse(two)
        assertNull(ConflictResolver.resolve(seg, listOf(Choice.OURS, null)))
        assertNull(ConflictResolver.resolve(seg, listOf(Choice.OURS)))
        assertEquals("a\ntheirs1\nb\ntheirs2\nc", ConflictResolver.resolveAll(two, Choice.THEIRS))
    }

    @Test fun `diff3 base section is dropped`() {
        val t = "<<<<<<< HEAD\nmine\n||||||| base\norig\n=======\ntheirs\n>>>>>>> other"
        val h = ConflictResolver.hunks(ConflictResolver.parse(t)).single()
        assertEquals(listOf("mine"), h.ours); assertEquals(listOf("theirs"), h.theirs)
    }

    @Test fun `unterminated markers and clean text are left alone`() {
        val broken = "x\n<<<<<<< HEAD\nmine\n=======\ntheirs"
        assertFalse(ConflictResolver.hasConflicts(broken))
        assertEquals(broken, ConflictResolver.resolve(ConflictResolver.parse(broken), emptyList()))
        assertFalse(ConflictResolver.hasConflicts("plain\nfile"))
    }

    @Test fun `equals signs inside code are not separators and trailing newline survives`() {
        val t = "<<<<<<< HEAD\nval a = 1\n=======\nval a = 2\n>>>>>>> x\n"
        assertEquals("val a = 2\n", ConflictResolver.resolveAll(t, Choice.THEIRS))
        assertTrue(ConflictResolver.hasConflicts(t))
    }
}
