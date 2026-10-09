package dev.axu.sheets.reader

import androidx.compose.runtime.mutableStateMapOf
import androidx.ink.strokes.Stroke
import dev.axu.sheets.annotations.Annotations
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The finished ink strokes of one document, per page, in page coordinates (PDF points), with an
 * undo/redo history for each page.
 *
 * The history holds only edits made since the document was opened, so strokes saved before can't
 * be undone, though undoing an erase brings them back.
 *
 * Backed by snapshot state so changes show up in the same frame they're made; the wet-to-dry ink
 * handoff relies on that to avoid flicker.
 */
class DocumentInk(saved: Annotations) {
    private sealed interface Edit {
        val page: Int

        class Add(override val page: Int, val stroke: Stroke) : Edit

        /** [strokes] sorted by their index in the page before erasing. */
        class Erase(override val page: Int, val strokes: List<IndexedValue<Stroke>>) : Edit
    }

    private val pages = mutableStateMapOf<Int, List<Stroke>>().apply { putAll(saved.pages) }

    /** Strokes in the order they were drawn, including erased ones, which undo may bring back. */
    private val drawingOrder = saved.drawingOrder.toMutableList()
    private val undoStacks = mutableStateMapOf<Int, List<Edit>>()
    private val redoStacks = mutableStateMapOf<Int, List<Edit>>()

    fun canUndo(page: Int): Boolean = !undoStacks[page].isNullOrEmpty()
    fun canRedo(page: Int): Boolean = !redoStacks[page].isNullOrEmpty()

    fun toAnnotations(): Annotations {
        val present = Collections.newSetFromMap(IdentityHashMap<Stroke, Boolean>())
        for (strokes in pages.values) present.addAll(strokes)
        // Strokes still on the pages, in drawing order; anything without a recorded place (from
        // files that predate drawing order) goes first.
        val ordered = drawingOrder.filter { it in present }
        val unordered = present - ordered.toSet()
        return Annotations(pages.toMap(), unordered.toList() + ordered)
    }

    fun strokesOn(page: Int): List<Stroke> = pages[page].orEmpty()

    fun add(page: Int, stroke: Stroke) = perform(Edit.Add(page, stroke))

    /** Removes [strokes] (which must be on [page]) as a single undoable edit. */
    fun erase(page: Int, strokes: Collection<Stroke>) {
        val indexed = strokesOn(page).withIndex().filter { it.value in strokes }
        if (indexed.isNotEmpty()) perform(Edit.Erase(page, indexed))
    }

    /** Starts erasing strokes on [page] one after another, to be undone together. */
    fun startErasing(page: Int) = EraseGesture(page)

    /** Strokes erased by one movement of the eraser: undone as one edit, however many it passed over. */
    inner class EraseGesture internal constructor(private val page: Int) {
        private var edit: Edit.Erase? = null

        /** Erases [strokes] (which must be on [page]) along with those erased before. */
        fun erase(strokes: Collection<Stroke>) {
            val previous = edit
            // Grow the gesture's edit while it's the latest, rather than adding one per stroke.
            val all = if (previous != null && undoStacks[page]?.lastOrNull() === previous) {
                undoStacks.pop(page)
                revert(previous)
                previous.strokes.map { it.value } + strokes
            } else {
                strokes
            }
            val indexed = strokesOn(page).withIndex().filter { it.value in all }
            if (indexed.isEmpty()) return
            edit = Edit.Erase(page, indexed).also(::perform)
        }
    }

    /** Reverts the most recent edit on [page]; returns false if there was nothing to undo. */
    fun undo(page: Int): Boolean {
        val edit = undoStacks.pop(page) ?: return false
        revert(edit)
        redoStacks.push(edit)
        return true
    }

    /** Reapplies the most recently undone edit on [page]; returns false if there was nothing to redo. */
    fun redo(page: Int): Boolean {
        val edit = redoStacks.pop(page) ?: return false
        apply(edit)
        undoStacks.push(edit)
        return true
    }

    private fun perform(edit: Edit) {
        apply(edit)
        undoStacks.push(edit)
        redoStacks.remove(edit.page)
    }

    private fun MutableMap<Int, List<Edit>>.push(edit: Edit) {
        this[edit.page] = this[edit.page].orEmpty() + edit
    }

    private fun MutableMap<Int, List<Edit>>.pop(page: Int): Edit? {
        val stack = this[page] ?: return null
        val edit = stack.lastOrNull() ?: return null
        this[page] = stack.dropLast(1)
        return edit
    }

    private fun apply(edit: Edit) {
        val strokes = strokesOn(edit.page)
        pages[edit.page] = when (edit) {
            is Edit.Add -> {
                drawingOrder += edit.stroke
                strokes + edit.stroke
            }

            is Edit.Erase -> {
                val erased = edit.strokes.mapTo(HashSet()) { it.value }
                strokes.filterNot { it in erased }
            }
        }
    }

    private fun revert(edit: Edit) {
        val strokes = strokesOn(edit.page)
        pages[edit.page] = when (edit) {
            is Edit.Add -> {
                drawingOrder.removeAt(drawingOrder.lastIndexOf(edit.stroke))
                strokes - edit.stroke
            }

            // Ascending order makes each original index valid again by the time it's inserted.
            is Edit.Erase -> strokes.toMutableList().apply {
                for ((index, stroke) in edit.strokes) add(index.coerceAtMost(size), stroke)
            }
        }
    }
}
