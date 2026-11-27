package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.data.Template
import com.ctodrs.saqrcode.git.BlameKit
import com.ctodrs.saqrcode.git.FileCommit
import com.ctodrs.saqrcode.git.GitException
import com.ctodrs.saqrcode.git.GitService
import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.xml.sax.InputSource
import java.io.StringReader

/** v0.1.49: per-file git history on real JGit repos, and XML-safe project names in templates. */
class V0149FileHistoryTest {
    private lateinit var tmp: File
    private lateinit var dir: File
    private val git = GitService()

    @Before fun setUp() {
        tmp = Files.createTempDirectory("saqr-hist").toFile()
        dir = File(tmp, "w").apply { mkdirs() }
        git.init(dir)
    }

    @After fun tearDown() { tmp.deleteRecursively() }

    private fun commit(msg: String, who: String) { git.stageAll(dir); git.commit(dir, msg, who, "$who@example.com") }

    @Test fun `history lists only commits that touched the file, newest first`() {
        File(dir, "a.txt").writeText("1\n"); commit("add a", "Rashid")
        File(dir, "b.txt").writeText("x\n"); commit("add b", "Sara")
        File(dir, "a.txt").writeText("2\n"); commit("change a", "Sara")
        val h = git.fileHistory(dir, "a.txt")
        assertEquals(listOf("change a", "add a"), h.map { it.subject })
        assertEquals(listOf("Sara", "Rashid"), h.map { it.author })
        assertTrue(h.all { it.sha.length == 7 })
    }

    @Test fun `limit caps the list`() {
        for (i in 1..5) { File(dir, "a.txt").writeText("$i\n"); commit("c$i", "Rashid") }
        assertEquals(listOf("c5", "c4"), git.fileHistory(dir, "a.txt", 2).map { it.subject })
    }

    @Test fun `untracked file and empty repo give an empty list`() {
        assertTrue(git.fileHistory(dir, "nothing.txt").isEmpty())
        File(dir, "a.txt").writeText("1\n"); commit("add a", "Rashid")
        File(dir, "new.txt").writeText("y\n")
        assertTrue(git.fileHistory(dir, "new.txt").isEmpty())
    }

    @Test fun `path escapes are refused`() {
        try { git.fileHistory(dir, "../x"); fail("expected GitException") } catch (_: GitException) {}
    }

    @Test fun `history row shows sha author age and subject`() {
        val c = FileCommit("a1b2c3d", "Sara", 1000, "Fix login")
        assertEquals("a1b2c3d · Sara · 2 h ago · Fix login", BlameKit.historyRow(c, 1000 + 7200, false))
        assertTrue(BlameKit.historyRow(c, 1000 + 7200, true).contains("قبل 2 ساعة"))
    }

    @Test fun `odd project names still give well-formed xml and html`() {
        listOf("R&D <x>", "Bob's App", "a \"quoted\" name").forEach { n ->
            Template.entries.forEach { t ->
                t.files(n).filter { it.key.endsWith(".xml") }.forEach { (path, text) ->
                    try {
                        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(text)))
                    } catch (e: Exception) { fail("$t/$n $path is not well-formed: ${e.message}") }
                }
            }
        }
    }
}
