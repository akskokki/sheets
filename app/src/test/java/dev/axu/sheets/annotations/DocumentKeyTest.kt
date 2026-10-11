package dev.axu.sheets.annotations

import dev.axu.sheets.annotations.DocumentKey.SAMPLE_BYTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Built from [SAMPLE_BYTES]. */
class DocumentKeyTest {
    private fun file(size: Int) = Random(size).nextBytes(size)

    private fun keyOf(bytes: ByteArray) = DocumentKey.of(bytes.size.toLong()) { offset, length ->
        bytes.copyOfRange(
            offset.toInt(),
            offset.toInt() + length,
        )
    }

    @Test
    fun aFileHasTheSameKeyWhetherItsSizeIsKnownOrNot() {
        val s = SAMPLE_BYTES
        for (size in listOf(0, 1, s - 1, s, s + 1, 2 * s - 1, 2 * s, 2 * s + 1, 3 * s + 17)) {
            val bytes = file(size)
            assertEquals("size $size", keyOf(bytes), DocumentKey.of(bytes.inputStream()))
        }
    }

    @Test
    fun savingChangesTheKey() {
        val original = file(5 * SAMPLE_BYTES)
        val key = keyOf(original)
        assertNotEquals("end rewritten", key, keyOf(original.copyOf().also { it[it.size - 1]++ }))
        assertNotEquals("start rewritten", key, keyOf(original.copyOf().also { it[0]++ }))
        assertNotEquals("appended to", key, keyOf(original + byteArrayOf(0)))
        assertNotEquals("cut short", key, keyOf(original.copyOf(original.size - 1)))
    }

    @Test
    fun smallFilesDifferAnywhere() {
        val original = file(2 * SAMPLE_BYTES)
        assertNotEquals(keyOf(original), keyOf(original.copyOf().also { it[SAMPLE_BYTES]++ }))
    }

    @Test
    fun keyingReadsTheSameAmountHoweverLargeTheFile() {
        var read = 0L
        DocumentKey.of(1L shl 32) { _, length ->
            read += length
            ByteArray(length)
        }
        assertTrue("read $read bytes", read <= 2L * SAMPLE_BYTES)
    }
}
