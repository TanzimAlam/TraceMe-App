package com.traceme.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette: https://colorhunt.co/palette/ffbe91ffddb0fffce1cfebff
val Peach = Color(0xFFFFBE91)
val PeachSoft = Color(0xFFFFDDB0)
val Cream = Color(0xFFFFFCE1)
val Sky = Color(0xFFCFEBFF)

val Ink = Color(0xFF3A2A20)
val InkSoft = Color(0xFF6E5A4A)
val InkFaint = Color(0xFF9B8574)

// Deeper tones drawn from the same family, used for text/buttons that need contrast.
val Clay = Color(0xFFA2542F)
val ClayDark = Color(0xFF87421F)
val SafeGreen = Color(0xFF2F7D63)
val WarnAmber = Color(0xFFB4702F)
val Danger = Color(0xFFB8422F)

val SurfaceWarm = Color(0xFFFFFDF2)
val CardWarm = Color(0xFFFFF7E3)
val OutlineWarm = Color(0xFFEBD3B0)

// Extra surface tints used by the premium cards.
val Glass = Color(0xE6FFFDF4)
val HeroTop = Color(0xFFFFD3AC)
val MistBlue = Color(0xFFE7F5FF)

private val OverwatchColors = lightColorScheme(
    primary = Clay,
    onPrimary = Color.White,
    primaryContainer = Peach,
    onPrimaryContainer = Ink,
    secondary = SafeGreen,
    onSecondary = Color.White,
    secondaryContainer = Sky,
    onSecondaryContainer = Color(0xFF1B3F4E),
    tertiary = WarnAmber,
    onTertiary = Color.White,
    tertiaryContainer = PeachSoft,
    onTertiaryContainer = Ink,
    background = Cream,
    onBackground = Ink,
    surface = SurfaceWarm,
    onSurface = Ink,
    surfaceVariant = PeachSoft,
    onSurfaceVariant = InkSoft,
    outline = OutlineWarm,
    error = Danger,
    onError = Color.White
)

/**
 * Type scale: an editorial serif for the wordmark and big numbers, a clean sans
 * for everything you actually have to read at night.
 */
private val OverwatchTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.5.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.5.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.15.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    // Uppercase micro-labels.
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.4.sp
    )
)

private val OverwatchShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val CardShape = RoundedCornerShape(26.dp)
val HeroShape = RoundedCornerShape(30.dp)

/** Soft directional washes — the difference between "flat beige" and "lit". */
object Wash {
    val hero = Brush.linearGradient(
        listOf(HeroTop, PeachSoft, Cream)
    )
    val page = Brush.verticalGradient(
        0f to Color(0xFFFFF7E6),
        0.45f to Cream,
        1f to Color(0xFFF3FAFF)
    )
    val clay = Brush.linearGradient(listOf(Color(0xFFB9663A), ClayDark))
    val green = Brush.linearGradient(listOf(Color(0xFF3E9C7B), Color(0xFF256A54)))
    val red = Brush.linearGradient(listOf(Color(0xFFD2604A), Color(0xFF9E3021)))
    val amber = Brush.linearGradient(listOf(Color(0xFFD99043), Color(0xFF9A5A1F)))
    val sky = Brush.linearGradient(listOf(Color(0xFFEAF7FF), Sky))
    val mint = Brush.linearGradient(listOf(Color(0xFFE9F6EF), Color(0xFFD5EDE2)))
}

@Composable
fun TraceMeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OverwatchColors,
        typography = OverwatchTypography,
        shapes = OverwatchShapes,
        content = content
    )
}
