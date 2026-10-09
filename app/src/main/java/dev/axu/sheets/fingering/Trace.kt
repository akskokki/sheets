package dev.axu.sheets.fingering

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A pen stroke's path in page coordinates (points), as plain values so it can be read on the PC. */
class Trace(val xs: FloatArray, val ys: FloatArray) {
    init {
        require(xs.isNotEmpty() && xs.size == ys.size) { "A trace needs matching, non-empty coordinates" }
    }

    val bounds = Bounds(xs.min(), ys.min(), xs.max(), ys.max())

    val length: Float by lazy {
        var length = 0f
        for (i in 1 until xs.size) length += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        length
    }
}

/** An upright rectangle in points; y grows downwards. */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    fun union(other: Bounds) =
        Bounds(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

    /** Whether [other] comes within [distance] of this, horizontally and vertically. */
    fun isNear(other: Bounds, distance: Float) = other.left <= right + distance &&
        left <= other.right + distance &&
        other.top <= bottom + distance &&
        top <= other.bottom + distance
}
