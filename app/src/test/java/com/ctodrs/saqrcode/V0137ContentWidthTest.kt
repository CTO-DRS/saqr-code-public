package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.ui.ContentWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V0137ContentWidthTest {
    @Test fun phonesAreUntouched() {
        for (w in listOf(320, 360, 411, 600, 720)) {
            assertEquals(w, ContentWidth.effective(w)); assertFalse(ContentWidth.isCapped(w))
        }
    }

    @Test fun tabletsAndFoldablesAreCapped() {
        for (w in listOf(721, 840, 1280)) {
            assertEquals(720, ContentWidth.effective(w)); assertTrue(ContentWidth.isCapped(w))
        }
    }

    @Test fun nonsenseWidthsAreSafe() {
        assertEquals(0, ContentWidth.effective(-5)); assertEquals(720, ContentWidth.effective(Int.MAX_VALUE))
    }
}
