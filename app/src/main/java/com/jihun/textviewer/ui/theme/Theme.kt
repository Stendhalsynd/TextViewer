package com.jihun.textviewer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DayColorScheme = lightColorScheme(
    primary = DayPrimary,
    onPrimary = DayBackground,
    background = DayBackground,
    onBackground = DayText,
    surface = DaySurface,
    onSurface = DayText,
    surfaceVariant = DaySurfaceVariant,
    onSurfaceVariant = DayMutedText,
)

private val NightColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = NightBackground,
    background = NightBackground,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightMutedText,
)

@Composable
fun TextViewerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NightColorScheme else DayColorScheme,
        typography = TextViewerTypography,
        content = content,
    )
}
