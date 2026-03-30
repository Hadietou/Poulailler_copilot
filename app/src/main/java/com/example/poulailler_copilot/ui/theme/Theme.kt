package com.example.poulailler_copilot.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NatureGreenPrimaryDark,
    onPrimary = NatureGreenOnPrimaryDark,
    primaryContainer = NatureGreenPrimaryContainerDark,
    onPrimaryContainer = NatureGreenOnPrimaryContainerDark,
    secondary = NatureEarthSecondaryDark,
    onSecondary = NatureEarthOnSecondaryDark,
    secondaryContainer = NatureEarthSecondaryContainerDark,
    onSecondaryContainer = NatureEarthOnSecondaryContainerDark,
    tertiary = NatureSkyTertiary,
    background = NatureBackgroundDark,
    surface = NatureSurfaceDark,
    onBackground = Color(0xFFE3E3DC),
    onSurface = Color(0xFFE3E3DC)
)

private val LightColorScheme = lightColorScheme(
    primary = NatureGreenPrimary,
    onPrimary = NatureGreenOnPrimary,
    primaryContainer = NatureGreenPrimaryContainer,
    onPrimaryContainer = NatureGreenOnPrimaryContainer,
    secondary = NatureEarthSecondary,
    onSecondary = NatureEarthOnSecondary,
    secondaryContainer = NatureEarthSecondaryContainer,
    onSecondaryContainer = NatureEarthOnSecondaryContainer,
    tertiary = NatureSkyTertiary,
    background = NatureBackground,
    surface = NatureSurface,
    onBackground = Color(0xFF1A1C18),
    onSurface = Color(0xFF1A1C18)
)

@Composable
fun Poulailler_copilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is set to false to respect the custom nature-inspired palette
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        SideEffect {
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}