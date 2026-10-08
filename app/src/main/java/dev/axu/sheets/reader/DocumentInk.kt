package dev.axu.sheets.reader

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.ink.strokes.Stroke

/**
 * The finished ink strokes of one document, per page, in page coordinates (PDF points).
 *
 * Backed by snapshot state so a stroke shows up in the same frame it's added; the wet-to-dry ink
 * handoff relies on that to avoid flicker.
 */
class DocumentInk {
    private val pages = mutableStateMapOf<Int, List<Stroke>>()

    /** Pages of strokes added this session, most recent last. */
    private val history = mutableStateListOf<Int>()

    val canUndo: Boolean get() = history.isNotEmpty()

    fun strokesOn(page: Int): List<Stroke> = pages[page].orEmpty()

    fun add(page: Int, stroke: Stroke) {
        pages[page] = strokesOn(page) + stroke
        history += page
    }

    /** Removes the most recently added stroke; returns its page, or null if there was nothing to undo. */
    fun undo(): Int? {
        val page = history.removeLastOrNull() ?: return null
        pages[page] = strokesOn(page).dropLast(1)
        return page
    }
}
