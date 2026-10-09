package dev.axu.sheets.reader

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.ink.strokes.Stroke
import dev.axu.sheets.annotations.Annotations
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The finished ink strokes of one document, per page, in page coordinates (PDF points), with an
 * undo/redo history.
 *
 * The history starts out as every saved stroke in the order it was drawn, so undo can step back
 * through earlier sessions too; erasing, replacing and redo are only remembered for this session.
 *
 * Backed by snapshot state so changes show up in the same frame they're made; the wet-to-dry ink
 * handoff relies on that to avoid flicker.
 */
class DocumentInk(saved: Annotations) {
    private sealed interface Edit {
        val page: Int

        class Add(override val page: Int, val stroke: Stroke) : Edit

        /** [removed] sorted by their index in the page before, and [added] after the rest. */
        class Replace(override val page: Int, val removed: List<IndexedValue<Stroke>>, val added: List<Stroke>) : Edit
    }

    private val pages = mutableStateMapOf<Int, List<Stroke>>().apply { putAll(saved.pages) }
    private val undoStack = mutableStateListOf<Edit>().apply {
        val pageOf = IdentityHashMap<Stroke, Int>()
        for ((page, strokes) in saved.pages) for (stroke in strokes) pageOf[stroke] = page
        for (stroke in saved.drawingOrder) pageOf[stroke]?.let { page -> add(Edit.Add(page, stroke)) }
    }
    private val redoStack = mutableStateListOf<Edit>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun toAnnotations(): Annotations {
        val present = Collections.newSetFromMap(IdentityHashMap<Stroke, Boolean>())
        for (strokes in pages.values) present.addAll(strokes)
        // Strokes still on the pages, in the order their additions happened; anything without a
        // recorded addition (there shouldn't be any) goes first.
        val ordered = undoStack.flatMap {
            when (it) {
                is Edit.Add -> listOf(it.stroke)
                is Edit.Replace -> it.added
            }
        }.filter { it in present }
        val unordered = present - ordered.toSet()
        return Annotations(pages.toMap(), unordered.toList() + ordered)
    }

    fun strokesOn(page: Int): List<Stroke> = pages[page].orEmpty()

    fun add(page: Int, stroke: Stroke) = perform(Edit.Add(page, stroke))

    /** Removes [strokes] (which must be on [page]) as a single undoable edit. */
    fun erase(page: Int, strokes: Collection<Stroke>) = replace(page, strokes, emptyList())

    /** Swaps [strokes] on [page] for [replacements] as a single undoable edit; nothing happens if none are there. */
    fun replace(page: Int, strokes: Collection<Stroke>, replacements: List<Stroke>) {
        val indexed = strokesOn(page).withIndex().filter { it.value in strokes }
        if (indexed.isNotEmpty()) perform(Edit.Replace(page, indexed, replacements))
    }

    /** Reverts the most recent edit; returns the page it was on, or null if there was nothing to undo. */
    fun undo(): Int? {
        val edit = undoStack.removeLastOrNull() ?: return null
        revert(edit)
        redoStack += edit
        return edit.page
    }

    /** Reapplies the most recently undone edit; returns its page, or null if there was nothing to redo. */
    fun redo(): Int? {
        val edit = redoStack.removeLastOrNull() ?: return null
        apply(edit)
        undoStack += edit
        return edit.page
    }

    private fun perform(edit: Edit) {
        apply(edit)
        undoStack += edit
        redoStack.clear()
    }

    private fun apply(edit: Edit) {
        val strokes = strokesOn(edit.page)
        pages[edit.page] = when (edit) {
            is Edit.Add -> strokes + edit.stroke

            is Edit.Replace -> {
                val removed = edit.removed.mapTo(HashSet()) { it.value }
                strokes.filterNot { it in removed } + edit.added
            }
        }
    }

    private fun revert(edit: Edit) {
        val strokes = strokesOn(edit.page)
        pages[edit.page] = when (edit) {
            is Edit.Add -> strokes - edit.stroke

            // Ascending order makes each original index valid again by the time it's inserted.
            is Edit.Replace -> (strokes - edit.added.toSet()).toMutableList().apply {
                for ((index, stroke) in edit.removed) add(index.coerceAtMost(size), stroke)
            }
        }
    }
}
