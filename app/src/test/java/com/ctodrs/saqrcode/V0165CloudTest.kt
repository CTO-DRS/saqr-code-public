package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.cloud.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class FakeSandbox(val log: MutableList<String>, var exec: (String) -> CloudExecResult) : CloudSandbox {
    var uploads = 0; var closed = 0; var lastFiles: List<CloudFile> = emptyList()
    override suspend fun upload(files: List<CloudFile>) { uploads++; lastFiles = files }
    override suspend fun exec(command: String, timeoutSec: Int, onLine: (String) -> Unit): CloudExecResult {
        log += "$command|$timeoutSec"; return exec(command).also { onLine(it.output) }
    }
    override suspend fun close() { closed++ }
}

class V0165CloudTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun setup(limits: CloudLimits = CloudLimits(), clockMs: () -> Long = { 0L }, execFn: (String) -> CloudExecResult = { CloudExecResult(0, "ok", false, 2_000) }): Triple<CloudWorkspace, FakeSandbox, CloudLedger> {
        val dir = tmp.newFolder("p"); java.io.File(dir, "main.py").writeText("print(1)")
        val sb = FakeSandbox(mutableListOf(), execFn)
        val ledger = CloudLedger(java.io.File(tmp.root, "ledger.json"))
        return Triple(CloudWorkspace({ sb }, ledger, limits, dir, { it.replace("SECRET", "***") }, clockMs), sb, ledger)
    }

    @Test fun `secrets and build output never upload`() {
        val d = tmp.newFolder("s")
        listOf(".env", ".env.local", "local.properties", "keystore.properties", "app/release.jks", "google-services.json", "a.apk", "src/Main.kt").forEach {
            java.io.File(d, it).apply { parentFile.mkdirs(); writeText("x") }
        }
        java.io.File(d, "build").mkdirs(); java.io.File(d, "build/out.txt").writeText("x")
        java.io.File(d, ".git").mkdirs(); java.io.File(d, ".git/config").writeText("x")
        val r = CloudSnapshot.collect(d)
        assertEquals(listOf("src/Main.kt"), r.files.map { it.path })
        assertTrue(r.skipped.containsAll(listOf(".env", ".env.local", "local.properties", "keystore.properties", "app/release.jks", "google-services.json", "a.apk")))
    }

    @Test fun `big files are skipped and total is capped`() {
        val d = tmp.newFolder("b"); java.io.File(d, "big.txt").writeBytes(ByteArray(3 * 1024 * 1024)); java.io.File(d, "ok.txt").writeText("a")
        val r = CloudSnapshot.collect(d)
        assertEquals(listOf("ok.txt"), r.files.map { it.path })
        assertTrue(r.skipped.any { it.contains("too large") })
    }

    @Test fun `run uploads once, redacts output and records time`() = runBlocking {
        val (w, sb, ledger) = setup(execFn = { CloudExecResult(1, "boom SECRET", false, 3_000) })
        val out = w.run("python main.py", 60)
        assertTrue(out.startsWith("[cloud · exit 1] 3s"))
        assertTrue(out.contains("boom ***") && !out.contains("SECRET"))
        w.run("python main.py", 60)
        assertEquals(1, sb.uploads)
        assertTrue(ledger.usedSecondsToday() >= 0)
    }

    @Test fun `changed files upload again`() = runBlocking {
        val (w, sb, _) = setup()
        w.run("x", 10)
        java.io.File(tmp.root, "p/main.py").writeText("print(2)")
        w.run("x", 10)
        assertEquals(2, sb.uploads)
    }

    @Test fun `timeout is clamped to limits and to what is left today`() = runBlocking {
        val (w, sb, ledger) = setup(CloudLimits(dailyMinutes = 1, maxCommandSec = 600))
        ledger.add(40)
        w.run("x", 9_999)
        assertEquals("x|20", sb.log.single())
    }

    @Test fun `daily cap blocks before any sandbox opens`() = runBlocking {
        var opened = false
        val dir = tmp.newFolder("c"); val ledger = CloudLedger(java.io.File(tmp.root, "l2.json")); ledger.add(31 * 60)
        val w = CloudWorkspace({ opened = true; throw AssertionError() }, ledger, CloudLimits(dailyMinutes = 30), dir)
        assertTrue(w.run("x", 10).startsWith("Error: the daily cloud limit"))
        assertFalse(opened)
    }

    @Test fun `provider failure is plain words and closes the sandbox`() = runBlocking {
        val dir = tmp.newFolder("f"); val ledger = CloudLedger(java.io.File(tmp.root, "l3.json"))
        val w = CloudWorkspace({ throw CloudException("no connection to the cloud") }, ledger, CloudLimits(), dir)
        assertEquals("Error: no connection to the cloud", w.run("x", 10))
        assertFalse(w.isOpen)
    }

    @Test fun `close is idempotent and idle expiry works`() = runBlocking {
        var now = 0L
        val (w, sb, _) = setup(CloudLimits(idleKillSec = 300), { now })
        w.run("x", 10); now = 100_000
        assertFalse(w.idleExpired()); now = 400_000
        assertTrue(w.idleExpired())
        w.close(); w.close()
        assertEquals(1, sb.closed)
    }

    @Test fun `long output is tailed`() = runBlocking {
        val (w, _, _) = setup(execFn = { CloudExecResult(0, "a".repeat(10_000) + "END", false, 1_000) })
        val out = w.run("x", 10)
        assertTrue(out.contains("earlier output cut") && out.endsWith("END") && out.length < 6_200)
    }

    @Test fun `test command detection`() {
        assertTrue(CloudTests.detect(setOf("gradlew"))!!.contains("gradlew test"))
        assertTrue(CloudTests.detect(setOf("package.json"))!!.contains("npm test"))
        assertTrue(CloudTests.detect(setOf("test_app.py"))!!.contains("pytest"))
        assertNull(CloudTests.detect(setOf("README.md")))
    }

    @Test fun `ledger keeps days apart and shows the meter`() {
        var day = LocalDate.of(2026, 11, 28)
        val l = CloudLedger(java.io.File(tmp.root, "l4.json")) { day }
        l.add(600); assertEquals(600, l.usedSecondsToday())
        day = day.plusDays(1); assertEquals(0, l.usedSecondsToday())
        day = day.minusDays(1)
        assertEquals("10/30 min today · ~$0.03", l.meter(CloudLimits()))
    }
}
