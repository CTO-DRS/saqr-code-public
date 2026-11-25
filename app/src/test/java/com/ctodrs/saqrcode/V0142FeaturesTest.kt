package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.git.CloudBuildKit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * v0.14.2 «أجنحة مفتوحة · البناء السحابي (BYOC)»: the workflow YAML, GitHub URL/owner parsing,
 * the API path builders, the contents-API base64 codec, run/job/artifact parsing, the run
 * state machine, log tails and error lines, the timeout rule, and the artifact-zip APK
 * extraction (with a real zip built in the test). Pure JVM — no sockets, no Android.
 */
class V0142FeaturesTest {

    // ------------------------------------------------- workflow yaml

    @Test
    fun `workflow yaml carries the whole build recipe`() {
        val y = CloudBuildKit.workflowYaml()
        assertTrue(y.contains("name: Saqr Build"))
        assertTrue(y.contains("workflow_dispatch:"))
        assertTrue(y.contains("actions/checkout@v4"))
        assertTrue(y.contains("actions/setup-java@v4"))
        assertTrue(y.contains("java-version: '17'"))
        assertTrue(y.contains("android-actions/setup-android@v3"))
        assertTrue(y.contains("gradle/actions/setup-gradle@v4"))
        assertTrue(y.contains("gradle-version: '8.11.1'"))
        assertTrue(y.contains("run: gradle assembleDebug --no-daemon --stacktrace"))
        assertTrue(y.contains("actions/upload-artifact@v4"))
        assertTrue(y.contains("name: saqr-apk"))
        assertTrue(y.contains("path: app/build/outputs/apk/debug/*.apk"))
        assertTrue(y.contains("if-no-files-found: error"))
        // No wrapper on device templates — the action-installed Gradle is the whole point.
        assertFalse(y.contains("./gradlew"))
        // Debug (not release): the APK must install without any signing setup.
        assertFalse(y.contains("assembleRelease"))
    }

    @Test
    fun `workflow yaml accepts a custom gradle version`() {
        assertTrue(CloudBuildKit.workflowYaml("8.9").contains("gradle-version: '8.9'"))
    }

    // ------------------------------------------------- owner/repo parsing

    @Test
    fun `owner repo parses every github remote shape`() {
        assertEquals("o" to "r", CloudBuildKit.ownerRepo("https://github.com/o/r.git"))
        assertEquals("o" to "r", CloudBuildKit.ownerRepo("https://github.com/o/r"))
        assertEquals("Owner" to "Repo.name", CloudBuildKit.ownerRepo("https://github.com/Owner/Repo.name.git"))
        assertEquals("o" to "r", CloudBuildKit.ownerRepo("git@github.com:o/r.git"))
        assertEquals("o" to "r", CloudBuildKit.ownerRepo("http://github.com/o/r"))
        assertNull(CloudBuildKit.ownerRepo("https://gitlab.com/o/r.git"))
        assertNull(CloudBuildKit.ownerRepo("https://github.com/o"))
        assertNull(CloudBuildKit.ownerRepo("https://github.com/"))
        assertNull(CloudBuildKit.ownerRepo(""))
    }

    // ------------------------------------------------- api paths

    @Test
    fun `api paths hit the right endpoints`() {
        assertEquals("/repos/o/r/actions/workflows/saqr-build.yml/dispatches", CloudBuildKit.dispatchPath("o", "r"))
        assertEquals("/repos/o/r/actions/workflows/saqr-build.yml/runs?per_page=10", CloudBuildKit.runsPath("o", "r"))
        assertEquals("/repos/o/r/actions/runs/42/jobs?per_page=10", CloudBuildKit.jobsPath("o", "r", 42))
        assertEquals("/repos/o/r/actions/jobs/7/logs", CloudBuildKit.jobLogPath("o", "r", 7))
        assertEquals("/repos/o/r/actions/runs/42/artifacts?per_page=20", CloudBuildKit.artifactsPath("o", "r", 42))
        assertEquals("/repos/o/r/actions/artifacts/9/zip", CloudBuildKit.artifactZipPath("o", "r", 9))
        assertEquals("/repos/o/r/contents/.github/workflows/saqr-build.yml?ref=feature%2Fx",
            CloudBuildKit.contentsGetPath("o", "r", "feature/x"))
        assertEquals("""{"ref":"main"}""", CloudBuildKit.dispatchBody("main"))
    }

    // ------------------------------------------------- contents codec

    @Test
    fun `contents base64 codec round trips including github line breaks`() {
        val yaml = CloudBuildKit.workflowYaml()
        assertEquals(yaml, CloudBuildKit.decodeContent(CloudBuildKit.encodeContent(yaml)))
        // GitHub embeds line breaks in the base64 body — the decoder must survive them.
        val broken = encodeContent(yaml).chunked(60).joinToString("\n")
        assertEquals(yaml, CloudBuildKit.decodeContent(broken))
        assertNull(CloudBuildKit.decodeContent(null))
        assertNull(CloudBuildKit.decodeContent("!!!not base64!!!"))
    }

    private fun encodeContent(s: String) = java.util.Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    // ------------------------------------------------- run/job parsing

    @Test
    fun `runs and jobs parse from the api envelopes`() {
        val runs = CloudBuildKit.parseRuns(JSONObject(
            """{"total_count":2,"workflow_runs":[
                {"id":20,"run_number":3,"status":"in_progress","conclusion":null,"html_url":"https://github.com/o/r/actions/runs/20"},
                {"id":10,"run_number":2,"status":"completed","conclusion":"success","html_url":"https://github.com/o/r/actions/runs/10"}]}"""))
        assertEquals(2, runs.size)
        assertEquals(20L, runs[0].id)
        assertEquals(3, runs[0].number)
        assertNull(runs[0].conclusion)
        assertEquals("success", runs[1].conclusion)
        // Newest (highest id) wins, whatever order the API returned.
        assertEquals(20L, CloudBuildKit.latestRun(runs)!!.id)
        assertEquals(20L, CloudBuildKit.latestRun(runs.reversed())!!.id)
        assertEquals(emptyList<CloudBuildKit.Run>(), CloudBuildKit.parseRuns(JSONObject("""{"workflow_runs":[]}""")))
        assertEquals(emptyList<CloudBuildKit.Run>(), CloudBuildKit.parseRuns(JSONObject("""{}""")))
    }

    @Test
    fun `jobs parse from the api envelope`() {
        val jobs = CloudBuildKit.parseJobs(JSONObject(
            """{"total_count":1,"jobs":[
                {"id":7,"name":"build","status":"completed","conclusion":"failure"}]}"""))
        assertEquals(1, jobs.size)
        assertEquals(7L, jobs[0].id)
        assertEquals("build", jobs[0].name)
        assertEquals("failure", jobs[0].conclusion)
    }

    // ------------------------------------------------- state machine

    @Test
    fun `state of covers the run lifecycle`() {
        assertEquals(CloudBuildKit.State.QUEUED, CloudBuildKit.stateOf("queued", null))
        assertEquals(CloudBuildKit.State.QUEUED, CloudBuildKit.stateOf("waiting", null))
        assertEquals(CloudBuildKit.State.RUNNING, CloudBuildKit.stateOf("in_progress", null))
        assertEquals(CloudBuildKit.State.SUCCESS, CloudBuildKit.stateOf("completed", "success"))
        assertEquals(CloudBuildKit.State.FAILURE, CloudBuildKit.stateOf("completed", "failure"))
        assertEquals(CloudBuildKit.State.FAILURE, CloudBuildKit.stateOf("completed", "startup_failure"))
        assertEquals(CloudBuildKit.State.CANCELLED, CloudBuildKit.stateOf("completed", "cancelled"))
        assertEquals(CloudBuildKit.State.CANCELLED, CloudBuildKit.stateOf("completed", "timed_out"))
    }

    // ------------------------------------------------- artifacts

    @Test
    fun `pick apk prefers the fresh apk artifact and skips expired`() {
        val json = JSONObject(
            """{"total_count":3,"artifacts":[
                {"id":1,"name":"old-apk","size_in_bytes":100,"expired":true},
                {"id":2,"name":"build-logs","size_in_bytes":50,"expired":false},
                {"id":3,"name":"saqr-apk","size_in_bytes":5000,"expired":false}]}""")
        assertEquals(3L, CloudBuildKit.pickApk(json)!!.id)
        // A single non-apk artifact is still the fallback (better than nothing).
        val onlyLogs = JSONObject("""{"artifacts":[{"id":9,"name":"build-logs","size_in_bytes":50,"expired":false}]}""")
        assertEquals(9L, CloudBuildKit.pickApk(onlyLogs)!!.id)
        // Expired-only or empty → null.
        assertNull(CloudBuildKit.pickApk(JSONObject("""{"artifacts":[{"id":1,"name":"saqr-apk","expired":true}]}""")))
        assertNull(CloudBuildKit.pickApk(JSONObject("""{"artifacts":[]}""")))
        assertNull(CloudBuildKit.pickApk(JSONObject("""{}""")))
    }

    // ------------------------------------------------- logs

    @Test
    fun `tail lines keeps the last lines and drops blanks`() {
        val log = (1..30).joinToString("\n") { "line $it" } + "\n\n\n"
        val tail = CloudBuildKit.tailLines(log, 5)
        assertEquals(listOf("line 26", "line 27", "line 28", "line 29", "line 30"), tail)
        assertTrue(CloudBuildKit.tailLines("", 5).isEmpty())
    }

    @Test
    fun `error lines name what broke the build`() {
        val log = """
            Welcome to Gradle 8.11.1!
            e: file:///home/runner/work/app/src/Main.kt:10:5 unresolved reference
            FAILURE: Build failed with an exception.
            * What went wrong:
            Execution failed for task ':app:compileDebugKotlin'
            BUILD FAILED in 2m 1s
        """.trimIndent()
        val errors = CloudBuildKit.errorLines(log)
        assertEquals(4, errors.size)
        assertTrue(errors.last().contains("BUILD FAILED"))
        assertTrue(CloudBuildKit.errorLines("all clean\nnothing here").isEmpty())
    }

    // ------------------------------------------------- timeout

    @Test
    fun `timeout applies the twenty minute rule`() {
        val now = 10_000_000L
        assertFalse(CloudBuildKit.timedOut(now, now + 19 * 60_000L))
        assertTrue(CloudBuildKit.timedOut(now, now + 21 * 60_000L))
        assertEquals(20L, CloudBuildKit.TIMEOUT_MINUTES)
    }

    // ------------------------------------------------- unzip

    @Test
    fun `unzip apk extracts the apk and stays inside the destination`() {
        val zip = File.createTempFile("artifact", ".zip")
        val dirOut = File(zip.parent, "unzipped-${System.nanoTime()}")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("saqr-apk/"))
            z.closeEntry()
            z.putNextEntry(ZipEntry("saqr-apk/app-debug.apk"))
            z.write("PK-FAKE-APK".toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("junk.txt"))
            z.write("not an apk".toByteArray())
            z.closeEntry()
        }
        val apk = CloudBuildKit.unzipApk(zip, dirOut)
        assertEquals("app-debug.apk", apk!!.name)
        assertEquals("PK-FAKE-APK", apk.readText())
        // Nothing escaped the destination directory.
        assertTrue(apk.parentFile!!.absolutePath.startsWith(dirOut.absolutePath))
        // A zip without any apk entry yields null.
        val noApk = File.createTempFile("empty", ".zip")
        ZipOutputStream(noApk.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("readme.md")); z.write("hi".toByteArray()); z.closeEntry()
        }
        assertNull(CloudBuildKit.unzipApk(noApk, File(dirOut.parent, "unzipped-empty-${System.nanoTime()}")))
        // Garbage bytes yield null too (no crash).
        val junk = File.createTempFile("junk", ".zip").apply { writeText("definitely not a zip") }
        assertNull(CloudBuildKit.unzipApk(junk, File(dirOut.parent, "unzipped-junk-${System.nanoTime()}")))
        zip.delete(); noApk.delete(); junk.delete()
    }
}
