package dev.axu.sheets.reader

import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.axu.sheets.pdf.PageSize
import dev.axu.sheets.testStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Built from [MARGIN], so tuning it needs no changes here. */
@RunWith(AndroidJUnit4::class)
class PageCropTest {
    private val page = PageSize(600f, 800f)
    private val music = RectF(0.2f, 0.1f, 0.8f, 0.9f)

    @Test
    fun cropsTheSidesToTheMusicPlusAMargin() {
        val crop = cropPage(page, music, notes = emptyList())
        assertEquals((music.left - MARGIN) * page.width, crop.left, 0.01f)
        assertEquals((music.right + MARGIN) * page.width, crop.right, 0.01f)
    }

    @Test
    fun keepsTheFullHeight() {
        val crop = cropPage(page, music, notes = emptyList())
        assertEquals(0f, crop.top)
        assertEquals(page.height, crop.bottom)
    }

    @Test
    fun blankPagesAreShownWhole() {
        assertEquals(page.toRect(), cropPage(page, music = null, notes = emptyList()))
    }

    @Test
    fun notesBesideTheMusicAreNeverCroppedAway() {
        val inLeftMargin = testStroke(x = 5f, y = 400f)
        val inRightMargin = testStroke(x = 570f, y = 400f)
        val crop = cropPage(page, music, notes = listOf(inLeftMargin, inRightMargin))
        for (note in listOf(inLeftMargin, inRightMargin)) {
            val box = note.shape.computeBoundingBox()!!
            assertTrue("$crop hides $box", crop.left <= box.xMin && box.xMax <= crop.right)
        }
    }

    @Test
    fun neverReachesPastThePage() {
        assertEquals(page.toRect(), cropPage(page, RectF(0f, 0f, 1f, 1f), notes = emptyList()))
    }
}
