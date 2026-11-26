package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.ui.chat.Deliverables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** v0.19.5 Majlis: files the agent mentions become file cards; nothing outside the project is ever offered. */
class V0195FileCardTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun root(): File = tmp.newFolder("proj")

    @Test fun `sandbox path from the real bug report resolves to the zip in the project`() {
        val r = root(); File(r, "android_app_package.zip").writeBytes(ByteArray(2048))
        val text = "يمكنك الآن تحميل **[android_app_package.zip](sandbox:/workspace/android_app_package.zip)**"
        val found = Deliverables.find(text, r)
        assertEquals(listOf("android_app_package.zip"), found.map { it.name })
    }

    @Test fun `bare names, nested paths and duplicates`() {
        val r = root(); File(r, "out").mkdirs(); File(r, "out/app.apk").writeText("x"); File(r, "notes.pdf").writeText("y")
        val found = Deliverables.find("Built out/app.apk then app.apk again, see /workspace/notes.pdf.", r)
        assertEquals(listOf("app.apk", "notes.pdf"), found.map { it.name })
    }

    @Test fun `a file the sandbox named by another path is found by name`() {
        val r = root(); File(r, "dist").mkdirs(); File(r, "dist/bundle.zip").writeText("z")
        assertEquals(listOf("bundle.zip"), Deliverables.find("saved to /home/user/tmp/bundle.zip", r).map { it.name })
    }

    @Test fun `missing files, traversal and absolute paths outside the project yield nothing`() {
        val r = root(); File(r.parentFile, "secret.zip").writeText("s")
        assertTrue(Deliverables.find("see ghost.zip", r).isEmpty())
        assertNull(Deliverables.resolve("../secret.zip", r))
        assertNull(Deliverables.resolve("sandbox:/../../secret.zip", r))
        assertTrue(Deliverables.find("no files here at all", r).isEmpty())
    }

    @Test fun `size labels and mime types`() {
        assertEquals("512 B", Deliverables.human(512)); assertEquals("2.0 KB", Deliverables.human(2048)); assertEquals("3.0 MB", Deliverables.human(3L * 1024 * 1024))
        assertEquals("application/zip", Deliverables.mime(File("a.zip")))
        assertEquals("application/vnd.android.package-archive", Deliverables.mime(File("a.APK")))
    }
}
