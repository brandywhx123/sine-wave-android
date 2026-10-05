package com.example.sinegenerator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Surface,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealDark,
    secondary = Teal,
    onSecondary = Surface,
    background = AppBackground,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = AppBackground,
    onSurfaceVariant = InkMuted,
    outline = Outline,
    outlineVariant = Outline,
    error = StopRed,
    onError = Surface
)

@Composable
fun SineGeneratorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content
    )
}
