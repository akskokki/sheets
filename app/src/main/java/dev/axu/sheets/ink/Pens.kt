package dev.axu.sheets.ink

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.StockBrushes

class PenColor(val name: String, val argb: Int)

/** Stroke widths in PDF points, so ink keeps its weight relative to the music at any zoom. */
enum class PenWidth(val label: String, val size: Float) {
    Fine("Fine", 0.8f),
    Medium("Medium", 1.4f),
    Bold("Bold", 2.4f),
}

object Pens {
    /**
     * Brush families with stable IDs for storage. Versions are pinned so saved ink keeps looking
     * the same when the Ink library updates its stock brushes.
     */
    private val families = mapOf(
        "pressure-pen-v1" to StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1),
    )
    private val pen = families.getValue("pressure-pen-v1")

    val colors = listOf(
        PenColor("Blue", 0xFF1A4FD6.toInt()),
        PenColor("Black", 0xFF1A1A1A.toInt()),
        PenColor("Red", 0xFFE0242B.toInt()),
        PenColor("Green", 0xFF16924F.toInt()),
        PenColor("Pink", 0xFFFF369B.toInt()),
    )

    private val brushes = HashMap<Pair<PenColor, PenWidth>, Brush>()

    fun brush(color: PenColor, width: PenWidth): Brush = brushes.getOrPut(color to width) {
        Brush.createWithColorIntArgb(
            family = pen,
            colorIntArgb = color.argb,
            size = width.size,
            // About a tenth of a pixel on screen.
            epsilon = 0.04f,
        )
    }

    val Default: Brush = brush(colors.first(), PenWidth.Medium)

    fun idOf(family: BrushFamily): String =
        families.entries.firstOrNull { it.value == family }?.key ?: error("Unknown brush family $family")

    fun familyOf(id: String): BrushFamily? = families[id]
}

/** The pen the user writes with, remembered across sessions. */
@Stable
class PenSettings(context: Context) {
    private val prefs = context.getSharedPreferences("pen", Context.MODE_PRIVATE)

    var color: PenColor by mutableStateOf(
        Pens.colors.firstOrNull { it.name == prefs.getString(KEY_COLOR, null) } ?: Pens.colors.first(),
    )
        private set

    var width: PenWidth by mutableStateOf(
        PenWidth.entries.firstOrNull { it.name == prefs.getString(KEY_WIDTH, null) } ?: PenWidth.Medium,
    )
        private set

    val brush: Brush get() = Pens.brush(color, width)

    fun select(color: PenColor) {
        this.color = color
        prefs.edit { putString(KEY_COLOR, color.name) }
    }

    fun select(width: PenWidth) {
        this.width = width
        prefs.edit { putString(KEY_WIDTH, width.name) }
    }

    private companion object {
        const val KEY_COLOR = "color"
        const val KEY_WIDTH = "width"
    }
}
