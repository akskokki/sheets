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
enum class PenWidth(val size: Float) {
    Fine(0.8f),
    Medium(1.4f),
    Bold(2.4f),
}

object Pens {
    /**
     * Brush families with stable IDs for storage. Versions are pinned so saved ink keeps looking
     * the same when the Ink library updates its stock brushes.
     */
    private val pen = StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1)
    private val families = mapOf("pressure-pen-v1" to pen)

    val colors = listOf(
        PenColor("Blue", 0xFF1A4FD6.toInt()),
        PenColor("Black", 0xFF1A1A1A.toInt()),
        PenColor("Red", 0xFFE0242B.toInt()),
        PenColor("Green", 0xFF1E5404.toInt()),
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

    val defaultColor = colors.first()
    val defaultWidth = PenWidth.Medium

    /** Offered to switch to before any others have been used. */
    val defaultRecents = listOf("Red", "Black").map { name -> Pen(colors.first { it.name == name }, defaultWidth) }
    val Default: Brush = brush(defaultColor, defaultWidth)

    fun idOf(family: BrushFamily): String =
        families.entries.firstOrNull { it.value == family }?.key ?: error("Unknown brush family $family")

    fun familyOf(id: String): BrushFamily? = families[id]
}

/** The pen the user writes with and the ones used before it, remembered across sessions. */
@Stable
class PenSettings(context: Context) {
    private val prefs = context.getSharedPreferences("pen", Context.MODE_PRIVATE)

    var color: PenColor by mutableStateOf(
        Pens.colors.firstOrNull { it.name == prefs.getString(KEY_COLOR, null) } ?: Pens.defaultColor,
    )
        private set

    var width: PenWidth by mutableStateOf(
        PenWidth.entries.firstOrNull { it.name == prefs.getString(KEY_WIDTH, null) } ?: Pens.defaultWidth,
    )
        private set

    val pen: Pen get() = Pen(color, width)

    val brush: Brush get() = Pens.brush(color, width)

    /** The pens used before this one, most recent first; see [RecentPens]. */
    var recents: List<Pen> by mutableStateOf(loadRecents())
        private set

    private var choosingFrom: Pen? = null

    /** Starts trying out colors and widths, after which only the pen finally chosen counts as used. */
    fun startChoosing() {
        choosingFrom = pen
    }

    fun doneChoosing() {
        val from = choosingFrom ?: return
        choosingFrom = null
        if (from != pen) saveRecents(RecentPens.afterSwitch(from, pen, recents))
    }

    fun switchTo(pen: Pen) {
        saveRecents(RecentPens.afterSwitch(this.pen, pen, recents))
        select(pen.color)
        select(pen.width)
    }

    fun select(color: PenColor) {
        this.color = color
        prefs.edit { putString(KEY_COLOR, color.name) }
    }

    fun select(width: PenWidth) {
        this.width = width
        prefs.edit { putString(KEY_WIDTH, width.name) }
    }

    private fun saveRecents(recents: List<Pen>) {
        this.recents = recents
        val saved = recents.joinToString(",") { "${it.color.name}/${it.width.name}" }
        prefs.edit { putString(KEY_RECENTS, saved) }
    }

    private fun loadRecents(): List<Pen> {
        val saved = prefs.getString(KEY_RECENTS, null) ?: return Pens.defaultRecents.filter { it != pen }
        return saved.split(",").mapNotNull { entry ->
            val (color, width) = entry.split("/").takeIf { it.size == 2 } ?: return@mapNotNull null
            Pen(
                Pens.colors.firstOrNull { it.name == color } ?: return@mapNotNull null,
                PenWidth.entries.firstOrNull { it.name == width } ?: return@mapNotNull null,
            )
        }
    }

    private companion object {
        const val KEY_COLOR = "color"
        const val KEY_WIDTH = "width"

        /** Color and width names, as "Red/Fine,Black/Medium". */
        const val KEY_RECENTS = "recents"
    }
}
