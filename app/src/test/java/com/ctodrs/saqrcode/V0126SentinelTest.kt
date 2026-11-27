package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.Sentinel
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.22: Sentinel morning digest scheduling and wording. */
class V0126SentinelTest {
    private val z = ZoneId.of("Asia/Riyadh")
    private fun at(h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, 27, h, m, 0, 0, z)

    @Test fun `next fire is today before eight and tomorrow after`() {
        assertEquals(at(8), Sentinel.nextFire(at(6, 30)))
        assertEquals(at(8).plusDays(1), Sentinel.nextFire(at(8)))
        assertEquals(at(8).plusDays(1), Sentinel.nextFire(at(17, 45)))
    }

    @Test fun `millis variant matches and month boundary rolls over`() {
        val now = ZonedDateTime.of(2026, 11, 30, 23, 0, 0, 0, z)
        assertEquals(ZonedDateTime.of(2026, 12, 1, 8, 0, 0, 0, z).toInstant().toEpochMilli(),
            Sentinel.nextFireMillis(now.toInstant().toEpochMilli(), z))
    }

    @Test fun `nothing to report means no notification`() {
        assertNull(Sentinel.message(Sentinel.Input(), false))
        assertNull(Sentinel.message(Sentinel.Input(dirty = listOf("a" to 0), yesterdayCost = 0.0), true))
    }

    @Test fun `english digest lists the busiest projects and spend`() {
        val m = Sentinel.message(Sentinel.Input(listOf("a" to 2, "b" to 9, "c" to 1, "d" to 4), 1.5, 12.0, 50.0), false)!!
        assertEquals("Saqr morning digest", m.title)
        assertTrue(m.body, m.body.contains("Uncommitted changes in 4 projects: b (9), d (4), a (2) and 1 more"))
        assertTrue(m.body, m.body.contains("Yesterday's model spend \$1.50; this month \$12.00 / \$50.00"))
    }

    @Test fun `arabic digest uses western digits`() {
        val m = Sentinel.message(Sentinel.Input(listOf("x" to 3), 0.0), true)!!
        assertEquals("ملخص الصباح من صقر", m.title)
        assertTrue(m.body, m.body.contains("تغييرات غير محفوظة في 1 مشروع: x (3)"))
        assertTrue(!m.body.contains("صرف"))
    }

    @Test fun `spend only digest`() {
        val m = Sentinel.message(Sentinel.Input(yesterdayCost = 0.25), false)!!
        assertEquals("Yesterday's model spend \$0.25", m.body)
    }
}
