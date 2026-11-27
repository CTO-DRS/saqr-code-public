package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.git.GitException
import com.ctodrs.saqrcode.git.GitService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** v0.1.29: cherry-pick the tip of another branch, on real JGit temp repos. */
class V0129GitCherryPickTest {
    private lateinit var tmp: File
    private lateinit var dir: File
    private val git = GitService()
    private val me = "Rashid" to "r@example.com"

    @Before fun setUp() {
        tmp = Files.createTempDirectory("saqr-cp").toFile()
        dir = File(tmp, "w").apply { mkdirs() }
        git.init(dir)
        File(dir, "a.txt").writeText("one\n")
        commit("base")
    }

    @After fun tearDown() { tmp.deleteRecursively() }

    private fun commit(msg: String) { git.stageAll(dir); git.commit(dir, msg, me.first, me.second) }

    @Test fun picksTipOfOtherBranchOntoCurrent() {
        git.createBranch(dir, "feature")
        File(dir, "b.txt").writeText("feature work\n")
        commit("add b")
        git.checkout(dir, "main")
        assertFalse(File(dir, "b.txt").exists())
        val id = git.cherryPick(dir, "feature", me.first, me.second)
        assertEquals(7, id.length)
        assertEquals("feature work\n", File(dir, "b.txt").readText())
        assertEquals("add b", git.log(dir, 1).first().message)
    }

    @Test fun conflictRollsBackAndNamesTheFile() {
        git.createBranch(dir, "feature")
        File(dir, "a.txt").writeText("feature\n")
        commit("feature edit")
        git.checkout(dir, "main")
        File(dir, "a.txt").writeText("main\n")
        commit("main edit")
        try { git.cherryPick(dir, "feature", me.first, me.second); throw AssertionError("expected conflict") }
        catch (e: GitException) { assertTrue(e.message!!, e.message!!.contains("a.txt")) }
        assertEquals("main\n", File(dir, "a.txt").readText())
        assertTrue(git.status(dir).conflicts.isEmpty())
    }

    @Test fun unknownRefIsRefused() {
        try { git.cherryPick(dir, "nope", me.first, me.second); throw AssertionError("expected failure") }
        catch (e: GitException) { assertTrue(e.message!!.contains("nope")) }
    }
}
