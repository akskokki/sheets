package dev.axu.sheets.reader

import android.content.Context
import android.graphics.RectF
import androidx.core.content.edit
import androidx.ink.strokes.Stroke
import dev.axu.sheets.pdf.CONTENT_DETECTION_VERSION
import dev.axu.sheets.pdf.PageSize
import dev.axu.sheets.pdf.PdfDocument
import dev.axu.sheets.pdf.contentBounds

/** Blank space kept beside the music, as a fraction of the page width: room to write next to it. */
internal const val MARGIN = 0.03f

/**
 * Crops the blank margins beside the music so it fills more of the screen. The top and bottom
 * margins are kept.
 *
 * Finding the music means rendering every page, so where it is gets remembered per document,
 * keyed like annotations.
 */
class PageCrops(context: Context) {
    private val prefs = context.getSharedPreferences("content_bounds", Context.MODE_PRIVATE)

    /** The part of each page to show, in points; see [cropPage]. */
    suspend fun of(document: PdfDocument, documentKey: String, ink: DocumentInk): List<RectF> {
        val content = remembered(documentKey)?.takeIf { it.size == document.pageCount }
            ?: document.contentBounds().also { remember(documentKey, it) }
        return document.pageSizes.mapIndexed { index, size -> cropPage(size, content[index], ink.strokesOn(index)) }
    }

    /** Pages are separated by ";", blank pages are empty. */
    private fun remembered(documentKey: String): List<RectF?>? =
        prefs.getString(prefKey(documentKey), null)?.split(';')?.map { page ->
            if (page.isEmpty()) return@map null
            val (left, top, right, bottom) = page.split(',').map { it.toFloat() }
            RectF(left, top, right, bottom)
        }

    private fun remember(documentKey: String, content: List<RectF?>) = prefs.edit {
        // Drop what an earlier version of detection found.
        for (key in prefs.all.keys) if (key.endsWith(":$documentKey")) remove(key)
        putString(prefKey(documentKey), content.joinToString(";") { it?.run { "$left,$top,$right,$bottom" } ?: "" })
    }

    private fun prefKey(documentKey: String) = "$CONTENT_DETECTION_VERSION:$documentKey"
}

/**
 * The part of a page of [size] to show, in points: its full height, and across, its [music] (as
 * fractions of the page; null if the page is blank) plus a margin, widened to show all [notes].
 * Blank pages are shown whole.
 */
internal fun cropPage(size: PageSize, music: RectF?, notes: List<Stroke>): RectF {
    if (music == null) return size.toRect()
    var left = (music.left - MARGIN) * size.width
    var right = (music.right + MARGIN) * size.width
    for (stroke in notes) {
        val box = stroke.shape.computeBoundingBox() ?: continue
        left = minOf(left, box.xMin)
        right = maxOf(right, box.xMax)
    }
    return RectF(left.coerceAtLeast(0f), 0f, right.coerceAtMost(size.width), size.height)
}
