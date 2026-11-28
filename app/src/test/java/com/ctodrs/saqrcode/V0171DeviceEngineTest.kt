package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.device.AdbRisk
import com.ctodrs.saqrcode.device.AdbRiskClassifier
import com.ctodrs.saqrcode.device.CommandEngine
import com.ctodrs.saqrcode.device.CommandResult
import com.ctodrs.saqrcode.device.DeviceAuditLog
import com.ctodrs.saqrcode.device.DeviceEngine
import com.ctodrs.saqrcode.device.LocalShellEngine
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V0171DeviceEngineTest {
    private fun risk(c: String) = AdbRiskClassifier.classify(c).risk

    @Test fun readsAreSafe() {
        assertEquals(AdbRisk.SAFE, risk("getprop ro.build.version.release"))
        assertEquals(AdbRisk.SAFE, risk("dumpsys battery"))
        assertEquals(AdbRisk.SAFE, risk("cat /proc/meminfo"))
        assertEquals(AdbRisk.SAFE, risk("pm list packages"))
        assertEquals(AdbRisk.SAFE, risk(""))
    }

    @Test fun settingChangesAreSensitive() {
        assertEquals(AdbRisk.SENSITIVE, risk("pm grant com.example.app android.permission.CAMERA"))
        assertEquals(AdbRisk.SENSITIVE, risk("am start -n com.example/.Main"))
        assertEquals(AdbRisk.SENSITIVE, risk("input tap 100 200"))
        assertEquals(AdbRisk.SENSITIVE, risk("getprop ro.product.model; id"))
    }

    @Test fun destructiveCommandsAreDangerous() {
        assertEquals(AdbRisk.DANGEROUS, risk("rm -rf /sdcard/Download"))
        assertEquals(AdbRisk.DANGEROUS, risk("pm uninstall com.example.app"))
        assertEquals(AdbRisk.DANGEROUS, risk("pm clear com.example.app"))
        assertEquals(AdbRisk.DANGEROUS, risk("reboot"))
        assertEquals(AdbRisk.DANGEROUS, risk("settings put global airplane_mode_on 1"))
        assertEquals(AdbRisk.DANGEROUS, risk("setprop persist.sys.x 1"))
        assertEquals(AdbRisk.DANGEROUS, risk("su -c id"))
    }

    private class Fake(override val id: String, val up: Boolean = true) : DeviceEngine {
        var ran = 0
        override suspend fun available() = up
        override suspend fun exec(command: String, timeoutMs: Long): CommandResult { ran++; return CommandResult(command, id, "ok", "", 0, 1) }
    }

    private fun log() = DeviceAuditLog(File.createTempFile("audit", ".jsonl"))

    @Test fun safeRunsWithoutAsking() = runBlocking {
        var asked = 0
        val e = Fake("local")
        val r = CommandEngine(listOf(e), log()) { _, _ -> asked++; true }.run("getprop ro.product.model")
        assertTrue(r.success); assertEquals(0, asked); assertEquals(1, e.ran)
    }

    @Test fun dangerousNeedsAYesEveryTime() = runBlocking {
        var asked = 0
        val e = Fake("local")
        val ce = CommandEngine(listOf(e), log()) { _, _ -> asked++; false }
        val r = ce.run("pm uninstall com.example.app")
        assertFalse(r.success); assertNotNull(r.refused); assertEquals(0, e.ran)
        ce.run("pm uninstall com.example.app")
        assertEquals(2, asked)
    }

    @Test fun rootIsNeverTheDefault() = runBlocking {
        val local = Fake("local", up = false); val root = Fake("root")
        val ce = CommandEngine(listOf(local, root), log()) { _, _ -> true }
        val r = ce.run("getprop ro.product.model")
        assertNotNull(r.refused); assertEquals(0, root.ran)
        assertTrue(ce.run("getprop ro.product.model", prefer = "root").success)
    }

    @Test fun auditRecordsAndRedactsSecrets() {
        val l = log()
        l.record("adb pair 192.168.1.5 password=hunter2", "local", AdbRisk.SENSITIVE, "approved", null)
        val e = l.entries()
        assertEquals(1, e.size)
        assertFalse(e[0].getString("cmd").contains("hunter2"))
    }

    @Test fun localShellRunsAndTimesOut() = runBlocking {
        val ok = LocalShellEngine().exec("echo hi", 5000)
        assertTrue(ok.success); assertEquals("hi", ok.output)
        val slow = LocalShellEngine().exec("sleep 5", 300)
        assertTrue(slow.timedOut); assertFalse(slow.success)
        assertNull(slow.refused)
    }
}

class V0172DeviceControlTest {
    @Test fun shizukuStateMapping() {
        assertEquals(com.ctodrs.saqrcode.device.ShizukuState.NOT_INSTALLED, com.ctodrs.saqrcode.device.ShizukuStatus.state(false, false, false))
        assertEquals(com.ctodrs.saqrcode.device.ShizukuState.NOT_RUNNING, com.ctodrs.saqrcode.device.ShizukuStatus.state(true, false, false))
        assertEquals(com.ctodrs.saqrcode.device.ShizukuState.NEEDS_PERMISSION, com.ctodrs.saqrcode.device.ShizukuStatus.state(true, true, false))
        assertEquals(com.ctodrs.saqrcode.device.ShizukuState.READY, com.ctodrs.saqrcode.device.ShizukuStatus.state(true, true, true))
    }

    private fun res(out: String) = CommandResult("x", "local", out, "", 0, 1)

    @Test fun summarizesDiagnostics() {
        val d = com.ctodrs.saqrcode.device.DeviceDiagnostics
        assertEquals("87%", d.summarize("Battery", res("Current Battery Service state:\n  level: 87\n  scale: 100")))
        assertEquals("1 MB free of 2 MB", d.summarize("Memory", res("MemTotal:  2048 kB\nMemFree: 100 kB\nMemAvailable: 1024 kB")))
        assertEquals("2.0 h", d.summarize("Uptime", res("7200.55 12345.00")))
        assertEquals("14", d.summarize("Android", res("14\n")))
        assertEquals("the user declined", d.summarize("Model", CommandResult("x", "local", "", "", -1, 0, refused = "the user declined")))
    }

    @Test fun diagnosticsAreAllSafe() {
        com.ctodrs.saqrcode.device.DeviceDiagnostics(CommandEngine(emptyList(), DeviceAuditLog(File.createTempFile("aud", ".j"))) { _, _ -> false }).items.forEach {
            assertEquals(it.command, AdbRisk.SAFE, AdbRiskClassifier.classify(it.command).risk)
        }
    }
}
