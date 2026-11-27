package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.data.SecureStore
import com.ctodrs.saqrcode.data.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** v0.1.57: Settings tells the user when the keystore failed and keys are stored unencrypted. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class V0157SecretsNoticeTest {
    @Test fun settingsReportsTheStoreState() {
        val app = RuntimeEnvironment.getApplication()
        val secure = SecureStore(app)
        val settings = SettingsStore(app, secure)
        assertEquals(secure.encrypted, settings.secretsEncrypted)
    }

    @Test fun theStoreStillWorksEitherWay() {
        val secure = SecureStore(RuntimeEnvironment.getApplication())
        secure.put("probe", " value ")
        assertEquals("value", secure.get("probe"))
        secure.put("probe", null)
        assertEquals(null, secure.get("probe"))
    }
}
