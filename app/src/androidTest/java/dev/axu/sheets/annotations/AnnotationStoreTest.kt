package dev.axu.sheets.annotations

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.axu.sheets.testStroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

@RunWith(AndroidJUnit4::class)
class AnnotationStoreTest {
    private val scope = CoroutineScope(SupervisorJob())
    private val local = MemoryAnnotationFiles()
    private val store = AnnotationStore(
        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
        local,
        notesFolder = MutableStateFlow(null),
        scope = scope,
    )

    @After
    fun tearDown() = scope.cancel()

    /** A file as a future version of the app might write it. */
    private val fromNewerVersion = ByteArrayOutputStream().also {
        DataOutputStream(it).apply {
            writeInt(0x53484E4B)
            writeInt(Int.MAX_VALUE)
            writeLong(System.currentTimeMillis())
        }
    }.toByteArray()

    @Test
    fun notesFromANewerVersionAreLeftAlone() = runBlocking {
        local.files["doc"] = fromNewerVersion

        assertTrue(store.load("doc").pages.isEmpty())
        store.save("doc", Annotations(mapOf(0 to listOf(testStroke(0f, 0f))), emptyList()))
        store.load("doc") // File access is serialized, so this waits for the save.

        assertArrayEquals(fromNewerVersion, local.files["doc"])
    }
}
