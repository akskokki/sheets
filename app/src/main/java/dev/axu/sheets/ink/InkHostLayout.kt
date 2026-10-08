package dev.axu.sheets.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor

/** Where a stylus stroke lands: the brush to use and how to map screen input into the target. */
class InkTarget(
    val brush: Brush,
    /** Maps host (window) coordinates to the target's coordinate space, e.g. PDF points. */
    val hostToTarget: Matrix,
    val onStrokeFinished: (Stroke) -> Unit,
)

fun interface InkTargetResolver {
    /** The target under ([x], [y]) in host coordinates, or null if the stylus should act like a finger there. */
    fun inkTargetAt(x: Float, y: Float): InkTarget?
}

val LocalInkHost = staticCompositionLocalOf<InkHostLayout> { error("No InkHostLayout") }

/**
 * Window-level host for the app's content that makes the stylus always draw.
 *
 * Stylus input that starts over an [InkTarget] never reaches the content; it's drawn as wet ink by
 * a front-buffered [InProgressStrokesView] layered above the content (lowest possible latency), then
 * handed to the target when finished. All other input, including the stylus outside of targets,
 * goes to the content as usual, except finger touches [PalmGuard] attributes to the writing hand.
 * Doing this at the View level keeps the routing in one place instead of every composable having to
 * tell pens, fingers and palms apart.
 */
@SuppressLint("ViewConstructor")
class InkHostLayout(context: Context, content: View) : FrameLayout(context) {
    var targetResolver: InkTargetResolver? = null

    private val wetInk = InProgressStrokesView(context)
    private val predictor = MotionEventPredictor.newInstance(this)
    private val targets = mutableMapOf<InProgressStrokeId, InkTarget>()
    private var activeStroke: InProgressStrokeId? = null
    private val palmGuard = PalmGuard()
    private var fingerGestureActive = false

    init {
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(wetInk, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        wetInk.addFinishedStrokesListener(
            object : InProgressStrokesFinishedListener {
                override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                    // The targets must start drawing these in this same frame to avoid flicker.
                    for ((id, stroke) in strokes) targets.remove(id)?.onStrokeFinished?.invoke(stroke)
                    wetInk.removeFinishedStrokes(strokes.keys)
                }
            },
        )
        wetInk.eagerInit()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.isStylus) {
            palmGuard.onStylusEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_DOWN) cancelFingerGesture()
            if (handleInk(event)) return true
        } else {
            if (palmGuard.shouldIgnore(event)) return true
            fingerGestureActive = event.actionMasked != MotionEvent.ACTION_UP &&
                event.actionMasked != MotionEvent.ACTION_CANCEL
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isStylus) palmGuard.onStylusEvent(event)
        return super.dispatchGenericMotionEvent(event)
    }

    /** When the pen comes down, whatever the fingers were doing was most likely the palm. */
    private fun cancelFingerGesture() {
        if (!fingerGestureActive) return
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        fingerGestureActive = false
        palmGuard.ignoreCurrentGesture()
    }

    /** Returns whether [event] was consumed as ink. */
    private fun handleInk(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val target = targetResolver?.inkTargetAt(event.x, event.y) ?: return false
            requestUnbufferedDispatch(event)
            predictor.record(event)
            val id = wetInk.startStroke(event, event.getPointerId(0), target.brush, target.hostToTarget)
            targets[id] = target
            activeStroke = id
            return true
        }

        val id = activeStroke ?: return false
        val pointerId = event.getPointerId(0)
        predictor.record(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val prediction = predictor.predict()
                try {
                    wetInk.addToStroke(event, pointerId, id, prediction)
                } finally {
                    prediction?.recycle()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (event.flags and MotionEvent.FLAG_CANCELED != 0) {
                    cancelStroke(id, event)
                } else {
                    wetInk.finishStroke(event, pointerId, id)
                }
                activeStroke = null
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelStroke(id, event)
                activeStroke = null
            }
        }
        return true
    }

    private fun cancelStroke(id: InProgressStrokeId, event: MotionEvent) {
        wetInk.cancelStroke(id, event)
        targets.remove(id)
    }
}

private val MotionEvent.isStylus: Boolean
    get() = getToolType(0).let { it == MotionEvent.TOOL_TYPE_STYLUS || it == MotionEvent.TOOL_TYPE_ERASER }
