package dev.axu.sheets.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Built from [INK_THRESHOLD] and [EDGE_BAND], so tuning them needs no changes here. */
class PrintedAreaTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    private fun gray(level: Int) = (0xFF shl 24) or (level shl 16) or (level shl 8) or level

    private fun page(width: Int, height: Int, draw: (set: (x: Int, y: Int, color: Int) -> Unit) -> Unit): IntArray {
        val pixels = IntArray(width * height) { white }
        draw { x, y, color -> pixels[y * width + x] = color }
        return pixels
    }

    @Test
    fun blankPageHasNothingPrinted() {
        assertNull(printedArea(page(20, 30) {}, 20, 30))
    }

    @Test
    fun boundsEverythingPrinted() {
        val pixels = page(20, 30) { set ->
            for (x in 4..15) set(x, 5, black)
            for (y in 5..24) set(10, y, black)
            for (x in 4..6) set(x, 24, black)
        }
        assertEquals(PixelArea(left = 4, top = 5, right = 16, bottom = 25), printedArea(pixels, 20, 30))
    }

    @Test
    fun ignoresSpecks() {
        val pixels = page(20, 30) { set ->
            for (x in 4..15) for (y in 10..12) set(x, y, black)
            set(1, 1, black)
            set(18, 28, black)
        }
        assertEquals(PixelArea(left = 4, top = 10, right = 16, bottom = 13), printedArea(pixels, 20, 30))
    }

    @Test
    fun ignoresPaleBackground() {
        val paper = gray((INK_THRESHOLD + 255) / 2)
        val pixels = IntArray(20 * 30) { paper }
        for (x in 4..15) for (y in 10..12) pixels[y * 20 + x] = black
        assertEquals(PixelArea(left = 4, top = 10, right = 16, bottom = 13), printedArea(pixels, 20, 30))
    }

    @Test
    fun countsColouredPrint() {
        val red = 0xFFD02020.toInt()
        val pixels = page(20, 30) { set -> for (x in 2..8) for (y in 3..4) set(x, y, red) }
        assertEquals(PixelArea(left = 2, top = 3, right = 9, bottom = 5), printedArea(pixels, 20, 30))
    }

    @Test
    fun ignoresScanningArtifactsAtTheEdges() {
        val artifact = (100 * EDGE_BAND / 2).toInt().coerceAtLeast(1) // Well within the edge band.
        val pixels = page(100, 100) { set ->
            for (x in 20..80) for (y in 30..60) set(x, y, black)
            for (y in 0..99) for (x in 100 - artifact..99) set(x, y, black) // the table beside a photographed page
            for (x in 0..99) for (y in 100 - artifact..99) set(x, y, black) // a shadow along the bottom
        }
        assertEquals(PixelArea(left = 20, top = 30, right = 81, bottom = 61), printedArea(pixels, 100, 100))
    }

    @Test
    fun keepsPrintRunningOffTheEdge() {
        val pixels = page(100, 100) { set ->
            for (x in 0..99) set(x, 40, black) // a staff line printed edge to edge
            for (x in 0..99) set(x, 44, black)
            set(50, 39, black)
        }
        assertEquals(PixelArea(left = 0, top = 39, right = 100, bottom = 45), printedArea(pixels, 100, 100))
    }
}
