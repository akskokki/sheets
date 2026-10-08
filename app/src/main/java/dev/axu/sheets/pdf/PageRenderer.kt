package dev.axu.sheets.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.LruCache

/**
 * Renders a document's pages. Whole pages are cached so paging back and forth is instant; zoomed
 * in detail is rendered on demand.
 *
 * Regions are in points. Don't modify one after passing it in: it's part of a cache key.
 */
class PageRenderer(private val document: PdfDocument) {
    private data class Key(val index: Int, val region: RectF, val width: Int, val height: Int)

    private val cache = object : LruCache<Key, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: Bitmap) = value.allocationByteCount
    }

    fun cached(index: Int, region: RectF, width: Int, height: Int): Bitmap? = cache[Key(index, region, width, height)]

    /** [region] of page [index] as shown when not zoomed in, scaled to [width] x [height] pixels. */
    suspend fun page(index: Int, region: RectF, width: Int, height: Int): Bitmap =
        cached(index, region, width, height)
            ?: document.render(index, width, height, region).also { cache.put(Key(index, region, width, height), it) }

    /** [region] of page [index] scaled to [width] x [height] pixels, for zoomed in detail. */
    suspend fun detail(index: Int, region: RectF, width: Int, height: Int): Bitmap =
        document.render(index, width, height, region)

    private companion object {
        // A full-screen page on the target tablet is ~16 MB; this keeps about six.
        const val MAX_BYTES = 96 * 1024 * 1024
    }
}
