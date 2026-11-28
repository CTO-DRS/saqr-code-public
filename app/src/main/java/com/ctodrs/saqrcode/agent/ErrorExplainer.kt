package com.ctodrs.saqrcode.agent

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * v0.1.18: turns a failed turn's exception into a category the chat can explain in plain words
 * (what happened + what to do) instead of showing only a raw exception string.
 * Pure and Android-free so it is unit-tested.
 */
object ErrorExplainer {
    /** The ordinal travels in Notice.count; keep the order stable. */
    enum class Kind { OFFLINE, TIMEOUT, AUTH, RATE_LIMIT, QUOTA, SERVER, TLS, OTHER, MODEL_NOT_FOUND, CONTEXT_TOO_LONG }

    fun classify(e: Throwable): Kind {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 6) {
            when (t) {
                is UnknownHostException, is ConnectException -> return Kind.OFFLINE
                is SocketTimeoutException -> return Kind.TIMEOUT
                is SSLException -> return Kind.TLS
            }
            t = t.cause; depth++
        }
        val m = (e.message ?: "").lowercase()
        val http = Regex("""http (\d{3})""").find(m)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            m.contains("all providers busy") -> Kind.RATE_LIMIT // Saqr Free AI: every free provider is at its limit for a minute
            m.contains("context length") || m.contains("context_length") || m.contains("maximum context") ||
                m.contains("too many tokens") || m.contains("prompt is too long") || m.contains("context window") -> Kind.CONTEXT_TOO_LONG
            m.contains("model_not_found") || (http == 404 && m.contains("model")) ||
                (m.contains("model") && m.contains("does not exist")) -> Kind.MODEL_NOT_FOUND
            http == 401 || http == 403 -> Kind.AUTH
            http == 429 -> if (m.contains("quota") || m.contains("billing") || m.contains("insufficient")) Kind.QUOTA else Kind.RATE_LIMIT
            http == 402 -> Kind.QUOTA
            http != null && http in 500..599 -> Kind.SERVER
            m.contains("insufficient_quota") || m.contains("exceeded your current quota") -> Kind.QUOTA
            // Mid-stream provider errors arrive without an HTTP prefix ("overloaded_error", "rate_limit_error").
            m.contains("rate_limit") || m.contains("rate limit") -> Kind.RATE_LIMIT
            m.contains("overloaded") || m.contains("temporarily unavailable") || m.contains("server_error") -> Kind.SERVER
            m.contains("timeout") || m.contains("timed out") -> Kind.TIMEOUT
            m.contains("unable to resolve host") || m.contains("failed to connect") -> Kind.OFFLINE
            e is IOException && m.contains("connection") -> Kind.OFFLINE
            else -> Kind.OTHER
        }
    }

    fun fromOrdinal(n: Int): Kind? = Kind.values().getOrNull(n)?.takeIf { n >= 0 }
}
