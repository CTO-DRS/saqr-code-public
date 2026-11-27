package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.git.BlameKit
import com.ctodrs.saqrcode.git.BlameLine
import com.ctodrs.saqrcode.git.GitException
import com.ctodrs.saqrcode.git.GitService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Wave E: git blame on real JGit temp repos, plus the pure grouping and age formatting. */
class V0138BlameTest {
    private lateinit var tmp: File
    private lateinit var dir: File
    private val git = GitService()

    @Before fun setUp() {
        tmp = Files.createTempDirectory("saqr-blame").toFile()
        dir = File(tmp, "w").apply { mkdirs() }
        git.init(dir)
    }

    @After fun tearDown() { tmp.deleteRecursively() }

    private fun commit(msg: String, who: String) { git.stageAll(dir); git.commit(dir, msg, who, "$who@example.com") }

    @Test fun blameNamesTheAuthorOfEachLine() {
        File(dir, "a.txt").writeText("one\ntwo\nthree\n")
        commit("base", "Rashid")
        File(dir, "a.txt").writeText("one\nTWO\nthree\nfour\n")
        commit("edit", "Sara")
        val lines = git.blame(dir, "a.txt")
        assertEquals(listOf("Rashid", "Sara", "Rashid", "Sara"), lines.map { it.author })
        val groups = BlameKit.group(lines)
        assertEquals(listOf(1 to 1, 2 to 1, 3 to 1, 4 to 1), groups.map { it.start to it.count })
        assertTrue(lines.all { it.sha.length == 7 })
    }

    @Test fun untrackedFileHasNoHistory() {
        File(dir, "a.txt").writeText("x\n"); commit("base", "Rashid")
        File(dir, "new.txt").writeText("y\n")
        try { git.blame(dir, "new.txt"); fail("expected GitException") } catch (_: GitException) {}
    }

    @Test fun pathEscapesAreRefused() {
        File(dir, "a.txt").writeText("x\n"); commit("base", "Rashid")
        try { git.blame(dir, "../secret"); fail("expected GitException") } catch (_: GitException) {}
    }

    @Test fun consecutiveLinesFromOneCommitFormOneGroup() {
        val l = listOf(BlameLine("A", 1, "aaa"), BlameLine("A", 1, "aaa"), BlameLine("B", 2, "bbb"), BlameLine("A", 1, "aaa"))
        val g = BlameKit.group(l)
        assertEquals(listOf(1 to 2, 3 to 1, 4 to 1), g.map { it.start to it.count })
        assertEquals(2, g[0].end)
        assertTrue(BlameKit.group(emptyList()).isEmpty())
    }

    @Test fun ageEnglish() {
        assertEquals("just now", BlameKit.age(1000, 990, false))
        assertEquals("5 min ago", BlameKit.age(1000 + 300, 1000, false))
        assertEquals("3 h ago", BlameKit.age(10_800, 0, false))
        assertEquals("2 d ago", BlameKit.age(2 * 86_400L, 0, false))
        assertEquals("4 mo ago", BlameKit.age(4 * 30 * 86_400L, 0, false))
        assertEquals("2 y ago", BlameKit.age(2 * 365 * 86_400L, 0, false))
        assertEquals("just now", BlameKit.age(10, 500, false))
    }

    @Test fun ageArabicUsesWesternDigits() {
        assertEquals("الآن", BlameKit.age(5, 0, true))
        assertEquals("قبل 5 دقيقة", BlameKit.age(300, 0, true))
        assertEquals("قبل 3 يومًا", BlameKit.age(3 * 86_400L, 0, true))
    }

    @Test fun summaryRow() {
        val g = BlameKit.group(listOf(BlameLine("Sara", 0, "abc1234"), BlameLine("Sara", 0, "abc1234"))).single()
        assertEquals("abc1234 · Sara · 3 h ago · line 1-2", BlameKit.summary(g, 10_800, false))
        assertEquals("abc1234 · Sara · قبل 3 ساعة · السطر 1-2", BlameKit.summary(g, 10_800, true))
    }
}
