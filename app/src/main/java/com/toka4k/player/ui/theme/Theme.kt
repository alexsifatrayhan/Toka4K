package com.toka4k.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonPurple,
    tertiary = GlowGreen,
    background = DarkBg,
    surface = SurfaceGray,
    onPrimary = DarkBg,
    onSecondary = BrightWhite,
    onTertiary = DarkBg,
    onBackground = BrightWhite,
    onSurface = BrightWhite,
)

@Composable
fun TokaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
