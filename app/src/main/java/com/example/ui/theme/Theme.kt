package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Immutable
data class ZarnegarExtendedColors(
    val isDark: Boolean,
    val profit: Color,
    val profitContainer: Color,
    val loss: Color,
    val lossContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val amber: Color,
    val border: Color,
    val card: Color,
    val elevated: Color,
    val mutedText: Color
)

val LocalZarnegarColors = staticCompositionLocalOf {
    ZarnegarExtendedColors(
        isDark = false,
        profit = ProfitLight,
        profitContainer = ProfitContainerLight,
        loss = LossLight,
        lossContainer = LossContainerLight,
        info = InfoLight,
        infoContainer = InfoContainerLight,
        amber = AmberLight,
        border = LightOutline,
        card = LightCard,
        elevated = LightElevated,
        mutedText = LightTextMuted
    )
}

private val ZarnegarDarkColorScheme = darkColorScheme(
    primary = DarkGoldPrimary,
    onPrimary = DarkOnGold,
    primaryContainer = DarkGoldContainer,
    onPrimaryContainer = DarkGoldPrimary,
    secondary = AmberDark,
    onSecondary = DarkOnGold,
    secondaryContainer = DarkElevated,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = InfoDark,
    onTertiary = DarkBg,
    tertiaryContainer = InfoContainerDark,
    onTertiaryContainer = DarkTextPrimary,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextMuted,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkElevated,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = LossDark,
    errorContainer = LossContainerDark,
    onError = DarkBg
)

private val ZarnegarLightColorScheme = lightColorScheme(
    primary = LightGoldPrimary,
    onPrimary = LightOnGold,
    primaryContainer = LightGoldContainer,
    onPrimaryContainer = LightGoldPrimary,
    secondary = AmberLight,
    onSecondary = LightOnGold,
    secondaryContainer = LightElevated,
    onSecondaryContainer = LightTextPrimary,
    tertiary = InfoLight,
    onTertiary = LightOnGold,
    tertiaryContainer = InfoContainerLight,
    onTertiaryContainer = LightTextPrimary,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightElevated,
    onSurfaceVariant = LightTextMuted,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightCard,
    surfaceContainerHigh = LightElevated,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = LossLight,
    errorContainer = LossContainerLight,
    onError = LightOnGold
)

val ZarnegarShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ZarnegarTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ZarnegarDarkColorScheme else ZarnegarLightColorScheme
    val extended = if (darkTheme) {
        ZarnegarExtendedColors(
            isDark = true,
            profit = ProfitDark,
            profitContainer = ProfitContainerDark,
            loss = LossDark,
            lossContainer = LossContainerDark,
            info = InfoDark,
            infoContainer = InfoContainerDark,
            amber = AmberDark,
            border = DarkOutline,
            card = DarkCard,
            elevated = DarkElevated,
            mutedText = DarkTextMuted
        )
    } else {
        ZarnegarExtendedColors(
            isDark = false,
            profit = ProfitLight,
            profitContainer = ProfitContainerLight,
            loss = LossLight,
            lossContainer = LossContainerLight,
            info = InfoLight,
            infoContainer = InfoContainerLight,
            amber = AmberLight,
            border = LightOutline,
            card = LightCard,
            elevated = LightElevated,
            mutedText = LightTextMuted
        )
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalZarnegarColors provides extended
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = ZarnegarShapes,
            content = content
        )
    }
}
