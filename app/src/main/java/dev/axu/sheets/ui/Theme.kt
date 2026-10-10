package dev.axu.sheets.ui

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * The app's own colors. The buttons' colors lie on one curve in OKLCH by depth: the deeper a surface
 * sits, the darker and slightly bluer it is.
 */
object SheetsColors {
    /** A raised button's face. */
    val Raised = Color(0xFFFFFFFF)

    /** A pressed button's face. */
    val Pressed = Color(0xFFF3F6F9)

    /** Button groups, and the faces of buttons flush with them. */
    val Group = Color(0xFFEDF0F5)

    /** Between buttons in a group. */
    val Divider = Color(0xFFD0D4DC)

    /** A button's side below a raised face, or the wall above a pressed one. */
    val Edge = Color(0xFFBFC4CD)

    /** Icons and labels on buttons. */
    val Ink = Color(0xFF1F1F1F)
}

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
