package dev.axu.sheets.fingering

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Reads a handwritten finger number, 1 to 5, from the strokes of one character; null unless it's
 * clearly one of them. Coordinates are in PDF points (1/72 inch).
 *
 * A 1 is recognized by its shape alone: a straight, upright stem, maybe led into by a flag, maybe
 * with a base. Other digits are compared with templates of the common ways of writing them, point by
 * point in writing order, so the direction strokes are written in counts. Misreading one digit as
 * another is worse than leaving it handwritten, so readings close to two digits are rejected.
 */
object FingeringRecognizer {
    /** Height range of a handwritten fingering; a staff space is about 5-7 points. */
    internal const val MIN_HEIGHT = 3f
    internal const val MAX_HEIGHT = 14f

    /** The stem of a 1 leans at most this many degrees from upright... */
    internal const val MAX_ONE_LEAN = 30.0

    /** ...its ends are at least this fraction of its length apart... */
    internal const val MIN_ONE_STRAIGHTNESS = 0.9f

    /** ...a flag leading up into its top is at most this long relative to it... */
    internal const val MAX_ONE_FLAG = 0.7f

    /** ...and climbs at least this many degrees, unlike the flat top of a 7. */
    internal const val MIN_ONE_FLAG_CLIMB = 20.0

    /** A base is a stroke no taller, and no further from the foot of the stem, than this part of it. */
    internal const val ONE_BASE_TOLERANCE = 0.2f

    /** Width range relative to height of the other digits. */
    internal const val MIN_ASPECT = 0.35f
    internal const val MAX_ASPECT = 1.25f

    /**
     * Furthest a character can be from a template to be read as its digit: the mean distance between
     * matched points, as a fraction of the character's size.
     */
    internal const val MAX_DISTANCE = 0.18f

    /** How many times further the next best digit has to be, or the reading is ambiguous. */
    internal const val MIN_MARGIN = 1.3f

    private const val POINTS = 32

    fun recognize(strokes: List<Trace>): Int? {
        if (strokes.isEmpty()) return null
        val bounds = strokes.map { it.bounds }.reduce(Bounds::union)
        if (bounds.height !in MIN_HEIGHT..MAX_HEIGHT) return null
        if (strokes.size == 1 && isOne(strokes[0])) return 1
        if (strokes.size == 2 &&
            (isOneWithBase(strokes[0], strokes[1]) || isOneWithBase(strokes[1], strokes[0]))
        ) {
            return 1
        }
        if (bounds.width / bounds.height !in MIN_ASPECT..MAX_ASPECT) return null

        val ranked = distances(strokes)?.entries?.sortedBy { it.value } ?: return null
        val best = ranked.firstOrNull() ?: return null
        val nextBest = ranked.getOrNull(1)?.value ?: Float.POSITIVE_INFINITY
        return best.key.takeIf { best.value <= MAX_DISTANCE && nextBest >= best.value * MIN_MARGIN }
    }

    /** How far [strokes] are from each digit's closest template; null if there's no line to follow. */
    internal fun distances(strokes: List<Trace>): Map<Int, Float>? {
        val distances = HashMap<Int, Float>()
        // People write a 4 or a 5 in either stroke order.
        for (order in if (strokes.size == 2) listOf(strokes, strokes.reversed()) else listOf(strokes)) {
            val points = normalized(order) ?: return null
            for (template in templates) {
                if (template.strokes != strokes.size) continue
                val distance = warpedDistance(points, template.points)
                distances.merge(template.digit, distance, ::min)
            }
        }
        return distances
    }

    /**
     * Whether [stroke] is a straight, upright line from its top down, possibly with a flag before
     * it. Drawn from the bottom up, it's read backwards.
     */
    private fun isOne(stroke: Trace): Boolean {
        val xs = stroke.xs
        val ys = stroke.ys
        val top = ys.indices.minBy { ys[it] }
        val (stem, flag) = when (top) {
            ys.lastIndex -> (top downTo 0) to IntRange.EMPTY
            0 -> (0..ys.lastIndex) to IntRange.EMPTY
            else -> (top..ys.lastIndex) to (0..top)
        }
        val stemLength = pathLength(xs, ys, stem)
        val dx = xs[stem.last] - xs[stem.first]
        val dy = ys[stem.last] - ys[stem.first]
        val lean = degrees(abs(dx), abs(dy))
        val climb = if (flag.isEmpty()) 90.0 else degrees(ys[flag.first] - ys[top], abs(xs[top] - xs[flag.first]))
        return stemLength > 0f &&
            lean <= MAX_ONE_LEAN &&
            hypot(dx, dy) >= stemLength * MIN_ONE_STRAIGHTNESS &&
            pathLength(xs, ys, flag) <= stemLength * MAX_ONE_FLAG &&
            climb >= MIN_ONE_FLAG_CLIMB
    }

    /** Whether [stem] is a 1 and [base] a flat stroke across its foot. */
    private fun isOneWithBase(stem: Trace, base: Trace): Boolean {
        if (!isOne(stem)) return false
        val tolerance = stem.bounds.height * ONE_BASE_TOLERANCE
        val foot = stem.ys.indices.maxBy { stem.ys[it] }
        val (left, top, right, bottom) = base.bounds
        return bottom - top <= tolerance &&
            abs((top + bottom) / 2 - stem.ys[foot]) <= tolerance &&
            stem.xs[foot] in left..right
    }

    private fun degrees(y: Float, x: Float) = Math.toDegrees(atan2(y, x).toDouble())

    private fun pathLength(xs: FloatArray, ys: FloatArray, indices: IntProgression): Float {
        var length = 0f
        var previous = -1
        for (i in indices) {
            if (previous >= 0) length += hypot(xs[i] - xs[previous], ys[i] - ys[previous])
            previous = i
        }
        return length
    }

    private class Template(val digit: Int, val strokes: Int, val points: Trace)

    /** Each stroke is points "x,y" in percent of a square, y downwards, in the usual writing direction. */
    private fun template(digit: Int, vararg strokes: String): Template {
        val traces = strokes.map { stroke ->
            val points = stroke.split(' ').map { point -> point.split(',').map { it.toFloat() / 100 } }
            Trace(FloatArray(points.size) { points[it][0] }, FloatArray(points.size) { points[it][1] })
        }
        return Template(digit, strokes.size, normalized(traces)!!)
    }

    private val templates = listOf(
        // Curved and angular 2s, and one with a short top over a long base.
        template(2, "5,25 25,3 55,0 85,12 90,32 75,52 35,80 0,100 100,100"),
        template(2, "0,15 40,0 90,10 90,30 0,100 100,100"),
        template(2, "0,0 35,2 42,20 30,45 5,80 5,90 40,92 100,100"),
        // Round and flat-topped 3s.
        template(3, "10,15 40,0 80,8 85,28 45,48 90,62 100,82 75,98 35,100 0,88"),
        template(3, "0,0 90,0 40,40 85,55 100,78 80,95 40,100 0,92"),
        // Open and closed 4s in two strokes, and in one: an open one goes back down its stem.
        template(4, "10,0 5,62 100,62", "70,0 70,100"),
        template(4, "65,0 0,65 100,65", "68,0 68,100"),
        template(4, "8,0 0,55 8,62 55,65 72,50 78,0 74,50 72,100"),
        template(4, "70,100 70,0 0,65 100,65"),
        // A 5 with its bar added afterwards, also a bar wider than the body, and one in a single stroke.
        template(5, "12,5 5,45 50,42 90,55 100,78 80,95 40,100 0,90", "15,0 100,0"),
        template(5, "10,5 5,45 40,40 62,55 65,78 50,95 25,100 0,90", "12,0 100,5"),
        template(5, "100,0 15,0 8,45 50,40 90,55 100,78 80,95 40,100 0,90"),
    )

    /**
     * [POINTS] points evenly spaced along the strokes in order, stretched to fill a unit square so
     * that narrow and wide handwriting compare alike; null if there's no line to follow.
     */
    private fun normalized(strokes: List<Trace>): Trace? {
        val total = strokes.fold(0f) { sum, stroke -> sum + stroke.length }
        if (total == 0f) return null
        val xs = FloatArray(POINTS)
        val ys = FloatArray(POINTS)
        var placed = 0
        var travelled = 0f // Along the strokes, up to the current segment; jumps between strokes don't count.
        for (stroke in strokes) {
            for (i in 1 until stroke.xs.size) {
                val x0 = stroke.xs[i - 1]
                val y0 = stroke.ys[i - 1]
                val segment = hypot(stroke.xs[i] - x0, stroke.ys[i] - y0)
                while (placed < POINTS) {
                    val at = total * placed / (POINTS - 1) - travelled
                    if (at > segment) break
                    val t = if (segment > 0f) at / segment else 0f
                    xs[placed] = x0 + (stroke.xs[i] - x0) * t
                    ys[placed] = y0 + (stroke.ys[i] - y0) * t
                    placed++
                }
                travelled += segment
            }
        }
        // Rounding can leave the very end unplaced.
        while (placed < POINTS) {
            xs[placed] = strokes.last().xs.last()
            ys[placed] = strokes.last().ys.last()
            placed++
        }
        val bounds = Trace(xs, ys).bounds
        val width = max(bounds.width, Float.MIN_VALUE)
        val height = max(bounds.height, Float.MIN_VALUE)
        for (i in 0 until POINTS) {
            xs[i] = (xs[i] - bounds.left) / width
            ys[i] = (ys[i] - bounds.top) / height
        }
        return Trace(xs, ys)
    }

    /**
     * Mean distance between the points of [a] and [b] when matched up in order, letting either
     * linger, so handwriting with parts longer or shorter than the template's still lines up
     * (dynamic time warping).
     */
    private fun warpedDistance(a: Trace, b: Trace): Float {
        val n = a.xs.size
        val m = b.xs.size
        // Total distance and number of matched pairs of the best matching of the first i and j points.
        val cost = Array(n + 1) { FloatArray(m + 1) { Float.POSITIVE_INFINITY } }
        val pairs = Array(n + 1) { IntArray(m + 1) }
        cost[0][0] = 0f
        for (i in 1..n) {
            for (j in 1..m) {
                var bi = i - 1
                var bj = j - 1
                if (cost[i - 1][j] < cost[bi][bj]) bj = j
                if (cost[i][j - 1] < cost[bi][bj]) {
                    bi = i
                    bj = j - 1
                }
                cost[i][j] = cost[bi][bj] + hypot(a.xs[i - 1] - b.xs[j - 1], a.ys[i - 1] - b.ys[j - 1])
                pairs[i][j] = pairs[bi][bj] + 1
            }
        }
        return cost[n][m] / pairs[n][m]
    }
}
