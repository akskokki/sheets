package dev.axu.sheets.reader

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.ink.brush.InputToolType
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.axu.sheets.ink.PenWidth
import dev.axu.sheets.ink.Pens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZoomedInkTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun inkStaysSharpWhenZoomedIn() {
        val zoom = 5f
        // Horizontal, from x = 10 to 30 at y = 10 points; one pixel per point before the zoom.
        val inputs = MutableStrokeInputBatch()
        for (i in 0..4) inputs.add(InputToolType.STYLUS, 10f + i * 5f, 10f, elapsedTimeMillis = i * 10L, pressure = 1f)
        val stroke = Stroke(Pens.brush(Pens.colors.first { it.name == "Black" }, PenWidth.Bold), inputs)
        val renderer = CanvasStrokeRenderer.create()
        val sizePx = 40 * zoom.toInt()

        rule.setContent {
            Box(Modifier.size(with(LocalDensity.current) { sizePx.toDp() }).testTag("page")) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = zoom
                            scaleY = zoom
                        },
                ) {
                    drawRect(Color.White)
                    drawStrokes(listOf(stroke), Matrix(), zoom, renderer)
                }
            }
        }

        // Down the middle of the stroke, only its two edges may be neither paper nor ink, and
        // each by about a screen pixel, however far the zoom has magnified them.
        val pixels = rule.onNodeWithTag("page").captureToImage().toPixelMap()
        val x = (20f * zoom).toInt()
        val column = (0 until pixels.height).map { pixels[x, it].red }
        val ink = column.min()
        val blurred = column.count { it > ink + 0.1f && it < 0.9f }
        assertTrue("$blurred blurred pixels across the stroke's edges", blurred <= 4)
    }
}
