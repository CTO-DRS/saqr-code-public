package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.device.*
import org.junit.Assert.*
import org.junit.Test

class V0174ShizukuEngineTest {
    @Test fun orderPrefersShizukuThenRoot() {
        assertEquals(listOf("shizuku", "root", "local"), ShizukuEngine.order(ShizukuState.READY, true))
        assertEquals(listOf("local"), ShizukuEngine.order(ShizukuState.NOT_RUNNING, false))
        assertEquals(listOf("root", "local"), ShizukuEngine.order(ShizukuState.NEEDS_PERMISSION, true))
    }
}
