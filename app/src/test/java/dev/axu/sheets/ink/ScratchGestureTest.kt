package dev.axu.sheets.ink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Shapes are in PDF points; a staff space is about 5-7 points on typical sheet music. */
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

    private fun parametric(steps: Int, point: (Double) -> Pair<Double, Double>) = Path().apply {
        for (i in 0..steps) {
            val (x, y) = point(i.toDouble() / steps)
            xs += x.toFloat()
            ys += y.toFloat()
        }
    }

    @Test
    fun zigzagInPlace() {
        val path = Path().lineTo(0f, 0f)
        repeat(3) { path.lineTo(20f, 1f).lineTo(0f, 2f) }
        path.lineTo(20f, 3f)
        assertTrue(path.matches())
    }

    @Test
    fun fastZigzagSampledOnlyAtTheTurns() {
        val xs = floatArrayOf(0f, 20f, 0f, 20f, 0f, 20f, 0f, 20f)
        val ys = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        assertTrue(ScratchGesture.matches(xs, ys))
    }

    @Test
    fun zigzagAcrossLongNote() {
        // Scratching out a fingering written along the staff: swings of 8 pt, progressing 40 pt.
        val path = Path().lineTo(0f, 0f)
        for (i in 1..8) path.lineTo(i * 5f, if (i % 2 == 1) 8f else 0f)
        assertTrue(path.matches())
    }

    @Test
    fun thinLoopScribble() {
        // Continuous thin ovals drifting sideways, the other common way to scratch something out.
        val path = parametric(400) { t ->
            val a = t * 4 * 2 * PI
            Pair(t * 12 + 3 * cos(a), 12 * sin(a))
        }
        assertTrue(path.matches())
    }

    @Test
    fun fiveTurnsAreNotEnough() {
        val path = Path().lineTo(0f, 0f)
        repeat(3) { path.lineTo(20f, 1f).lineTo(0f, 2f) }
        assertFalse(path.matches())
    }

    @Test
    fun tinyJitterIsIgnored() {
        val path = Path().lineTo(0f, 0f)
        repeat(4) { path.lineTo(1f, 0.2f).lineTo(0f, 0.4f) }
        assertFalse(path.matches())
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
        for (i in 1..16) path.lineTo(i * 4f, if (i % 2 == 1) 3f else 0f)
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
