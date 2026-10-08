package dev.axu.sheets.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes

/** Brushes, sized in PDF points so ink keeps its weight relative to the music at any zoom. */
object Pens {
    val Default: Brush = Brush.createWithColorIntArgb(
        family = StockBrushes.pressurePen(),
        colorIntArgb = 0xFF1A4FD6.toInt(),
        size = 1.4f,
        // About a tenth of a pixel on screen.
        epsilon = 0.04f,
    )
}
