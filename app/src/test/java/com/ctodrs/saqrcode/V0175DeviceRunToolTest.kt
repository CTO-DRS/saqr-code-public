package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ToolRegistry
import com.ctodrs.saqrcode.agent.Tools
import org.junit.Assert.*
import org.junit.Test

class V0175DeviceRunToolTest {
    @Test fun toolIsDeclaredOnce() {
        assertEquals(1, Tools.specs.count { it.name == Tools.DEVICE_RUN })
        assertNotNull(ToolRegistry.get(Tools.DEVICE_RUN))
    }
}
