package dev.axu.sheets.fingering

import java.util.Random
import kotlin.math.tan

/**
 * A shape to write, as strokes of points "x,y" in percent of a square, y downwards, in writing
 * order. Drawn independently of the recognizer's templates.
 */
internal class Shape(vararg strokes: String) {
    private val strokes = strokes.map { stroke ->
        stroke.split(' ').map { point -> point.split(',').let { it[0].toFloat() / 100 to it[1].toFloat() / 100 } }
    }

    /**
     * Writes this [height] points tall and [aspect] times as wide, with its top left at ([left],
     * [top]), leaning right by [slant] degrees. A sloppy hand is imitated by moving each of the
     * shape's points by about [wobble] of its size, at random from [random]. Points are sampled
     * densely along it, like pen input.
     */
    fun write(
        left: Float = 0f,
        top: Float = 0f,
        height: Float = 7f,
        aspect: Float = 0.7f,
        slant: Float = 0f,
        wobble: Float = 0f,
        random: Random? = null,
    ): List<Trace> {
        val lean = tan(Math.toRadians(slant.toDouble())).toFloat()
        fun shake() = if (random == null) 0f else random.nextGaussian().toFloat() * wobble
        return strokes.map { shape ->
            val points = shape.map { (x, y) -> x + shake() to y + shake() }
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            fun add(x: Float, y: Float) {
                xs += left + (x * aspect + (1 - y) * lean) * height
                ys += top + y * height
            }
            add(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                val (x0, y0) = points[i - 1]
                val (x1, y1) = points[i]
                for (step in 1..10) add(x0 + (x1 - x0) * step / 10, y0 + (y1 - y0) * step / 10)
            }
            Trace(xs.toFloatArray(), ys.toFloatArray())
        }
    }
}

/** Common ways of writing each finger number. */
internal val digitShapes: Map<String, Pair<Int, Shape>> = mapOf(
    "plain 1" to (1 to Shape("50,0 48,100")),
    "1 with a flag" to (1 to Shape("15,25 65,0 62,100")),
    "1 with a flag and base" to (1 to Shape("20,30 55,0 55,100", "15,100 95,100")),
    "curved 2" to (2 to Shape("10,20 35,2 65,0 88,15 85,40 60,62 25,85 2,100 98,98")),
    "angular 2" to (2 to Shape("5,10 50,0 95,15 85,35 5,100 100,100")),
    "round 3" to (3 to Shape("8,12 45,0 82,10 80,30 45,47 88,60 98,80 72,97 30,100 2,85")),
    "flat-topped 3" to (3 to Shape("5,0 95,2 45,38 90,55 98,78 78,96 35,100 3,90")),
    "open 4" to (4 to Shape("15,0 8,60 100,60", "72,5 70,100")),
    "closed 4" to (4 to Shape("60,0 2,62 100,62", "64,0 64,100")),
    "closed 4 in one stroke" to (4 to Shape("68,100 68,0 0,62 100,62")),
    "5 with its bar after" to (5 to Shape("15,8 8,48 55,40 92,55 98,80 75,97 35,100 2,88", "18,0 98,2")),
    "5 with its bar first" to (5 to Shape("18,0 98,2", "15,8 8,48 55,40 92,55 98,80 75,97 35,100 2,88")),
    "5 in one stroke" to (5 to Shape("95,0 18,2 10,48 55,40 92,55 98,80 75,97 35,100 2,88")),
)
