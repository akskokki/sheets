package dev.axu.sheets.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.axu.sheets.annotations.Annotations
import dev.axu.sheets.testStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentInkTest {
    private val a = testStroke(10f, 10f)
    private val b = testStroke(20f, 20f)
    private val c = testStroke(30f, 30f)

    @Test
    fun strokesSavedBeforeOpeningCantBeUndone() {
        val ink = DocumentInk(Annotations(mapOf(0 to listOf(a)), listOf(a)))
        assertFalse(ink.canUndo(0))
        assertFalse(ink.undo(0))

        ink.add(0, b)
        assertTrue(ink.undo(0))
        assertFalse(ink.undo(0))
        assertEquals(listOf(a), ink.strokesOn(0))
    }

    @Test
    fun undoAndRedoOnlyTouchTheirOwnPage() {
        val ink = DocumentInk(Annotations.Empty)
        ink.add(0, a)
        ink.add(1, b)

        assertTrue(ink.undo(0))
        assertEquals(listOf(b), ink.strokesOn(1))
        assertFalse(ink.undo(0))
        assertFalse(ink.canRedo(1))

        // A new edit on another page leaves this page's redo alone.
        ink.add(1, c)
        assertTrue(ink.redo(0))
        assertEquals(listOf(a), ink.strokesOn(0))
        assertEquals(listOf(b, c), ink.strokesOn(1))
    }

    @Test
    fun drawingOrderIsKeptAcrossSaves() {
        val ink = DocumentInk(Annotations(mapOf(0 to listOf(a)), listOf(a)))
        ink.add(1, b)
        ink.add(0, c)
        val saved = ink.toAnnotations()
        assertEquals(listOf(a, b, c), saved.drawingOrder)
        assertEquals(listOf(a, b, c), DocumentInk(saved).toAnnotations().drawingOrder)
    }

    @Test
    fun undoingAnEraseRestoresStrokesAndTheirOrder() {
        // Saved strokes, so erasing notes saved before opening can be undone too.
        val ink = DocumentInk(Annotations(mapOf(0 to listOf(a, b, c)), listOf(a, b, c)))
        ink.erase(0, listOf(b))
        assertEquals(listOf(a, c), ink.strokesOn(0))
        assertEquals(listOf(a, c), ink.toAnnotations().drawingOrder)

        assertTrue(ink.undo(0))
        assertEquals(listOf(a, b, c), ink.strokesOn(0))
        assertEquals(listOf(a, b, c), ink.toAnnotations().drawingOrder)
    }

    @Test
    fun oneSweepOfTheEraserIsUndoneAtOnce() {
        val ink = DocumentInk(Annotations(mapOf(0 to listOf(a, b, c)), listOf(a, b, c)))
        val eraser = ink.startErasing(0)
        eraser.erase(listOf(c))
        eraser.erase(listOf(a))
        assertEquals(listOf(b), ink.strokesOn(0))

        assertTrue(ink.undo(0))
        assertEquals(listOf(a, b, c), ink.strokesOn(0))
        assertTrue(ink.redo(0))
        assertEquals(listOf(b), ink.strokesOn(0))
    }

    @Test
    fun redoIsClearedByNewEdits() {
        val ink = DocumentInk(Annotations.Empty)
        ink.add(0, a)
        ink.undo(0)
        assertTrue(ink.canRedo(0))
        ink.add(0, b)
        assertFalse(ink.canRedo(0))
        assertEquals(listOf(b), ink.toAnnotations().drawingOrder)
    }
}
