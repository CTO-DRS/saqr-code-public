package com.ctodrs.saqrcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v0.19.3 guard: icon-only click targets must carry a semantic Role, and EN/AR string keys stay in lockstep. */
class V0193A11yTest {
    private val root = File("src/main").takeIf { it.exists() } ?: File("app/src/main")
    private fun keys(f: String) = Regex("<string name=\"([^\"]+)\"").findAll(File(root, f).readText()).map { it.groupValues[1] }.toSet()

    @Test fun `arabic and english keys match and the rating label exists in both`() {
        val en = keys("res/values/strings.xml"); val ar = keys("res/values-ar/strings.xml")
        assertEquals(emptySet<String>(), en - ar); assertEquals(emptySet<String>(), ar - en)
        assertTrue("skills_rate_n" in en)
    }

    @Test fun `circle and rounded icon-only clickables declare a role`() {
        val offenders = File(root, "java/com/ctodrs/saqrcode/ui").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l ->
                val iconBox = l.contains("clip(CircleShape).") || l.contains("clip(RoundedCornerShape(50))")
                if (iconBox && l.contains(".clickable") && !l.contains("role =") && !l.contains("semantics")) "${f.name}:${i + 1}" else null
            }
        }.toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
