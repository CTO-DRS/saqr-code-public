package com.ctodrs.saqrcode

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.ctodrs.saqrcode.agent.McpOAuth
import com.ctodrs.saqrcode.agent.McpOAuthHttp

/**
 * v0.14.1 · مستقبل OAuth · OAuth callback: the browser lands the PKCE redirect on
 * `saqr://oauth/callback` and this activity finishes the flow — validates the anti-forgery
 * `state` against the persisted pending flow, exchanges the code at the token endpoint
 * (background thread), stores the tokens in the encrypted SecureStore and returns to the app.
 * It has no UI of its own; one toast names the outcome.
 */
class OAuthCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as SaqrApp
        val red = intent?.data?.toString()?.let { McpOAuth.parseRedirect(it) }
        val pending = red?.state?.let { app.settings.mcpOAuthPending(it) }
        when {
            // Not a redirect at all (or missing both code and error): unreadable response.
            red == null -> {
                toast(R.string.mcp_oauth_failed_invalid)
                done()
            }
            // The server (or the user) denied the authorization.
            red.error != null -> {
                if (red.state != null) app.settings.clearMcpOAuthPending(red.state)
                toast(R.string.mcp_oauth_denied)
                done()
            }
            // The state does not match any flow we started — never trust it.
            pending == null || red.state != pending.state -> {
                toast(R.string.mcp_oauth_state_mismatch)
                done()
            }
            else -> {
                val p = pending
                val code = red.code!!
                Thread {
                    val ok = runCatching {
                        val json = McpOAuthHttp.postToken(
                            p.tokenUrl,
                            McpOAuth.tokenRequestBody(code, p.verifier, p.clientId, p.redirectUri, p.resource),
                        )
                        val tokens = json?.let { McpOAuth.parseTokenResponse(it, System.currentTimeMillis()) }
                        if (tokens != null) {
                            app.settings.setMcpTokens(p.serverName, tokens)
                            true
                        } else false
                    }.getOrDefault(false)
                    app.settings.clearMcpOAuthPending(p.state)
                    runOnUiThread {
                        toast(if (ok) R.string.mcp_oauth_success else R.string.mcp_oauth_failed_short)
                        done()
                    }
                }.start()
            }
        }
    }

    /** Back to the app; the Settings screen shows the new status when it is reopened. */
    private fun done() {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()
}
