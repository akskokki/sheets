package dev.axu.sheets.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF1A4FD6),
    background = Color.White,
    surface = Color.White,
)

/** Material's look, with [Press] in place of its ripple on everything tappable. */
@Composable
fun SheetsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors) {
        CompositionLocalProvider(LocalIndication provides Press, content = content)
    }
}
