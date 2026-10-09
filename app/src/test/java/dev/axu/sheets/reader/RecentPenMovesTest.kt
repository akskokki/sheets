package dev.axu.sheets.reader

import dev.axu.sheets.ink.Pen
import dev.axu.sheets.ink.PenColor
import dev.axu.sheets.ink.PenWidth
import dev.axu.sheets.ink.RecentPens
import dev.axu.sheets.reader.RecentPenMoves.Arriving
import dev.axu.sheets.reader.RecentPenMoves.Leaving
import dev.axu.sheets.reader.RecentPenMoves.Move
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPenMovesTest {
    private val pens = (0..RecentPens.COUNT + 1).map { Pen(PenColor("Color $it", it), PenWidth.Medium) }
    private val current = pens[0]
    private val recents = pens.subList(1, RecentPens.COUNT + 1)

    @Test
    fun aRecentPenChosenGoesIntoThePenAndTheOnesItPassedMoveAlong() {
        for (chosen in recents) {
            val after = RecentPens.afterSwitch(current, chosen, recents)
            val moves = RecentPenMoves.between(recents, after, current = chosen)
            val chosenAt = recents.indexOf(chosen)

            assertEquals(Leaving.ToPen, moves[chosenAt]?.leaves)
            assertEquals(current, moves[0]?.new)
            assertEquals(Arriving.FadesIn, moves[0]?.arrives)
            // The ones before the chosen pen each slide one place on; the ones after it stay put.
            for (i in 0 until chosenAt) {
                assertEquals(Leaving.Onward, moves[i]?.leaves)
                assertEquals(Arriving.SlidesIn, moves[i + 1]?.arrives)
            }
            assertEquals(List(recents.size - chosenAt - 1) { null }, moves.drop(chosenAt + 1))
        }
    }

    @Test
    fun aNewPenPushesTheLastRecentOneAway() {
        val new = pens.last()
        val after = RecentPens.afterSwitch(current, new, recents)
        val moves = RecentPenMoves.between(recents, after, current = new)

        assertEquals(Move(recents.first(), Leaving.Onward, current, Arriving.FadesIn), moves.first())
        assertEquals(Move(recents.last(), Leaving.Away, recents[recents.size - 2], Arriving.SlidesIn), moves.last())
    }

    @Test
    fun aPlaceEmptyUntilNowTakesThePenThatMovesOnIntoIt() {
        val fewer = recents.dropLast(1)
        val new = pens.last()
        val after = RecentPens.afterSwitch(current, new, fewer)
        val moves = RecentPenMoves.between(fewer, after, current = new)

        assertEquals(Move(null, null, fewer.last(), Arriving.SlidesIn), moves.last())
    }

    @Test
    fun nothingMovesUnlessThePensChange() {
        assertEquals(recents.map { null }, RecentPenMoves.between(recents, recents, current))
    }
}
