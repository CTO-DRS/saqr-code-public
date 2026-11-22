package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.terminal.LinuxEnv
import com.ctodrs.saqrcode.terminal.TarExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Opt-in (SAQR_INTEGRATION=1, needs network + a host `proot`): downloads the pinned Alpine
 * minirootfs, verifies its sha256, extracts it with our TarExtractor and runs commands with
 * the exact proot argument list the app uses.
 */
class LinuxIntegrationTest {
    @Test fun alpineThroughProot() {
        assumeTrue(System.getenv("SAQR_INTEGRATION") == "1")
        val proot = System.getenv("SAQR_PROOT") ?: "/usr/bin/proot"

        val work = Files.createTempDirectory("saqr-it").toFile()
        val tgz = File(work, "alpine.tar.gz")
        URL(LinuxEnv.ALPINE_URL).openStream().use { i -> tgz.outputStream().use { i.copyTo(it) } }
        val sha = MessageDigest.getInstance("SHA-256").digest(tgz.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(LinuxEnv.ALPINE_SHA256, sha)

        val rootfs = File(work, "rootfs")
        tgz.inputStream().use { TarExtractor.extractGz(it, rootfs) }
        // Structural checks of the extracted rootfs.
        assertTrue(File(rootfs, "etc/alpine-release").readText().contains(LinuxEnv.ALPINE_VERSION))
        assertTrue(File(rootfs, "bin/busybox").canExecute())
        assertTrue(Files.isSymbolicLink(File(rootfs, "bin/sh").toPath()))
        assertTrue(File(rootfs, "lib/ld-musl-aarch64.so.1").exists())
        // Optional: compare against a reference extraction made with GNU tar.
        System.getenv("SAQR_REF_ROOTFS")?.let { ref ->
            fun listing(root: File) = root.walkTopDown().map { f ->
                val rel = f.relativeTo(root).path
                val link = Files.isSymbolicLink(f.toPath())
                "$rel|${if (link) "L:" + Files.readSymbolicLink(f.toPath()) else if (f.isDirectory) "D" else "F:" + f.length() + ":" + f.canExecute()}"
            }.toSortedSet()
            val mine = listing(rootfs); val theirs = listing(File(ref))
            assertEquals("diff: " + (theirs - mine).take(10) + " / " + (mine - theirs).take(10), theirs, mine)
            println("rootfs identical to GNU tar extraction: ${mine.size} entries")
        }
        if (System.getenv("SAQR_PROOT_RUN") != "1" || !File(proot).canExecute()) return
        File(rootfs, "etc/resolv.conf").writeText("nameserver 8.8.8.8\n")
        val project = File(work, "proj").apply { mkdirs() }
        File(project, "hello.txt").writeText("مرحبا\n")

        fun run(cmd: String): Pair<Int, String> {
            // Host (Debian/upstream) proot lacks the Termux-only flags; drop just those.
            val unsupported = (System.getenv("SAQR_PROOT_DROP") ?: "").split(',').filter { it.isNotBlank() }.toSet()
            val args = LinuxEnv.prootArgs(proot, rootfs.path, project.path, listOf("-c", cmd)).filter { it !in unsupported }
            val pb = ProcessBuilder(args)
            pb.redirectErrorStream(true)
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor(60, TimeUnit.SECONDS)
            return p.exitValue() to out
        }

        val (c1, o1) = run("cat /etc/alpine-release && pwd && cat hello.txt && id -u && echo x > made.txt")
        println(o1)
        assertEquals(0, c1)
        assertTrue(o1.contains(LinuxEnv.ALPINE_VERSION))
        assertTrue(o1.contains("/workspace"))
        assertTrue(o1.contains("مرحبا"))
        assertTrue(File(project, "made.txt").exists())
        val (c2, o2) = run("ls /bin | head -3; busybox --help | head -1")
        println(o2)
        assertEquals(0, c2)
    }
}
