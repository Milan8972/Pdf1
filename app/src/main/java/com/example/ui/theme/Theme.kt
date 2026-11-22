package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class PaperPilotExtraColors(
    val isDark: Boolean,
    val elevatedSurface: Color,
    val subtleBorder: Color,
    val mutedText: Color,
    val brandCoral: Color,
    val brandAmber: Color,
    val successTeal: Color
)

val LocalPaperPilotColors = staticCompositionLocalOf {
    PaperPilotExtraColors(
        isDark = false,
        elevatedSurface = LightSurface,
        subtleBorder = LightOutline,
        mutedText = LightMuted,
        brandCoral = BrandCoral,
        brandAmber = BrandAmber,
        successTeal = LightTertiary
    )
}

val PaperPilotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PaperPilotTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentPreset: AccentPreset = AccentPreset.CORAL,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (useDark) {
        darkColorScheme(
            primary = accentPreset.darkPrimary,
            onPrimary = DarkOnPrimary,
            primaryContainer = accentPreset.darkContainer,
            onPrimaryContainer = DarkOnPrimaryContainer,
            secondary = DarkSecondary,
            onSecondary = DarkOnSecondary,
            secondaryContainer = DarkSecondaryContainer,
            onSecondaryContainer = DarkOnSecondaryContainer,
            tertiary = DarkTertiary,
            onTertiary = DarkOnTertiary,
            tertiaryContainer = DarkTertiaryContainer,
            onTertiaryContainer = DarkOnTertiaryContainer,
            background = DarkBackground,
            onBackground = DarkText,
            surface = DarkSurface,
            onSurface = DarkText,
            surfaceVariant = DarkElevated,
            onSurfaceVariant = DarkMuted,
            surfaceContainer = DarkSurface,
            surfaceContainerHigh = DarkElevated,
            surfaceContainerLowest = DarkBackground,
            outline = DarkOutline,
            outlineVariant = DarkOutline.copy(alpha = 0.65f),
            error = DarkDanger,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = accentPreset.lightPrimary,
            onPrimary = LightOnPrimary,
            primaryContainer = accentPreset.lightContainer,
            onPrimaryContainer = LightOnPrimaryContainer,
            secondary = LightSecondary,
            onSecondary = LightOnSecondary,
            secondaryContainer = LightSecondaryContainer,
            onSecondaryContainer = LightOnSecondaryContainer,
            tertiary = LightTertiary,
            onTertiary = LightOnTertiary,
            tertiaryContainer = LightTertiaryContainer,
            onTertiaryContainer = LightOnTertiaryContainer,
            background = LightBackground,
            onBackground = LightText,
            surface = LightSurface,
            onSurface = LightText,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightMuted,
            surfaceContainer = LightSurface,
            surfaceContainerHigh = LightSurfaceVariant,
            surfaceContainerLowest = LightSurface,
            outline = LightOutline,
            outlineVariant = LightOutline.copy(alpha = 0.7f),
            error = LightDanger,
            onError = Color.White
        )
    }

    val extraColors = PaperPilotExtraColors(
        isDark = useDark,
        elevatedSurface = if (useDark) DarkElevated else LightSurface,
        subtleBorder = if (useDark) DarkOutline else LightOutline,
        mutedText = if (useDark) DarkMuted else LightMuted,
        brandCoral = BrandCoral,
        brandAmber = BrandAmber,
        successTeal = if (useDark) DarkTertiary else LightTertiary
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(LocalPaperPilotColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PaperPilotTypography,
            shapes = PaperPilotShapes,
            content = content
        )
    }
}
