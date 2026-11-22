package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.Tools
import com.ctodrs.saqrcode.ui.chat.MarkdownParser
import com.ctodrs.saqrcode.ui.chat.MdBlock
import com.ctodrs.saqrcode.ui.chat.formatTokens
import com.ctodrs.saqrcode.ui.components.latinDigits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLogicTest {
    @Test fun markdownBlocks() {
        val b = MarkdownParser.parse("# عنوان\nفقرة **عريضة**\n\n- بند\n1. أول\n```kotlin\nval x = 1\n```\n> اقتباس\n```js\nunclosed")
        assertTrue(b[0] is MdBlock.Heading)
        assertTrue(b[1] is MdBlock.Para)
        assertEquals(MdBlock.Bullet("بند", null, 0), b[2])
        assertEquals(MdBlock.Bullet("أول", "1", 0), b[3])
        assertEquals(MdBlock.Code("kotlin", "val x = 1", true), b[4])
        assertTrue(b[5] is MdBlock.Quote)
        assertEquals(MdBlock.Code("js", "unclosed", false), b[6]) // still streaming
    }

    @Test fun digitsAndTokens() {
        assertEquals("قبل 12 دقيقة", "قبل ١٢ دقيقة".latinDigits())
        assertEquals("55.4k", formatTokens(55_400))
        assertEquals("200k", formatTokens(200_000))
        assertEquals("1M", formatTokens(1_000_000))
    }

    @Test fun planToolIsExposed() {
        assertTrue(Tools.specs.any { it.name == Tools.PLAN })
    }
}
