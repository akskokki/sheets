package dev.axu.sheets.ink

import android.view.MotionEvent

/**
 * Decides which finger gestures to ignore because they're most likely the writing hand's palm:
 * those that start while the stylus is touching or hovering over the screen, or shortly after.
 *
 * This complements the device's own palm rejection, which some tablets enable per app.
 */
internal class PalmGuard {
    private var stylusDown = false
    private var stylusHovering = false
    private var lastStylusEventTime = Long.MIN_VALUE / 2
    private var ignoringGesture = false

    fun onStylusEvent(event: MotionEvent) {
        lastStylusEventTime = event.eventTime
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> stylusDown = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stylusDown = false
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> stylusHovering = true
            MotionEvent.ACTION_HOVER_EXIT -> stylusHovering = false
        }
    }

    /** Whether to drop this finger event (and the rest of its gesture). */
    fun shouldIgnore(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) ignoringGesture = isStylusActive(event.eventTime)
        val ignore = ignoringGesture
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            ignoringGesture = false
        }
        return ignore
    }

    /** Ignores the rest of the finger gesture in progress. */
    fun ignoreCurrentGesture() {
        ignoringGesture = true
    }

    private fun isStylusActive(now: Long): Boolean {
        val sinceStylus = now - lastStylusEventTime
        // A missed hover exit must not block touch forever, hence the hover timeout.
        return stylusDown || (stylusHovering && sinceStylus < HOVER_TIMEOUT_MS) || sinceStylus < GRACE_MS
    }

    private companion object {
        /** A palm often lands just as the pen lifts between notes. */
        const val GRACE_MS = 300L
        const val HOVER_TIMEOUT_MS = 2_000L
    }
}
