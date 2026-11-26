package com.ctodrs.saqrcode

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import com.ctodrs.saqrcode.ui.chat.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.19.4: a link wrapped in bold/italic/strike used to render as raw "[label](url)" text, which Arabic bidi scrambled. */
class V0194MarkdownLinkTest {
    private fun ann(s: String) = MarkdownParser.inline(s, Color.Gray, Color.Red, Color.Blue)

    @Test fun `bold around a link becomes a bold link with only the label visible`() {
        val a = ann("يمكنك الآن تحميل **[android_app_package.zip](sandbox:/workspace/android_app_package.zip)** الآن")
        assertEquals("يمكنك الآن تحميل android_app_package.zip الآن", a.text)
        // sandbox: paths are files, not web links: no clickable annotation (a file card opens them)
        assertEquals(0, a.getLinkAnnotations(0, a.length).size)
        val web = ann("**[docs](https://a.b/c)**")
        assertEquals("https://a.b/c", (web.getLinkAnnotations(0, web.length)[0].item as LinkAnnotation.Url).url)
        assertTrue(web.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold })
        assertTrue(!a.text.contains("](") && !a.text.contains("["))
    }

    @Test fun `plain link italic link and strike link all keep only the label`() {
        for (w in listOf("*", "~~")) {
            val a = ann("${w}[ملف](https://a.b/c)${w}")
            assertEquals("ملف", a.text); assertEquals(1, a.getLinkAnnotations(0, a.length).size)
        }
        assertEquals("x", ann("[x](https://a.b)").text)
    }
}
