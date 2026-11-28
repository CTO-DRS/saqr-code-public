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
