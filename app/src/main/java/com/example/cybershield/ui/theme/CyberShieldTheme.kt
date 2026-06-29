package com.example.cybershield.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Light color scheme ───────────────────────────────────────────────
private val LightColorScheme =
    lightColorScheme(
        primary = Blue800,
        onPrimary = White,
        primaryContainer = Blue50,
        onPrimaryContainer = Blue900,
        secondary = Teal600,
        onSecondary = White,
        secondaryContainer = Teal50,
        onSecondaryContainer = Teal700,
        tertiary = Amber500,
        onTertiary = Black,
        tertiaryContainer = Amber100,
        onTertiaryContainer = Amber700,
        error = WrongRed,
        onError = White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Grey50,
        onBackground = Grey900,
        surface = White,
        onSurface = Grey900,
        surfaceVariant = Grey100,
        onSurfaceVariant = Grey700,
        outline = Grey200,
    )

// ── Dark color scheme ────────────────────────────────────────────────
private val DarkColorScheme =
    darkColorScheme(
        primary = Blue600,
        onPrimary = Blue900,
        primaryContainer = Blue800,
        onPrimaryContainer = Blue100,
        secondary = Teal200,
        onSecondary = Teal700,
        secondaryContainer = Teal700,
        onSecondaryContainer = Teal50,
        tertiary = Amber500,
        onTertiary = Black,
        tertiaryContainer = Amber700,
        onTertiaryContainer = Amber100,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Grey900,
        onBackground = Grey100,
        surface = Grey800,
        onSurface = Grey200,
        surfaceVariant = Grey700,
        onSurfaceVariant = Grey200,
        outline = Grey700,
    )

// ── Main theme composable ─────────────────────────────────────────────
@Composable
fun CyberShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color: uses wallpaper colors on Android 12+
    // Set to false to always use CyberShield brand colors
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    // Make status bar transparent and match theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat
                .getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CyberShieldTypography,
        content = content,
    )
}
