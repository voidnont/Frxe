package com.frxe.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FrxeColors = darkColorScheme(
    primary = Color(0xFFB7FF59),
    onPrimary = Color(0xFF0B1104),
    secondary = Color(0xFFD7D8E2),
    background = Color(0xFF050507),
    onBackground = Color(0xFFF9F9FC),
    surface = Color(0xFF101116),
    onSurface = Color(0xFFF9F9FC),
    surfaceVariant = Color(0xFF181A20),
    onSurfaceVariant = Color(0xFFD7D8E2),
    outline = Color(0xFF5A5D68)
)

@Composable
fun FrxeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FrxeColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
