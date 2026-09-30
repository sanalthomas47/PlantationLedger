package com.santhomach.plantationledger.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Color(0xFF001A0D),
    primaryContainer = EmeraldDark,
    onPrimaryContainer = Color(0xFFB3F0D3),

    secondary = GoldBright,
    onSecondary = Color(0xFF2A1C00),
    secondaryContainer = GoldDeep,
    onSecondaryContainer = GoldSurface,

    tertiary = Color(0xFFFFB3AB),
    onTertiary = Color(0xFF3A0000),
    tertiaryContainer = RubyDeep,
    onTertiaryContainer = RubyLight,

    background = DarkBg,
    onBackground = Color(0xFFD8EFE0),
    surface = DarkSurface,
    onSurface = Color(0xFFD0EDD8),
    surfaceVariant = DarkVariant,
    onSurfaceVariant = Color(0xFFADCBB9),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldSurface,
    onPrimaryContainer = EmeraldDeep,

    secondary = GoldPrimary,
    onSecondary = Color.White,
    secondaryContainer = GoldSurface,
    onSecondaryContainer = GoldDeep,

    tertiary = RubyPrimary,
    onTertiary = Color.White,
    tertiaryContainer = RubyLight,
    onTertiaryContainer = RubyDeep,

    background = CreamSurface,
    onBackground = Color(0xFF0D1F15),
    surface = Color.White,
    onSurface = Color(0xFF0D1F15),
    surfaceVariant = Color(0xFFEEF4EF),
    onSurfaceVariant = Color(0xFF3D5240),

    error = RubyPrimary,
    onError = Color.White
)

@Composable
fun PlantationLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+, but disabled so the estate palette is used.
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
