package dev.axu.sheets.pdf

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import kotlin.math.min

/** Page dimensions in PDF points (1/72 inch). */
data class PageSize(val width: Float, val height: Float) {
    /** The scale at which the page just fits inside [maxWidth] x [maxHeight]. */
    fun scaleToFit(maxWidth: Float, maxHeight: Float): Float = min(maxWidth / width, maxHeight / height)
}

/**
 * A read-only PDF backed by the platform [PdfRenderer].
 *
 * [PdfRenderer] allows only one open page at a time and isn't thread-safe, so all access is
 * serialized through [mutex].
 */
class PdfDocument private constructor(
    private val file: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageSizes: List<PageSize>,
) {
    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int get() = pageSizes.size

    /** Renders page [index] scaled to exactly [width] x [height] pixels, on white. */
    suspend fun render(index: Int, width: Int, height: Int): Bitmap = mutex.withLock {
        withContext(Dispatchers.IO) {
            check(!closed) { "Document is closed" }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            renderer.openPage(index).use { page ->
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
            bitmap
        }
    }

    suspend fun close() = mutex.withLock {
        if (!closed) {
            closed = true
            renderer.close()
            file.close()
        }
    }

    companion object {
        suspend fun open(resolver: ContentResolver, uri: Uri): PdfDocument = withContext(Dispatchers.IO) {
            val file = resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(uri.toString())
            try {
                val renderer = PdfRenderer(file)
                val sizes = List(renderer.pageCount) { index ->
                    renderer.openPage(index).use { PageSize(it.width.toFloat(), it.height.toFloat()) }
                }
                PdfDocument(file, renderer, sizes)
            } catch (e: Exception) {
                file.close()
                throw e
            }
        }
    }
}
