package com.traceme.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun TraceMeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OverwatchColors, content = content)
}
