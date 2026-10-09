package dev.axu.sheets.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.axu.sheets.annotations.Annotations
import dev.axu.sheets.testStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentInkTest {
    private val a = testStroke(10f, 10f)
    private val b = testStroke(20f, 20f)
    private val c = testStroke(30f, 30f)

    @Test
    fun undoStepsBackThroughSavedStrokesInDrawingOrder() {
        // b on page 1 was drawn last, even though page 0 comes first.
        val ink = DocumentInk(Annotations(mapOf(0 to listOf(a), 1 to listOf(b)), listOf(a, b)))
        assertTrue(ink.canUndo)
        assertEquals(1, ink.undo())
        assertEquals(0, ink.undo())
        assertNull(ink.undo())
        assertTrue(ink.strokesOn(0).isEmpty() && ink.strokesOn(1).isEmpty())
    }

    @Test
    fun drawingOrderIsKeptAcrossSaves() {
        val ink = DocumentInk(Annotations(mapOf(0 to listOf(a)), listOf(a)))
        ink.add(1, b)
        ink.add(0, c)
        val saved = ink.toAnnotations()
        assertEquals(listOf(a, b, c), saved.drawingOrder)

        val reloaded = DocumentInk(saved)
        assertEquals(0, reloaded.undo())
        assertEquals(listOf(a), reloaded.strokesOn(0))
    }

    @Test
    fun undoingAnEraseRestoresStrokesAndTheirOrder() {
        val ink = DocumentInk(Annotations(mapOf(0 to listOf(a, b, c)), listOf(a, b, c)))
        ink.erase(0, listOf(b))
        assertEquals(listOf(a, c), ink.strokesOn(0))
        assertEquals(listOf(a, c), ink.toAnnotations().drawingOrder)

        assertEquals(0, ink.undo())
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

        assertEquals(0, ink.undo())
        assertEquals(listOf(a, b, c), ink.strokesOn(0))
        assertEquals(0, ink.redo())
        assertEquals(listOf(b), ink.strokesOn(0))
    }

    @Test
    fun redoIsClearedByNewEdits() {
        val ink = DocumentInk(Annotations.Empty)
        ink.add(0, a)
        ink.undo()
        assertTrue(ink.canRedo)
        ink.add(0, b)
        assertFalse(ink.canRedo)
        assertEquals(listOf(b), ink.toAnnotations().drawingOrder)
    }
}
