package dev.axu.sheets.reader

import android.content.Context
import android.graphics.RectF
import androidx.core.content.edit
import dev.axu.sheets.pdf.PdfDocument
import dev.axu.sheets.pdf.contentBounds

/** Blank space kept around the music, as a fraction of the page: room to write next to it. */
private const val MARGIN = 0.03f

/** Bump when finding the music changes, so it's found again rather than remembered. */
private const val DETECTION_VERSION = 1

/**
 * Crops pages to their music, trimming blank margins so it fills more of the screen.
 *
 * Finding the music means rendering every page, so where it is gets remembered per document,
 * keyed like annotations.
 */
class PageCrops(context: Context) {
    private val prefs = context.getSharedPreferences("content_bounds", Context.MODE_PRIVATE)

    /**
     * The part of each page to show, in points: its music plus a margin, widened to show any notes
     * outside it. Blank pages are shown whole.
     */
    suspend fun of(document: PdfDocument, documentKey: String, ink: DocumentInk): List<RectF> {
        val content = remembered(documentKey)?.takeIf { it.size == document.pageCount }
            ?: document.contentBounds().also { remember(documentKey, it) }
        return document.pageSizes.mapIndexed { index, size ->
            val shown = content[index]?.let { RectF(it) } ?: return@mapIndexed size.toRect()
            shown.inset(-MARGIN, -MARGIN)
            for (stroke in ink.strokesOn(index)) {
                val box = stroke.shape.computeBoundingBox() ?: continue
                shown.union(box.xMin / size.width, box.yMin / size.height, box.xMax / size.width, box.yMax / size.height)
            }
            shown.intersect(0f, 0f, 1f, 1f)
            RectF(shown.left * size.width, shown.top * size.height, shown.right * size.width, shown.bottom * size.height)
        }
    }

    /** Pages are separated by ";", blank pages are empty. */
    private fun remembered(documentKey: String): List<RectF?>? =
        prefs.getString("$DETECTION_VERSION:$documentKey", null)?.split(';')?.map { page ->
            if (page.isEmpty()) return@map null
            val (left, top, right, bottom) = page.split(',').map { it.toFloat() }
            RectF(left, top, right, bottom)
        }

    private fun remember(documentKey: String, content: List<RectF?>) = prefs.edit {
        putString("$DETECTION_VERSION:$documentKey", content.joinToString(";") { it?.run { "$left,$top,$right,$bottom" } ?: "" })
    }
}
