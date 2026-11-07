package com.whatsgo.app.ui.login

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val ExpressiveSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF424242),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBEBEB),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF616161),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6D6D6),
    onTertiaryContainer = Color(0xFF111111),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF444444),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFE9E9E9),
    surfaceContainerHighest = Color(0xFFE2E2E2),
    outline = Color(0xFF737373),
    outlineVariant = Color(0xFFC4C4C4),
    error = Color(0xFF2B2B2B),
    onError = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color(0xFFF2F2F2),
    inversePrimary = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFC8C8C8),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = Color(0xFFE6E6E6),
    tertiary = Color(0xFFA8A8A8),
    onTertiary = Color(0xFF1A1A1A),
    tertiaryContainer = Color(0xFF3D3D3D),
    onTertiaryContainer = Color(0xFFE0E0E0),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0F0F0F),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1F1F1F),
    surfaceContainerHighest = Color(0xFF292929),
    outline = Color(0xFF8C8C8C),
    outlineVariant = Color(0xFF3A3A3A),
    error = Color(0xFFE0E0E0),
    onError = Color(0xFF000000),
    inverseSurface = Color(0xFFE8E8E8),
    inverseOnSurface = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFF000000)
)

@Composable
fun LoginTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}