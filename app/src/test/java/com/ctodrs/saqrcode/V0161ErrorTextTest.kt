package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.agent.ErrorExplainer
import com.ctodrs.saqrcode.agent.LlmException
import com.ctodrs.saqrcode.ui.errorExplanationRes
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** v0.1.61: one table of plain-words explanations, shared by the chat and the Settings model fetch. */
class V0161ErrorTextTest {
    @Test fun everyKindExceptOtherHasAnExplanation() {
        for (k in ErrorExplainer.Kind.values()) {
            if (k == ErrorExplainer.Kind.OTHER) assertNull(errorExplanationRes(k)) else assertNotNull("no text for $k", errorExplanationRes(k))
        }
    }

    @Test fun modelFetchFailuresMapToTheRightWords() {
        assertEquals(R.string.err_auth, errorExplanationRes(ErrorExplainer.classify(LlmException("HTTP 401: bad key"))))
        assertEquals(R.string.err_offline, errorExplanationRes(ErrorExplainer.classify(UnknownHostException("api.x.com"))))
        assertEquals(R.string.err_rate_limit, errorExplanationRes(ErrorExplainer.classify(LlmException("HTTP 429: slow down"))))
        assertNull(errorExplanationRes(ErrorExplainer.classify(LlmException("weird"))))
    }
}
