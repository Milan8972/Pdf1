package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Brand & Logo colors
val BrandCoral = Color(0xFFFF6B6B)
val BrandAmber = Color(0xFFFFB020)
val DarkLogoBadgeBg = Color(0xFF1E2540)

// Light palette
val LightPrimaryAction = Color(0xFFD03A44)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFFE4E6)
val LightOnPrimaryContainer = Color(0xFF5C1119)
val LightSecondary = Color(0xFFF59E0B)
val LightOnSecondary = Color(0xFF1A1523)
val LightSecondaryContainer = Color(0xFFFEF3C7)
val LightOnSecondaryContainer = Color(0xFF5A3700)
val LightTertiary = Color(0xFF14B8A6)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFCCFBF1)
val LightOnTertiaryContainer = Color(0xFF064E46)
val LightBackground = Color(0xFFFFF8F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF7EFEA)
val LightText = Color(0xFF1A1523)
val LightMuted = Color(0xFF6B6475)
val LightOutline = Color(0xFFE7DDD8)
val LightDanger = Color(0xFFDC2626)

// Dark palette
val DarkPrimaryAction = Color(0xFFFF7A7A)
val DarkOnPrimary = Color(0xFF2B080D)
val DarkPrimaryContainer = Color(0xFF431A22)
val DarkOnPrimaryContainer = Color(0xFFFFDADA)
val DarkSecondary = Color(0xFFFFB020)
val DarkOnSecondary = Color(0xFF261900)
val DarkSecondaryContainer = Color(0xFF3A290D)
val DarkOnSecondaryContainer = Color(0xFFFFE1A6)
val DarkTertiary = Color(0xFF2DD4BF)
val DarkOnTertiary = Color(0xFF002B26)
val DarkTertiaryContainer = Color(0xFF103632)
val DarkOnTertiaryContainer = Color(0xFFB5F5EC)
val DarkBackground = Color(0xFF0F0D14)
val DarkSurface = Color(0xFF1A1722)
val DarkElevated = Color(0xFF25212F)
val DarkText = Color(0xFFEDE9F2)
val DarkMuted = Color(0xFFA59FB0)
val DarkOutline = Color(0xFF342F42)
val DarkDanger = Color(0xFFEF4444)

// Tool Category Tints
val TintConvert = Color(0xFFFF6B6B)
val TintOrganize = Color(0xFFF59E0B)
val TintEditSign = Color(0xFF8B5CF6)
val TintProtect = Color(0xFF64748B)
val TintOptimize = Color(0xFF10B981)
val TintScan = Color(0xFF14B8A6)
val TintExtract = Color(0xFF14B8A6)
val TintImageUtils = Color(0xFF3B82F6)

enum class AccentPreset(
    val label: String,
    val lightPrimary: Color,
    val darkPrimary: Color,
    val lightContainer: Color,
    val darkContainer: Color,
    val swatch: Color
) {
    CORAL(
        label = "Coral",
        lightPrimary = Color(0xFFD03A44),
        darkPrimary = Color(0xFFFF7A7A),
        lightContainer = Color(0xFFFFE4E6),
        darkContainer = Color(0xFF431A22),
        swatch = Color(0xFFFF6B6B)
    ),
    AMBER(
        label = "Amber",
        lightPrimary = Color(0xFFB45309),
        darkPrimary = Color(0xFFFFB020),
        lightContainer = Color(0xFFFEF3C7),
        darkContainer = Color(0xFF3E290A),
        swatch = Color(0xFFF59E0B)
    ),
    TEAL(
        label = "Teal",
        lightPrimary = Color(0xFF0F766E),
        darkPrimary = Color(0xFF2DD4BF),
        lightContainer = Color(0xFFCCFBF1),
        darkContainer = Color(0xFF113834),
        swatch = Color(0xFF14B8A6)
    ),
    VIOLET(
        label = "Violet",
        lightPrimary = Color(0xFF6D28D9),
        darkPrimary = Color(0xFFA78BFA),
        lightContainer = Color(0xFFEDE9FE),
        darkContainer = Color(0xFF2C1E4A),
        swatch = Color(0xFF8B5CF6)
    ),
    EMERALD(
        label = "Emerald",
        lightPrimary = Color(0xFF047857),
        darkPrimary = Color(0xFF34D399),
        lightContainer = Color(0xFFD1FAE5),
        darkContainer = Color(0xFF0E3529),
        swatch = Color(0xFF10B981)
    )
}
