package dev.axu.sheets.ui

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF1A4FD6),
    background = Color.White,
    surface = Color.White,
)

/**
 * Material's usual feedback, except nothing lights up while the pen hovers over it: the pen hovers
 * whenever it's near the screen, so highlights would flicker on everything it passes.
 */
private val NoHoverRipple = RippleConfiguration(
    rippleAlpha = RippleAlpha(
        draggedAlpha = 0.16f,
        focusedAlpha = 0.1f,
        hoveredAlpha = 0f,
        pressedAlpha = 0.1f,
    ),
)

@Composable
fun SheetsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors) {
        CompositionLocalProvider(LocalRippleConfiguration provides NoHoverRipple, content = content)
    }
}
