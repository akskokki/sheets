package dev.axu.sheets

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import dev.axu.sheets.ink.Pens

/** A short horizontal stroke starting at ([x], [y]), in page points. */
fun testStroke(x: Float, y: Float): Stroke {
    val inputs = MutableStrokeInputBatch()
    for (i in 0..4) inputs.add(InputToolType.STYLUS, x + i * 5f, y, elapsedTimeMillis = i * 10L, pressure = 0.5f)
    return Stroke(Pens.Default, inputs)
}
