package dev.axu.sheets.pdf

import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Pages are scanned at this width in pixels; a couple of points of precision is plenty. */
private const val SCAN_WIDTH = 400

/** A pixel whose darkest channel is below this is printed on. */
private const val INK_THRESHOLD = 192

/**
 * How far in from each edge, as a fraction of the page, marks that touch the edge are assumed to
 * be scanning artifacts (shadows, the table under a photographed page) rather than print.
 */
private const val EDGE_BAND = 0.1f

/**
 * Where anything is printed on each page, as fractions of the page size, or null for blank pages.
 *
 * Renders every page, so this is slow for long documents.
 */
suspend fun PdfDocument.contentBounds(): List<RectF?> = withContext(Dispatchers.Default) {
    pageSizes.mapIndexed { index, size ->
        val width = SCAN_WIDTH
        val height = (width * size.height / size.width).roundToInt().coerceAtLeast(1)
        val bitmap = render(index, width, height)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        printedArea(pixels, width, height)?.let {
            RectF(it.left / width.toFloat(), it.top / height.toFloat(), it.right / width.toFloat(), it.bottom / height.toFloat())
        }
    }
}

/** A rectangle of pixels; [right] and [bottom] are exclusive. */
internal data class PixelArea(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * The smallest area containing everything printed in [pixels] (ARGB, row by row), or null if blank.
 *
 * Ignores scanning artifacts: isolated pixels (noise), and marks that touch the edge of the page
 * without reaching further in than [EDGE_BAND].
 */
internal fun printedArea(pixels: IntArray, width: Int, height: Int): PixelArea? {
    val printed = BooleanArray(pixels.size) { i ->
        val pixel = pixels[i]
        minOf(pixel shr 16 and 0xFF, pixel shr 8 and 0xFF, pixel and 0xFF) < INK_THRESHOLD
    }
    fun printedAt(x: Int, y: Int) = x in 0 until width && y in 0 until height && printed[y * width + x]

    eraseEdgeArtifacts(printed, width, height)

    fun hasPrintedNeighbour(x: Int, y: Int): Boolean {
        for (dy in -1..1) {
            for (dx in -1..1) {
                if ((dx != 0 || dy != 0) && printedAt(x + dx, y + dy)) return true
            }
        }
        return false
    }

    var left = width
    var top = height
    var right = 0
    var bottom = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (!printedAt(x, y) || !hasPrintedNeighbour(x, y)) continue
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x + 1)
            bottom = maxOf(bottom, y + 1)
        }
    }
    return if (right > left) PixelArea(left, top, right, bottom) else null
}

/** Clears connected marks that touch the edge of the page and stay within [EDGE_BAND] of it. */
private fun eraseEdgeArtifacts(printed: BooleanArray, width: Int, height: Int) {
    val bandX = (width * EDGE_BAND).toInt()
    val bandY = (height * EDGE_BAND).toInt()
    val visited = BooleanArray(printed.size)
    val component = IntArray(printed.size)

    fun flood(start: Int) {
        if (!printed[start] || visited[start]) return
        visited[start] = true
        component[0] = start
        var size = 1
        var next = 0
        var withinBand = true
        while (next < size) {
            val i = component[next++]
            val x = i % width
            val y = i / width
            if (x in bandX until width - bandX && y in bandY until height - bandY) withinBand = false
            fun visit(nx: Int, ny: Int) {
                if (nx !in 0 until width || ny !in 0 until height) return
                val n = ny * width + nx
                if (printed[n] && !visited[n]) {
                    visited[n] = true
                    component[size++] = n
                }
            }
            visit(x - 1, y)
            visit(x + 1, y)
            visit(x, y - 1)
            visit(x, y + 1)
        }
        if (withinBand) for (k in 0 until size) printed[component[k]] = false
    }

    for (x in 0 until width) {
        flood(x)
        flood((height - 1) * width + x)
    }
    for (y in 0 until height) {
        flood(y * width)
        flood(y * width + width - 1)
    }
}
