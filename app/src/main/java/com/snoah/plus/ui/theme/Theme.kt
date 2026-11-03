package com.snoah.plus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun SnoahPlusTheme(
    darkTheme: Boolean = true,
    accent: Color = SnoahViolet,
    content: @Composable () -> Unit
) {
    val darkScheme = darkColorScheme(
        primary = accent,
        secondary = SnoahPink,
        tertiary = SnoahCyan,
        background = SnoahBlack,
        surface = SnoahSurface,
        surfaceVariant = SnoahCard,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = SnoahText,
        onSurface = SnoahText,
        onSurfaceVariant = SnoahMuted
    )
    val lightScheme = lightColorScheme(
        primary = accent,
        secondary = SnoahPink,
        tertiary = SnoahCyan,
        background = Color(0xFFF7F5FF),
        surface = Color.White,
        surfaceVariant = Color(0xFFEDEAFF),
        onBackground = Color(0xFF16121F),
        onSurface = Color(0xFF16121F),
        onSurfaceVariant = Color(0xFF5B5570)
    )
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme else lightScheme,
        typography = SnoahTypography,
        content = content
    )
}
