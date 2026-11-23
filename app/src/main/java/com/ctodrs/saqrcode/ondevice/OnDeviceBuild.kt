package com.ctodrs.saqrcode.ondevice

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.ctodrs.saqrcode.terminal.LinuxEnv
import com.ctodrs.saqrcode.terminal.TarExtractor
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * v0.7 · بناء APK على الجهاز · On-device APK build (experimental, "lite" path).
 *
 * Prepares a Debian-based build environment inside proot on the phone:
 *  1. Ubuntu base arm64 rootfs (glibc, ~34 MB) extracted next to the Alpine runtime rootfs;
 *  2. OpenJDK 17 + unzip installed through apt inside proot;
 *  3. Android cmdline-tools + `platforms;android-35` via sdkmanager (Java jars, arch-free);
 *  4. an arm64 `aapt2` binary fetched from a community GitHub release (Google does not
 *     publish linux-arm64 build-tools; aapt2 is the only native binary AGP needs);
 *  5. a Gradle 8.11.1 distribution (Java, arch-free).
 * Then `assembleDebug` runs inside proot with `android.aapt2FromMavenOverride` pointed at
 * the arm64 binary, and the produced APK is installed through a content-URI intent.
 *
 * Everything streams into [log]; the whole path is explicit so failures say what to fix.
 * On a mid-range phone the first bootstrap downloads ~600 MB and a small Compose app
 * builds in 5–15 minutes.
 */
class OnDeviceBuild(private val context: Context, val projectDir: File) {

    enum class Phase { NOT_SETUP, BOOTSTRAPPING, READY, BUILDING, BUILT, FAILED }

    // Paths on the Android filesystem (outside any rootfs).
    private val rootfs = File(context.filesDir, "linux/debian")
    private val sdkDir = File(context.filesDir, "linux/android-sdk")
    private val gradleDir = File(context.filesDir, "linux/gradle")
    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    private val proot = File(nativeDir, "libproot.so")
    private val readyMarker = File(context.filesDir, "linux/.saqr_build_ok")

    private val _phase = MutableStateFlow(if (readyMarker.exists()) Phase.READY else Phase.NOT_SETUP)
    val phase: StateFlow<Phase> = _phase
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log
    private val _line = MutableStateFlow("")
    val progressLine: StateFlow<String> = _line
    private val _apk = MutableStateFlow<File?>(findApk())
    val apk: StateFlow<File?> = _apk

    private val javaHome = "/usr/lib/jvm/java-17-openjdk-arm64"
    private val sdkRoot = "/opt/android-sdk"
    private val gradleHome = "/opt/gradle/gradle-8.11.1"

    private fun addLog(l: String) {
        _log.value = (_log.value + l).takeLast(MAX_LOG_LINES)
    }

    private fun findApk(): File? {
        val dir = File(projectDir, "dist")
        return dir.listFiles()?.filter { it.isFile && it.name.endsWith(".apk") }?.maxByOrNull { it.lastModified() }
    }

    // ------------------------------------------------------------ bootstrap

    suspend fun bootstrap(): Boolean = withContext(Dispatchers.IO) {
        if (_phase.value == Phase.READY) return@withContext true
        _phase.value = Phase.BOOTSTRAPPING
        try {
            if (!File(rootfs, ".saqr_installed").isFile) {
                _line.value = "Downloading Debian base (ubuntu-base arm64)…"
                addLog("• Downloading rootfs (~34 MB)…")
                val gz = download(UBUNTU_BASE_URL, File(context.cacheDir, "ubuntu-base.tar.gz"))
                rootfs.deleteRecursively(); rootfs.mkdirs()
                gz.inputStream().use { TarExtractor.extractGz(it, rootfs) { } }
                gz.delete()
                File(rootfs, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
                File(rootfs, "etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
                File(rootfs, ".saqr_installed").writeText("ubuntu-base")
                addLog("✓ rootfs ready")
            }

            if (!File(rootfs, javaHome + "/bin/java").isFile) {
                _line.value = "Installing OpenJDK 17 (apt)…"
                addLog("• apt-get install openjdk-17-jdk-headless unzip (several minutes on first run)…")
                val code = prootRun(
                    "export DEBIAN_FRONTEND=noninteractive; apt-get update -qq && apt-get install -y -qq openjdk-17-jdk-headless unzip > /tmp/apt.log 2>&1 || tail -20 /tmp/apt.log",
                    timeoutSec = 1_200,
                )
                if (code != 0) error("apt-get failed (exit $code) — check the log below")
                addLog("✓ JDK 17 ready")
            }

            if (!File(sdkDir, "cmdline-tools/latest/bin/sdkmanager").isFile) {
                _line.value = "Downloading Android cmdline-tools…"
                val zip = download(CMDTOOLS_URL, File(context.cacheDir, "sdk-cmdtools.zip"))
                addLog("• Extracting cmdline-tools…")
                val tmp = File(context.cacheDir, "sdk-cmdtools")
                tmp.deleteRecursively(); tmp.mkdirs()
                unzip(zip, tmp)
                val moved = File(tmp, "moved")
                File(tmp, "cmdline-tools").renameTo(moved) // zip root folder
                val latest = File(sdkDir, "cmdline-tools/latest")
                latest.parentFile?.mkdirs()
                moved.renameTo(latest)
                zip.delete(); tmp.deleteRecursively()
                addLog("✓ cmdline-tools ready")
            }

            if (!File(sdkDir, "platforms/android-35").isDirectory) {
                _line.value = "Installing android-35 platform (sdkmanager)…"
                addLog("• sdkmanager platforms;android-35…")
                val code = prootRun(
                    "export JAVA_HOME=$javaHome ANDROID_HOME=$sdkRoot; " +
                        "yes | $sdkRoot/cmdline-tools/latest/bin/sdkmanager --sdk_root=$sdkRoot --licenses > /dev/null 2>&1; " +
                        "$sdkRoot/cmdline-tools/latest/bin/sdkmanager --sdk_root=$sdkRoot \"platforms;android-35\" > /tmp/sdkm.log 2>&1 || tail -20 /tmp/sdkm.log",
                    timeoutSec = 1_200,
                )
                if (code != 0) error("sdkmanager failed (exit $code)")
                addLog("✓ android-35 platform ready")
            }

            val aapt2 = File(sdkDir, "build-tools/35.0.0/aapt2")
            if (!aapt2.isFile) {
                _line.value = "Fetching arm64 aapt2…"
                addLog("• Looking up the latest aapt2 release (aarch64-android-builds)…")
                val api = getJson(GITHUB_AAPT2_API)
                val assets = api.optJSONArray("assets") ?: error("no assets in the aapt2 release")
                var url = ""
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val name = a.optString("name")
                    if (name.lowercase().contains("aapt2") && (name.endsWith(".tar.gz") || name.equals("aapt2", ignoreCase = true))) {
                        url = a.optString("browser_download_url")
                        if (name.endsWith(".tar.gz")) break
                    }
                }
                if (url.isEmpty()) error(
                    "No aapt2 asset found. Put an arm64 aapt2 binary manually at files/linux/android-sdk/build-tools/35.0.0/aapt2 and retry.",
                )
                aapt2.parentFile?.mkdirs()
                val f = download(url, aapt2)
                if (f.name.endsWith(".tar.gz")) {
                    f.inputStream().use { TarExtractor.extractGz(it, aapt2.parentFile!!) { } }
                    f.delete()
                    val inner = aapt2.parentFile?.listFiles()?.firstOrNull { it.name == "aapt2" && it.isFile }
                    check(inner != null) { "aapt2 not found inside the archive" }
                }
                aapt2.setExecutable(true, false)
                addLog("✓ aapt2 arm64 ready")
            }

            if (!File(gradleDir, "gradle-8.11.1/bin/gradle").isFile) {
                _line.value = "Downloading Gradle 8.11.1…"
                addLog("• gradle-8.11.1-bin.zip (~130 MB)…")
                val zip = download(GRADLE_URL, File(context.cacheDir, "gradle.zip"))
                val tmp = File(context.cacheDir, "gradle-unz")
                tmp.deleteRecursively(); tmp.mkdirs()
                unzip(zip, tmp)
                gradleDir.deleteRecursively(); gradleDir.mkdirs()
                File(tmp, "gradle-8.11.1").renameTo(File(gradleDir, "gradle-8.11.1"))
                zip.delete(); tmp.deleteRecursively()
                addLog("✓ Gradle ready")
            }

            readyMarker.writeText("ok")
            _line.value = ""
            _phase.value = Phase.READY
            addLog("✓ Build environment ready — “Build APK” is unlocked.")
            true
        } catch (e: Exception) {
            _line.value = ""
            _phase.value = Phase.FAILED
            addLog("✗ Bootstrap failed: ${e.message}")
            addLog("  Tap “Prepare” to retry; finished steps are skipped on retry.")
            false
        }
    }

    // ------------------------------------------------------------ build

    suspend fun build(): Boolean = withContext(Dispatchers.IO) {
        if (_phase.value != Phase.READY) return@withContext false
        _phase.value = Phase.BUILDING
        try {
            ensureGradleProps()
            addLog("• Gradle assembleDebug (this can take 5–15 minutes on a phone)…")
            val cmd = "export JAVA_HOME=$javaHome ANDROID_HOME=$sdkRoot ANDROID_SDK_ROOT=$sdkRoot; " +
                "$gradleHome/bin/gradle --no-daemon -p /workspace assembleDebug --console=plain -Dorg.gradle.jvmargs=-Xmx1400m"
            val code = prootRun(cmd, timeoutSec = 2_400)
            if (code != 0) error("gradle exited $code")
            val out = File(projectDir, "app/build/outputs/apk/debug")
            val apk = out.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
                ?: error("no APK produced — check the log for the real error")
            val dist = File(projectDir, "dist").apply { mkdirs() }
            val target = File(dist, "app-debug.apk")
            apk.copyTo(target, overwrite = true)
            _apk.value = target
            _phase.value = Phase.BUILT
            addLog("✓ APK ready: dist/app-debug.apk (" + target.length() / 1024 + " KB)")
            true
        } catch (e: Exception) {
            _line.value = ""
            _phase.value = Phase.FAILED
            addLog("✗ Build failed: ${e.message}")
            addLog("  Common fixes: a compile error in the project (see the log), or memory — close other apps.")
            false
        }
    }

    /** Points AGP at the arm64 aapt2 and keeps the memory sane, in the project's gradle.properties. */
    private fun ensureGradleProps() {
        val f = File(projectDir, "gradle.properties")
        val current = runCatching { if (f.isFile) f.readText() else "" }.getOrDefault("")
        val lines = current.lines().toMutableList()
        fun upsert(key: String, value: String) {
            val i = lines.indexOfFirst { it.startsWith(key + "=") }
            if (i >= 0) lines[i] = "$key=$value" else lines.add("$key=$value")
        }
        upsert("android.aapt2FromMavenOverride", "$sdkRoot/build-tools/35.0.0/aapt2")
        if (lines.none { it.startsWith("org.gradle.jvmargs=") }) upsert("org.gradle.jvmargs", "-Xmx1400m")
        f.parentFile?.mkdirs()
        f.writeText(lines.joinToString("\n"))
    }

    /** The "Install" intent for the produced APK (user confirms unknown-sources install). */
    fun installIntent(): Intent? {
        val apk = _apk.value ?: return null
        val share = File(context.cacheDir, "apk").apply { mkdirs() }
        val tmp = File(share, apk.name)
        apk.copyTo(tmp, overwrite = true)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", tmp)
        return Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // ------------------------------------------------------------ process + download

    /** Runs [cmd] inside the Debian rootfs with the SDK/Gradle bound at /opt; streams into [log]. */
    private suspend fun prootRun(cmd: String, timeoutSec: Long): Int = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
        val args = buildProotArgs(cmd)
        val pb = ProcessBuilder(args).directory(projectDir).redirectErrorStream(true)
        pb.environment().apply {
            put("PROOT_LOADER", File(nativeDir, "libproot-loader.so").absolutePath)
            put("PROOT_TMP_DIR", tmp.absolutePath)
            put("LD_LIBRARY_PATH", nativeDir.absolutePath)
        }
        val p = pb.start()
        p.outputStream.close()
        val started = System.currentTimeMillis()
        val reader = Thread {
            runCatching {
                p.inputStream.bufferedReader().use { r ->
                    while (true) {
                        val l = r.readLine() ?: break
                        if (l.isBlank()) continue
                        addLog(l.take(240))
                        _line.value = l.take(120)
                    }
                }
            }
        }.apply { start() }
        val finished = p.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) { p.destroyForcibly(); addLog("[timed out after ${timeoutSec}s]") }
        reader.join(2_000)
        _line.value = "…${(System.currentTimeMillis() - started) / 1000}s"
        if (finished) p.exitValue() else -1
    }

    /** Like [LinuxEnv.prootArgs], plus /opt binds for the SDK and Gradle. */
    private fun buildProotArgs(cmd: String): List<String> = listOf(
        proot.absolutePath, "--kill-on-exit", "--link2symlink", "-L", "--sysvipc", "-0",
        "-r", rootfs.absolutePath,
        "-b", "/dev", "-b", "/proc", "-b", "/sys",
        "-b", projectDir.absolutePath + ":/workspace",
        "-b", sdkDir.absolutePath + ":" + sdkRoot,
        "-b", gradleDir.absolutePath + ":/opt/gradle",
        "-w", "/workspace",
        "/usr/bin/env", "-i",
        "HOME=/root", "USER=root", "TERM=dumb", "LANG=C.UTF-8",
        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "/bin/sh", "-c", cmd,
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun download(url: String, to: File): File {
        addLog("  ↓ $url")
        val req = Request.Builder().url(url).header("User-Agent", "SaqrCode/0.7").build()
        http.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "download failed: HTTP ${resp.code}" }
            to.parentFile?.mkdirs()
            to.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
        }
        return to
    }

    private fun getJson(url: String): JSONObject {
        val req = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SaqrCode/0.7").build()
        http.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "GitHub API HTTP ${resp.code}" }
            return JSONObject(resp.body!!.string())
        }
    }

    private fun unzip(zip: File, target: File) {
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { z ->
            var e = z.nextEntry
            val root = target.canonicalPath
            while (e != null) {
                val f = File(target, e.name)
                if (!f.canonicalPath.startsWith(root)) error("bad zip entry: " + e.name)
                if (e.isDirectory) f.mkdirs()
                else {
                    f.parentFile?.mkdirs()
                    f.outputStream().use { z.copyTo(it) }
                }
                z.closeEntry()
                e = z.nextEntry
            }
        }
    }

    companion object {
        const val MAX_LOG_LINES = 400

        /** Ubuntu base arm64: glibc rootfs with apt. */
        const val UBUNTU_BASE_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04.5-base-arm64.tar.gz"

        /** Android cmdline-tools (Java + shell, works on arm64). */
        const val CMDTOOLS_URL = "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

        /** Gradle distribution (Java, works on arm64). */
        const val GRADLE_URL = "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip"

        /** The arm64 aapt2 community build; resolved through the GitHub API for the latest release. */
        const val GITHUB_AAPT2_API = "https://api.github.com/repos/Llzzy/aarch64-android-builds/releases/latest"
    }
}
