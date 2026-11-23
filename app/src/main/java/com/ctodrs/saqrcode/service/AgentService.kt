package com.ctodrs.saqrcode.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.ctodrs.saqrcode.MainActivity
import com.ctodrs.saqrcode.ui.Workspace

/**
 * Keeps the app process (and with it the agent loops, their coroutines and the proot / shell
 * processes they spawn, which are children of this process) alive while a turn runs: foreground
 * notification, a partial wake lock (60 min timeout, re-armed every step) and a Wi-Fi lock, held
 * only while a turn is actually running (not while it waits for the user). START_STICKY: if the
 * system kills the process, it restarts the service, which resumes interrupted turns from the
 * saved sessions.
 */
class AgentService : Service() {
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var inForeground = false
    private var type = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        when (intent?.action) {
            null -> resumeAfterKill() // sticky restart after the process was killed
            ACTION_STOP -> intent.getStringExtra(MainActivity.EXTRA_PROJECT)?.let { Workspace.peek(it)?.agent?.stop() }
            ACTION_ANSWER -> {
                val p = intent.getStringExtra(MainActivity.EXTRA_PROJECT)
                val q = intent.getStringExtra(EXTRA_QUESTION)
                val o = intent.getStringExtra(EXTRA_OPTION)
                if (p != null && q != null && o != null) {
                    Notifs.cancelAttention(this, p)
                    Workspace.get(p)?.agent?.answerFromNotification(q, o)
                }
            }
        }
        syncWithRuntime()
        return START_STICKY
    }

    /** The OS killed the process mid-turn: continue each interrupted turn from its saved history. */
    private fun resumeAfterKill() {
        val projects = AgentRuntime.active.read()
        for (p in projects) {
            val ws = runCatching { Workspace.get(p) }.getOrNull() ?: continue
            if (!ws.agent.resumeInterrupted()) {
                // waiting on a question or nothing to resume: nothing runs, keep the chat as it was
                com.ctodrs.saqrcode.agent.SessionRepair.pendingAsk(ws.agent.items)?.let {
                    Notifs.attention(this, p, com.ctodrs.saqrcode.agent.RunWait.forAsk(it.request), AgentRuntime.lang())
                }
                AgentRuntime.active.remove(p)
            }
        }
    }

    @SuppressLint("MissingPermission") // the ongoing notification of a foreground service
    private fun goForeground() {
        val n = Notifs.ongoing(this, AgentRuntime.headline(), (AgentRuntime.runs().size - 1).coerceAtLeast(0), AgentRuntime.lang())
        if (!inForeground) {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            startFg(n, type)
            inForeground = true
        } else runCatching { getSystemService(android.app.NotificationManager::class.java).notify(Notifs.ID_ONGOING, n) }
    }

    private fun startFg(n: android.app.Notification, t: Int) {
        try { ServiceCompat.startForeground(this, Notifs.ID_ONGOING, n, t) }
        catch (e: Exception) {
            // e.g. dataSync not allowed (time limit on Android 15): use the special-use type
            if (Build.VERSION.SDK_INT >= 34 && t != ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) {
                runCatching { ServiceCompat.startForeground(this, Notifs.ID_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
                    .onSuccess { type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE }
                    .onFailure { stopSelf() }
            } else stopSelf()
        }
    }

    /** Android 15: a dataSync service gets ~6 h per day. Switch to the special-use type, or stop. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        if (Build.VERSION.SDK_INT >= 34 && fgsType != ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) {
            val n = Notifs.ongoing(this, AgentRuntime.headline(), (AgentRuntime.runs().size - 1).coerceAtLeast(0), AgentRuntime.lang())
            runCatching { ServiceCompat.startForeground(this, Notifs.ID_ONGOING, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
                .onSuccess { type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE; return }
        }
        releaseLocks()
        stopSelf()
    }

    /** Match notification and locks to the runtime; stop when no turn is left. */
    fun syncWithRuntime() {
        if (AgentRuntime.runs().isEmpty()) {
            releaseLocks()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            inForeground = false
            stopSelf()
            return
        }
        goForeground()
        if (AgentRuntime.locksNeeded) acquireLocks() else releaseLocks()
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val w = wake ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SaqrCode:agent").apply { setReferenceCounted(false) }.also { wake = it }
        if (!w.isHeld) w.acquire(WAKE_TIMEOUT_MS)
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val l = wifi ?: wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SaqrCode:agent").apply { setReferenceCounted(false) }.also { wifi = it }
        if (!l.isHeld) runCatching { l.acquire() }
    }

    /** Re-arm the wake lock's 60-minute safety timeout (called once per agent step). */
    fun renewLocks() {
        if (!AgentRuntime.locksNeeded) return
        wake?.let { if (it.isHeld) it.release() }
        acquireLocks()
    }

    private fun releaseLocks() {
        wake?.let { if (it.isHeld) it.release() }
        wifi?.let { if (it.isHeld) runCatching { it.release() } }
    }

    /** Swiped from recents: keep going while a turn runs (the process stays up thanks to the foreground service). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Workspace.saveAll()
        if (AgentRuntime.runs().isEmpty()) stopSelf()
    }

    override fun onDestroy() {
        releaseLocks()
        Workspace.saveAll()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_RUN = "com.ctodrs.saqrcode.action.RUN"
        const val ACTION_STOP = "com.ctodrs.saqrcode.action.STOP"
        const val ACTION_ANSWER = "com.ctodrs.saqrcode.action.ANSWER"
        const val EXTRA_QUESTION = "question"
        const val EXTRA_OPTION = "option"
        const val WAKE_TIMEOUT_MS = 60 * 60 * 1000L
        @Volatile var instance: AgentService? = null; private set
    }
}
