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
import androidx.compose.ui.unit.dp

@Immutable
data class SportsPalette(
    val noDrawPrimary: Color,
    val noDrawContainer: Color,
    val homeAccent: Color,
    val homeContainer: Color,
    val awayAccent: Color,
    val drawWarning: Color,
    val drawWarningContainer: Color,
    val lossDanger: Color,
    val lossDangerContainer: Color,
    val cardBorder: Color,
    val elevatedCard: Color,
)

val DarkSportsPalette = SportsPalette(
    noDrawPrimary = PitchEmerald,
    noDrawContainer = EmeraldContainerDark,
    homeAccent = TacticalCyan,
    homeContainer = CyanContainerDark,
    awayAccent = AwayIndigo,
    drawWarning = AmberWarn,
    drawWarningContainer = AmberContainerDark,
    lossDanger = CoralRed,
    lossDangerContainer = CoralContainerDark,
    cardBorder = BorderSlate,
    elevatedCard = ElevatedSlate,
)

val LightSportsPalette = SportsPalette(
    noDrawPrimary = EmeraldLight,
    noDrawContainer = EmeraldContainerLight,
    homeAccent = CyanLight,
    homeContainer = CyanContainerLight,
    awayAccent = AwayIndigoLight,
    drawWarning = AmberLight,
    drawWarningContainer = AmberContainerLight,
    lossDanger = CoralLight,
    lossDangerContainer = CoralContainerLight,
    cardBorder = DaylightBorder,
    elevatedCard = DaylightElevated,
)

val LocalSportsPalette = staticCompositionLocalOf { DarkSportsPalette }

private val DarkColorScheme = darkColorScheme(
    primary = PitchEmerald,
    onPrimary = OnEmeraldDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    secondary = TacticalCyan,
    onSecondary = OnCyanDark,
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = OnCyanContainerDark,
    tertiary = AmberWarn,
    onTertiary = OnAmberDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    error = CoralRed,
    onError = OnCoralDark,
    errorContainer = CoralContainerDark,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = TacticalSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = TacticalSurface,
    surfaceContainer = ElevatedSlate,
    surfaceContainerHigh = HighestSlate,
    outline = BorderSlate,
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldLight,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = OnEmeraldContainerLight,
    secondary = CyanLight,
    onSecondary = Color.White,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = OnCyanContainerLight,
    tertiary = AmberLight,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
    error = CoralLight,
    onError = Color.White,
    errorContainer = CoralContainerLight,
    background = DaylightBg,
    onBackground = TextPrimaryLight,
    surface = DaylightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = DaylightElevated,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = DaylightSurface,
    surfaceContainer = DaylightElevated,
    outline = DaylightBorder,
)

val NoDrawShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NoDrawTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val sportsPalette = if (darkTheme) DarkSportsPalette else LightSportsPalette

    CompositionLocalProvider(LocalSportsPalette provides sportsPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NoDrawTypography,
            shapes = NoDrawShapes,
            content = content,
        )
    }
}
