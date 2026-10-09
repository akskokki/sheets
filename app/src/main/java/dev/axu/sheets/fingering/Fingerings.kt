package dev.axu.sheets.fingering

import dev.axu.sheets.fingering.FingeringRecognizer.MAX_HEIGHT
import kotlin.math.max
import kotlin.math.min

/** A finger number found among strokes: the indices of its strokes, its digit, and where it is. */
class Fingering(val strokes: List<Int>, val digit: Int, val bounds: Bounds)

/**
 * Finds the finger numbers among strokes written in one go, leaving other writing alone.
 *
 * A digit has at most two strokes, written one after the other, like a 5 and its bar; crossing
 * strokes that don't make a digit together are something else, not two digits. Characters
 * side by side are read together: fingerings over quick notes can be as close as letters in a word,
 * so they're told apart from text such as a date by what's there instead. A digit is a fingering
 * only if everything beside it is one too; a date has a dot, a slash or another digit. Numbers made
 * of 1 to 5 alone, like bar 12, are cleaned up like fingerings, which leaves them saying the same.
 * Characters above or below each other aren't side by side, so fingerings stacked for a chord are
 * each found.
 */
object Fingerings {
    /** Consecutive strokes this close, in points, may be parts of one digit, like a 5 and its bar. */
    internal const val JOIN_GAP = 2f

    /** Characters side by side closer than this, in character heights, are read together. */
    internal const val RUN_GAP = 1f

    /**
     * The fingerings among [written], strokes written in one go in the order they were written,
     * given the [other] strokes already on the page, in the order they were drawn.
     */
    fun find(written: List<Trace>, other: List<Trace>): List<Fingering> {
        if (written.isEmpty()) return emptyList()
        // Only ink close enough to be beside something just written matters.
        val area = written.map { it.bounds }.reduce(Bounds::union)
        val earlier = characters(other.filter { it.bounds.isNear(area, MAX_HEIGHT * (1 + RUN_GAP)) })
        val new = characters(written)
        val all = earlier + new

        // Group characters side by side, directly or through others. Anything longer than a
        // character, like a slur over the notes, isn't part of a group.
        val parent = IntArray(all.size) { it }
        fun root(i: Int): Int = if (parent[i] == i) i else root(parent[i]).also { parent[i] = it }
        val characters = all.indices.filter { all[it].bounds.width <= MAX_HEIGHT }
        for (i in characters) {
            for (j in characters) {
                if (i < j && areSideBySide(all[i].bounds, all[j].bounds)) parent[root(i)] = root(j)
            }
        }
        val text = characters.filter { all[it].digit == null }.mapTo(HashSet(), ::root)
        return new.withIndex().mapNotNull { (i, character) ->
            val (strokes, bounds, digit) = character
            if (digit == null || root(earlier.size + i) in text) null else Fingering(strokes, digit, bounds)
        }
    }

    private data class Character(val strokes: List<Int>, val bounds: Bounds, val digit: Int?)

    /**
     * Splits [strokes] into characters, joining consecutive strokes that are close together when
     * they make a digit together, and ones that overlap anyway.
     */
    private fun characters(strokes: List<Trace>): List<Character> {
        val characters = ArrayList<Character>()
        var i = 0
        while (i < strokes.size) {
            val next = strokes.getOrNull(i + 1)
            val near = next != null && strokes[i].bounds.isNear(next.bounds, JOIN_GAP)
            val pair = if (near) FingeringRecognizer.recognize(listOf(strokes[i], next)) else null
            val indices = if (pair != null || (near && strokes[i].bounds.isNear(next.bounds, 0f))) {
                listOf(i, i + 1)
            } else {
                listOf(i)
            }
            val digit = if (indices.size == 2) pair else FingeringRecognizer.recognize(listOf(strokes[i]))
            characters += Character(indices, indices.map { strokes[it].bounds }.reduce(Bounds::union), digit)
            i += indices.size
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
