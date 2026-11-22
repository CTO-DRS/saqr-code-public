package com.ctodrs.saqrcode.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ctodrs.saqrcode.R

/*
 * Saqr design system ("Falcon").
 *
 * Signature accent: Saqr Gold (#E9B44C). Why gold and not teal: the falcon (صقر) and Gulf falconry
 * are the brand's whole identity, gold reads as premium on near-black graphite, it keeps ~10:1
 * contrast on the dark background, and it sets Saqr apart from the blue accents most coding tools
 * (PolyDev included) use. Teal is kept only as a quiet supporting hue for info states.
 */

@Immutable
data class SaqrColors(
    val isDark: Boolean,
    val bg: Color,            // app background
    val surface1: Color,      // cards, sheets
    val surface2: Color,      // raised: pills, chips, top-bar glass base
    val surface3: Color,      // inputs, pressed, hover
    val border: Color,        // hairline
    val borderStrong: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,        // gold for fills (buttons, progress)
    val accentText: Color,    // gold tuned for text / icons on the current background
    val accentSoft: Color,    // tinted backgrounds
    val onAccent: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val warningBg: Color,
    val warningBorder: Color,
    val info: Color,
    val diffAdd: Color,
    val diffAddBg: Color,
    val diffDel: Color,
    val diffDelBg: Color,
    val codeBg: Color,
    val codeHeader: Color,
    val termBg: Color,
    val termText: Color,
    val glass: Color,         // floating bars
    val scrim: Color,
)

val DarkSaqr = SaqrColors(
    isDark = true,
    bg = Color(0xFF0A0B0D),
    surface1 = Color(0xFF111317),
    surface2 = Color(0xFF171A1F),
    surface3 = Color(0xFF1F232A),
    border = Color(0xFF23272E),
    borderStrong = Color(0xFF30353E),
    text1 = Color(0xFFEDEEF0),
    text2 = Color(0xFFA4AAB3),
    text3 = Color(0xFF6D737D),
    accent = Color(0xFFE9B44C),
    accentText = Color(0xFFEDBE5E),
    accentSoft = Color(0x1FE9B44C),
    onAccent = Color(0xFF1B1305),
    success = Color(0xFF46C98B),
    danger = Color(0xFFF2555A),
    warning = Color(0xFFF0A93B),
    warningBg = Color(0xFF221A0C),
    warningBorder = Color(0xFF5E4519),
    info = Color(0xFF5FB3A1),
    diffAdd = Color(0xFF5BD47F),
    diffAddBg = Color(0x1F5BD47F),
    diffDel = Color(0xFFF4736B),
    diffDelBg = Color(0x1FF4736B),
    codeBg = Color(0xFF0D0F12),
    codeHeader = Color(0xFF14171B),
    termBg = Color(0xFF07080A),
    termText = Color(0xFFD3D9E0),
    glass = Color(0xE6171A1F),
    scrim = Color(0xFF0A0B0D),
)

val LightSaqr = SaqrColors(
    isDark = false,
    bg = Color(0xFFF6F5F2),
    surface1 = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF0EEE9),
    surface3 = Color(0xFFE7E4DE),
    border = Color(0xFFE3E0D9),
    borderStrong = Color(0xFFD3CFC6),
    text1 = Color(0xFF16181B),
    text2 = Color(0xFF545A63),
    text3 = Color(0xFF8A8F97),
    accent = Color(0xFFE2A93D),
    accentText = Color(0xFF9A6A10),
    accentSoft = Color(0x24E2A93D),
    onAccent = Color(0xFF1B1305),
    success = Color(0xFF1E9A5A),
    danger = Color(0xFFD5383E),
    warning = Color(0xFFB7791F),
    warningBg = Color(0xFFFFF6E5),
    warningBorder = Color(0xFFEBCB8B),
    info = Color(0xFF2E8B78),
    diffAdd = Color(0xFF1A7F37),
    diffAddBg = Color(0x1F2DA44E),
    diffDel = Color(0xFFC62B31),
    diffDelBg = Color(0x1FE5534B),
    codeBg = Color(0xFFF8F6F1),
    codeHeader = Color(0xFFEFECE5),
    termBg = Color(0xFF0E1013),
    termText = Color(0xFFD3D9E0),
    glass = Color(0xEBFFFFFF),
    scrim = Color(0xFFF6F5F2),
)

/** Spacing scale (dp). */
object Sp {
    val xxs = 2.dp; val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp
    val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp
}

/** Corner radius scale (dp). */
object Radius {
    val xs = 6.dp; val sm = 10.dp; val md = 14.dp; val lg = 18.dp; val xl = 24.dp; val xxl = 28.dp
}

/** Motion tokens. */
object Motion {
    const val fast = 140
    const val base = 220
    const val slow = 320
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

val PlexArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** Type scale. Arabic needs generous line heights; mono is for code, paths and numbers. */
@Immutable
data class SaqrType(
    val display: TextStyle = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 40.sp, lineHeightStyle = trim),
    val title1: TextStyle = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 32.sp, lineHeightStyle = trim),
    val title2: TextStyle = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 27.sp, lineHeightStyle = trim),
    val title3: TextStyle = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, lineHeightStyle = trim),
    val body: TextStyle = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 25.sp, lineHeightStyle = trim),
    val bodySmall: TextStyle = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 21.sp, lineHeightStyle = trim),
    val label: TextStyle = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 19.sp, lineHeightStyle = trim),
    val caption: TextStyle = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, lineHeightStyle = trim),
    val micro: TextStyle = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, lineHeightStyle = trim),
    val mono: TextStyle = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
    val monoSmall: TextStyle = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    val monoMicro: TextStyle = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

val LocalSaqrColors = staticCompositionLocalOf { DarkSaqr }
val LocalSaqrType = staticCompositionLocalOf { SaqrType() }

object Saqr {
    val colors: SaqrColors @Composable @ReadOnlyComposable get() = LocalSaqrColors.current
    val type: SaqrType @Composable @ReadOnlyComposable get() = LocalSaqrType.current
}

/** "system" | "dark" | "light" */
@Composable
fun SaqrTheme(themeMode: String = "dark", content: @Composable () -> Unit) {
    val dark = when (themeMode) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    val c = if (dark) DarkSaqr else LightSaqr
    val type = SaqrType()
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accentText,
        secondary = c.info, onSecondary = Color.Black,
        background = c.bg, onBackground = c.text1,
        surface = c.surface1, onSurface = c.text1, surfaceVariant = c.surface2, onSurfaceVariant = c.text2,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface1, surfaceContainer = c.surface1,
        surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface3, surfaceBright = c.surface3,
        error = c.danger, outline = c.borderStrong, outlineVariant = c.border, scrim = Color.Black,
        inverseSurface = c.text1, inverseOnSurface = c.bg,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accentText,
        secondary = c.info, onSecondary = Color.White,
        background = c.bg, onBackground = c.text1,
        surface = c.surface1, onSurface = c.text1, surfaceVariant = c.surface2, onSurfaceVariant = c.text2,
        surfaceContainerLowest = c.surface1, surfaceContainerLow = c.surface1, surfaceContainer = c.surface1,
        surfaceContainerHigh = c.surface1, surfaceContainerHighest = c.surface2, surfaceBright = c.surface1,
        error = c.danger, outline = c.borderStrong, outlineVariant = c.border,
        inverseSurface = c.text1, inverseOnSurface = c.bg,
    )
    val m3Type = Typography(
        displayLarge = type.display, displayMedium = type.display, displaySmall = type.title1,
        headlineLarge = type.title1, headlineMedium = type.title1, headlineSmall = type.title2,
        titleLarge = type.title2, titleMedium = type.title3, titleSmall = type.label.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = type.body, bodyMedium = type.bodySmall, bodySmall = type.caption,
        labelLarge = type.label, labelMedium = type.caption.copy(fontWeight = FontWeight.Medium), labelSmall = type.micro,
    )
    CompositionLocalProvider(LocalSaqrColors provides c, LocalSaqrType provides type) {
        MaterialTheme(colorScheme = scheme, typography = m3Type, content = content)
    }
}
