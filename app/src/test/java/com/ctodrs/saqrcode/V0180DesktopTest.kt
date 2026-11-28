package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.cloud.CloudDesktop
import com.ctodrs.saqrcode.agent.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class V0180DesktopTest {
    @Test fun clickBounds() {
        assertNotNull(CloudDesktop.build("click", JSONObject("""{"x":10,"y":20}""")))
        assertNull(CloudDesktop.build("click", JSONObject("""{"x":1024,"y":20}""")))
        assertNull(CloudDesktop.build("click", JSONObject("""{"y":20}""")))
        assertNull(CloudDesktop.build("click", JSONObject("""{"x":5,"y":768}""")))
    }
    @Test fun typeAndKey() {
        assertNull(CloudDesktop.build("type", JSONObject("""{"text":""}""")))
        assertEquals("cu/keyboard/type", CloudDesktop.build("type", JSONObject("""{"text":"hi"}"""))!!.first)
        assertNotNull(CloudDesktop.build("key", JSONObject("""{"key":"ctrl+l"}""")))
        assertNull(CloudDesktop.build("key", JSONObject("""{"key":"a;rm -rf"}""")))
        assertNull(CloudDesktop.build("shell", JSONObject()))
    }
    @Test fun describes() {
        assertTrue(CloudDesktop.describe("look", JSONObject("""{"description":"a screen"}""")).startsWith("a screen"))
        assertTrue(CloudDesktop.describe("click", JSONObject("""{"error":"x","detail":"limit"}""")).contains("limit"))
        assertEquals("ok", CloudDesktop.describe("type", JSONObject("{}")))
    }
    @Test fun toolDeclared() {
        assertEquals(1, Tools.specs.count { it.name == Tools.CLOUD_DESKTOP })
        assertNotNull(ToolRegistry.get(Tools.CLOUD_DESKTOP))
        assertTrue(Tools.CLOUD_DESKTOP in Tools.CLOUD)
    }
}
