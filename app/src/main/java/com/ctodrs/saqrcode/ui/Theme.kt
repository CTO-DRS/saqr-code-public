package com.ctodrs.saqrcode.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ctodrs.saqrcode.R

val Gold = Color(0xFFD9A441)
val Teal = Color(0xFF5FB3A1)
val Bg = Color(0xFF0E1116)
val Surface1 = Color(0xFF161B22)
val Surface2 = Color(0xFF1E252E)
val AddBg = Color(0x332EA043)
val DelBg = Color(0x33F85149)

val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

private val colors = darkColorScheme(
    primary = Gold, onPrimary = Color(0xFF1A1206),
    secondary = Teal, onSecondary = Color(0xFF04201A),
    background = Bg, onBackground = Color(0xFFE6E6E6),
    surface = Surface1, onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Surface2, onSurfaceVariant = Color(0xFFB8C0CC),
    surfaceContainer = Surface1, surfaceContainerHigh = Surface2, surfaceContainerHighest = Surface2,
    surfaceContainerLow = Surface1, surfaceContainerLowest = Bg,
    error = Color(0xFFF85149), outline = Color(0xFF3A4350), outlineVariant = Color(0xFF2A313B),
)

private fun Typography.withFont(f: FontFamily): Typography {
    fun TextStyle.ff() = copy(fontFamily = f)
    return copy(
        displayLarge = displayLarge.ff(), displayMedium = displayMedium.ff(), displaySmall = displaySmall.ff(),
        headlineLarge = headlineLarge.ff(), headlineMedium = headlineMedium.ff(), headlineSmall = headlineSmall.ff(),
        titleLarge = titleLarge.ff(), titleMedium = titleMedium.ff(), titleSmall = titleSmall.ff(),
        bodyLarge = bodyLarge.ff(), bodyMedium = bodyMedium.ff(), bodySmall = bodySmall.ff(),
        labelLarge = labelLarge.ff(), labelMedium = labelMedium.ff(), labelSmall = labelSmall.ff(),
    )
}

@Composable
fun SaqrTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = Typography().withFont(PlexArabic), content = content)
}
