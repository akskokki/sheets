package dev.axu.sheets.annotations

import java.io.InputStream
import java.security.MessageDigest

/**
 * Identifies a PDF by its contents, so notes follow it when it's renamed or moved: a SHA-256 of
 * its size and its first and last [SAMPLE_BYTES], in constant time however large the file.
 *
 * A PDF ends with the index of its objects and its document ID, which any editor rewrites when
 * saving, and starts with its first page; a change that keeps the size and both ends is
 * practically impossible.
 *
 * Every stored note, reading position and crop is keyed by this, so neither [SAMPLE_BYTES] nor
 * what's hashed may ever change.
 */
object DocumentKey {
    const val SAMPLE_BYTES = 64 * 1024

    private val TAG = "sheets-document-key-1".toByteArray()

    /** The key of a file of [size] bytes, given a way to [read] `length` bytes at an offset. */
    fun of(size: Long, read: (offset: Long, length: Int) -> ByteArray): String {
        val headLength = minOf(size, SAMPLE_BYTES.toLong()).toInt()
        val tailStart = maxOf(headLength.toLong(), size - SAMPLE_BYTES)
        return digest(size, read(0, headLength), read(tailStart, (size - tailStart).toInt()))
    }

    /** The key of a file read from [input] to its end, for files whose size isn't known up front. */
    fun of(input: InputStream): String {
        val head = ByteArray(SAMPLE_BYTES)
        var headLength = 0
        while (headLength < SAMPLE_BYTES) {
            val read = input.read(head, headLength, SAMPLE_BYTES - headLength)
            if (read < 0) break
            headLength += read
        }
        // The last SAMPLE_BYTES after the head, in a ring.
        val ring = ByteArray(SAMPLE_BYTES)
        var rest = 0L
        while (true) {
            val at = (rest % SAMPLE_BYTES).toInt()
            val read = input.read(ring, at, SAMPLE_BYTES - at)
            if (read < 0) break
            rest += read
        }
        val tailLength = minOf(rest, SAMPLE_BYTES.toLong()).toInt()
        val tail = ByteArray(tailLength) { ring[((rest - tailLength + it) % SAMPLE_BYTES).toInt()] }
        return digest(headLength + rest, head.copyOf(headLength), tail)
    }

    private fun digest(size: Long, head: ByteArray, tail: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(TAG)
        digest.update(ByteArray(8) { (size shr (56 - 8 * it)).toByte() })
        digest.update(head)
        digest.update(tail)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
