package dev.axu.sheets.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.toSize
import kotlin.math.abs

/**
 * Zoom of the current page: content at p in the viewport is shown at `offset + scale * p`.
 */
@Stable
class PageZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    val isZoomed: Boolean get() = scale > 1f

    /** Zooms by [zoomChange] around [centroid] and pans by [pan], keeping the viewport covered. */
    fun transform(centroid: Offset, pan: Offset, zoomChange: Float, viewport: Size) {
        val newScale = (scale * zoomChange).coerceIn(1f, MAX_SCALE)
        val anchored = centroid - (centroid - offset) * (newScale / scale) + pan
        scale = newScale
        offset = Offset(
            anchored.x.coerceIn(viewport.width * (1 - newScale), 0f),
            anchored.y.coerceIn(viewport.height * (1 - newScale), 0f),
        )
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private companion object {
        const val MAX_SCALE = 5f
    }
}

/**
 * Pinch to zoom, and drag to pan while zoomed. Runs before the pager sees the events, consuming
 * them only once they've moved past touch slop, so taps (including multi-finger ones) still work.
 */
suspend fun PointerInputScope.detectZoom(zoom: PageZoom) = awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    var accumulatedZoom = 1f
    var accumulatedPan = Offset.Zero
    var pastSlop = false

    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val fingers = event.changes.count { it.pressed }
        // One finger pages through the document unless zoomed in, when it pans instead.
        val ours = fingers >= 2 || zoom.isZoomed
        val zoomChange = event.calculateZoom()
        val pan = event.calculatePan()

        if (!pastSlop) {
            accumulatedZoom *= zoomChange
            accumulatedPan += pan
            val pinched = abs(1 - accumulatedZoom) * event.calculateCentroidSize(useCurrent = false)
            pastSlop = ours && (pinched > viewConfiguration.touchSlop || accumulatedPan.getDistance() > viewConfiguration.touchSlop)
        }
        // No centroid when none of the pointers were down before this event.
        val centroid = event.calculateCentroid(useCurrent = false)
        if (pastSlop && ours && centroid.isSpecified) {
            zoom.transform(centroid, pan, zoomChange, size.toSize())
            for (change in event.changes) if (change.positionChanged()) change.consume()
        }
    } while (event.changes.any { it.pressed })
}
