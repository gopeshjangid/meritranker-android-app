package com.example.meritrankerstudent.theme

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
    primary = MeritRankerColors.AiCyanLight, // Cyan 400 (#22d3ee - Website Dark Mode Primary)
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E293B), // Slate 800
    onPrimaryContainer = Color(0xFFF8FAFC),
    secondary = MeritRankerColors.BrandPurpleLight, // Purple 500
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3B0764),
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiary = MeritRankerColors.BrandOrangeLight,
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = Color(0xFF7C2D12),
    onTertiaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF0F172A), // Deep navy slate (#0f172a - Website Background)
    surface = Color(0xFF1E293B), // Slate 800 (#1e293b - Website Card)
    surfaceVariant = Color(0xFF334155), // Slate 700
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFF94A3B8), // Slate 400 soft grey text
    outline = Color(0xFF334155), // Slate 700
    outlineVariant = Color(0xFF1E293B),
    error = MeritRankerColors.ErrorLight,
    errorContainer = MeritRankerColors.ErrorContainerDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7), // Sky/Aqua 600 (#0284c7 - Website Light Mode Primary)
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE), // Sky 100 (Clean subtle aqua container)
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = MeritRankerColors.BrandPurple,
    onSecondary = Color.White,
    secondaryContainer = MeritRankerColors.BrandPurpleContainerLight,
    onSecondaryContainer = Color(0xFF5B21B6),
    tertiary = MeritRankerColors.BrandOrange,
    onTertiary = Color.White,
    tertiaryContainer = MeritRankerColors.BrandOrangeContainerLight,
    onTertiaryContainer = MeritRankerColors.BrandOrangeDark,
    background = MeritRankerColors.Slate50, // #f8fafc - Website Light Background
    surface = Color(0xFFFFFFFF),
    surfaceVariant = MeritRankerColors.Slate100,
    onBackground = MeritRankerColors.Slate900,
    onSurface = MeritRankerColors.Slate900,
    onSurfaceVariant = MeritRankerColors.Slate500,
    outline = MeritRankerColors.Slate200,
    outlineVariant = MeritRankerColors.Slate100,
    error = MeritRankerColors.ErrorDark,
    errorContainer = MeritRankerColors.ErrorContainerLight,
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun MeritRankerStudentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Strictly preserve MeritRanker brand colors
    content: @Composable () -> Unit,
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
