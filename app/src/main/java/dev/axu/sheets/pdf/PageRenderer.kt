package dev.axu.sheets.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.LruCache

/**
 * Renders a document's pages. Whole pages are cached so paging back and forth is instant; zoomed
 * in detail is rendered on demand.
 */
class PageRenderer(private val document: PdfDocument) {
    private data class Key(val index: Int, val width: Int, val height: Int)

    private val cache = object : LruCache<Key, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: Bitmap) = value.allocationByteCount
    }

    fun cached(index: Int, width: Int, height: Int): Bitmap? = cache[Key(index, width, height)]

    /** Page [index] scaled to [width] x [height] pixels. */
    suspend fun page(index: Int, width: Int, height: Int): Bitmap =
        cached(index, width, height)
            ?: document.render(index, width, height).also { cache.put(Key(index, width, height), it) }

    /** [region] of page [index], in points, scaled to [width] x [height] pixels. */
    suspend fun detail(index: Int, region: RectF, width: Int, height: Int): Bitmap =
        document.render(index, width, height, region)

    private companion object {
        // A full-screen page on the target tablet is ~16 MB; this keeps about six.
        const val MAX_BYTES = 96 * 1024 * 1024
    }
}
