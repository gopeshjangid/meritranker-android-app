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
    primary = MeritRankerColors.BrandBlueLight, // Blue 500
    onPrimary = MeritRankerColors.Slate900,
    primaryContainer = MeritRankerColors.BrandBlueContainerDark, // Blue 900
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = MeritRankerColors.BrandPurpleLight, // Purple 500
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = MeritRankerColors.BrandPurpleContainerDark,
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiary = MeritRankerColors.BrandOrange,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = MeritRankerColors.BrandOrangeContainerDark,
    onTertiaryContainer = Color(0xFFFFDBCF),
    background = MeritRankerColors.Slate950,
    surface = MeritRankerColors.Slate800,
    surfaceVariant = MeritRankerColors.Slate800,
    onBackground = MeritRankerColors.Slate50,
    onSurface = MeritRankerColors.Slate50,
    onSurfaceVariant = MeritRankerColors.Slate400,
    outline = MeritRankerColors.Slate700,
    outlineVariant = MeritRankerColors.Slate800,
    error = MeritRankerColors.ErrorLight,
    errorContainer = MeritRankerColors.ErrorContainerDark,
    onErrorContainer = Color(0xFFFECACA)
)

private val LightColorScheme = lightColorScheme(
    primary = MeritRankerColors.BrandBlue, // Blue 600
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = MeritRankerColors.BrandBlueContainerLight, // Blue 50
    onPrimaryContainer = MeritRankerColors.BrandBlueDark, // Blue 700/800
    secondary = MeritRankerColors.BrandPurple, // Purple 600
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = MeritRankerColors.BrandPurpleContainerLight, // Purple 50
    onSecondaryContainer = Color(0xFF5B21B6),
    tertiary = MeritRankerColors.BrandOrange,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = MeritRankerColors.BrandOrangeContainerLight,
    onTertiaryContainer = MeritRankerColors.BrandOrangeDark,
    background = MeritRankerColors.Slate50,
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
