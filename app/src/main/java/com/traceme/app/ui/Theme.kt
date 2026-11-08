package com.traceme.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette: https://colorhunt.co/palette/ffced8ffddddfff4deffe8c4
val Pink = Color(0xFFFFCED8)
val PinkSoft = Color(0xFFFFDDDD)
val Cream = Color(0xFFFFF4DE)
val Sand = Color(0xFFFFE8C4)

val Ink = Color(0xFF3A2430)
val InkSoft = Color(0xFF6F5162)
val InkFaint = Color(0xFF9A7D8C)

val Berry = Color(0xFFB34A6B)
val BerryDark = Color(0xFF8F3A55)
val SafeGreen = Color(0xFF3F7F68)
val WarnAmber = Color(0xFFB4702F)
val Danger = Color(0xFFC0453F)

val SurfaceWarm = Color(0xFFFFFBF5)
val CardWarm = Color(0xFFFFF7EC)
val OutlineWarm = Color(0xFFE2C4CD)

private val TraceMeColors = lightColorScheme(
    primary = Berry,
    onPrimary = Color.White,
    primaryContainer = Pink,
    onPrimaryContainer = Ink,
    secondary = SafeGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1F0E9),
    onSecondaryContainer = Color(0xFF1F473A),
    tertiary = WarnAmber,
    onTertiary = Color.White,
    tertiaryContainer = Sand,
    onTertiaryContainer = Ink,
    background = Cream,
    onBackground = Ink,
    surface = SurfaceWarm,
    onSurface = Ink,
    surfaceVariant = PinkSoft,
    onSurfaceVariant = InkSoft,
    outline = OutlineWarm,
    error = Danger,
    onError = Color.White
)

@Composable
fun TraceMeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TraceMeColors, content = content)
}
