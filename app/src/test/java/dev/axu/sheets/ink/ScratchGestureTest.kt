package dev.axu.sheets.ink

import dev.axu.sheets.ink.ScratchGesture.MIN_REVERSALS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shapes are in PDF points; a staff space is about 5-7 points on typical sheet music.
 *
 * Scratches are built from [MIN_REVERSALS], so tuning it needs no changes here.
 */
class ScratchGestureTest {
    private class Path {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()

        /** Adds a straight segment to (x, y), sampled densely like real input. */
        fun lineTo(x: Float, y: Float): Path {
            if (xs.isEmpty()) {
                xs += x
                ys += y
                return this
            }
            val x0 = xs.last()
            val y0 = ys.last()
            for (i in 1..10) {
                xs += x0 + (x - x0) * i / 10
                ys += y0 + (y - y0) * i / 10
            }
            return this
        }

        fun matches() = ScratchGesture.matches(xs.toFloatArray(), ys.toFloatArray())
    }

    /**
     * Strokes [swing] long back and forth with [turns] turns between them, each [step] further on,
     * sampled densely or (like fast input) only at the turns.
     */
    private fun zigzag(turns: Int, swing: Float, step: Float, dense: Boolean = true) = Path().apply {
        for (i in 0..turns + 1) {
            val x = if (i % 2 == 0) 0f else swing
            val y = i * step
            if (dense) {
                lineTo(x, y)
            } else {
                xs += x
                ys += y
            }
        }
    }

    private fun parametric(steps: Int, point: (Double) -> Pair<Double, Double>) = Path().apply {
        for (i in 0..steps) {
            val (x, y) = point(i.toDouble() / steps)
            xs += x.toFloat()
            ys += y.toFloat()
        }
    }

    @Test
    fun zigzagInPlace() {
        assertTrue(zigzag(MIN_REVERSALS, swing = 20f, step = 1f).matches())
    }

    @Test
    fun fastZigzagSampledOnlyAtTheTurns() {
        assertTrue(zigzag(MIN_REVERSALS, swing = 20f, step = 1f, dense = false).matches())
    }

    @Test
    fun zigzagAcrossLongNote() {
        // Scratching out a fingering written along the staff: swings of 8 pt, progressing 5 pt each.
        assertTrue(zigzag(MIN_REVERSALS, swing = 8f, step = 5f).matches())
    }

    @Test
    fun thinLoopScribble() {
        // Continuous thin ovals drifting sideways, the other common way to scratch something out.
        // Each loop turns twice across the drift.
        val loops = MIN_REVERSALS / 2 + 1
        val path = parametric(100 * loops) { t ->
            val a = t * loops * 2 * PI
            Pair(t * 3 * loops + 3 * cos(a), 12 * sin(a))
        }
        assertTrue(path.matches())
    }

    @Test
    fun oneTurnTooFewIsNotEnough() {
        assertFalse(zigzag(MIN_REVERSALS - 1, swing = 20f, step = 1f).matches())
    }

    @Test
    fun tinyJitterIsIgnored() {
        assertFalse(zigzag(MIN_REVERSALS + 2, swing = 1f, step = 0.2f).matches())
    }

    @Test
    fun straightLine() {
        assertFalse(Path().lineTo(0f, 0f).lineTo(100f, 10f).matches())
    }

    @Test
    fun hairpin() {
        assertFalse(Path().lineTo(60f, 0f).lineTo(0f, 6f).lineTo(60f, 12f).matches())
    }

    @Test
    fun trill() {
        // tr~~~~: small waves along a long line.
        val path = parametric(400) { t -> Pair(t * 80, 2 * sin(t * 10 * 2 * PI)) }
        assertFalse(path.matches())
    }

    @Test
    fun sharpTrill() {
        val path = Path().lineTo(0f, 0f)
        // Plenty of turns, but they don't double back.
        for (i in 1..3 * MIN_REVERSALS) path.lineTo(i * 4f, if (i % 2 == 1) 3f else 0f)
        assertFalse(path.matches())
    }

    @Test
    fun noteCircledTwice() {
        val path = parametric(200) { t -> Pair(10 * cos(t * 4 * PI), 10 * sin(t * 4 * PI)) }
        assertFalse(path.matches())
    }

    @Test
    fun passageCircledTwiceWithOval() {
        val path = parametric(200) { t -> Pair(24 * cos(t * 4 * PI), 12 * sin(t * 4 * PI)) }
        assertFalse(path.matches())
    }

    @Test
    fun letterW() {
        val path = Path().lineTo(0f, 0f).lineTo(3f, 10f).lineTo(6f, 2f).lineTo(9f, 10f).lineTo(12f, 0f)
        assertFalse(path.matches())
    }

    @Test
    fun letterM() {
        val path = Path().lineTo(0f, 10f).lineTo(0f, 0f).lineTo(5f, 7f).lineTo(10f, 0f).lineTo(10f, 10f)
        assertFalse(path.matches())
    }
}
