package dev.axu.sheets.reader

import android.graphics.Matrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import androidx.ink.brush.Brush
import androidx.ink.strokes.Stroke
import dev.axu.sheets.ink.InkTarget
import dev.axu.sheets.ink.InkTargetResolver
import dev.axu.sheets.pdf.PageSize

/**
 * Tracks where pages are on screen so stylus input can be mapped onto the page under it.
 *
 * Positions are in the root composable's coordinates, which match the ink host's.
 */
class PageInkTargets(
    private val pageSizes: List<PageSize>,
    private val brush: () -> Brush,
    private val onStrokeFinished: (page: Int, stroke: Stroke) -> Unit,
) : InkTargetResolver {
    private val pageRects = mutableMapOf<Int, Rect>()
    private val excludedRects = mutableMapOf<Any, Rect>()

    fun onPagePositioned(page: Int, coordinates: LayoutCoordinates) {
        pageRects[page] = coordinates.rootRect()
    }

    fun onPageRemoved(page: Int) {
        pageRects.remove(page)
    }

    /** Marks UI floating above the pages (e.g. a toolbar) where the stylus should act like a finger. */
    fun onExclusionPositioned(key: Any, coordinates: LayoutCoordinates) {
        excludedRects[key] = coordinates.rootRect()
    }

    fun onExclusionRemoved(key: Any) {
        excludedRects.remove(key)
    }

    override fun inkTargetAt(x: Float, y: Float): InkTarget? {
        val point = Offset(x, y)
        if (excludedRects.values.any { point in it }) return null
        val (page, rect) = pageRects.entries.firstOrNull { point in it.value } ?: return null
        val pointsPerPx = pageSizes[page].width / rect.width
        val hostToPage = Matrix().apply {
            setTranslate(-rect.left, -rect.top)
            postScale(pointsPerPx, pointsPerPx)
        }
        return InkTarget(brush(), hostToPage) { stroke -> onStrokeFinished(page, stroke) }
    }

    // Not boundsInRoot(): that's clipped to the screen, which would distort pages mid-swipe.
    private fun LayoutCoordinates.rootRect() = Rect(positionInRoot(), size.toSize())
}
