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
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        secure = SecureStore(this)
        settings = SettingsStore(this, secure)
        projects = ProjectRepository(this)
        linux = LinuxEnv(this)
    }

    companion object {
        lateinit var instance: SaqrApp; private set
    }
}
