package dev.axu.sheets.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.axu.sheets.annotations.AnnotationCodec
import dev.axu.sheets.annotations.AnnotationStore
import dev.axu.sheets.annotations.Annotations
import dev.axu.sheets.annotations.MemoryAnnotationFiles
import dev.axu.sheets.testStroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class InkSessionsTest {
    private val scope = CoroutineScope(SupervisorJob())
    private val local = MemoryAnnotationFiles()
    private val store = AnnotationStore(
        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
        local,
        notesFolder = MutableStateFlow(null),
        scope = scope,
    )
    private val sessions = InkSessions(store)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun undoHistorySurvivesLeavingTheSheet() = runBlocking {
        val ink = sessions.open("doc")
        ink.add(0, testStroke(10f, 10f))
        store.save("doc", ink.toAnnotations())

        assertTrue(sessions.open("doc").undo(0))
    }

    @Test
    fun notesChangedElsewhereAreLoadedRatherThanWrittenOver() = runBlocking {
        val ink = sessions.open("doc")
        ink.add(0, testStroke(10f, 10f))
        store.save("doc", ink.toAnnotations())
        store.load("doc") // File access is serialized, so this waits for the save.

        val elsewhere = Annotations(mapOf(0 to List(2) { testStroke(20f, 20f) }), emptyList())
        local.files["doc"] = ByteArrayOutputStream()
            .also { AnnotationCodec.encode(elsewhere, System.currentTimeMillis() + 60_000, it) }
            .toByteArray()

        val reopened = sessions.open("doc")
        assertEquals(2, reopened.strokesOn(0).size)
        assertFalse(reopened.canUndo(0))
    }
}
