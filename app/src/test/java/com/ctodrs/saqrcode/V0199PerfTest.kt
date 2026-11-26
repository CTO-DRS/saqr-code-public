package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.ui.chat.MarkdownParser
import com.ctodrs.saqrcode.ui.chat.StreamThrottle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V0199PerfTest {
    @Test fun `throttle waits out the rest of the interval and never more`() {
        assertEquals(0L, StreamThrottle.waitMs(now = 1000, lastUpdate = 0))
        assertEquals(60L, StreamThrottle.waitMs(now = 1030, lastUpdate = 1000))
        assertEquals(0L, StreamThrottle.waitMs(now = 1090, lastUpdate = 1000))
        assertEquals(StreamThrottle.INTERVAL_MS, StreamThrottle.waitMs(now = 900, lastUpdate = 1000))
    }

    @Test fun `parsing a long mixed arabic reply stays fast`() {
        val para = "هذا **نص** طويل مع [رابط](https://x.y) و `كود` وقائمة:\n- بند أول\n- بند ثان\n\n```kotlin\nval a = 1\n```\n\n"
        val src = para.repeat(150)
        MarkdownParser.parse(src) // warm up
        val t0 = System.nanoTime()
        val blocks = MarkdownParser.parse(src)
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("PERF parse ${src.length} chars -> ${blocks.size} blocks in $ms ms")
        assertTrue("parse took $ms ms", ms < 1500)
    }
}
