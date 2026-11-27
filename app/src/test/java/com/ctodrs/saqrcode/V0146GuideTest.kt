package com.ctodrs.saqrcode

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.46: every user-guide topic exists in English and Arabic, and the Arabic text is really Arabic. */
class V0146GuideTest {
    private fun strings(path: String): Map<String, String> {
        val f = listOf(File(path), File("app/$path")).first { it.exists() }
        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(f.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private val en = strings("src/main/res/values/strings.xml")
    private val ar = strings("src/main/res/values-ar/strings.xml")
    private val keys = (1..6).flatMap { listOf("guide_${it}_title", "guide_${it}_body") } + "guide_section"

    @Test fun `every guide string exists in both languages and is not blank`() {
        keys.forEach {
            assertTrue("missing en $it", !en[it].isNullOrBlank())
            assertTrue("missing ar $it", !ar[it].isNullOrBlank())
        }
    }

    @Test fun `arabic guide text contains Arabic letters`() {
        val arabic = Regex("[\\u0600-\\u06FF]")
        keys.forEach { assertTrue("ar $it not Arabic", arabic.containsMatchIn(ar[it]!!)) }
    }

    @Test fun `english guide text has no Arabic letters`() {
        val arabic = Regex("[\\u0600-\\u06FF]")
        keys.forEach { assertTrue("en $it has Arabic", !arabic.containsMatchIn(en[it]!!)) }
    }

    @Test fun `shortcut guide names the real shortcuts`() {
        listOf("Ctrl+Enter", "Ctrl+F", "Esc").forEach {
            assertTrue(en["guide_5_body"]!!.contains(it)); assertTrue(ar["guide_5_body"]!!.contains(it))
        }
    }

    @Test fun `guide titles are distinct`() {
        assertEquals(6, (1..6).map { en["guide_${it}_title"] }.toSet().size)
    }
}
