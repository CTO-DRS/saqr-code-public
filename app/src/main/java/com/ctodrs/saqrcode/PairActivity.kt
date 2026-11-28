package com.ctodrs.saqrcode

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.ctodrs.saqrcode.cloud.FreeAi

/**
 * v0.1.69 · One-tap owner pairing. The owner's link `saqr://pair?code=...` lands here; the code
 * (valid 15 minutes) is traded at the proxy for this install's own cloud-capable token, which is
 * stored encrypted and switches the cloud computer on. No UI; one toast names the outcome.
 */
class PairActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SaqrApp
        val code = intent?.data?.toString()?.let { FreeAi.parsePairCode(it) }
        if (code == null) { toast(R.string.pair_failed); finish(); return }
        Thread {
            val token = runCatching { kotlinx.coroutines.runBlocking { FreeAi.pair(app.settings.cloudProxyUrl, code, app.settings.freeAiInstallId) } }.getOrNull()
            runOnUiThread {
                if (token == null) toast(R.string.pair_failed)
                else {
                    app.settings.cloudProxyToken = token
                    app.settings.cloudEnabled = true
                    toast(R.string.pair_done)
                    startActivity(android.content.Intent(this, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
                finish()
            }
        }.start()
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()
}
