package dev.axu.sheets.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.LruCache

/**
 * Renders a document's pages. Whole pages are cached so paging back and forth is instant; zoomed
 * in detail is rendered on demand. Bitmaps start uploading to the GPU as soon as they're rendered,
 * so the frame that first shows one doesn't wait for it.
 *
 * Regions are in points. Don't modify one after passing it in: it's part of a cache key.
 */
class PageRenderer(private val render: suspend (index: Int, width: Int, height: Int, region: RectF) -> Bitmap) {
    constructor(document: PdfDocument) : this(document::render)

    private data class Key(val index: Int, val region: RectF, val width: Int, val height: Int)

    private val cache = object : LruCache<Key, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: Bitmap) = value.allocationByteCount
    }

    fun cached(index: Int, region: RectF, width: Int, height: Int): Bitmap? = cache[Key(index, region, width, height)]

    /** [region] of page [index] as shown when not zoomed in, scaled to [width] x [height] pixels. */
    suspend fun page(index: Int, region: RectF, width: Int, height: Int): Bitmap = cached(index, region, width, height)
        ?: render(index, width, height, region).also {
            it.prepareToDraw()
            cache.put(Key(index, region, width, height), it)
        }

    /** Forgets every page but [index], to hold less while the document isn't on screen. */
    fun keepOnly(index: Int) {
        for (key in cache.snapshot().keys) if (key.index != index) cache.remove(key)
    }

    /** [region] of page [index] scaled to [width] x [height] pixels, for zoomed in detail. */
    suspend fun detail(index: Int, region: RectF, width: Int, height: Int): Bitmap =
        render(index, width, height, region).also { it.prepareToDraw() }

    private companion object {
        // A full-screen page on the target tablet is ~16 MB; this keeps about six.
        const val MAX_BYTES = 96 * 1024 * 1024
    }
}
