package dev.axu.sheets.reader

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.ink.brush.Brush
import androidx.ink.strokes.Stroke
import dev.axu.sheets.ink.InkTarget
import dev.axu.sheets.ink.InkTargetResolver

/**
 * Tracks where pages are on screen so stylus input can be mapped onto the page under it.
 *
 * Positions are in the root composable's coordinates, which match the ink host's.
 */
class PageInkTargets(
    /** The part of each page shown, in points. */
    private val crops: List<RectF>,
    private val brush: () -> Brush,
    private val onStrokeStarted: () -> Unit,
    private val onStrokeFinished: (page: Int, stroke: Stroke) -> Unit,
) : InkTargetResolver {
    // Coordinates rather than rectangles: zooming changes where a page is drawn without laying it
    // out again, so positions are only accurate when read at the moment the pen comes down.
    private val pages = mutableMapOf<Int, LayoutCoordinates>()
    private val exclusions = mutableMapOf<Any, LayoutCoordinates>()

    fun onPagePositioned(page: Int, coordinates: LayoutCoordinates) {
        pages[page] = coordinates
    }

    fun onPageRemoved(page: Int) {
        pages.remove(page)
    }

    /** Marks UI floating above the pages (e.g. a toolbar) where the stylus should act like a finger. */
    fun onExclusionPositioned(key: Any, coordinates: LayoutCoordinates) {
        exclusions[key] = coordinates
    }

    fun onExclusionRemoved(key: Any) {
        exclusions.remove(key)
    }

    override fun inkTargetAt(x: Float, y: Float): InkTarget? {
        val point = Offset(x, y)
        if (exclusions.values.any { point in it.rootRect() }) return null
        for ((page, coordinates) in pages) {
            val rect = coordinates.rootRect()
            if (point !in rect) continue
            val crop = crops[page]
            val pointsPerPx = crop.width() / rect.width
            val hostToPage = Matrix().apply {
                setTranslate(-rect.left, -rect.top)
                postScale(pointsPerPx, pointsPerPx)
                postTranslate(crop.left, crop.top)
            }
            return InkTarget(brush(), hostToPage, onStrokeStarted) { stroke -> onStrokeFinished(page, stroke) }
        }
        return null
    }

    /**
     * Where this is drawn on screen, including any zoom. Not boundsInRoot(): that's clipped to the
     * screen, which would distort pages mid-swipe or zoomed in.
     */
    private fun LayoutCoordinates.rootRect(): Rect {
        if (!isAttached) return Rect.Zero
        return Rect(localToRoot(Offset.Zero), localToRoot(Offset(size.width.toFloat(), size.height.toFloat())))
    }
}
