package dev.axu.sheets.pdf

import android.graphics.Bitmap
import android.util.LruCache

/** Keeps recently rendered pages around so paging back and forth is instant. */
class PageBitmapCache(private val document: PdfDocument) {
    private data class Key(val index: Int, val width: Int, val height: Int)

    private val cache = object : LruCache<Key, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: Bitmap) = value.allocationByteCount
    }

    fun cached(index: Int, width: Int, height: Int): Bitmap? = cache[Key(index, width, height)]

    suspend fun get(index: Int, width: Int, height: Int): Bitmap =
        cached(index, width, height)
            ?: document.render(index, width, height).also { cache.put(Key(index, width, height), it) }

    private companion object {
        // A full-screen page on the target tablet is ~16 MB; this keeps about six.
        const val MAX_BYTES = 96 * 1024 * 1024
    }
}
