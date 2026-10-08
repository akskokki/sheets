package dev.axu.sheets.annotations

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.axu.sheets.testStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class AnnotationCodecTest {
    private fun roundTrip(annotations: Annotations, savedAt: Long = 1234L): Pair<ByteArray, Annotations> {
        val bytes = ByteArrayOutputStream().also { AnnotationCodec.encode(annotations, savedAt, it) }.toByteArray()
        return bytes to bytes.inputStream().use(AnnotationCodec::decode)
    }

    @Test
    fun keepsStrokesPerPageAndDrawingOrder() {
        val a = testStroke(10f, 10f)
        val b = testStroke(20f, 20f)
        val c = testStroke(30f, 30f)
        // Drawn c (page 1), then a, then b (page 0).
        val (_, decoded) = roundTrip(Annotations(mapOf(0 to listOf(a, b), 1 to listOf(c)), listOf(c, a, b)))

        assertEquals(setOf(0, 1), decoded.pages.keys)
        assertEquals(2, decoded.pages.getValue(0).size)
        val (decodedA, decodedB) = decoded.pages.getValue(0)
        val decodedC = decoded.pages.getValue(1).single()
        assertEquals(
            listOf(decodedC, decodedA, decodedB).map {
                System.identityHashCode(it)
            },
            decoded.drawingOrder.map { System.identityHashCode(it) },
        )
        assertSame(decodedA, decoded.drawingOrder[1])
        assertEquals(a.inputs.size, decodedA.inputs.size)
        assertEquals(a.brush, decodedA.brush)
    }

    @Test
    fun savedTimeCanBeReadWithoutDecoding() {
        val (bytes, _) = roundTrip(Annotations(mapOf(0 to listOf(testStroke(0f, 0f))), emptyList()), savedAt = 42L)
        assertEquals(42L, bytes.inputStream().use(AnnotationCodec::savedAtMillis))
    }

    @Test
    fun emptyPagesAreDropped() {
        val (_, decoded) = roundTrip(Annotations(mapOf(3 to emptyList()), emptyList()))
        assertEquals(emptyMap<Int, Any>(), decoded.pages)
    }
}
