package dev.axu.sheets.fingering

import dev.axu.sheets.fingering.Fingerings.JOIN_GAP
import dev.axu.sheets.fingering.Fingerings.RUN_GAP
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characters are 7 points tall. Gaps are built from [RUN_GAP] and [JOIN_GAP], so tuning them needs
 * no changes here.
 */
class FingeringsTest {
    private val one = digitShapes.getValue("plain 1").second
    private val two = digitShapes.getValue("curved 2").second
    private val three = digitShapes.getValue("round 3").second
    private val five = digitShapes.getValue("5 in one stroke").second
    private val height = 7f

    private val seven = Shape("0,0 85,0 35,100")

    private fun digits(written: List<Trace>, other: List<Trace> = emptyList()) =
        Fingerings.find(written, other).map { it.digit }

    private val List<Trace>.bounds get() = map { it.bounds }.reduce(Bounds::union)

    /** Writes this with its ink [gap] to the right of [previous]. */
    private fun Shape.writeAfter(previous: List<Trace>, gap: Float) =
        write(left = previous.bounds.right + gap - write().bounds.left)

    /** Writes this with its ink [gap] below [previous]. */
    private fun Shape.writeBelow(previous: List<Trace>, gap: Float) =
        write(top = previous.bounds.bottom + gap - write().bounds.top)

    @Test
    fun fingeringsWrittenAlongAPassageAreEachFound() {
        for (line in RealFingerings.lines) {
            val found = Fingerings.find(line.flatMap { it.second }, emptyList())
            assertEquals(line.map { it.first }, found.map { it.digit })
        }
    }

    @Test
    fun aDateStaysHandwritten() {
        val gap = 1f
        val day = one.write()
        val day2 = two.writeAfter(day, gap)
        val dot = listOf(Trace(floatArrayOf(day2.bounds.right + gap), floatArrayOf(day2.bounds.bottom)))
        val month = three.writeAfter(dot, gap)
        val dot2 = listOf(Trace(floatArrayOf(month.bounds.right + gap), floatArrayOf(month.bounds.bottom)))
        assertEquals(emptyList<Int>(), digits(day + day2 + dot + month + dot2))
    }

    @Test
    fun fingeringsCloseTogetherAreEachFound() {
        val first = one.write()
        assertEquals(listOf(1, 2), digits(first + two.writeAfter(first, height * 0.1f)))
    }

    @Test
    fun aDigitBesideOtherWritingStaysHandwritten() {
        val gap = RUN_GAP * height
        val digit = three.write()
        assertEquals(emptyList<Int>(), digits(digit + seven.writeAfter(digit, gap * 0.9f)))
        assertEquals(listOf(3), digits(digit + seven.writeAfter(digit, gap * 1.1f)))
    }

    @Test
    fun aDigitBesideEarlierWritingStaysHandwritten() {
        val earlier = seven.write()
        assertEquals(emptyList<Int>(), digits(three.writeAfter(earlier, 1f), other = earlier))
    }

    @Test
    fun aFingeringBesideEarlierFingeringsIsFound() {
        val earlier = one.write()
        assertEquals(listOf(3), digits(three.writeAfter(earlier, 1f), other = earlier))
    }

    @Test
    fun aFingeringUnderASlurIsFound() {
        val slur = Shape("0,100 20,30 50,0 80,30 100,100").write(left = -10f, top = -1f, height = 9f, aspect = 4f)
        assertEquals(listOf(3), digits(three.write(), other = slur))
    }

    @Test
    fun fingeringsStackedTightlyForAChordAreEachFound() {
        val top = five.write()
        val middle = three.writeBelow(top, JOIN_GAP * 0.5f)
        val bottom = one.writeBelow(middle, JOIN_GAP * 0.5f)
        assertEquals(listOf(5, 3, 1), digits(top + middle + bottom))
    }

    @Test
    fun aFiveWithItsBarNotQuiteTouchingIsOneFingering() {
        val body = Shape("15,8 8,48 55,40 92,55 98,80 75,97 35,100 2,88").write()
        val y = body.bounds.top - JOIN_GAP * 0.9f
        val bar = listOf(Trace(floatArrayOf(body.bounds.left + 0.5f, body.bounds.right), floatArrayOf(y, y)))
        assertEquals(listOf(5), digits(body + bar))
    }
}
