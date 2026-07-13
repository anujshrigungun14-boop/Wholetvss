package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MovieRed,
    onPrimary = Color.White,
    secondary = CinemaGold,
    onSecondary = Color.Black,
    tertiary = AccentOrange,
    background = PremiumDarkBg,
    onBackground = SoftWhite,
    surface = DarkSurface,
    onSurface = SoftWhite,
    surfaceVariant = DeepGrey,
    onSurfaceVariant = MutedSlate
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark mode for cinematic vibe
    dynamicColor: Boolean = false, // Disable dynamic colors to keep wholeTV's distinct brand identity
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
