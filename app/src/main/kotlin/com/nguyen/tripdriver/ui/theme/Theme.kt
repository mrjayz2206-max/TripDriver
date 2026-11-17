/*
 * Copyright (c) 2026 Nguyên. All rights reserved.
 */

package com.nguyen.tripdriver.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
  primary = DarkPrimary,
  onPrimary = DarkOnPrimary,
  primaryContainer = DarkPrimaryContainer,
  onPrimaryContainer = DarkOnPrimaryContainer,
  secondary = DarkSecondary,
  onSecondary = DarkOnSecondary,
  secondaryContainer = DarkSecondaryContainer,
  onSecondaryContainer = DarkOnSecondaryContainer,
  background = DarkBackground,
  onBackground = DarkOnSurface,
  surface = DarkSurface,
  onSurface = DarkOnSurface,
  surfaceVariant = DarkSurfaceContainer,
  onSurfaceVariant = DarkOnSurfaceVariant,
  surfaceContainerLowest = DarkBackground,
  surfaceContainerLow = DarkSurfaceContainerLow,
  surfaceContainer = DarkSurfaceContainer,
  surfaceContainerHigh = DarkSurfaceContainerHigh,
  surfaceContainerHighest = DarkSurfaceContainerHigh,
  error = DarkError,
  onError = DarkOnError,
  errorContainer = DarkErrorContainer,
  onErrorContainer = DarkOnErrorContainer,
  outline = DarkOutline,
  outlineVariant = DarkOutlineVariant,
)

private val LightColorScheme = lightColorScheme(
  primary = LightPrimary,
  onPrimary = LightOnPrimary,
  primaryContainer = LightPrimaryContainer,
  onPrimaryContainer = LightOnPrimaryContainer,
  secondary = LightSecondary,
  onSecondary = LightOnSecondary,
  secondaryContainer = LightSecondaryContainer,
  onSecondaryContainer = LightOnSecondaryContainer,
  background = LightBackground,
  onBackground = LightOnSurface,
  surface = LightSurface,
  onSurface = LightOnSurface,
  surfaceVariant = LightSurfaceContainer,
  onSurfaceVariant = LightOnSurfaceVariant,
  surfaceContainerLowest = LightSurface,
  surfaceContainerLow = LightSurfaceContainerLow,
  surfaceContainer = LightSurfaceContainer,
  surfaceContainerHigh = LightSurfaceContainerHigh,
  surfaceContainerHighest = LightSurfaceContainerHigh,
  error = LightError,
  onError = LightOnError,
  errorContainer = LightErrorContainer,
  onErrorContainer = LightOnErrorContainer,
  outline = LightOutline,
  outlineVariant = LightOutlineVariant,
)

@Composable
fun TripDriverTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}
