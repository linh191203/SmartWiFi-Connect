package com.example.smartwificonnect.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

val LocalAppDarkMode = staticCompositionLocalOf { false }

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    secondary = Secondary,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = OnPrimary,
    primaryContainer = DarkPrimaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    secondary = DarkSecondary,
)

@Composable
fun SmartWifiAppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppDarkMode provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = AppTypography,
        ) {
            ProvideTextStyle(
                value = TextStyle(fontFamily = RoundedUiFontFamily),
                content = content,
            )
        }
    }
}
