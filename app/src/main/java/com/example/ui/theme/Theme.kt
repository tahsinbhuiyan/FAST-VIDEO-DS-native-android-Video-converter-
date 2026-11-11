package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DsCyanPrimary,
    onPrimary = Color.Black,
    primaryContainer = DsCyanDark,
    onPrimaryContainer = Color.White,
    secondary = DsAmberAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = Color(0xFFFEF3C7),
    tertiary = DsEmeraldValid,
    onTertiary = Color.Black,
    background = DsSlateBg,
    onBackground = DsTextPrimary,
    surface = DsSurface,
    onSurface = DsTextPrimary,
    surfaceVariant = DsSurfaceVariant,
    onSurfaceVariant = DsTextSecondary,
    outline = DsBorder
)

private val LightColorScheme = darkColorScheme(
    primary = DsCyanPrimary,
    onPrimary = Color.Black,
    secondary = DsAmberAccent,
    background = DsSlateBg,
    surface = DsSurface,
    onSurface = DsTextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent high-tech DS theme
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
