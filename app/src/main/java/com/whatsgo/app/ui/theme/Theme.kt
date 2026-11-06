package com.whatsgo.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel

// Indigo / Violet — deliberately not WhatsApp-green or Telegram-blue
private val LightColors = lightColorScheme(
    primary = Color(0xFF5B5BD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2DFFF),
    onPrimaryContainer = Color(0xFF15116B),
    secondary = Color(0xFF7A5AF8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBE2FF),
    onSecondaryContainer = Color(0xFF250A5C),
    tertiary = Color(0xFFB8457A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E4),
    onTertiaryContainer = Color(0xFF3E001D),
    background = Color(0xFFFAF9FF),
    onBackground = Color(0xFF1B1B23),
    surface = Color(0xFFFAF9FF),
    onSurface = Color(0xFF1B1B23),
    surfaceVariant = Color(0xFFE4E1F0),
    onSurfaceVariant = Color(0xFF46455A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F1FB),
    surfaceContainer = Color(0xFFEDEBF7),
    surfaceContainerHigh = Color(0xFFE7E5F1),
    surfaceContainerHighest = Color(0xFFE1DFEB),
    outline = Color(0xFF77758B),
    outlineVariant = Color(0xFFC7C4D9),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    inverseSurface = Color(0xFF302F38),
    inverseOnSurface = Color(0xFFF2EFFA),
    inversePrimary = Color(0xFFC4C1FF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC4C1FF),
    onPrimary = Color(0xFF272287),
    primaryContainer = Color(0xFF3E389E),
    onPrimaryContainer = Color(0xFFE2DFFF),
    secondary = Color(0xFFCFBDFF),
    onSecondary = Color(0xFF3F2482),
    secondaryContainer = Color(0xFF573C99),
    onSecondaryContainer = Color(0xFFEBE2FF),
    tertiary = Color(0xFFFFB0CB),
    onTertiary = Color(0xFF5E1133),
    tertiaryContainer = Color(0xFF7D2B4A),
    onTertiaryContainer = Color(0xFFFFD9E4),
    background = Color(0xFF131218),
    onBackground = Color(0xFFE5E1F0),
    surface = Color(0xFF131218),
    onSurface = Color(0xFFE5E1F0),
    surfaceVariant = Color(0xFF46455A),
    onSurfaceVariant = Color(0xFFC7C4D9),
    surfaceContainerLowest = Color(0xFF0D0C12),
    surfaceContainerLow = Color(0xFF1B1A22),
    surfaceContainer = Color(0xFF201F28),
    surfaceContainerHigh = Color(0xFF2B2A33),
    surfaceContainerHighest = Color(0xFF36343E),
    outline = Color(0xFF918F9F),
    outlineVariant = Color(0xFF46455A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    inverseSurface = Color(0xFFE5E1F0),
    inverseOnSurface = Color(0xFF302F38),
    inversePrimary = Color(0xFF5B5BD6)
)

@Composable
fun ChatAppTheme(
    themeViewModel: ThemeViewModel = viewModel(),
    content: @Composable () -> Unit
) {
    val settings by themeViewModel.settings.collectAsState()

    val darkTheme = when (settings.darkModeOption) {
        DarkModeOption.SYSTEM -> isSystemInDarkTheme()
        DarkModeOption.LIGHT -> false
        DarkModeOption.DARK -> true
    }

    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamic = settings.dynamicColorEnabled && supportsDynamic

    val colors = when {
        useDynamic -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}