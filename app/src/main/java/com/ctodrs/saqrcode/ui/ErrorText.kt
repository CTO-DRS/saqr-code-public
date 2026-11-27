package com.ctodrs.saqrcode.ui

import androidx.annotation.StringRes
import com.ctodrs.saqrcode.R
import com.ctodrs.saqrcode.agent.ErrorExplainer

/** The plain-words explanation for a classified provider error, or null for OTHER (show the raw message). One table for the chat and Settings. */
@StringRes
fun errorExplanationRes(kind: ErrorExplainer.Kind): Int? = when (kind) {
    ErrorExplainer.Kind.OFFLINE -> R.string.err_offline
    ErrorExplainer.Kind.TIMEOUT -> R.string.err_timeout
    ErrorExplainer.Kind.AUTH -> R.string.err_auth
    ErrorExplainer.Kind.RATE_LIMIT -> R.string.err_rate_limit
    ErrorExplainer.Kind.QUOTA -> R.string.err_quota
    ErrorExplainer.Kind.SERVER -> R.string.err_server
    ErrorExplainer.Kind.MODEL_NOT_FOUND -> R.string.err_model_not_found
    ErrorExplainer.Kind.CONTEXT_TOO_LONG -> R.string.err_context_long
    ErrorExplainer.Kind.TLS -> R.string.err_tls
    ErrorExplainer.Kind.OTHER -> null
}

/** A line to show for a failure: its message, or the error type when the message is null or blank. Never empty. */
fun errorLine(e: Throwable): String = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
