package com.ctodrs.saqrcode.ondevice

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import com.ctodrs.saqrcode.terminal.LinuxEnv
import com.ctodrs.saqrcode.terminal.TarExtractor
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * v0.7/v0.8 · بناء APK على الجهاز · On-device APK build (experimental).
 *
 * Prepares a Debian-based build environment inside proot on the phone:
 *  1. Ubuntu base arm64 rootfs (glibc, ~34 MB) extracted next to the Alpine runtime rootfs;
 *  2. OpenJDK 17 + unzip installed through apt inside proot;
 *  3. Android cmdline-tools + `platforms;android-35` via sdkmanager (Java jars, arch-free);
 *  4. an arm64 `aapt2` binary (Google does not publish linux-arm64 build-tools; aapt2 is the
 *     only native binary AGP needs) fetched from a mirror list, ELF-verified;
 *  5. a Gradle 8.11.1 distribution (Java, arch-free).
 * Then `assembleDebug` runs inside proot with `android.aapt2FromMavenOverride` pointed at
 * the arm64 binary, and the produced APK is installed through a content-URI intent.
 *
 * v0.8: every download is resumable and pinned to the publisher's SHA-256
 * ([Downloader]), aapt2 comes from a mirror list that accepts a user URL from Settings,
 * failures get actionable [Hints] in the log, and the tab can show the built app's crashes
 * ([LogcatStream]).
 *
 * v0.9: the aapt2 sources became curated [Aapt2Mirrors.Preset]s, `build()` can produce a
 * *signed* release APK from a user keystore ([SigningInit] init script), and cache components
 * can be measured and reclaimed through [BuildCache] and [reclaim].
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

    /** v0.8/v0.9: aapt2 source from Settings — blank, a preset id ([Aapt2Mirrors]) or a direct URL. */
    var aapt2Mirror: String = ""

    /** v0.9: directories reclaimable from the Build tab (see [BuildCache]). */
    fun cacheComponents(): List<BuildCache.Entry> =
        BuildCache.components(context.filesDir, context.cacheDir).filter { it.id != "linux-home" }

    /**
     * v0.9: deletes a cache component. Environment components (rootfs / SDK / Gradle) also drop
     * the ready marker so the tab returns to NOT_SETUP and the next Prepare re-fetches only what
     * is missing.
     */
    fun reclaim(entry: BuildCache.Entry): Boolean {
        val deleted = entry.dir.deleteRecursively()
        val touchesEnv = entry.dir.absolutePath in setOf(rootfs.absolutePath, sdkDir.absolutePath, gradleDir.absolutePath)
        if (touchesEnv) {
            readyMarker.takeIf { it.exists() }?.delete()
            if (_phase.value != Phase.BOOTSTRAPPING && _phase.value != Phase.BUILDING) _phase.value = Phase.NOT_SETUP
        }
        addLog(if (deleted) "✓ reclaimed " + entry.label else "! nothing to reclaim in " + entry.label)
        return deleted
    }

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
                val gz = fetch(ART_ROOTFS, "Downloading Debian base (ubuntu-base arm64)…", "rootfs (~34 MB)")
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
                val zip = fetch(ART_CMDTOOLS, "Downloading Android cmdline-tools…", "cmdline-tools (~150 MB)")
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

            fetchAapt2()

            if (!File(gradleDir, "gradle-8.11.1/bin/gradle").isFile) {
                val zip = fetch(ART_GRADLE, "Downloading Gradle 8.11.1…", "Gradle (~130 MB)")
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
            failWithHints("Bootstrap", e)
            false
        }
    }

    /** v0.8/v0.9: fetches the arm64 aapt2 through the curated mirrors and ELF-validates it. */
    private fun fetchAapt2() {
        val aapt2 = File(sdkDir, "build-tools/35.0.0/aapt2")
        if (aapt2.isFile) {
            if (Elf.isArm64(aapt2)) { addLog("✓ aapt2 arm64 ready (cached)"); return }
            addLog("! cached aapt2 is not an arm64 ELF — refetching")
            aapt2.delete()
        }
        _line.value = "Fetching arm64 aapt2…"
        aapt2.parentFile?.mkdirs()
        val dl = File(aapt2.path + ".dl")
        val preset = Aapt2Mirrors.resolve(aapt2Mirror)
        val url = if (preset.viaApi) {
            addLog("• Looking up the latest aapt2 release (${preset.label})…")
            Downloader.releaseAssetUrl(http, listOf(preset.url), "aapt2", ".tar.gz")
        } else {
            addLog("• Fetching aapt2 from ${preset.label}")
            preset.url
        }
        simpleDownload(url, dl)
        if (dl.name.endsWith(".tar.gz") || dl.name.endsWith(".tgz") || dl.name.endsWith(".dl")) {
            // archive (or unknown content): unpack and locate the binary
            dl.inputStream().use { runCatching { TarExtractor.extractGz(it, aapt2.parentFile!!) { } } }
            dl.delete()
            if (!aapt2.isFile) {
                val inner = aapt2.parentFile?.listFiles()?.firstOrNull { it.name == "aapt2" && it.isFile }
                    ?: error("aapt2 not found inside the archive from $url")
                inner.renameTo(aapt2)
            }
        } else {
            if (!dl.renameTo(aapt2)) dl.copyTo(aapt2, overwrite = true)
            dl.delete()
        }
        if (!Elf.isArm64(aapt2)) {
            aapt2.delete()
            error("aapt2 fetched but not a valid arm64 ELF — try another mirror in Settings → On-device build")
        }
        aapt2.setExecutable(true, false)
        addLog("✓ aapt2 arm64 ready")
    }

    /** One pinned artifact into the cache dir, logging progress. Returns the local file. */
    private fun fetch(a: Downloader.Artifact, message: String, short: String): File {
        _line.value = message
        addLog("• $message ($short)")
        val dest = File(context.cacheDir, a.name)
        return Downloader.download(http, a, dest, onProgress = { done, total ->
            _line.value = message + " " + if (total > 0) "${done * 100 / total}% (${done / 1_000_000} MB)" else "(${done / 1_000_000} MB)"
        }).also { addLog("  ↓ ${a.primary.take(120)}") }
    }

    /** Unpinned direct download (only used for the aapt2 user mirror / GitHub asset). */
    private fun simpleDownload(url: String, to: File): File {
        addLog("  ↓ $url".take(200))
        to.parentFile?.mkdirs()
        val req = okhttp3.Request.Builder().url(url).header("User-Agent", "SaqrCode/0.8").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("download failed: HTTP ${resp.code}")
            to.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
        }
        return to
    }

    /** v0.8: shared failure logging with a friendly hint line. */
    private fun failWithHints(stage: String, e: Exception) {
        _line.value = ""
        _phase.value = Phase.FAILED
        val msg = e.message ?: e.toString()
        addLog("✗ $stage failed: $msg")
        Hints.forMessage(msg)?.let { addLog("→ $it") }
        addLog("  Tap “Prepare” to retry; finished steps are skipped on retry.")
    }

    // ------------------------------------------------------------ build

    /**
     * v0.9: builds the APK. Without [signing] this is the v0.7/v0.8 debug build; with a complete
     * [SigningConfig] it runs `assembleRelease` against the user's keystore (signed by AGP through
     * the generated init script) and lands in dist/app-release.apk.
     */
    suspend fun build(signing: SigningConfig? = null): Boolean = withContext(Dispatchers.IO) {
        if (_phase.value != Phase.READY) return@withContext false
        val sc = signing?.takeIf { it.complete() }
        val release = sc != null
        val ksFile = if (release) File(projectDir, sc!!.keystorePath.trim()) else null
        if (release && (ksFile?.isFile != true)) {
            _phase.value = Phase.FAILED
            addLog("✗ keystore not found in the project: " + sc!!.keystorePath)
            addLog("  → copy the .jks/.keystore file into the project (Files tab), e.g. keystore/release.jks")
            return@withContext false
        }
        _phase.value = Phase.BUILDING
        try {
            ensureGradleProps()
            val env = ArrayList<Pair<String, String>>()
            val initScript: File?
            if (release) {
                initScript = File(File(context.cacheDir, "saqr-sign"), "init.gradle")
                initScript.parentFile?.mkdirs()
                initScript.writeText(SigningInit.script())
                env.add(SigningInit.ENV_KS to "/workspace/" + sc!!.keystorePath.trim())
                env.add(SigningInit.ENV_KS_PASS to sc.storePassword)
                env.add(SigningInit.ENV_ALIAS to sc.alias.trim())
                env.add(SigningInit.ENV_KEY_PASS to sc.keyPassword)
                addLog("• Gradle assembleRelease (signed with " + sc.keystorePath.trim() + ")…")
            } else {
                initScript = null
                addLog("• Gradle assembleDebug (this can take 5–15 minutes on a phone)…")
            }
            val scriptArg = if (initScript != null) " --init-script " + SigningInit.SCRIPT_PATH else ""
            val task = if (release) "assembleRelease" else "assembleDebug"
            val cmd = "export JAVA_HOME=$javaHome ANDROID_HOME=$sdkRoot ANDROID_SDK_ROOT=$sdkRoot; " +
                "$gradleHome/bin/gradle --no-daemon -p /workspace $task$scriptArg --console=plain -Dorg.gradle.jvmargs=-Xmx1400m"
            val binds = if (initScript != null) listOf(initScript.absolutePath to SigningInit.SCRIPT_PATH) else emptyList()
            val code = prootRun(cmd, timeoutSec = 2_400, extraEnv = env, extraBinds = binds)
            if (code != 0) error("gradle exited $code")
            val variant = if (release) "release" else "debug"
            val out = File(projectDir, "app/build/outputs/apk/$variant")
            val apk = out.listFiles()?.firstOrNull { it.name.endsWith(".apk") }
                ?: error("no APK produced — check the log for the real error")
            val dist = File(projectDir, "dist").apply { mkdirs() }
            val target = File(dist, "app-$variant.apk")
            apk.copyTo(target, overwrite = true)
            _apk.value = target
            _phase.value = Phase.BUILT
            addLog("✓ APK ready: dist/" + target.name + " (" + target.length() / 1024 + " KB)")
            true
        } catch (e: Exception) {
            failWithHints("Build", e)
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

    /** v0.8: the built app's recent crashes, parsed from the device logcat. */
    fun crashReport(): List<String> {
        val pkg = LogcatStream.guessPackage(
            runCatching { File(projectDir, "app/build.gradle.kts").readText() }.getOrDefault(""),
        )
        val dump = runCatching {
            val p = ProcessBuilder("sh", "-c", LogcatStream.dumpCommand()).redirectErrorStream(true).start()
            p.outputStream.close()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor(20, TimeUnit.SECONDS)
            out
        }.getOrDefault("")
        if (dump.isBlank()) return emptyList()
        val crashes = LogcatStream.crashes(dump, pkg)
        return LogcatStream.summarize(crashes)
    }

    // ------------------------------------------------------------ process

    /** Runs [cmd] inside the Debian rootfs with the SDK/Gradle bound at /opt; streams into [log]. */
    private suspend fun prootRun(
        cmd: String,
        timeoutSec: Long,
        extraEnv: List<Pair<String, String>> = emptyList(),
        extraBinds: List<Pair<String, String>> = emptyList(),
    ): Int = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "proot-tmp").apply { mkdirs() }
        val args = buildProotArgs(cmd, extraEnv, extraBinds)
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

    /** Like [LinuxEnv.prootArgs], plus /opt binds for the SDK and Gradle and optional extra binds/env (v0.9). */
    private fun buildProotArgs(
        cmd: String,
        extraEnv: List<Pair<String, String>> = emptyList(),
        extraBinds: List<Pair<String, String>> = emptyList(),
    ): List<String> = buildList {
        add(proot.absolutePath); add("--kill-on-exit"); add("--link2symlink"); add("-L"); add("--sysvipc"); add("-0")
        add("-r"); add(rootfs.absolutePath)
        add("-b"); add("/dev"); add("-b"); add("/proc"); add("-b"); add("/sys")
        add("-b"); add(projectDir.absolutePath + ":/workspace")
        add("-b"); add(sdkDir.absolutePath + ":" + sdkRoot)
        add("-b"); add(gradleDir.absolutePath + ":/opt/gradle")
        extraBinds.forEach { (host, guest) -> add("-b"); add(host + ":" + guest) }
        add("-w"); add("/workspace")
        add("/usr/bin/env"); add("-i")
        add("HOME=/root"); add("USER=root"); add("TERM=dumb"); add("LANG=C.UTF-8")
        add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        extraEnv.forEach { (k, v) -> add("$k=$v") }
        add("/bin/sh"); add("-c"); add(cmd)
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

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

        /** Ubuntu base arm64: glibc rootfs with apt. Pinned to the publisher's SHA-256. */
        val ART_ROOTFS = Downloader.Artifact(
            "ubuntu-base.tar.gz",
            "075d4abd2817a5023ab0a82f5cb314c5ec0aa64a9c0b40fd3154ca3bfdae979f",
            listOf("https://cdimage.ubuntu.com/ubuntu-base/releases/22.04/release/ubuntu-base-22.04.5-base-arm64.tar.gz"),
        )

        /** Android cmdline-tools (Java + shell, works on arm64). Pinned to Google's checksum. */
        val ART_CMDTOOLS = Downloader.Artifact(
            "sdk-cmdtools.zip",
            "2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258",
            listOf("https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"),
        )

        /** Gradle distribution (Java, works on arm64). Pinned to gradle.org's checksum. */
        val ART_GRADLE = Downloader.Artifact(
            "gradle.zip",
            "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6",
            listOf(
                "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip",
                "https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip",
            ),
        )

        /** The arm64 aapt2 community build; resolved through the GitHub API (preset in v0.9). */
        const val GITHUB_AAPT2_API = "https://api.github.com/repos/Llzzy/aarch64-android-builds/releases/latest"
    }
}

/**
 * v0.9 · قائمة مرايا aapt2 الجاهزة · Curated arm64-aapt2 sources.
 *
 * Google publishes no linux-arm64 aapt2, so the build needs a community build. Two verified
 * sources ship as presets: the GitHub API lookup of `Llzzy/aarch64-android-builds` (the v0.8
 * default, always resolves the latest release) and the stable `releases/latest/download` URL of
 * `ReVanced/aapt2` (a raw binary asset). A direct URL from Settings wins over both; every
 * fetched binary is ELF-validated before use regardless of its source.
 */
object Aapt2Mirrors {
    data class Preset(val id: String, val label: String, val url: String, val viaApi: Boolean = false)

    val AUTO = Preset("auto", "aarch64-android-builds", OnDeviceBuild.GITHUB_AAPT2_API, viaApi = true)
    val REVANCED = Preset("revanced", "ReVanced/aapt2", "https://github.com/ReVanced/aapt2/releases/latest/download/aapt2-arm64-v8a")

    val PRESETS = listOf(AUTO, REVANCED)

    /** The effective preset for a stored setting: blank / "auto" → AUTO, preset id → it, URL → custom, else AUTO. */
    fun resolve(setting: String): Preset = when {
        setting.isBlank() || setting.trim() == AUTO.id -> AUTO
        PRESETS.any { it.id == setting.trim() } -> PRESETS.first { it.id == setting.trim() }
        setting.trim().startsWith("http") -> Preset("custom", "custom mirror", setting.trim())
        else -> AUTO
    }
}
