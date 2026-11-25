package com.ctodrs.saqrcode

import android.app.Application
import com.ctodrs.saqrcode.data.ProjectRepository
import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import com.ctodrs.saqrcode.terminal.LinuxEnv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Tiny manual DI container. */
class SaqrApp : Application() {
    lateinit var secure: SecureStore; private set
    lateinit var settings: SettingsStore; private set
    lateinit var projects: ProjectRepository; private set
    lateinit var linux: LinuxEnv; private set
    /** v0.12: last on-device build result per project, for the live home cards. */
    lateinit var builds: com.ctodrs.saqrcode.data.BuildHistory; private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        secure = SecureStore(this)
        settings = SettingsStore(this, secure)
        projects = ProjectRepository(this)
        linux = LinuxEnv(this)
        builds = com.ctodrs.saqrcode.data.BuildHistory(this)
        // v0.13.5 desert mode: the stored mode seeds the gate, then the network watcher keeps it honest.
        com.ctodrs.saqrcode.agent.DesertState.mode =
            com.ctodrs.saqrcode.agent.DesertMode.Mode.from(settings.desert)
        registerConnectivityWatcher()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: android.app.Activity) { com.ctodrs.saqrcode.service.AgentRuntime.visibleActivities++ }
            override fun onActivityStopped(a: android.app.Activity) {
                com.ctodrs.saqrcode.service.AgentRuntime.visibleActivities = (com.ctodrs.saqrcode.service.AgentRuntime.visibleActivities - 1).coerceAtLeast(0)
            }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityResumed(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })
    }

    companion object {
        lateinit var instance: SaqrApp; private set
    }

    /**
     * v0.13.5 desert mode: one default-network callback feeds [DesertState.online].
     * "Usable internet" means a network that both has the INTERNET capability and was
     * validated by the system (captive portals stay desert until they let traffic through).
     */
    private fun registerConnectivityWatcher() {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return
        fun usable(n: android.net.Network?): Boolean {
            val caps = n?.let { cm.getNetworkCapabilities(it) } ?: return false
            return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        com.ctodrs.saqrcode.agent.DesertState.online = usable(cm.activeNetwork)
        runCatching {
            cm.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { com.ctodrs.saqrcode.agent.DesertState.online = usable(cm.activeNetwork) || usable(network) }
                override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) {
                    com.ctodrs.saqrcode.agent.DesertState.online = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                }
                override fun onLost(network: android.net.Network) { com.ctodrs.saqrcode.agent.DesertState.online = usable(cm.activeNetwork) }
            })
        }
    }
}
