package com.ctodrs.saqrcode.device

/** v0.1.72 · What Shizuku can do for us right now. Pure mapping; the live probe is in [ShizukuProbe]. */
enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY }

object ShizukuStatus {
    fun state(installed: Boolean, binderAlive: Boolean, permissionGranted: Boolean): ShizukuState = when {
        !binderAlive -> if (installed) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
        !permissionGranted -> ShizukuState.NEEDS_PERMISSION
        else -> ShizukuState.READY
    }
}

/** Live check against the Shizuku app. Never throws: any failure reads as "not running". */
object ShizukuProbe {
    private const val PKG = "moe.shizuku.privileged.api"

    fun state(ctx: android.content.Context): ShizukuState {
        val installed = runCatching { ctx.packageManager.getPackageInfo(PKG, 0); true }.getOrDefault(false)
        val alive = runCatching { rikka.shizuku.Shizuku.pingBinder() }.getOrDefault(false)
        val granted = alive && runCatching { rikka.shizuku.Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
        return ShizukuStatus.state(installed, alive, granted)
    }

    fun requestPermission() { runCatching { rikka.shizuku.Shizuku.requestPermission(4711) } }
}

/**
 * v0.1.74 · Runs commands as the Shizuku shell user (uid 2000) through Shizuku's own process API.
 * That API is hidden in the library, so it is reached by reflection; any failure reads as "not available"
 * and the command falls back to the next engine. Every command still passes the CommandEngine risk gate first.
 */
class ShizukuEngine(private val ctx: android.content.Context) : DeviceEngine {
    override val id = "shizuku"
    override suspend fun available(): Boolean = ShizukuProbe.state(ctx) == ShizukuState.READY
    override suspend fun exec(command: String, timeoutMs: Long): CommandResult =
        runStarted(id, command, timeoutMs) { newProcess(arrayOf("sh", "-c", command)) }

    companion object {
        fun newProcess(argv: Array<String>): Process {
            val m = rikka.shizuku.Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            return m.invoke(null, argv, null, null) as Process
        }

        /** Picks engines in order of how much they can do, only those that exist. */
        fun order(state: ShizukuState, rooted: Boolean): List<String> =
            buildList { if (state == ShizukuState.READY) add("shizuku"); if (rooted) add("root"); add("local") }
    }
}
