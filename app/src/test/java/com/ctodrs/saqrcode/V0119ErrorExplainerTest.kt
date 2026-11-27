package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ErrorExplainer
import com.ctodrs.saqrcode.agent.ErrorExplainer.Kind
import com.ctodrs.saqrcode.agent.LlmException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v0.1.18: failed turns are classified so the chat can explain them in plain words. */
class V0119ErrorExplainerTest {
    private fun k(e: Throwable) = ErrorExplainer.classify(e)

    @Test fun `network failures are offline`() {
        assertEquals(Kind.OFFLINE, k(UnknownHostException("api.openai.com")))
        assertEquals(Kind.OFFLINE, k(ConnectException("failed")))
        assertEquals(Kind.OFFLINE, k(IOException("wrapped", UnknownHostException("x"))))
    }

    @Test fun `timeouts`() {
        assertEquals(Kind.TIMEOUT, k(SocketTimeoutException("read timed out")))
        assertEquals(Kind.TIMEOUT, k(LlmException("request timeout")))
    }

    @Test fun `http statuses map to kinds`() {
        assertEquals(Kind.AUTH, k(LlmException("HTTP 401: bad key")))
        assertEquals(Kind.AUTH, k(LlmException("HTTP 403: forbidden")))
        assertEquals(Kind.RATE_LIMIT, k(LlmException("HTTP 429: slow down")))
        assertEquals(Kind.QUOTA, k(LlmException("HTTP 429: You exceeded your current quota")))
        assertEquals(Kind.QUOTA, k(LlmException("HTTP 402: payment required")))
        assertEquals(Kind.SERVER, k(LlmException("HTTP 503: overloaded")))
    }

    @Test fun `tls and unknown`() {
        assertEquals(Kind.TLS, k(SSLHandshakeException("bad cert")))
        assertEquals(Kind.OTHER, k(IllegalStateException("boom")))
        assertEquals(Kind.OTHER, k(LlmException("Empty response")))
    }

    @Test fun `model not found and context too long`() {
        assertEquals(Kind.MODEL_NOT_FOUND, k(LlmException("HTTP 404: model gpt-x not found")))
        assertEquals(Kind.MODEL_NOT_FOUND, k(LlmException("HTTP 400: {\"code\":\"model_not_found\"}")))
        assertEquals(Kind.CONTEXT_TOO_LONG, k(LlmException("HTTP 400: maximum context length is 8192 tokens")))
        assertEquals(Kind.CONTEXT_TOO_LONG, k(LlmException("HTTP 400: prompt is too long")))
        assertEquals(Kind.OTHER, k(LlmException("HTTP 404: not found")))
    }

    @Test fun `existing ordinals stay stable`() {
        assertEquals(7, Kind.OTHER.ordinal)
        assertEquals(Kind.MODEL_NOT_FOUND, ErrorExplainer.fromOrdinal(8))
    }

    @Test fun `ordinal round trip`() {
        Kind.values().forEach { assertEquals(it, ErrorExplainer.fromOrdinal(it.ordinal)) }
        assertNull(ErrorExplainer.fromOrdinal(-1))
        assertNull(ErrorExplainer.fromOrdinal(99))
    }
}
