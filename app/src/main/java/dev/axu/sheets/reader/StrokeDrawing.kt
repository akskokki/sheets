package dev.axu.sheets.reader

import android.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke

/** Draws [strokes] given in page coordinates onto a page drawn at [pageToPx] pixels per point. */
fun DrawScope.drawStrokes(
    strokes: List<Stroke>,
    pageToPx: Float,
    renderer: CanvasStrokeRenderer,
    alpha: Float = 1f,
) {
    if (strokes.isEmpty() || alpha <= 0f) return
    val pageToCanvas = Matrix().apply { setScale(pageToPx, pageToPx) }
    drawIntoCanvas {
        val canvas = it.nativeCanvas
        val checkpoint = if (alpha < 1f) canvas.saveLayerAlpha(null, (alpha * 255).toInt()) else canvas.save()
        // The renderer only uses the matrix for level of detail; the canvas has to apply it.
        canvas.concat(pageToCanvas)
        for (stroke in strokes) renderer.draw(canvas, stroke, pageToCanvas)
        canvas.restoreToCount(checkpoint)
    }
}
