package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisColors.NeonCyan,
    onPrimary = JarvisColors.VoidBlack,
    primaryContainer = JarvisColors.CyanTint,
    onPrimaryContainer = JarvisColors.FrostWhite,
    secondary = JarvisColors.MatrixEmerald,
    onSecondary = JarvisColors.VoidBlack,
    secondaryContainer = JarvisColors.EmeraldTint,
    onSecondaryContainer = JarvisColors.FrostWhite,
    error = JarvisColors.DangerCrimson,
    onError = JarvisColors.FrostWhite,
    background = JarvisColors.VoidBlack,
    onBackground = JarvisColors.FrostWhite,
    surface = JarvisColors.DeepHudSurface,
    onSurface = JarvisColors.FrostWhite,
    surfaceVariant = JarvisColors.GlassSurfaceStrong,
    onSurfaceVariant = JarvisColors.MutedCyan,
    outline = JarvisColors.CyanBorder
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = JarvisTypography,
        content = content
    )
}
