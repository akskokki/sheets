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

    /** Takes a stylus event's masked action and time. */
    fun onStylusEvent(action: Int, time: Long) {
        lastStylusEventTime = time
        when (action) {
            MotionEvent.ACTION_DOWN -> stylusDown = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stylusDown = false
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> stylusHovering = true
            MotionEvent.ACTION_HOVER_EXIT -> stylusHovering = false
        }
    }

    /** Whether to drop a finger event, given its masked action and time (and the rest of its gesture). */
    fun shouldIgnore(action: Int, time: Long): Boolean {
        if (action == MotionEvent.ACTION_DOWN) ignoringGesture = isStylusActive(time)
        val ignore = ignoringGesture
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
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

    companion object {
        /** A palm often lands just as the pen lifts between notes. */
        internal const val GRACE_MS = 300L
        internal const val HOVER_TIMEOUT_MS = 2_000L
    }
}
