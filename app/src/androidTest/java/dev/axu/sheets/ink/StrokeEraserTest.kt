package dev.axu.sheets.ink

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.axu.sheets.testStroke
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StrokeEraserTest {
    // Horizontal, from x = 10 to 30 at y = 10.
    private val stroke = testStroke(10f, 10f)
    private val strokes = listOf(stroke)

    @Test
    fun theEraserErasesStrokesItPassesOver() {
        assertEquals(strokes, StrokeEraser.touched(20f, 0f, 20f, 20f, strokes))
    }

    @Test
    fun theEraserErasesWhereItFirstTouchesDown() {
        assertEquals(strokes, StrokeEraser.touched(20f, 10f, 20f, 10f, strokes))
    }

    @Test
    fun theEraserNeedNotHitAStrokeExactly() {
        val nearby = 10f + StrokeEraser.RADIUS * 0.9f
        assertEquals(strokes, StrokeEraser.touched(12f, nearby, 28f, nearby, strokes))
        assertEquals(strokes, StrokeEraser.touched(28f, nearby, 12f, nearby, strokes))
        assertEquals(strokes, StrokeEraser.touched(20f, nearby, 20f, nearby, strokes))
    }

    @Test
    fun theEraserLeavesStrokesItPassesBeside() {
        val beside = 10f + StrokeEraser.RADIUS + Pens.Default.size
        assertEquals(emptyList<Any>(), StrokeEraser.touched(12f, beside, 28f, beside, strokes))
        assertEquals(emptyList<Any>(), StrokeEraser.touched(28f, beside, 12f, beside, strokes))
        assertEquals(emptyList<Any>(), StrokeEraser.touched(12f, beside, 28f, beside + 10f, strokes))
        assertEquals(emptyList<Any>(), StrokeEraser.touched(20f, beside, 20f, beside, strokes))
    }
}
