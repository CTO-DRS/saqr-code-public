package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.git.GitException
import com.ctodrs.saqrcode.git.GitService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** v0.1.28: stash and apply on real JGit temp repos. */
class V0128GitStashTest {
    private lateinit var tmp: File
    private lateinit var dir: File
    private val git = GitService()

    @Before fun setUp() {
        tmp = Files.createTempDirectory("saqr-stash").toFile()
        dir = File(tmp, "w").apply { mkdirs() }
        git.init(dir)
        File(dir, "a.txt").writeText("one\n")
        git.stageAll(dir)
        git.commit(dir, "first", "Rashid", "r@example.com")
    }

    @After fun tearDown() { tmp.deleteRecursively() }

    @Test fun stashShelvesTrackedAndUntrackedThenPopRestores() {
        File(dir, "a.txt").writeText("two\n")
        File(dir, "new.txt").writeText("fresh\n")
        val id = git.stash(dir, "Rashid", "r@example.com")
        assertNotNull(id)
        assertEquals(1, git.stashCount(dir))
        assertEquals("one\n", File(dir, "a.txt").readText())
        assertFalse(File(dir, "new.txt").exists())
        val conflicts = git.stashPop(dir)
        assertTrue(conflicts.isEmpty())
        assertEquals(0, git.stashCount(dir))
        assertEquals("two\n", File(dir, "a.txt").readText())
        assertEquals("fresh\n", File(dir, "new.txt").readText())
    }

    @Test fun stashWithNothingChangedReturnsNull() {
        assertNull(git.stash(dir, "Rashid", "r@example.com"))
        assertEquals(0, git.stashCount(dir))
    }

    @Test fun popWithoutStashFailsClearly() {
        try { git.stashPop(dir); throw AssertionError("expected failure") }
        catch (e: GitException) { assertTrue(e.message!!.contains("No stash")) }
    }

    @Test fun stashBeforeFirstCommitIsRefused() {
        val fresh = File(tmp, "empty").apply { mkdirs() }
        git.init(fresh)
        try { git.stash(fresh, "R", "r@example.com"); throw AssertionError("expected failure") }
        catch (e: GitException) { assertTrue(e.message!!.contains("Commit once")) }
    }
}
