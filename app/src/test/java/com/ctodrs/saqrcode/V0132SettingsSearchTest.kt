package com.ctodrs.saqrcode

import com.ctodrs.saqrcode.ui.SettingsSearch
import com.ctodrs.saqrcode.ui.SettingsSearch.Entry
import org.junit.Assert.assertEquals
import org.junit.Test

class V0132SettingsSearchTest {
    private val entries = listOf(
        Entry("appearance", "Appearance", listOf("Theme", "Language")),
        Entry("agent", "Agent", listOf("Approval mode", "Team mode")),
        Entry("voice", "Voice", listOf("Hands-free", "Voice language")),
        Entry("ar", "الصوت", listOf("اللغة الصوتية", "الإرسال التلقائي")),
    )

    @Test fun blankQueryMatchesNothing() = assertEquals(emptyList<String>(), SettingsSearch.match(entries, "  "))

    @Test fun matchesRowTitleCaseInsensitive() = assertEquals(listOf("agent"), SettingsSearch.match(entries, "APPROVAL"))

    @Test fun allWordsMustMatch() {
        assertEquals(listOf("agent"), SettingsSearch.match(entries, "team mode"))
        assertEquals(emptyList<String>(), SettingsSearch.match(entries, "team theme"))
    }

    @Test fun titleMatchesRankFirst() {
        // "language" is a row in appearance and voice; the voice title does not contain it, so list order decides.
        assertEquals(listOf("appearance", "voice"), SettingsSearch.match(entries, "language"))
        val e = listOf(Entry("a", "Other", listOf("voice")), Entry("b", "Voice", emptyList()))
        assertEquals(listOf("b", "a"), SettingsSearch.match(e, "voice"))
    }

    @Test fun arabicNormalization() {
        assertEquals(listOf("ar"), SettingsSearch.match(entries, "الإرسال"))
        assertEquals(listOf("ar"), SettingsSearch.match(entries, "الارسال"))
        assertEquals(listOf("ar"), SettingsSearch.match(entries, "الصَوت"))
    }

    @Test fun easternDigitsAreUnified() {
        assertEquals("v 12", SettingsSearch.normalize("V ١٢"))
    }
}
