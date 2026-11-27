package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.data.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.48: every project template scaffolds a complete, placeholder-free starter. */
class V0148TemplateIntegrityTest {
    private val names = listOf("Demo", "My Notes", "تطبيقي", "9lives")

    private val entry = mapOf(
        Template.WEB to "index.html", Template.PYTHON to "main.py", Template.NODE to "index.js",
        Template.COMPOSE to "MainActivity.kt", Template.IME to "ArabicImeService.kt", Template.LITE_WEB to "MainActivity.kt",
    )

    @Test fun `every template has a readme and agent rules`() {
        Template.entries.forEach { t ->
            val f = t.files("Demo")
            assertTrue("$t README", f.containsKey("README.md"))
            assertTrue("$t AGENTS", f.containsKey("AGENTS.md"))
        }
    }

    @Test fun `no file is empty and no placeholder is left, for any project name`() {
        Template.entries.forEach { t ->
            names.forEach { n ->
                t.files(n).forEach { (path, text) ->
                    assertTrue("$t/$n $path is empty", text.isNotBlank())
                    assertTrue("$t/$n $path has @@", !text.contains("@@"))
                    assertTrue("$t/$n $path has {{", !text.contains("{{"))
                    assertTrue("$t/$n $path has TODO_REPLACE", !text.contains("TODO_REPLACE"))
                }
            }
        }
    }

    @Test fun `each template has its entry file`() {
        entry.forEach { (t, file) ->
            assertTrue("$t entry $file", t.files("Demo").keys.any { it.endsWith(file) })
        }
    }

    @Test fun `file paths are relative and safe`() {
        Template.entries.forEach { t ->
            t.files("Demo").keys.forEach { p ->
                assertTrue("$t $p", !p.startsWith("/") && !p.contains("..") && !p.contains("\\"))
            }
        }
    }

    @Test fun `android templates carry a manifest and arabic strings`() {
        listOf(Template.COMPOSE, Template.IME).forEach { t ->
            val f = t.files("Demo")
            assertTrue("$t manifest", f.containsKey("app/src/main/AndroidManifest.xml"))
            assertTrue("$t gradle", f.containsKey("app/build.gradle.kts") && f.containsKey("settings.gradle.kts"))
            assertTrue("$t ar strings", f.containsKey("app/src/main/res/values-ar/strings.xml"))
        }
        assertTrue(Template.LITE_WEB.files("Demo").containsKey("app/src/main/AndroidManifest.xml"))
    }

    @Test fun `package names are valid identifiers for odd project names`() {
        names.forEach { n ->
            val pkg = com.ctodrs.saqrcode.data.TemplateFiles.pkg(n)
            assertTrue("$n -> $pkg", Regex("[a-z][a-z0-9]*").matches(pkg))
        }
        assertEquals("app9lives", com.ctodrs.saqrcode.data.TemplateFiles.pkg("9lives"))
    }
}
