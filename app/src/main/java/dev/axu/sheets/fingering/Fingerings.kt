package dev.axu.sheets.fingering

import kotlin.math.max
import kotlin.math.min

/** A finger number found among strokes: the indices of its strokes, its digit, and where it is. */
class Fingering(val strokes: List<Int>, val digit: Int, val bounds: Bounds)

/**
 * Finds the finger numbers among strokes written in one go, leaving other writing alone.
 *
 * A digit has at most two strokes, written one after the other, like a 5 and its bar. A fingering
 * stands on its own; digits with other characters beside them, like a date or a bar number, are
 * text. Handwriting keeps characters well under a character's height apart, while fingerings over
 * neighboring notes are further apart than that. Characters above or below each other don't count,
 * so fingerings stacked for a chord are each found.
 */
object Fingerings {
    /** Consecutive strokes this close, in points, may be parts of one digit, like a 5 and its bar. */
    internal const val JOIN_GAP = 2f

    /** Characters side by side closer than this, in character heights, are text. */
    internal const val RUN_GAP = 1f

    /**
     * The fingerings among [written], strokes written in one go in the order they were written,
     * given the bounds of [other] ink already on the page.
     */
    fun find(written: List<Trace>, other: List<Bounds>): List<Fingering> {
        val characters = characters(written)
        // Anything larger, like a slur over the notes, isn't a character next to which a fingering
        // would be text.
        val neighbors = (characters.map { it.bounds } + other).filter {
            it.width <= FingeringRecognizer.MAX_HEIGHT && it.height <= FingeringRecognizer.MAX_HEIGHT
        }
        return characters.mapNotNull { (strokes, bounds, digit) ->
            if (digit == null || neighbors.any { it !== bounds && areSideBySide(bounds, it) }) return@mapNotNull null
            Fingering(strokes, digit, bounds)
        }
    }

    private data class Character(val strokes: List<Int>, val bounds: Bounds, val digit: Int?)

    /**
     * Splits [written] into characters, joining consecutive strokes that are close together when
     * they make a digit together.
     */
    private fun characters(written: List<Trace>): List<Character> {
        val characters = ArrayList<Character>()
        var i = 0
        while (i < written.size) {
            val pair = if (i + 1 < written.size && written[i].bounds.isNear(written[i + 1].bounds, JOIN_GAP)) {
                FingeringRecognizer.recognize(listOf(written[i], written[i + 1]))
            } else {
                null
            }
            val strokes = if (pair != null) listOf(i, i + 1) else listOf(i)
            val digit = pair ?: FingeringRecognizer.recognize(listOf(written[i]))
            characters += Character(strokes, strokes.map { written[it].bounds }.reduce(Bounds::union), digit)
            i += strokes.size
        }
        return characters
    }

    /** Whether [a] and [b] are on the same line, closer together than [RUN_GAP] (overlapping counts). */
    private fun areSideBySide(a: Bounds, b: Bounds): Boolean {
        val overlap = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (overlap < min(a.height, b.height) / 2) return false
        val gap = max(b.left - a.right, a.left - b.right)
        return gap < RUN_GAP * max(a.height, b.height)
    }
}
