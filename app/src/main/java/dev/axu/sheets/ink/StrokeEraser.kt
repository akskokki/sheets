package dev.axu.sheets.ink

import androidx.ink.geometry.AffineTransform
import androidx.ink.geometry.ImmutableBox
import androidx.ink.geometry.ImmutableTriangle
import androidx.ink.geometry.ImmutableVec
import androidx.ink.geometry.Intersection.intersects
import androidx.ink.strokes.Stroke
import kotlin.math.hypot

/** Erasing whole strokes by touching them with the eraser. Coordinates are in PDF points. */
object StrokeEraser {
    /** How close the eraser has to pass to a stroke to erase it, about 1.5 mm. */
    const val RADIUS = 4f

    /** The strokes among [candidates] that the eraser touches moving from ([x0], [y0]) to ([x1], [y1]). */
    fun touched(x0: Float, y0: Float, x1: Float, y1: Float, candidates: List<Stroke>): List<Stroke> {
        if (candidates.isEmpty()) return emptyList()
        val length = hypot(x1 - x0, y1 - y0)
        if (length == 0f) {
            val area = ImmutableBox.fromCenterAndDimensions(ImmutableVec(x0, y0), 2 * RADIUS, 2 * RADIUS)
            return candidates.filter { it.shape.intersects(area, AffineTransform.IDENTITY) }
        }
        // The path widened by the radius to either side, as two triangles. Not past its ends: each
        // movement starts where the last one ended. (Ink's own parallelogram from a segment and
        // padding misplaces rotated ones.)
        val nx = -(y1 - y0) / length * RADIUS
        val ny = (x1 - x0) / length * RADIUS
        val a = ImmutableVec(x0 + nx, y0 + ny)
        val b = ImmutableVec(x0 - nx, y0 - ny)
        val c = ImmutableVec(x1 - nx, y1 - ny)
        val d = ImmutableVec(x1 + nx, y1 + ny)
        val first = ImmutableTriangle(a, b, c)
        val second = ImmutableTriangle(a, c, d)
        return candidates.filter {
            it.shape.intersects(first, AffineTransform.IDENTITY) ||
                it.shape.intersects(second, AffineTransform.IDENTITY)
        }
    }
}
