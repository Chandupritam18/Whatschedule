package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = SkeditTealPrimary,
  onPrimary = Color(0xFF00291F),
  primaryContainer = SkeditTealContainer,
  onPrimaryContainer = SkeditOnTealContainer,
  secondary = SkeditCyanSecondary,
  onSecondary = Color(0xFF00263A),
  secondaryContainer = SkeditCyanContainer,
  onSecondaryContainer = SkeditOnCyanContainer,
  tertiary = SkeditAmberTertiary,
  onTertiary = Color(0xFF331D00),
  tertiaryContainer = SkeditAmberContainer,
  onTertiaryContainer = SkeditOnAmberContainer,
  background = SkeditNavyDark,
  onBackground = Color(0xFFE8F0FE),
  surface = SkeditNavySurface,
  onSurface = Color(0xFFE8F0FE),
  surfaceVariant = SkeditNavyCard,
  onSurfaceVariant = Color(0xFFAEC2DF),
  outline = Color(0xFF334C73)
)

private val LightColorScheme = lightColorScheme(
  primary = Color(0xFF008F6F),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFC4FFF0),
  onPrimaryContainer = Color(0xFF002B20),
  secondary = Color(0xFF0284C7),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE0F2FE),
  onSecondaryContainer = Color(0xFF072F49),
  tertiary = Color(0xFFD97706),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFFEF3C7),
  onTertiaryContainer = Color(0xFF451A03),
  background = SkeditLightBackground,
  onBackground = Color(0xFF0F172A),
  surface = SkeditLightSurface,
  onSurface = Color(0xFF0F172A),
  surfaceVariant = SkeditLightSurfaceVariant,
  onSurfaceVariant = Color(0xFF475569),
  outline = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
