package dev.axu.sheets.ink

import dev.axu.sheets.ink.RecentPens.Arriving
import dev.axu.sheets.ink.RecentPens.Change
import dev.axu.sheets.ink.RecentPens.Leaving
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPensTest {
    private val pens = (0..RecentPens.COUNT + 1).map { Pen(PenColor("Color $it", it), PenWidth.Medium) }
    private val current = pens[0]
    private val recents = pens.subList(1, RecentPens.COUNT + 1)

    @Test
    fun switchingToARecentPenKeepsThePenYouLeftWithinReach() {
        val switchedTo = recents.last()
        val after = RecentPens.afterSwitch(current, switchedTo, recents)
        assertEquals(listOf(current) + recents.dropLast(1), after)

        // So switching back and forth is one tap each way.
        assertEquals(listOf(switchedTo) + recents.dropLast(1), RecentPens.afterSwitch(switchedTo, current, after))
    }

    @Test
    fun aNewPenPushesOutTheLeastRecentOne() {
        val new = pens.last()
        assertEquals(listOf(current) + recents.dropLast(1), RecentPens.afterSwitch(current, new, recents))
    }

    @Test
    fun theSamePenIsNeverListedTwice() {
        val sameAsCurrent = current.copy()
        assertEquals(recents, RecentPens.afterSwitch(sameAsCurrent, current, recents))
        assertEquals(listOf(current), RecentPens.afterSwitch(current, pens.last(), listOf(current)))
    }

    @Test
    fun aRecentPenChosenGoesIntoThePenAndTheOnesItPassedMoveAlong() {
        for (chosen in recents) {
            val after = RecentPens.afterSwitch(current, chosen, recents)
            val changes = RecentPens.changes(recents, after, current = chosen)
            val chosenAt = recents.indexOf(chosen)

            assertEquals(Leaving.ToPen, changes[chosenAt]?.left)
            assertEquals(current, changes[0]?.arriving)
            assertEquals(Arriving.FadesIn, changes[0]?.arrived)
            // The ones before the chosen pen each slide one place on; the ones after it stay put.
            for (i in 0 until chosenAt) {
                assertEquals(Leaving.Onward, changes[i]?.left)
                assertEquals(Arriving.SlidesIn, changes[i + 1]?.arrived)
            }
            assertEquals(List(recents.size - chosenAt - 1) { null }, changes.drop(chosenAt + 1))
        }
    }

    @Test
    fun aNewPenPushesTheLastRecentOneAway() {
        val new = pens.last()
        val after = RecentPens.afterSwitch(current, new, recents)
        val changes = RecentPens.changes(recents, after, current = new)

        assertEquals(Change(recents.first(), Leaving.Onward, current, Arriving.FadesIn), changes.first())
        assertEquals(Change(recents.last(), Leaving.Away, recents[recents.size - 2], Arriving.SlidesIn), changes.last())
    }

    @Test
    fun nothingMovesUnlessThePensChange() {
        assertEquals(recents.map { null }, RecentPens.changes(recents, recents, current))
    }
}
