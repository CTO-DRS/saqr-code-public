package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.ui.previewCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** v0.1.16: preview entry-point discovery and ordering. */
class V0117LivePreviewTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun touch(rel: String): File = File(tmp.root, rel).apply { parentFile?.mkdirs(); writeText("<html></html>") }

    @Test
    fun `index html at root wins over deeper files`() {
        touch("docs/page.html")
        touch("index.html")
        val c = previewCandidates(tmp.root)
        assertEquals("index.html", c.first().name)
        assertEquals(2, c.size)
    }

    @Test
    fun `without index the shallowest file wins, then name`() {
        touch("z/deep.html")
        touch("b.html")
        touch("a.html")
        val c = previewCandidates(tmp.root).map { it.name }
        assertEquals(listOf("a.html", "b.html", "deep.html"), c)
    }

    @Test
    fun `node_modules and git are skipped, htm counts`() {
        touch("node_modules/pkg/demo.html")
        touch(".git/hooks/sample.html")
        touch("page.htm")
        val c = previewCandidates(tmp.root)
        assertEquals(1, c.size)
        assertEquals("page.htm", c.first().name)
    }

    @Test
    fun `empty project has no candidates`() {
        assertTrue(previewCandidates(tmp.root).isEmpty())
    }

    @Test
    fun `non-html files are ignored`() {
        touch("readme.md")
        touch("style.css")
        assertTrue(previewCandidates(tmp.root).isEmpty())
    }
}
