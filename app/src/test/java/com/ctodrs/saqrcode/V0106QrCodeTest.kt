package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.ui.DeveloperLinks
import com.ctodrs.saqrcode.ui.QrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V0106QrCodeTest {
    @Test fun `sizes follow the version and finders are in place`() {
        assertEquals(21, QrCode.encode("hi").size)
        assertEquals(25, QrCode.encode("https://t.me/CTO_DRS").size)
        val m = QrCode.encode("https://cto-drs.com")
        assertTrue(m[0][0] && m[0][6] && m[6][0] && m[6][6] && !m[1][1] && m[2][2])
        assertTrue(m[0][m.size - 1] && m[m.size - 1][0])
    }

    @Test fun `every developer link encodes and rejects oversize input`() {
        val dir = File("build/qr").apply { mkdirs() }
        DeveloperLinks.all.forEach { l ->
            val m = QrCode.encode(l.url)
            File(dir, "${l.id}.txt").writeText(l.url + "\n" + m.joinToString("\n") { r -> r.joinToString("") { if (it) "1" else "0" } })
        }
        File(dir, "long.txt").writeText("x".repeat(150).let { s -> s + "\n" + QrCode.encode(s).joinToString("\n") { r -> r.joinToString("") { if (it) "1" else "0" } } })
        runCatching { QrCode.encode("x".repeat(181)) }.let { assertTrue(it.isFailure) }
    }
}
