package dev.axu.sheets.reader

import android.content.Context
import android.graphics.RectF
import androidx.core.content.edit
import dev.axu.sheets.pdf.PdfDocument
import dev.axu.sheets.pdf.contentBounds

/** Blank space kept around the music, as a fraction of the page: room to write next to it. */
private const val MARGIN = 0.03f

/**
 * Crops pages to their music, trimming blank margins so it fills more of the screen.
 *
 * Finding the music means rendering every page, so where it is gets remembered per document,
 * keyed like annotations.
 */
class PageCrops(context: Context) {
    private val prefs = context.getSharedPreferences("content_bounds", Context.MODE_PRIVATE)

    /**
     * The part of each page to show, in points: the music plus a margin, widened to show any notes
     * outside it. Every page is cropped alike, relative to its size, so they all share one scale.
     */
    suspend fun of(document: PdfDocument, documentKey: String, ink: DocumentInk): List<RectF> {
        val content = remembered(documentKey)
            ?: document.contentBounds()?.also { remember(documentKey, it) }
            ?: return document.pageSizes.map { it.toRect() }
        val shown = RectF(content).apply { inset(-MARGIN, -MARGIN) }
        for ((index, size) in document.pageSizes.withIndex()) {
            for (stroke in ink.strokesOn(index)) {
                val box = stroke.shape.computeBoundingBox() ?: continue
                shown.union(box.xMin / size.width, box.yMin / size.height, box.xMax / size.width, box.yMax / size.height)
            }
        }
        shown.intersect(0f, 0f, 1f, 1f)
        return document.pageSizes.map {
            RectF(shown.left * it.width, shown.top * it.height, shown.right * it.width, shown.bottom * it.height)
        }
    }

    private fun remembered(documentKey: String): RectF? {
        val (left, top, right, bottom) = prefs.getString(documentKey, null)?.split(',')?.map { it.toFloat() }
            ?: return null
        return RectF(left, top, right, bottom)
    }

    private fun remember(documentKey: String, content: RectF) = prefs.edit {
        putString(documentKey, with(content) { "$left,$top,$right,$bottom" })
    }
}
