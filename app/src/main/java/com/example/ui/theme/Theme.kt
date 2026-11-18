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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.domain.model.ThemeMode

@Immutable
data class NovaSemanticColors(
    val elevatedSurface: Color,
    val subtleBorder: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val errorContainer: Color,
    val secondaryText: Color,
)

private val DarkSemanticColors = NovaSemanticColors(
    elevatedSurface = NovaDarkElevatedSurface,
    subtleBorder = NovaDarkBorder,
    success = NovaSuccess,
    successContainer = NovaSuccessContainerDark,
    warning = NovaWarning,
    warningContainer = NovaWarningContainerDark,
    errorContainer = NovaErrorContainerDark,
    secondaryText = NovaDarkSecondaryText,
)

private val LightSemanticColors = NovaSemanticColors(
    elevatedSurface = NovaLightElevatedSurface,
    subtleBorder = NovaLightBorder,
    success = NovaSuccess,
    successContainer = NovaSuccessContainerLight,
    warning = NovaWarning,
    warningContainer = NovaWarningContainerLight,
    errorContainer = NovaErrorContainerLight,
    secondaryText = NovaLightSecondaryText,
)

val LocalNovaColors = staticCompositionLocalOf { DarkSemanticColors }

object NovaTheme {
    val colors: NovaSemanticColors
        @Composable get() = LocalNovaColors.current
}

private val NovaDarkColorScheme = darkColorScheme(
    primary = NovaAccentBlue,
    onPrimary = NovaOnAccent,
    primaryContainer = NovaPrimaryContainerDark,
    onPrimaryContainer = NovaSecondaryBlue,
    secondary = NovaSecondaryBlue,
    onSecondary = NovaDarkBackground,
    secondaryContainer = NovaDarkElevatedSurface,
    onSecondaryContainer = NovaDarkPrimaryText,
    tertiary = NovaSuccess,
    onTertiary = NovaDarkBackground,
    background = NovaDarkBackground,
    onBackground = NovaDarkPrimaryText,
    surface = NovaDarkSurface,
    onSurface = NovaDarkPrimaryText,
    surfaceVariant = NovaDarkElevatedSurface,
    onSurfaceVariant = NovaDarkSecondaryText,
    surfaceContainerLowest = NovaDarkBackground,
    surfaceContainerLow = NovaDarkSurface,
    surfaceContainer = NovaDarkSurface,
    surfaceContainerHigh = NovaDarkElevatedSurface,
    surfaceContainerHighest = NovaDarkSurfaceHighest,
    outline = NovaDarkBorder,
    outlineVariant = NovaDarkBorder.copy(alpha = 0.6f),
    error = NovaError,
    onError = NovaDarkBackground,
    errorContainer = NovaErrorContainerDark,
    onErrorContainer = NovaError,
)

private val NovaLightColorScheme = lightColorScheme(
    primary = NovaAccentBlueDark,
    onPrimary = NovaOnAccent,
    primaryContainer = NovaPrimaryContainerLight,
    onPrimaryContainer = NovaAccentBlueDark,
    secondary = NovaAccentBlue,
    onSecondary = NovaOnAccent,
    secondaryContainer = NovaLightElevatedSurface,
    onSecondaryContainer = NovaLightPrimaryText,
    tertiary = NovaSuccess,
    onTertiary = NovaOnAccent,
    background = NovaLightBackground,
    onBackground = NovaLightPrimaryText,
    surface = NovaLightSurface,
    onSurface = NovaLightPrimaryText,
    surfaceVariant = NovaLightElevatedSurface,
    onSurfaceVariant = NovaLightSecondaryText,
    surfaceContainerLowest = NovaLightSurface,
    surfaceContainerLow = NovaLightBackground,
    surfaceContainer = NovaLightSurface,
    surfaceContainerHigh = NovaLightElevatedSurface,
    surfaceContainerHighest = NovaLightSurfaceHighest,
    outline = NovaLightBorder,
    outlineVariant = NovaLightBorder.copy(alpha = 0.7f),
    error = NovaError,
    onError = NovaOnAccent,
    errorContainer = NovaErrorContainerLight,
    onErrorContainer = Color(0xFFB3261E),
)

private val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NovaSpaceTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) NovaDarkColorScheme else NovaLightColorScheme
    val semanticColors = if (useDarkTheme) DarkSemanticColors else LightSemanticColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            if (activity != null) {
                WindowCompat.getInsetsController(activity.window, view)
                    .isAppearanceLightStatusBars = !useDarkTheme
                WindowCompat.getInsetsController(activity.window, view)
                    .isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    CompositionLocalProvider(LocalNovaColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NovaTypography,
            shapes = NovaShapes,
            content = content,
        )
    }
}
