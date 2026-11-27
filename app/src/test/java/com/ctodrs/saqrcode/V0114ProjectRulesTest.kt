package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ProjectRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** v0.1.13: root + nested AGENTS.md collection for the system prompt. */
class V0114ProjectRulesTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun write(dir: File, name: String, text: String) {
        dir.mkdirs()
        File(dir, name).writeText(text)
    }

    @Test
    fun `root rules always apply`() {
        val root = tmp.newFolder("p")
        write(root, "AGENTS.md", "# Rules\n- small files")
        val out = ProjectRules.collect(root, emptyList())
        assertTrue(out.contains("small files"))
        assertFalse(out.contains("—"))  // the root file carries no directory label
    }

    @Test
    fun `nested rules apply when their files were touched`() {
        val root = tmp.newFolder("p")
        write(root, "AGENTS.md", "root rules")
        write(File(root, "app/src"), "AGENTS.md", "src rules")
        write(File(root, "docs"), "AGENTS.md", "docs rules")
        val out = ProjectRules.collect(root, listOf("app/src/Main.kt"))
        assertTrue(out.contains("root rules"))
        assertTrue(out.contains("src rules"))
        assertTrue(out.contains("app/src/AGENTS.md"))   // nested files are labeled
        assertFalse(out.contains("docs rules"))          // untouched dirs stay out
    }

    @Test
    fun `ancestors of a touched file apply from root down`() {
        val root = tmp.newFolder("p")
        write(File(root, "a"), "AGENTS.md", "a rules")
        write(File(root, "a/b"), "AGENTS.md", "b rules")
        val out = ProjectRules.collect(root, listOf("a/b/c/deep.kt"))
        assertTrue(out.contains("a rules"))
        assertTrue(out.contains("b rules"))
    }

    @Test
    fun `missing and empty files are skipped`() {
        val root = tmp.newFolder("p")
        write(File(root, "x"), "AGENTS.md", "   ")
        assertEquals("", ProjectRules.collect(root, listOf("x/f.kt", "y/g.kt")))
    }

    @Test
    fun `total budget caps the collection`() {
        val root = tmp.newFolder("p")
        write(root, "AGENTS.md", "x".repeat(ProjectRules.PER_FILE_CAP))
        write(File(root, "d"), "AGENTS.md", "y".repeat(ProjectRules.PER_FILE_CAP))
        val out = ProjectRules.collect(root, listOf("d/f.kt"))
        assertTrue(out.length <= ProjectRules.TOTAL_CAP)
    }

    @Test
    fun `paths outside the project are ignored`() {
        val root = tmp.newFolder("p")
        write(root, "AGENTS.md", "root rules")
        val out = ProjectRules.collect(root, listOf("../escape.kt", "/abs/path.kt"))
        assertTrue(out.contains("root rules"))
    }
}
