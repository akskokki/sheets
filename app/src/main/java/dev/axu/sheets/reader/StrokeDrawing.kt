package dev.axu.sheets.reader

import android.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke

/**
 * Draws [strokes] given in page coordinates (points) onto a page drawn with [pageToCanvas], which
 * a layer above then magnifies [zoomScale] times.
 */
fun DrawScope.drawStrokes(
    strokes: List<Stroke>,
    pageToCanvas: Matrix,
    zoomScale: Float,
    renderer: CanvasStrokeRenderer,
    alpha: Float = 1f,
) {
    if (strokes.isEmpty() || alpha <= 0f) return
    drawIntoCanvas {
        val canvas = it.nativeCanvas
        val checkpoint = if (alpha < 1f) canvas.saveLayerAlpha(null, (alpha * 255).toInt()) else canvas.save()
        canvas.concat(pageToCanvas)
        // The renderer doesn't apply its matrix, only sizes the edges' anti-aliasing from it, so it
        // needs the full scale on screen: without the zoom, magnified edges come out blurry.
        val pageToScreen = Matrix(pageToCanvas).apply { postScale(zoomScale, zoomScale) }
        for (stroke in strokes) renderer.draw(canvas, stroke, pageToScreen)
        canvas.restoreToCount(checkpoint)
    }
}
