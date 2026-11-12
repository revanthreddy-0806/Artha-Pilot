package com.arthapilot.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RonasColors {
    val BgCanvas = Color(0xFF040404)
    val SurfaceCard = Color(0xFF111314)
    val SurfaceElevated = Color(0xFF16191A)
    val SurfaceIcon = Color(0xFF1C2022)
    val BorderSubtle = Color(0xFF222627)
    val DividerMuted = Color(0xFF505554)
    val TextPrimary = Color(0xFFF0EFED)
    val TextSecondary = Color(0xFF919EA2)
    val TextMuted = Color(0xFF7E8A8C)
    val GrowthGreen = Color(0xFF10EB78)
    val GrowthGreenBg = Color(0x1F10EB78)
    val DeclineRed = Color(0xFFFF4D4D)
    val DeclineRedBg = Color(0x1FFF4D4D)
    val AccentAmber = Color(0xFFF59E0B)
}

private val LightColors = lightColorScheme(
    primary = RonasColors.TextPrimary,
    onPrimary = RonasColors.BgCanvas,
    primaryContainer = RonasColors.SurfaceElevated,
    onPrimaryContainer = RonasColors.TextPrimary,
    secondary = RonasColors.GrowthGreen,
    onSecondary = RonasColors.BgCanvas,
    tertiary = RonasColors.AccentAmber,
    background = RonasColors.BgCanvas,
    onBackground = RonasColors.TextPrimary,
    surface = RonasColors.SurfaceCard,
    onSurface = RonasColors.TextPrimary,
    surfaceVariant = RonasColors.SurfaceElevated,
    onSurfaceVariant = RonasColors.TextSecondary,
    outline = RonasColors.BorderSubtle,
    outlineVariant = RonasColors.DividerMuted,
    error = RonasColors.DeclineRed,
    onError = RonasColors.BgCanvas,
)

private val DarkColors = darkColorScheme(
    primary = RonasColors.TextPrimary,
    onPrimary = RonasColors.BgCanvas,
    primaryContainer = RonasColors.SurfaceElevated,
    onPrimaryContainer = RonasColors.TextPrimary,
    secondary = RonasColors.GrowthGreen,
    onSecondary = RonasColors.BgCanvas,
    tertiary = RonasColors.AccentAmber,
    background = RonasColors.BgCanvas,
    onBackground = RonasColors.TextPrimary,
    surface = RonasColors.SurfaceCard,
    onSurface = RonasColors.TextPrimary,
    surfaceVariant = RonasColors.SurfaceElevated,
    onSurfaceVariant = RonasColors.TextSecondary,
    outline = RonasColors.BorderSubtle,
    outlineVariant = RonasColors.DividerMuted,
    error = RonasColors.DeclineRed,
    onError = RonasColors.BgCanvas,
)

@Composable
fun ArthaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}