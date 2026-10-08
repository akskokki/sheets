package dev.axu.sheets.ink

import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_HOVER_ENTER
import android.view.MotionEvent.ACTION_HOVER_EXIT
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import dev.axu.sheets.ink.PalmGuard.Companion.GRACE_MS
import dev.axu.sheets.ink.PalmGuard.Companion.HOVER_TIMEOUT_MS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Timed from [GRACE_MS] and [HOVER_TIMEOUT_MS], so tuning them needs no changes here. */
class PalmGuardTest {
    private val guard = PalmGuard()

    private fun pen(action: Int, at: Long) = guard.onStylusEvent(action, at)

    private fun finger(action: Int, at: Long) = guard.shouldIgnore(action, at)

    /** The pen touches down at [from] and lifts away at [to]. */
    private fun write(from: Long, to: Long) {
        pen(ACTION_HOVER_ENTER, from - 10)
        pen(ACTION_DOWN, from)
        pen(ACTION_UP, to)
        pen(ACTION_HOVER_EXIT, to)
    }

    @Test
    fun fingersWorkWhenThePenIsAway() {
        assertFalse(finger(ACTION_DOWN, 1_000))
    }

    @Test
    fun touchWhileWritingIsIgnored() {
        pen(ACTION_DOWN, 1_000)
        assertTrue(finger(ACTION_DOWN, 1_000 + 2 * GRACE_MS))
    }

    @Test
    fun touchWhileThePenHoversIsIgnored() {
        pen(ACTION_HOVER_ENTER, 1_000)
        assertTrue(finger(ACTION_DOWN, 1_000 + GRACE_MS + 1))
    }

    @Test
    fun touchJustAfterLiftingThePenIsIgnored() {
        write(from = 1_000, to = 1_500)
        assertTrue(finger(ACTION_DOWN, 1_500 + GRACE_MS - 1))
    }

    @Test
    fun touchOnceThePenIsGoneWorks() {
        write(from = 1_000, to = 1_500)
        assertFalse(finger(ACTION_DOWN, 1_500 + GRACE_MS))
    }

    @Test
    fun anIgnoredTouchStaysIgnoredUntilItLifts() {
        write(from = 1_000, to = 1_500)
        assertTrue(finger(ACTION_DOWN, 1_400))
        val later = 1_500 + 10 * GRACE_MS
        assertTrue(finger(ACTION_MOVE, later))
        assertTrue(finger(ACTION_UP, later))
        assertFalse(finger(ACTION_DOWN, later + 1))
    }

    @Test
    fun aMissedHoverExitDoesNotBlockFingersForever() {
        pen(ACTION_HOVER_ENTER, 1_000)
        assertFalse(finger(ACTION_DOWN, 1_000 + HOVER_TIMEOUT_MS))
    }
}
