package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ProjectIndex
import com.ctodrs.saqrcode.agent.Tools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** v0.13 «عقل الصقر» · project index (فهرس المشروع): symbol extraction per language, Arabic-aware
 *  search, ranking, ignore rules, rendering, and the <200ms acceptance on a 500-file project. */
class V0131FeaturesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun index(): ProjectIndex = ProjectIndex(tmp.root)

    // ------------------------------------------------------------ extraction

    @Test
    fun `kotlin symbols are extracted with kinds and line numbers`() {
        File(tmp.root, "Main.kt").writeText(
            """
            package demo

            enum class Mode { FAST, SLOW }
            sealed interface State
            data class User(val name: String)
            object Registry {
                suspend fun fetch(id: String): User {
                    return User("x")
                }
                private fun helper() {}
            }
            class Repo(private val r: Registry)
            """.trimIndent()
        )
        val ix = index(); ix.rebuild()
        val hits = ix.search("Registry").map { "${it.kind.id}:${it.name}:${it.line}" }
        assertTrue("object Registry found", "object:Registry:6" in hits)
        val user = ix.search("User").first { it.kind == ProjectIndex.Kind.CLASS }
        assertEquals(5, user.line)
        val fetch = ix.search("fetch").first { it.kind == ProjectIndex.Kind.FUNCTION }
        assertEquals(7, fetch.line)
        assertTrue(ix.search("Mode").any { it.kind == ProjectIndex.Kind.ENUM && it.name == "Mode" })
        assertTrue(ix.search("State").any { it.kind == ProjectIndex.Kind.INTERFACE })
        assertTrue(ix.search("Repo").any { it.kind == ProjectIndex.Kind.CLASS })
        assertTrue(ix.search("helper").any { it.kind == ProjectIndex.Kind.FUNCTION })
    }

    @Test
    fun `python and javascript symbols are extracted`() {
        File(tmp.root, "server.py").writeText(
            """
            class الشبكة:
                def connect(self):
                    pass

                async def stream(self):
                    pass
            """.trimIndent()
        )
        File(tmp.root, "app.js").writeText(
            """
            export default class App {}
            function fetchData() {}
            const handler = async (req) => {}
            """.trimIndent()
        )
        val ix = index(); ix.rebuild()
        assertTrue(ix.search("الشبكه").any { it.kind == ProjectIndex.Kind.CLASS }) // ة → ه via normalization
        assertTrue(ix.search("connect").any { it.kind == ProjectIndex.Kind.FUNCTION })
        assertTrue(ix.search("stream").any { it.kind == ProjectIndex.Kind.FUNCTION })
        assertTrue(ix.search("App").any { it.kind == ProjectIndex.Kind.CLASS })
        assertTrue(ix.search("fetchData").any { it.kind == ProjectIndex.Kind.FUNCTION })
        assertTrue(ix.search("handler").any { it.kind == ProjectIndex.Kind.FUNCTION }) // const arrow fn
    }

    // ------------------------------------------------------------ arabic-aware search

    @Test
    fun `arabic file paths match across spelling variants`() {
        val dir = File(tmp.root, "lib").apply { mkdirs() }
        File(dir, "الملفات.kt").writeText("class الملفات\n")
        File(dir, "الشبكة.kt").writeText("class الشبكة\n")
        val ix = index(); ix.rebuild()
        val hits = ix.search("الملفات") // exact spelling
        assertTrue(hits.isNotEmpty())
        // hamza form: «ألملفات» collapses to «الملفات» (أ → ا)
        assertTrue(ix.search("ألملفات").isNotEmpty())
        // taa marbouta + shadda variants: «الشّبكه» finds «الشبكة» (ة → ه, diacritics stripped)
        val normalized = ix.search("الشّبكه")
        assertTrue(normalized.isNotEmpty())
        assertEquals("الشبكة.kt", normalized.first().name)
    }

    // ------------------------------------------------------------ ranking

    @Test
    fun `exact name ranks first, then prefix`() {
        File(tmp.root, "a.kt").writeText("class parse\nfun parseUrl() {}\n")
        File(tmp.root, "deep/nested/b.kt").apply { parentFile.mkdirs() }.writeText("fun parseable() {}\n")
        val ix = index(); ix.rebuild()
        assertEquals("parse", ix.search("parse").first().name) // exact beats prefix
        val pref = ix.search("parseUrl")
        assertEquals("parseUrl", pref.first().name)
        // prefix beats plain substring across files, shorter path first
        val p = ix.search("pars")
        assertTrue(p.indexOfFirst { it.name == "parse" } < p.indexOfFirst { it.name == "parseable" })
    }

    // ------------------------------------------------------------ ignore rules & rendering

    @Test
    fun `ignored dirs and oversize files are skipped`() {
        File(tmp.root, ".git/HEAD").apply { parentFile.mkdirs() }.writeText("class Ghost1 {}\n")
        File(tmp.root, "build/out.kt").apply { parentFile.mkdirs() }.writeText("class Ghost2 {}\n")
        File(tmp.root, "node_modules/pkg/index.js").apply { parentFile.mkdirs() }.writeText("class Ghost3 {}\n")
        File(tmp.root, ".saqr/memory.json").apply { parentFile.mkdirs() }.writeText("{}")
        val big = "class Big {\n}" + "// pad\n".repeat(80_000) // > 512KB of comments
        assertTrue(big.length > 512 * 1024)
        File(tmp.root, "Big.kt").writeText(big)
        File(tmp.root, "README.md").writeText("# مشروع\n")
        val ix = index(); ix.rebuild()
        assertNull(ix.search("Ghost1").firstOrNull())
        assertNull(ix.search("Ghost2").firstOrNull())
        assertNull(ix.search("Ghost3").firstOrNull())
        assertNull(ix.search("Big").firstOrNull()) // oversize source skipped
        assertTrue(ix.search("README").isNotEmpty()) // text files still indexed as FILE entries
    }

    @Test
    fun `render is a compact file-line kind list with a footer`() {
        File(tmp.root, "Main.kt").writeText("fun main() {}\n")
        val ix = index(); ix.rebuild()
        val out = ix.render("main")
        assertTrue("Main.kt:1" in out)
        assertTrue("· fun · main" in out)
        assertTrue(out.endsWith(")"))
        assertTrue(ix.render("لا_شيء_موجود").startsWith("No match"))
        assertTrue(ix.render("").isEmpty() || ix.search("").isEmpty())
    }

    @Test
    fun `project_search tool is exposed to the model with a required query`() {
        val spec = Tools.specs.first { it.name == Tools.PROJECT_SEARCH }
        val required = spec.parameters.getJSONArray("required")
        assertEquals("query", required.getString(0))
        assertTrue(spec.description.contains("symbol index"))
    }

    // ------------------------------------------------------------ acceptance: <200ms on 500 files

    @Test
    fun `first result under 200ms on a 500-file project`() {
        // 500 Kotlin files, 6 symbols each (~3000 symbols) + one Arabic-named file
        val dir = File(tmp.root, "src").apply { mkdirs() }
        val body = StringBuilder()
        for (i in 1..500) {
            body.setLength(0)
            body.append("package gen\n")
            body.append("class Cls$i\n")
            body.append("object Reg$i\n")
            repeat(4) { j -> body.append("fun sym${i}_$j() {}\n") }
            File(dir, "F$i.kt").writeText(body.toString())
        }
        File(dir, "الشبكة.kt").writeText("class الشبكة\n")

        val ix = index()
        // index generation happens at project open (background) — its cost is a field note, not the gate
        val tBuild = System.currentTimeMillis()
        ix.rebuild()
        println("[field-note] 500-file index build: ${System.currentTimeMillis() - tBuild}ms, ${ix.size} symbols")
        assertTrue(ix.size > 3000)
        // the acceptance: the first search result lands under 200ms
        val t0 = System.currentTimeMillis()
        val hits = ix.search("sym250_2")
        val elapsed = System.currentTimeMillis() - t0
        assertTrue("first result must be <200ms, was ${elapsed}ms", elapsed < 200)
        assertEquals("sym250_2", hits.first().name)
        assertEquals(ProjectIndex.Kind.FUNCTION, hits.first().kind)
        // warm Arabic query on the same index
        val t1 = System.currentTimeMillis()
        assertTrue(ix.search("الشبكه").isNotEmpty())
        assertTrue(System.currentTimeMillis() - t1 < 200)
    }
}
