package dev.axu.sheets.ink

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
}
