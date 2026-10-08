package dev.axu.sheets.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import dev.axu.sheets.pdf.PageBitmapCache
import dev.axu.sheets.pdf.PageSize
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One PDF page, scaled to fit the available space and centered.
 *
 * [drawOverlay] draws on top of the page in page coordinates (PDF points).
 */
@Composable
fun PdfPage(
    index: Int,
    pageSize: PageSize,
    pages: PageBitmapCache,
    modifier: Modifier = Modifier,
    drawOverlay: DrawScope.(pageToPx: Float) -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fitted = fitInside(pageSize, constraints.maxWidth, constraints.maxHeight)
        // produceState keeps its value when the keys change (e.g. on rotation), so the previous
        // size's bitmap stays up, stretched to fit, until the new one is ready.
        val bitmap by produceState<ImageBitmap?>(
            initialValue = pages.cached(index, fitted.width, fitted.height)?.asImageBitmap(),
            index, fitted,
        ) {
            value = pages.get(index, fitted.width, fitted.height).asImageBitmap()
        }
        val pageModifier = with(LocalDensity.current) {
            Modifier.size(fitted.width.toDp(), fitted.height.toDp())
        }
        Box(modifier.then(pageModifier)) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.White)
                bitmap?.let { drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) }
                drawOverlay(size.width / pageSize.width)
            }
        }
    }
}

private fun fitInside(page: PageSize, maxWidth: Int, maxHeight: Int): IntSize {
    val scale = min(maxWidth / page.width, maxHeight / page.height)
    return IntSize((page.width * scale).roundToInt(), (page.height * scale).roundToInt())
}
