package dev.axu.sheets.fingering

import dev.axu.sheets.fingering.DigitGlyphs.HEIGHT
import org.junit.Assert.assertEquals
import org.junit.Test

class DigitGlyphsTest {
    @Test
    fun cleanDigitsAreTheSameSizeAndCenteredWhereAsked() {
        for (digit in 1..5) {
            val bounds = DigitGlyphs.write(digit, 100f, 50f).map { it.bounds }.reduce(Bounds::union)
            assertEquals(HEIGHT, bounds.height, 0.01f)
            assertEquals(100f, (bounds.left + bounds.right) / 2, 0.01f)
            assertEquals(50f, (bounds.top + bounds.bottom) / 2, 0.01f)
        }
    }

    /** So a fingering written beside a cleaned-up one is still found. */
    @Test
    fun cleanDigitsReadAsThemselves() {
        for (digit in 1..5) assertEquals(digit, FingeringRecognizer.recognize(DigitGlyphs.write(digit, 0f, 0f)))
    }
}
