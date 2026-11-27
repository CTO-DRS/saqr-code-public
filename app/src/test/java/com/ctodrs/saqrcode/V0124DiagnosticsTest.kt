package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.CompletionGate
import com.ctodrs.saqrcode.agent.Diagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.1.21: structured diagnostics are pulled out of raw check output for the verifier. */
class V0124DiagnosticsTest {
    @Test fun `kotlin compiler errors`() {
        val d = Diagnostics.parse("e: file:///p/src/A.kt:12:5 Unresolved reference: foo\ne: /p/B.kt: (7, 3): Type mismatch")
        assertEquals(listOf("/p/src/A.kt:12: Unresolved reference: foo", "/p/B.kt:7: Type mismatch"), d.map { it.toString() })
    }

    @Test fun `tsc and gcc style`() {
        val d = Diagnostics.parse("src/a.ts(12,5): error TS2304: Cannot find name 'x'.\nmain.c:40:9: error: expected ';'\nlib/x.js:3:1: warning: unused")
        assertEquals(3, d.size)
        assertEquals("src/a.ts", d[0].file); assertEquals(12, d[0].line)
        assertEquals("main.c:40: expected ';'", d[1].toString())
        assertEquals("lib/x.js:3: unused", d[2].toString())
    }

    @Test fun `python traceback uses the last frame and the exception`() {
        val out = "Traceback (most recent call last):\n  File \"app.py\", line 3, in <module>\n    main()\n  File \"app.py\", line 9, in main\n    x = 1/0\nZeroDivisionError: division by zero"
        assertEquals(listOf("app.py:9: ZeroDivisionError: division by zero"), Diagnostics.parse(out).map { it.toString() })
    }

    @Test fun `duplicates collapse and the limit holds`() {
        val one = "e: file:///p/A.kt:1:1 bad\n".repeat(5)
        assertEquals(1, Diagnostics.parse(one).size)
        val many = (1..60).joinToString("\n") { "m.c:$it:1: error: e$it" }
        assertEquals(10, Diagnostics.parse(many, limit = 10).size)
    }

    @Test fun `plain text and stack frames give nothing`() {
        assertTrue(Diagnostics.parse("BUILD FAILED\n\tat com.x.Foo.bar(Foo.java:12)\nsomething went wrong").isEmpty())
        assertEquals("", Diagnostics.digest(""))
        assertEquals("", Diagnostics.digest(null))
    }

    @Test fun `digest summarises files and feedback carries it first`() {
        val lint = "FAIL\ne: file:///p/A.kt:12:5 Unresolved reference: foo\ne: file:///p/B.kt:3:1 Oops"
        val dg = Diagnostics.digest(lint)
        assertTrue(dg.startsWith("2 diagnostic(s) in 2 file(s):"))
        val fb = CompletionGate.feedback(lint, null, 1)
        assertTrue(fb.indexOf("lint — where") in 0 until fb.indexOf("## lint\n"))
        assertTrue(fb.contains("- /p/A.kt:12: Unresolved reference: foo"))
    }
}
