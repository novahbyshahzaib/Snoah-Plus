package com.snoah.plus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun SnoahPlusTheme(
    darkTheme: Boolean = true,
    accent: Color = Color.White,
    content: @Composable () -> Unit
) {
    // Dark: premium black. Light: premium white. One monochrome accent
    // only (white / silver / graphite); bright colours are gone.
    val darkScheme = darkColorScheme(
        primary = accent,
        onPrimary = if (accent.luminance() > 0.6f) Color.Black else Color.White,
        secondary = Color(0xFFD4D4D8),
        onSecondary = Color.Black,
        tertiary = Color(0xFFA1A1AA),
        background = SnoahBlack,
        surface = SnoahSurface,
        surfaceVariant = SnoahCard,
        surfaceTint = SnoahSurface,
        primaryContainer = SnoahCard,
        onPrimaryContainer = SnoahText,
        secondaryContainer = SnoahCard,
        onSecondaryContainer = SnoahText,
        onBackground = SnoahText,
        onSurface = SnoahText,
        onSurfaceVariant = SnoahMuted,
        outline = SnoahBorder,
        outlineVariant = SnoahBorder
    )
    val lightScheme = lightColorScheme(
        primary = Color.Black,
        onPrimary = Color.White,
        secondary = Color(0xFF3F3F46),
        onSecondary = Color.White,
        tertiary = Color(0xFF71717A),
        background = Color.White,
        surface = Color.White,
        surfaceVariant = Color(0xFFF4F4F5),
        surfaceTint = Color.White,
        primaryContainer = Color(0xFFF4F4F5),
        onPrimaryContainer = Color.Black,
        secondaryContainer = Color(0xFFF4F4F5),
        onSecondaryContainer = Color.Black,
        onBackground = Color.Black,
        onSurface = Color.Black,
        onSurfaceVariant = Color(0xFF71717A),
        outline = Color(0xFFE4E4E7),
        outlineVariant = Color(0xFFE4E4E7)
    )
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme else lightScheme,
        typography = SnoahTypography,
        content = content
    )
}
