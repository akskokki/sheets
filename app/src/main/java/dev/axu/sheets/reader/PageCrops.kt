package dev.axu.sheets.reader

import android.content.Context
import android.graphics.RectF
import androidx.core.content.edit
import dev.axu.sheets.pdf.PdfDocument
import dev.axu.sheets.pdf.contentBounds

/** Blank space kept beside the music, as a fraction of the page width: room to write next to it. */
private const val MARGIN = 0.03f

/** Bump when finding the music changes, so it's found again rather than remembered. */
private const val DETECTION_VERSION = 1

/**
 * Crops the blank margins beside the music so it fills more of the screen. The top and bottom
 * margins are kept.
 *
 * Finding the music means rendering every page, so where it is gets remembered per document,
 * keyed like annotations.
 */
class PageCrops(context: Context) {
    private val prefs = context.getSharedPreferences("content_bounds", Context.MODE_PRIVATE)

    /**
     * The part of each page to show, in points: its full height, and its music plus a margin across,
     * widened to show any notes beside it. Blank pages are shown whole.
     */
    suspend fun of(document: PdfDocument, documentKey: String, ink: DocumentInk): List<RectF> {
        val content = remembered(documentKey)?.takeIf { it.size == document.pageCount }
            ?: document.contentBounds().also { remember(documentKey, it) }
        return document.pageSizes.mapIndexed { index, size ->
            val music = content[index] ?: return@mapIndexed size.toRect()
            var left = (music.left - MARGIN) * size.width
            var right = (music.right + MARGIN) * size.width
            for (stroke in ink.strokesOn(index)) {
                val box = stroke.shape.computeBoundingBox() ?: continue
                left = minOf(left, box.xMin)
                right = maxOf(right, box.xMax)
            }
            RectF(left.coerceAtLeast(0f), 0f, right.coerceAtMost(size.width), size.height)
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
