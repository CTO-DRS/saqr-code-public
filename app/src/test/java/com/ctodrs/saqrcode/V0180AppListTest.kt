package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.device.*
import org.junit.Assert.*
import org.junit.Test

class V0180AppListTest {
    @Test fun parses() {
        val p = AppList.parse("package:com.b\npackage:/data/app/x/base.apk=com.a\nnoise\npackage:com.b\n")
        assertEquals(listOf("com.a", "com.b"), p)
    }
    @Test fun describes() {
        assertTrue(AppList.describe(emptyList()).startsWith("No apps"))
        assertTrue(AppList.describe(listOf("a", "b"), 1).endsWith("…"))
        assertTrue(AppList.describe(listOf("a", "b")).startsWith("2 apps"))
    }
    @Test fun commandIsSafe() = assertEquals(AdbRisk.SAFE, AdbRiskClassifier.classify(AppList.COMMAND_USER).risk)
}
