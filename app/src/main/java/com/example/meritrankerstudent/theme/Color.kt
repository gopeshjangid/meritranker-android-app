package com.example.meritrankerstudent.theme

import androidx.compose.ui.graphics.Color

/**
 * MeritRanker Unified Brand Color Palette
 * Sourced from MeritRanker Production Platform (https://meritranker.com)
 */
object MeritRankerColors {
    // Core Brand Colors
    val BrandBlue = Color(0xFF2563EB) // Blue 600 - Primary Brand
    val BrandBlueDark = Color(0xFF1D4ED8) // Blue 700
    val BrandBlueLight = Color(0xFF3B82F6) // Blue 500
    val BrandBlueContainerLight = Color(0xFFEFF6FF) // Blue 50
    val BrandBlueContainerDark = Color(0xFF1E3A8A) // Blue 900

    val BrandOrange = Color(0xFFF97316) // Orange 500 - Brand Identity Accent
    val BrandOrangeDark = Color(0xFFEA580C) // Orange 600
    val BrandOrangeLight = Color(0xFFFB923C) // Orange 400
    val BrandOrangeContainerLight = Color(0xFFFFF7ED) // Orange 50
    val BrandOrangeContainerDark = Color(0xFF7C2D12) // Orange 900

    val BrandPurple = Color(0xFF7C3AED) // Purple 600 - AI Accent
    val BrandPurpleLight = Color(0xFFA855F7) // Purple 500
    val BrandPurpleContainerLight = Color(0xFFF5F3FF) // Purple 50
    val BrandPurpleContainerDark = Color(0xFF3B0764) // Purple 950

    val AiCyan = Color(0xFF06B6D4) // Cyan 500 - Smart Engine Glow
    val AiCyanLight = Color(0xFF22D3EE) // Cyan 400
    val AiCyanContainerLight = Color(0xFFECFEFF)
    val AiCyanContainerDark = Color(0xFF164E63)

    // Semantic States
    val Success = Color(0xFF10B981) // Emerald 500
    val SuccessLight = Color(0xFF34D399) // Emerald 400
    val SuccessDark = Color(0xFF059669) // Emerald 600
    val SuccessContainerLight = Color(0xFFECFDF5)
    val SuccessContainerDark = Color(0xFF064E3B)

    val Warning = Color(0xFFF59E0B) // Amber 500
    val WarningLight = Color(0xFFFBBF24) // Amber 400
    val WarningDark = Color(0xFFD97706) // Amber 600
    val WarningContainerLight = Color(0xFFFFFBEB)
    val WarningContainerDark = Color(0xFF78350F)

    val Error = Color(0xFFEF4444) // Red 500
    val ErrorLight = Color(0xFFF87171) // Red 400
    val ErrorDark = Color(0xFFDC2626) // Red 600
    val ErrorContainerLight = Color(0xFFFEF2F2)
    val ErrorContainerDark = Color(0xFF7F1D1D)

    // Slate Neutrals
    val Slate50 = Color(0xFFF8FAFC)
    val Slate100 = Color(0xFFF1F5F9)
    val Slate200 = Color(0xFFE2E8F0)
    val Slate300 = Color(0xFFCBD5E1)
    val Slate400 = Color(0xFF94A3B8)
    val Slate500 = Color(0xFF64748B)
    val Slate600 = Color(0xFF475569)
    val Slate700 = Color(0xFF334155)
    val Slate800 = Color(0xFF1E293B)
    val Slate900 = Color(0xFF0F172A)
    val Slate950 = Color(0xFF0B1120)

    // Legacy backwards-compatibility aliases
    val EliteBackground = Slate950
    val EliteSurface = Slate800
    val EliteSurfaceDim = Color(0xFF0A1322)
    val EliteSurfaceContainerLowest = Color(0xFF050E1D)
    val EliteSurfaceContainerLow = Color(0xFF131C2B)
    val EliteSurfaceContainer = Color(0xFF17202F)
    val EliteSurfaceContainerHigh = Color(0xFF212A3A)
    val EliteSurfaceContainerHighest = Color(0xFF2C3545)
    val ElitePrimary = AiCyan
    val EliteOnPrimary = Slate950
    val ElitePrimaryContainer = AiCyan
    val EliteOnPrimaryContainer = Slate950
    val EliteSecondary = Color(0xFFBCC7DE)
    val EliteSecondaryContainer = Color(0xFF3E495D)
    val EliteOnSecondaryContainer = Color(0xFFAEB9D0)
    val EliteOnSurface = Color(0xFFFFFFFF)
    val EliteOnSurfaceVariant = Slate400
    val EliteOutline = Slate700
    val EliteOutlineVariant = Slate700
    val EliteSuccess = SuccessLight
    val EliteWarning = Warning
    val EliteError = ErrorLight
    val EliteErrorContainer = ErrorContainerDark
    val EliteOnErrorContainer = Color(0xFFFFDAD6)
}
