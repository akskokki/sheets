package dev.axu.sheets.fingering

/** Clean finger numbers, shaped like printed ones, as pen strokes in page points. */
object DigitGlyphs {
    /** Height of a clean digit in points, about that of printed fingering. */
    internal const val HEIGHT = 7f

    private const val CURVE_STEPS = 12
    private const val LINE_STEPS = 4

    /** The strokes of [digit] (1 to 5), centered on ([x], [y]). */
    fun write(digit: Int, x: Float, y: Float): List<Trace> {
        val strokes = glyphs.getValue(digit)
        val bounds = strokes.map { it.bounds }.reduce(Bounds::union)
        val left = x - bounds.width * HEIGHT / 2
        val top = y - bounds.height * HEIGHT / 2
        return strokes.map { stroke ->
            Trace(
                FloatArray(stroke.xs.size) { left + (stroke.xs[it] - bounds.left) * HEIGHT },
                FloatArray(stroke.ys.size) { top + (stroke.ys[it] - bounds.top) * HEIGHT },
            )
        }
    }

    /**
     * Each glyph is one stroke 1 tall, y downwards: M moves to a point, L draws a line to one and C a
     * cubic curve through two control points to one.
     */
    private val glyphs = mapOf(
        1 to "M .1,.22 L .36,0 L .36,1",
        2 to "M .06,.24 C .08,.08 .2,0 .3,0 C .44,0 .55,.1 .55,.25 C .55,.45 .35,.62 .04,1 L .58,1",
        3 to "M .06,.14 C .12,.04 .22,0 .31,0 C .46,0 .54,.1 .54,.23 C .54,.37 .43,.46 .26,.47 " +
            "C .45,.48 .58,.58 .58,.73 C .58,.9 .45,1 .29,1 C .17,1 .08,.94 .03,.84",
        4 to "M .44,1 L .44,0 L .02,.68 L .62,.68",
        5 to "M .54,0 L .14,0 L .09,.44 C .17,.38 .25,.36 .32,.36 C .48,.36 .58,.49 .58,.66 " +
            "C .58,.86 .46,1 .29,1 C .17,1 .08,.94 .03,.85",
    ).mapValues { (_, path) -> listOf(trace(path)) }

    private fun trace(path: String): Trace {
        val xs = ArrayList<Float>()
        val ys = ArrayList<Float>()
        val tokens = path.split(' ').iterator()
        fun point() = tokens.next().split(',').let { it[0].toFloat() to it[1].toFloat() }
        while (tokens.hasNext()) {
            when (tokens.next()) {
                "M" -> {
                    val (x, y) = point()
                    xs += x
                    ys += y
                }

                "L" -> {
                    val (x0, y0) = xs.last() to ys.last()
                    val (x, y) = point()
                    for (i in 1..LINE_STEPS) {
                        xs += x0 + (x - x0) * i / LINE_STEPS
                        ys += y0 + (y - y0) * i / LINE_STEPS
                    }
                }

                "C" -> {
                    val (x0, y0) = xs.last() to ys.last()
                    val (x1, y1) = point()
                    val (x2, y2) = point()
                    val (x3, y3) = point()
                    for (i in 1..CURVE_STEPS) {
                        val t = i.toFloat() / CURVE_STEPS
                        val u = 1 - t
                        xs += u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3
                        ys += u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3
                    }
                }
            }
        }
        return Trace(xs.toFloatArray(), ys.toFloatArray())
    }
}
