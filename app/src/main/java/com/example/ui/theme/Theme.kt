package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AnimeFlowDarkColorScheme = darkColorScheme(
    primary = CyanElectric,
    onPrimary = ObsidianCanvas,
    primaryContainer = CyanMuted,
    onPrimaryContainer = CyanGlow,
    secondary = SakuraMagenta,
    onSecondary = TextPrimary,
    secondaryContainer = Color(0xFF4C1330),
    onSecondaryContainer = SakuraSoft,
    tertiary = VioletDirector,
    onTertiary = TextPrimary,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianCanvas,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianCard,
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorder.copy(alpha = 0.6f),
    error = CrimsonError,
    onError = TextPrimary
)

private val AnimeFlowShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun AnimeFlowTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AnimeFlowDarkColorScheme,
        typography = AnimeFlowTypography,
        shapes = AnimeFlowShapes,
        content = content
    )
}
