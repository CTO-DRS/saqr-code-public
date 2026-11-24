package com.ctodrs.saqrcode.terminal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Root-less Linux: proot (bundled as lib*.so so it is exec-able from nativeLibraryDir on
 * Android 10+) + Alpine minirootfs downloaded on first use. The current project is bound
 * at /workspace. Falls back to Android's /system/bin/sh when the rootfs is not installed.
 */
class LinuxEnv(private val context: Context) {
    enum class State { NOT_INSTALLED, INSTALLING, READY, ERROR }

    val rootfs = File(context.filesDir, "linux/alpine")
    private val marker = File(rootfs, ".saqr_installed")
    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    private val proot = File(nativeDir, "libproot.so")

    private val _state = MutableStateFlow(if (marker.exists()) State.READY else State.NOT_INSTALLED)
    val state: StateFlow<State> = _state
    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress

    val prootAvailable get() = proot.exists()
    val ready get() = marker.exists() && prootAvailable

    suspend fun install(): Result<Unit> = withContext(Dispatchers.IO) {
        _state.value = State.INSTALLING
        runCatching {
            val tmp = File(context.cacheDir, "alpine.tar.gz")
            _progress.value = "Downloading Alpine $ALPINE_VERSION…"
            val http = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build()
            http.newCall(Request.Builder().url(ALPINE_URL).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("Download failed: HTTP ${resp.code}")
                tmp.outputStream().use { resp.body!!.byteStream().copyTo(it) }
            }
            val sha = sha256(tmp)
            check(sha.equals(ALPINE_SHA256, ignoreCase = true)) { "Checksum mismatch" }
            _progress.value = "Extracting…"
            rootfs.deleteRecursively()
            rootfs.mkdirs()
            var n = 0
            tmp.inputStream().use { TarExtractor.extractGz(it, rootfs) { if (++n % 50 == 0) _progress.value = "Extracting… $n" } }
            tmp.delete()
            File(rootfs, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            File(rootfs, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
            File(rootfs, "workspace").mkdirs()
            File(rootfs, "root").mkdirs()
            File(rootfs, "etc/profile.d").mkdirs()
            writeProfile()
            marker.writeText(ALPINE_VERSION)
            _progress.value = ""
            _state.value = State.READY
        }.onFailure {
            _progress.value = it.message ?: "error"
            _state.value = State.ERROR
        }
    }

    /**
     * Builds a process for [shellArgs] (e.g. ["-c", cmd] or ["-i"]) inside the env, cwd = project.
     * [mergeStderr] stays true for one-shot commands; stdio MCP servers need it false so stderr
     * banners never pollute the JSON-RPC stream on stdout.
     */
    fun processBuilder(projectDir: File, shellArgs: List<String>, mergeStderr: Boolean = true): ProcessBuilder {
        val pb: ProcessBuilder
        if (ready) {
            val tmp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
            val cmd = prootArgs(proot.absolutePath, rootfs.absolutePath, projectDir.absolutePath, shellArgs)
            pb = ProcessBuilder(cmd)
            pb.environment().apply {
                put("PROOT_LOADER", File(nativeDir, "libproot-loader.so").absolutePath)
                put("PROOT_TMP_DIR", tmp.absolutePath)
                put("LD_LIBRARY_PATH", nativeDir.absolutePath)
            }
        } else {
            pb = ProcessBuilder(listOf("/system/bin/sh") + shellArgs)
            pb.environment()["HOME"] = projectDir.absolutePath
        }
        pb.directory(projectDir)
        if (mergeStderr) pb.redirectErrorStream(true)
        return pb
    }

    /** argv0-first command, cwd and environment for an interactive PTY session (Termux JNI). */
    data class PtyCommand(val executable: String, val args: Array<String>, val env: Array<String>, val cwd: String, val linux: Boolean)

    private fun writeProfile() = runCatching {
        File(rootfs, "etc/profile.d").mkdirs()
        File(rootfs, "etc/profile.d/saqr.sh").writeText(PROFILE)
    }

    fun ptyCommand(projectDir: File): PtyCommand {
        if (ready) {
            writeProfile()
            val tmp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
            val args = prootArgs(proot.absolutePath, rootfs.absolutePath, projectDir.absolutePath, listOf("-l"), term = "xterm-256color")
            val env = arrayOf(
                "PROOT_LOADER=" + File(nativeDir, "libproot-loader.so").absolutePath,
                "PROOT_TMP_DIR=" + tmp.absolutePath,
                "LD_LIBRARY_PATH=" + nativeDir.absolutePath,
                "PATH=/system/bin", "TERM=xterm-256color", "HOME=" + projectDir.absolutePath,
            )
            return PtyCommand(proot.absolutePath, args.toTypedArray(), env, projectDir.absolutePath, true)
        }
        val env = arrayOf("TERM=xterm-256color", "HOME=" + projectDir.absolutePath, "PATH=/system/bin:/system/xbin", "TMPDIR=" + context.cacheDir.absolutePath)
        return PtyCommand("/system/bin/sh", arrayOf("sh"), env, projectDir.absolutePath, false)
    }

    data class ExecResult(val exitCode: Int, val output: String, val timedOut: Boolean)

    /** One-shot command used by the agent. Output is capped. */
    suspend fun exec(projectDir: File, command: String, timeoutSec: Long = 120, maxOutput: Int = 30_000): ExecResult =
        withContext(Dispatchers.IO) {
            val p = processBuilder(projectDir, listOf("-c", command)).start()
            p.outputStream.close()
            val out = StringBuilder()
            val reader = Thread {
                runCatching {
                    p.inputStream.bufferedReader().use { r ->
                        val buf = CharArray(4096)
                        while (true) {
                            val n = r.read(buf); if (n < 0) break
                            synchronized(out) { if (out.length < maxOutput * 2) out.append(buf, 0, n) }
                        }
                    }
                }
            }.apply { start() }
            val finished = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) p.destroyForcibly()
            reader.join(2000)
            var text = synchronized(out) { Ansi.strip(out.toString()) }
            if (text.length > maxOutput) text = text.take(maxOutput / 2) + "\n…[truncated]…\n" + text.takeLast(maxOutput / 2)
            ExecResult(if (finished) p.exitValue() else -1, text, !finished)
        }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(65536); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** Pure, testable proot command line. */
        fun prootArgs(proot: String, rootfs: String, projectDir: String, shellArgs: List<String>, term: String = "dumb"): List<String> = listOf(
            proot, "--kill-on-exit", "--link2symlink", "-L", "--sysvipc", "-0",
            "-r", rootfs,
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "$projectDir:/workspace",
            "-w", "/workspace",
            "/usr/bin/env", "-i",
            "HOME=/root", "USER=root", "TERM=$term", "LANG=C.UTF-8", "COLORTERM=truecolor",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "/bin/sh",
        ) + shellArgs

        /** Coloured prompt (Saqr gold user, soft blue path) + colour aliases for the PTY terminal. */
        const val PROFILE = "export PS1='\\[\\e[1;38;2;233;180;76m\\]saqr\\[\\e[0m\\]:\\[\\e[38;5;110m\\]\\w\\[\\e[0m\\]\\$ '\n" +
            "alias ls='ls --color=auto'\nalias grep='grep --color=auto'\nexport CLICOLOR=1\n"

        const val ALPINE_VERSION = "3.24.4"
        const val ALPINE_URL = "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.4-aarch64.tar.gz"
        const val ALPINE_SHA256 = "4b14c18f7e90a063b61462ea1462a5732c154bf6adad594974fe6487cd98a8de"
    }
}

object Ansi {
    private val re = Regex("""\u001B\[[0-9;?]*[ -/]*[@-~]|\u001B\][^\u0007]*\u0007|\u001B[()][AB012]|\r(?!\n)""")
    fun strip(s: String) = re.replace(s, "")
}
