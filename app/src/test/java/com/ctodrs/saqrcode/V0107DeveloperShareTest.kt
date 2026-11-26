package com.ctodrs.saqrcode

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ctodrs.saqrcode.ui.DeveloperShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class V0107DeveloperShareTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `qr bitmap is square with a quiet zone and dark finder corner`() {
        val b = DeveloperShare.qrBitmap("https://t.me/CTO_DRS", 8)
        assertEquals(b.width, b.height)
        assertEquals(25 + 8, b.width / 8)
        assertEquals(0xFFFFFFFF.toInt(), b.getPixel(0, 0))
        assertEquals(0xFF000000.toInt(), b.getPixel(4 * 8 + 1, 4 * 8 + 1))
    }

    @Test fun `share link fires a chooser carrying the url`() {
        DeveloperShare.shareLink(app, "https://cto-drs.com")
        val i = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, i.action)
        val inner = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("https://cto-drs.com", inner.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test fun `share qr writes a png and fires an image chooser`() {
        DeveloperShare.shareQr(app, "https://cto-drs.com", "Website")
        val i = shadowOf(app).nextStartedActivity
        assertNotNull(i)
        val inner = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("image/png", inner.type)
        assertTrue(java.io.File(app.cacheDir, "qr/qr-Website.png").length() > 100)
    }
}
