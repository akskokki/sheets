package dev.axu.sheets.reader

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.axu.sheets.pdf.PageRenderer
import dev.axu.sheets.pdf.size
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** Wait for a pinch to settle before rendering sharper detail. */
private const val DETAIL_DELAY_MILLIS = 120L

/** Part of a page, in points, rendered at the current zoom's resolution. */
private class Detail(val bitmap: ImageBitmap, val region: RectF)

/**
 * The [crop] (in points) of one PDF page, scaled to fit the available space and centered,
 * optionally zoomed.
 *
 * [overlays] draw on top of the page in order, clipped to the crop; [pageToCanvas] maps points to
 * it, and the zoom then magnifies it [zoomScale] times. Each is drawn in a layer of its own, so a
 * change in one doesn't redraw the page or the others. Nothing shows until the page is rendered,
 * so overlays never appear without the page under them.
 */
@Composable
fun PdfPage(
    index: Int,
    crop: RectF,
    pages: PageRenderer,
    zoom: PageZoom?,
    modifier: Modifier = Modifier,
    overlays: List<DrawScope.(pageToCanvas: Matrix, zoomScale: Float) -> Unit> = emptyList(),
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .graphicsLayer {
                if (zoom != null) {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val viewport = IntSize(constraints.maxWidth, constraints.maxHeight)
        val fitScale = crop.size.scaleToFit(viewport.width.toFloat(), viewport.height.toFloat())
        val fitted = fittedSize(crop, viewport)

        // produceState keeps its value when the keys change (e.g. on rotation), so the previous
        // size's bitmap stays up, stretched to fit, until the new one is ready.
        val bitmap by produceState<ImageBitmap?>(
            initialValue = pages.cached(index, crop, fitted.width, fitted.height)?.asImageBitmap(),
            index,
            crop,
            fitted,
        ) {
            val rendered = pages.page(index, crop, fitted.width, fitted.height)
            if (value?.asAndroidBitmap() !== rendered) value = rendered.asImageBitmap()
        }

        // Zoomed in, the fitted bitmap gets blurry; overlay the visible part rendered sharply.
        var detail by remember(index, crop, fitted) { mutableStateOf<Detail?>(null) }
        LaunchedEffect(zoom, index, crop, fitted) {
            if (zoom == null) {
                detail = null
                return@LaunchedEffect
            }
            snapshotFlow { zoom.scale to zoom.offset }.collectLatest { (zoomScale, zoomOffset) ->
                if (zoomScale <= 1f) {
                    detail = null
                    return@collectLatest
                }
                delay(DETAIL_DELAY_MILLIS)
                val region = visibleRegion(crop, fitScale, fitted, viewport, zoomScale, zoomOffset)
                    ?: return@collectLatest
                val pxPerPoint = fitScale * zoomScale
                val width = (region.width() * pxPerPoint).roundToInt()
                val height = (region.height() * pxPerPoint).roundToInt()
                if (width > 0 && height > 0) {
                    detail = Detail(pages.detail(index, region, width, height).asImageBitmap(), region)
                }
            }
        }

        val pageModifier = with(LocalDensity.current) {
            Modifier.size(fitted.width.toDp(), fitted.height.toDp())
        }
        val rendered by remember { derivedStateOf { bitmap != null } }
        Box(modifier.then(pageModifier).clipToBounds()) {
            if (!rendered) return@Box
            Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                val image = bitmap ?: return@Canvas
                val pageToPx = size.width / crop.width()
                drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                detail?.let {
                    val r = it.region
                    drawImage(
                        it.bitmap,
                        dstOffset = IntOffset(
                            ((r.left - crop.left) * pageToPx).roundToInt(),
                            ((r.top - crop.top) * pageToPx).roundToInt(),
                        ),
                        dstSize = IntSize((r.width() * pageToPx).roundToInt(), (r.height() * pageToPx).roundToInt()),
                    )
                }
            }
            for (overlay in overlays) {
                Canvas(Modifier.fillMaxSize().graphicsLayer()) {
                    val pageToPx = size.width / crop.width()
                    val pageToCanvas = Matrix().apply {
                        setTranslate(-crop.left, -crop.top)
                        postScale(pageToPx, pageToPx)
                    }
                    overlay(pageToCanvas, zoom?.scale ?: 1f)
                }
            }
        }
    }
}

/** The size in pixels of a page's [crop] (in points) when fitted to a [viewport]. */
fun fittedSize(crop: RectF, viewport: IntSize): IntSize {
    val fitScale = crop.size.scaleToFit(viewport.width.toFloat(), viewport.height.toFloat())
    return IntSize((crop.width() * fitScale).roundToInt(), (crop.height() * fitScale).roundToInt())
}

/** The part of the [crop] visible in the viewport at the given zoom, in points, or null if none is. */
private fun visibleRegion(
    crop: RectF,
    fitScale: Float,
    fitted: IntSize,
    viewport: IntSize,
    zoomScale: Float,
    zoomOffset: Offset,
): RectF? {
    // Viewport -> unzoomed viewport -> page pixels (the page is centered) -> points.
    val pageLeft = (viewport.width - fitted.width) / 2f
    val pageTop = (viewport.height - fitted.height) / 2f
    fun toPoints(x: Float, y: Float) = Offset(
        ((x - zoomOffset.x) / zoomScale - pageLeft) / fitScale + crop.left,
        ((y - zoomOffset.y) / zoomScale - pageTop) / fitScale + crop.top,
    )
    val topLeft = toPoints(0f, 0f)
    val bottomRight = toPoints(viewport.width.toFloat(), viewport.height.toFloat())
    val region = RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    return region.takeIf { it.intersect(crop) }
}
