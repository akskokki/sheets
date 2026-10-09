package dev.axu.sheets.ink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** How far the eraser reaches around the pen. */
internal class EraserCursorView(context: Context) : View(context) {
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var pressed = false
    private var shown = false

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = PRESSED_FILL
    }

    /** Shows the eraser's reach, [radius] px around ([x], [y]) in this view's coordinates. */
    fun show(x: Float, y: Float, radius: Float, pressed: Boolean) {
        centerX = x
        centerY = y
        this.radius = radius
        this.pressed = pressed
        shown = true
        invalidate()
    }

    fun hide() {
        if (!shown) return
        shown = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (!shown) return
        if (pressed) canvas.drawCircle(centerX, centerY, radius, fill)
        outline.color = if (pressed) PRESSED_OUTLINE else HOVER_OUTLINE
        canvas.drawCircle(centerX, centerY, radius, outline)
    }

    private companion object {
        const val PRESSED_FILL = 0x1A1F2937
        const val PRESSED_OUTLINE = 0xB31F2937.toInt()
        const val HOVER_OUTLINE = 0x591F2937
    }
}
