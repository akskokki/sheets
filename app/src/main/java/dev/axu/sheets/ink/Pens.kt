package dev.axu.sheets.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.StockBrushes

/** Brushes, sized in PDF points so ink keeps its weight relative to the music at any zoom. */
object Pens {
    /**
     * Brush families with stable IDs for storage. Versions are pinned so saved ink keeps looking
     * the same when the Ink library updates its stock brushes.
     */
    private val families = mapOf(
        "pressure-pen-v1" to StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1),
    )

    val Default: Brush = Brush.createWithColorIntArgb(
        family = families.getValue("pressure-pen-v1"),
        colorIntArgb = 0xFF1A4FD6.toInt(),
        size = 1.4f,
        // About a tenth of a pixel on screen.
        epsilon = 0.04f,
    )

    fun idOf(family: BrushFamily): String =
        families.entries.firstOrNull { it.value == family }?.key ?: error("Unknown brush family $family")

    fun familyOf(id: String): BrushFamily? = families[id]
}
