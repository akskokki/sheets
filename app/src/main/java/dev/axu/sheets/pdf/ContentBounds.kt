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
 * Where anything is printed, as fractions of the page size, combined over every page so that
 * cropping all pages to it keeps them at the same scale. Null if every page is blank.
 *
 * Renders every page, so this is slow for long documents.
 */
suspend fun PdfDocument.contentBounds(): RectF? = withContext(Dispatchers.Default) {
    var bounds: RectF? = null
    for ((index, size) in pageSizes.withIndex()) {
        val width = SCAN_WIDTH
        val height = (width * size.height / size.width).roundToInt().coerceAtLeast(1)
        val bitmap = render(index, width, height)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        val printed = printedArea(pixels, width, height) ?: continue
        val page = RectF(
            printed.left / width.toFloat(),
            printed.top / height.toFloat(),
            printed.right / width.toFloat(),
            printed.bottom / height.toFloat(),
        )
        bounds = bounds?.apply { union(page) } ?: page
    }
    bounds
}

/** A rectangle of pixels; [right] and [bottom] are exclusive. */
internal data class PixelArea(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * The smallest area containing everything printed in [pixels] (ARGB, row by row), or null if blank.
 * Isolated pixels, e.g. scanner noise, don't count.
 */
internal fun printedArea(pixels: IntArray, width: Int, height: Int): PixelArea? {
    val printed = BooleanArray(pixels.size) { i ->
        val pixel = pixels[i]
        minOf(pixel shr 16 and 0xFF, pixel shr 8 and 0xFF, pixel and 0xFF) < INK_THRESHOLD
    }
    fun printedAt(x: Int, y: Int) = x in 0 until width && y in 0 until height && printed[y * width + x]

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
