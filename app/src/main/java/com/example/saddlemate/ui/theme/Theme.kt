package com.example.saddlemate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldDarkPrimary,
    onPrimary = EmeraldDarkOnPrimary,
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = EmeraldDarkOnContainer,
    secondary = CyanSlateDarkSecondary,
    onSecondary = CyanSlateDarkOnSecondary,
    secondaryContainer = CyanSlateDarkContainer,
    onSecondaryContainer = CyanSlateDarkOnContainer,
    tertiary = GoldDarkTertiary,
    onTertiary = GoldDarkOnTertiary,
    tertiaryContainer = GoldDarkContainer,
    onTertiaryContainer = GoldDarkOnContainer,
    background = SportBgDark,
    onBackground = SportOnSurfaceDark,
    surface = SportSurfaceDark,
    onSurface = SportOnSurfaceDark,
    surfaceVariant = SportSurfaceVariantDark,
    onSurfaceVariant = SportOnSurfaceVariantDark,
    outline = SportOutlineDark,
)

private val LightColorScheme = lightColorScheme(
    primary = ForestPrimary,
    onPrimary = ForestOnPrimary,
    primaryContainer = ForestPrimaryContainer,
    onPrimaryContainer = ForestOnPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = SlateOnSecondary,
    secondaryContainer = SlateSecondaryContainer,
    onSecondaryContainer = SlateOnSecondaryContainer,
    tertiary = AmberTertiary,
    onTertiary = AmberOnTertiary,
    tertiaryContainer = AmberTertiaryContainer,
    onTertiaryContainer = AmberOnTertiaryContainer,
    background = SportBgLight,
    onBackground = SportOnSurfaceLight,
    surface = SportSurfaceLight,
    onSurface = SportOnSurfaceLight,
    surfaceVariant = SportSurfaceVariantLight,
    onSurfaceVariant = SportOnSurfaceVariantLight,
    outline = SportOutlineLight,
)

private val SportShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun SaddleMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disabled dynamic wallpaper tinting so the sporty SaddleMate colors stay consistent across devices.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SportShapes,
        content = content,
    )
}
