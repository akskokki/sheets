package dev.axu.sheets.fingering

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Turns handwritten finger numbers into clean ones. Strokes are handed over as they're drawn; once
 * the pen has been up for a moment, the ones still on their page are checked for fingerings (see
 * [Fingerings]), and each found is swapped for a clean digit through [replace], in the same color.
 *
 * Sizes are judged relative to the page, as if it were A4 paper, so a score scanned at a larger size
 * works the same.
 *
 * Runs on the main thread, like the ink it reads and changes.
 */
class FingeringCleanup(
    private val scope: CoroutineScope,
    /** The strokes on a page, in the order they were drawn. */
    private val strokesOn: (page: Int) -> List<Stroke>,
    /** In points. */
    private val pageWidth: (page: Int) -> Float,
    private val replace: (page: Int, handwriting: List<Stroke>, clean: List<Stroke>) -> Unit,
) {
    private val written = mutableListOf<Pair<Int, Stroke>>()
    private var pause: Job? = null

    /** Call when the pen comes down to write. */
    fun onPenDown() {
        pause?.cancel()
    }

    /** Call when the pen lifts, whether or not its stroke is kept. */
    fun onPenUp() {
        pause?.cancel()
        pause = scope.launch {
            delay(PAUSE_MILLIS)
            cleanUp()
        }
    }

    /** Call when [stroke] has been added to [page]. */
    fun onStrokeAdded(page: Int, stroke: Stroke) {
        written += page to stroke
    }

    private fun cleanUp() {
        val byPage = written.groupBy({ it.first }, { it.second })
        written.clear()
        for ((page, strokes) in byPage) {
            val onPage = strokesOn(page)
            val present = Collections.newSetFromMap(IdentityHashMap<Stroke, Boolean>()).apply { addAll(onPage) }
            // Some may have been undone or erased since.
            val new = strokes.filter { it in present }
            if (new.isEmpty()) continue
            val newSet = Collections.newSetFromMap(IdentityHashMap<Stroke, Boolean>()).apply { addAll(new) }
            val other = onPage.filter { it !in newSet }
            val scale = pageWidth(page) / A4_WIDTH
            for (fingering in Fingerings.find(new.map { it.toTrace(scale) }, other.map { it.toTrace(scale) })) {
                val handwriting = fingering.strokes.map { new[it] }
                val center = fingering.bounds
                val glyph = DigitGlyphs.write(
                    fingering.digit,
                    (center.left + center.right) / 2,
                    (center.top + center.bottom) / 2,
                )
                replace(page, handwriting, glyph.map { it.toStroke(scale, handwriting.first().brush) })
            }
        }
    }

    /** In points on an A4 page, the page's being [scale] times as large. */
    private fun Stroke.toTrace(scale: Float): Trace {
        val input = StrokeInput()
        val xs = FloatArray(inputs.size)
        val ys = FloatArray(inputs.size)
        for (i in 0 until inputs.size) {
            inputs.populate(i, input)
            xs[i] = input.x / scale
            ys[i] = input.y / scale
        }
        return Trace(xs, ys)
    }

    /** As if written evenly with [handwriting]'s pen at a fixed width, on a page [scale] times A4. */
    private fun Trace.toStroke(scale: Float, handwriting: Brush): Stroke {
        val inputs = MutableStrokeInputBatch()
        for (i in xs.indices) {
            inputs.add(
                InputToolType.STYLUS,
                xs[i] * scale,
                ys[i] * scale,
                elapsedTimeMillis = i * 8L,
                pressure = PRESSURE,
            )
        }
        val brush = Brush.createWithColorIntArgb(
            handwriting.family,
            handwriting.colorIntArgb,
            WIDTH * scale,
            handwriting.epsilon,
        )
        return Stroke(brush, inputs)
    }

    internal companion object {
        /** Width of an A4 page in points. */
        const val A4_WIDTH = 595f

        /** How long the pen has to be up before what was written is cleaned up. */
        const val PAUSE_MILLIS = 600L

        /** Brush size of clean digits in points, so they all have the same weight. */
        const val WIDTH = 0.8f

        /** Even pressure for clean digits. */
        const val PRESSURE = 0.5f
    }
}
