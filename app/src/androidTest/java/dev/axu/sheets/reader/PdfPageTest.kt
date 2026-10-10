package dev.axu.sheets.reader

import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.axu.sheets.pdf.PageRenderer
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.graphics.Color as AndroidColor

@RunWith(AndroidJUnit4::class)
class PdfPageTest {
    @get:Rule
    val rule = createComposeRule()

    private val sizePx = 200

    private fun PixelMap.has(color: Color): Boolean {
        for (y in 0 until height) for (x in 0 until width) if (this[x, y] == color) return true
        return false
    }

    @Test
    fun notesNeverShowWithoutTheirMusic() {
        val rendered = CompletableDeferred<Unit>()
        // The "music" is solid blue, the "notes" a red square.
        val pages = PageRenderer { _, width, height, _ ->
            rendered.await()
            createBitmap(width, height).apply { eraseColor(AndroidColor.BLUE) }
        }
        rule.setContent {
            Box(Modifier.size(with(LocalDensity.current) { sizePx.toDp() }).testTag("page")) {
                PdfPage(0, RectF(0f, 0f, 100f, 100f), pages, zoom = null) { _, _ ->
                    drawRect(Color.Red, Offset(10f, 10f), Size(40f, 40f))
                }
            }
        }

        val before = rule.onNodeWithTag("page").captureToImage().toPixelMap()
        assertFalse("notes shown before the music", before.has(Color.Red))

        rendered.complete(Unit)
        rule.waitUntil {
            val after = rule.onNodeWithTag("page").captureToImage().toPixelMap()
            after.has(Color.Red) && after.has(Color.Blue)
        }
    }
}
