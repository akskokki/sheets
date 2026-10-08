package dev.axu.sheets.ink

import androidx.ink.geometry.AffineTransform
import androidx.ink.geometry.Intersection.intersects
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Erasing by scribbling back and forth over ink, like scratching something out on paper. */
object ScratchOut {
    /** The strokes among [candidates] that [stroke] scratches out; empty if it isn't a scratch-out. */
    fun erasedBy(stroke: Stroke, candidates: List<Stroke>): List<Stroke> {
        if (candidates.isEmpty()) return emptyList()
        val inputs = stroke.inputs
        val input = StrokeInput()
        val xs = FloatArray(inputs.size)
        val ys = FloatArray(inputs.size)
        for (i in 0 until inputs.size) {
            inputs.populate(i, input)
            xs[i] = input.x
            ys[i] = input.y
        }
        if (!ScratchGesture.matches(xs, ys)) return emptyList()
        return candidates.filter {
            stroke.shape.intersects(it.shape, AffineTransform.IDENTITY, AffineTransform.IDENTITY)
        }
    }
}

/**
 * Recognizes the shape of a scratch-out: a compact back-and-forth scribble, either zigzags or thin
 * loops. Coordinates are in PDF points (1/72 inch).
 *
 * Musical markings must not match: trills and wavy lines don't double back along their length,
 * hairpins reverse only once, and circled notes keep a steady distance from their center whereas a
 * scribble keeps crossing its middle.
 */
object ScratchGesture {
    /** Smallest scratch worth considering, about 1.5 mm. */
    private const val MIN_EXTENT = 4f
    private const val MIN_REVERSALS = 4
    /** Swings shorter than this fraction of the scratch's extent are jitter, not reversals. */
    private const val SWING_FRACTION = 0.3f
    /**
     * Path length relative to the length of the shape. A scribble covers its ground several times;
     * a zigzag progressing across a note needs swings at least ~1.5x higher than they advance,
     * which trills don't have.
     */
    private const val MIN_DENSITY = 1.75f
    /** Spread of distances from the center relative to their mean; near 0 for a circle. */
    private const val MIN_RADIAL_VARIATION = 0.3f
    private const val RESAMPLED_POINTS = 200

    fun matches(rawXs: FloatArray, rawYs: FloatArray): Boolean {
        if (rawXs.size < 2) return false
        // Input is sampled in time, so it's sparse where the pen moves fast and dense where it
        // slows down to turn; even spacing makes the measurements below independent of speed.
        val (xs, ys) = resample(rawXs, rawYs, max(pathLength(rawXs, rawYs) / RESAMPLED_POINTS, 0.1f))
        if (xs.size < 2 * MIN_REVERSALS) return false
        val axis = principalAxis(xs, ys)
        val along = project(xs, ys, axis)
        val across = project(xs, ys, axis + PI / 2)
        val length = along.extent()
        if (length < MIN_EXTENT) return false

        // Scratching in place goes back and forth along the main axis; scratching out a longer
        // note goes back and forth across it while progressing along it.
        val reversals = max(reversals(along), reversals(across))
        return reversals >= MIN_REVERSALS &&
            pathLength(xs, ys) / length >= MIN_DENSITY &&
            radialVariation(xs, ys) >= MIN_RADIAL_VARIATION
    }

    /** Angle of the direction in which the points spread the most. */
    private fun principalAxis(xs: FloatArray, ys: FloatArray): Double {
        val mx = xs.average()
        val my = ys.average()
        var cxx = 0.0
        var cyy = 0.0
        var cxy = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - mx
            val dy = ys[i] - my
            cxx += dx * dx
            cyy += dy * dy
            cxy += dx * dy
        }
        return 0.5 * atan2(2 * cxy, cxx - cyy)
    }

    private fun project(xs: FloatArray, ys: FloatArray, angle: Double): FloatArray {
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        return FloatArray(xs.size) { xs[it] * c + ys[it] * s }
    }

    private fun FloatArray.extent() = max() - min()

    /** Direction changes along a 1D projection, ignoring swings too small to be deliberate. */
    private fun reversals(values: FloatArray): Int {
        val threshold = max(values.extent() * SWING_FRACTION, MIN_EXTENT / 4)
        var direction = 0 // Not moving yet.
        var extreme = values[0] // Furthest point reached in the current direction.
        var count = 0
        for (v in values) {
            when (direction) {
                0 -> if (abs(v - extreme) >= threshold) {
                    direction = if (v > extreme) 1 else -1
                    extreme = v
                }
                1 -> if (v > extreme) {
                    extreme = v
                } else if (extreme - v >= threshold) {
                    count++
                    direction = -1
                    extreme = v
                }
                else -> if (v < extreme) {
                    extreme = v
                } else if (v - extreme >= threshold) {
                    count++
                    direction = 1
                    extreme = v
                }
            }
        }
        return count
    }

    /** Points every [spacing] along the polyline through the given points. */
    private fun resample(xs: FloatArray, ys: FloatArray, spacing: Float): Pair<FloatArray, FloatArray> {
        val outX = ArrayList<Float>()
        val outY = ArrayList<Float>()
        outX += xs[0]
        outY += ys[0]
        var carried = 0f // Distance travelled since the last emitted point.
        for (i in 1 until xs.size) {
            val segment = hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
            var at = spacing - carried
            while (at <= segment) {
                val t = at / segment
                outX += xs[i - 1] + (xs[i] - xs[i - 1]) * t
                outY += ys[i - 1] + (ys[i] - ys[i - 1]) * t
                at += spacing
            }
            carried = segment - (at - spacing)
        }
        return outX.toFloatArray() to outY.toFloatArray()
    }

    private fun pathLength(xs: FloatArray, ys: FloatArray): Float {
        var length = 0f
        for (i in 1 until xs.size) length += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        return length
    }

    /** Coefficient of variation of the points' distances from their centroid. */
    private fun radialVariation(xs: FloatArray, ys: FloatArray): Float {
        val mx = xs.average().toFloat()
        val my = ys.average().toFloat()
        val distances = FloatArray(xs.size) { hypot(xs[it] - mx, ys[it] - my) }
        val mean = distances.average()
        if (mean == 0.0) return 0f
        val variance = distances.sumOf { (it - mean) * (it - mean) } / distances.size
        return (sqrt(variance) / mean).toFloat()
    }
}
