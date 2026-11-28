package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.device.*
import org.junit.Assert.*
import org.junit.Test

class V0176LogcatTest {
    private val log = """
        11-28 10:00:00.000  100  100 I Foo: hello
        11-28 10:00:01.000  200  200 E AndroidRuntime: FATAL EXCEPTION: main
        11-28 10:00:01.000  200  200 E AndroidRuntime: Process: com.example.app, PID: 200
        11-28 10:00:01.000  200  200 E AndroidRuntime: java.lang.NullPointerException: boom
        11-28 10:00:02.000  300  300 E ActivityManager: ANR in com.other.app (com.other.app/.Main)
    """.trimIndent()

    @Test fun findsCrashAndAnr() {
        val f = LogcatAnalyzer.analyze(log)
        assertEquals(2, f.size)
        assertEquals(LogcatAnalyzer.Kind.CRASH, f[0].kind)
        assertEquals("com.example.app", f[0].process)
        assertTrue(f[0].summary, f[0].summary.contains("NullPointerException"))
        assertEquals(LogcatAnalyzer.Kind.ANR, f[1].kind)
        assertEquals("com.other.app", f[1].process)
    }

    @Test fun emptyLogIsClean() {
        assertTrue(LogcatAnalyzer.analyze("nothing here").isEmpty())
        assertTrue(LogcatAnalyzer.describe(emptyList()).startsWith("No crashes"))
    }

    @Test fun commandIsSafe() = assertEquals(AdbRisk.SAFE, AdbRiskClassifier.classify(LogcatAnalyzer.COMMAND).risk)
}
