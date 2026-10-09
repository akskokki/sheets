package dev.axu.sheets.fingering

import dev.axu.sheets.fingering.FingeringRecognizer.MAX_ASPECT
import dev.axu.sheets.fingering.FingeringRecognizer.MAX_HEIGHT
import dev.axu.sheets.fingering.FingeringRecognizer.MAX_ONE_LEAN
import dev.axu.sheets.fingering.FingeringRecognizer.MIN_ASPECT
import dev.axu.sheets.fingering.FingeringRecognizer.MIN_HEIGHT
import dev.axu.sheets.fingering.FingeringRecognizer.ONE_BASE_TOLERANCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.tan

/**
 * Shapes are in PDF points. Size, lean, proportion and base limits are built from their constants, so
 * tuning them needs no changes here.
 */
class FingeringRecognizerTest {
    private val roundThree = digitShapes.getValue("round 3").second

    @Test
    fun commonWaysOfWritingEachDigitAreRead() {
        for ((name, digitAndShape) in digitShapes) {
            val (digit, shape) = digitAndShape
            for (slant in listOf(-10f, 0f, 15f)) {
                for (aspect in listOf(0.5f, 0.75f, 1f)) {
                    for (height in listOf(5f, 9f)) {
                        val strokes = shape.write(height = height, aspect = aspect, slant = slant)
                        assertEquals(
                            "$name, slant $slant, aspect $aspect",
                            digit,
                            FingeringRecognizer.recognize(strokes),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun aDigitIsNeverMistakenForAnother() {
        val random = Random(1)
        for ((name, digitAndShape) in digitShapes) {
            val (digit, shape) = digitAndShape
            repeat(200) {
                val strokes = shape.write(
                    height = 4f + random.nextFloat() * 8f,
                    aspect = 0.5f + random.nextFloat() * 0.5f,
                    slant = -10f + random.nextFloat() * 30f,
                    wobble = 0.05f,
                    random = random,
                )
                val read = FingeringRecognizer.recognize(strokes)
                if (read != null) assertEquals(name, digit, read)
            }
        }
    }

    @Test
    fun realHandwrittenFingeringsAreReadAndNeverMisread() {
        val read = RealFingerings.all.map { (digit, strokes) -> digit to FingeringRecognizer.recognize(strokes) }
        for ((digit, reading) in read) if (reading != null) assertEquals(digit, reading)
        val unread = read.count { it.second == null }
        assertTrue("$unread of ${read.size} unread", unread <= read.size / 20)
    }

    @Test
    fun musicalMarkingsAndOtherDigitsAreNotFingerings() {
        val marks = mapOf(
            "tenuto" to Shape("0,50 100,50").write(aspect = 1f),
            "accent" to Shape("0,0 100,50 0,100").write(height = 5f, aspect = 1.4f),
            "marcato" to Shape("0,100 50,0 100,100").write(aspect = 0.8f),
            "bow mark" to Shape("0,0 50,100 100,0").write(aspect = 0.8f),
            "short hairpin" to Shape("100,0 0,50 100,100").write(height = 4f, aspect = 2.5f),
            "slur" to Shape("0,100 20,30 50,0 80,30 100,100").write(height = 5f, aspect = 5f),
            "circled note" to Shape(circle).write(aspect = 1f),
            "plus" to Shape("0,50 100,50", "50,0 50,100").write(aspect = 1f),
            "cross" to Shape("0,0 100,100", "100,0 0,100").write(aspect = 1f),
            "0" to Shape(circle).write(),
            "6" to Shape("75,0 35,30 10,70 30,100 70,92 78,62 42,52 12,72").write(),
            "7" to Shape("0,0 85,0 35,100").write(),
            "7 with a rising top" to Shape("0,8 85,0 35,100").write(),
            "L" to Shape("0,0 0,100 100,100").write(),
            "first stroke of a 4" to Shape("15,0 8,60 100,60").write(),
            "8" to Shape("75,10 45,0 12,12 50,50 88,78 50,100 12,80 50,50 85,20 75,10").write(),
            "9" to Shape("80,25 45,0 10,22 38,48 80,30 78,22 72,100").write(),
        )
        for ((name, strokes) in marks) assertNull(name, FingeringRecognizer.recognize(strokes))
    }

    @Test
    fun onlyFingeringSizedWritingIsRead() {
        assertNull(FingeringRecognizer.recognize(roundThree.write(height = MIN_HEIGHT * 0.9f)))
        assertEquals(3, FingeringRecognizer.recognize(roundThree.write(height = MIN_HEIGHT * 1.1f)))
        assertEquals(3, FingeringRecognizer.recognize(roundThree.write(height = MAX_HEIGHT * 0.9f)))
        assertNull(FingeringRecognizer.recognize(roundThree.write(height = MAX_HEIGHT * 1.1f)))
    }

    @Test
    fun onlyDigitShapedWritingIsRead() {
        assertNull(FingeringRecognizer.recognize(roundThree.write(aspect = MIN_ASPECT * 0.9f)))
        assertEquals(3, FingeringRecognizer.recognize(roundThree.write(aspect = MIN_ASPECT * 1.1f)))
        assertEquals(3, FingeringRecognizer.recognize(roundThree.write(aspect = MAX_ASPECT * 0.95f)))
        assertNull(FingeringRecognizer.recognize(roundThree.write(aspect = MAX_ASPECT * 1.1f)))
    }

    @Test
    fun aOneIsAnUprightLine() {
        fun line(lean: Double) =
            listOf(Trace(floatArrayOf(7 * tan(Math.toRadians(lean)).toFloat(), 0f), floatArrayOf(0f, 7f)))
        assertEquals(1, FingeringRecognizer.recognize(line(MAX_ONE_LEAN - 5)))
        assertNull(FingeringRecognizer.recognize(line(MAX_ONE_LEAN + 5)))
    }

    @Test
    fun aOneMayHaveABaseAcrossItsFoot() {
        val stem = Trace(floatArrayOf(2f, 2f), floatArrayOf(0f, 7f))
        fun base(below: Float) = Trace(floatArrayOf(0f, 4f), floatArrayOf(7f + below, 7f + below))
        assertEquals(1, FingeringRecognizer.recognize(listOf(stem, base(7 * ONE_BASE_TOLERANCE * 0.9f))))
        assertEquals(1, FingeringRecognizer.recognize(listOf(base(0f), stem)))
        assertNull(FingeringRecognizer.recognize(listOf(stem, base(7 * ONE_BASE_TOLERANCE * 1.1f))))
    }

    private companion object {
        val circle = (0..20).joinToString(" ") { step ->
            val angle = Math.PI * 2 * step / 20
            "${50 + 50 * kotlin.math.cos(angle)},${50 + 50 * kotlin.math.sin(angle)}"
        }
    }
}
