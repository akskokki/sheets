package dev.axu.sheets.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import kotlin.math.max

/** Longest press, from the first finger down to the last up, that still counts as a tap. */
private const val TAP_TIMEOUT_MILLIS = 400L

/**
 * Detects quick, still taps with any number of fingers. Anything that moves or lingers is left to
 * other gestures, such as swiping pages.
 */
suspend fun PointerInputScope.detectTaps(
    onTap: (Offset) -> Unit,
    onMultiFingerTap: (fingers: Int) -> Unit,
) = awaitEachGesture {
    val first = awaitFirstDown(requireUnconsumed = false)
    val downPositions = mutableMapOf<PointerId, Offset>(first.id to first.position)
    var fingers = 1
    var isTap = true

    while (true) {
        val event = awaitPointerEvent()
        for (change in event.changes) {
            if (change.changedToDownIgnoreConsumed()) downPositions[change.id] = change.position
            val down = downPositions[change.id] ?: continue
            if (change.isConsumed || (change.position - down).getDistance() > viewConfiguration.touchSlop) {
                isTap = false
            }
            if (change.uptimeMillis - first.uptimeMillis > TAP_TIMEOUT_MILLIS) isTap = false
        }
        fingers = max(fingers, event.changes.count { it.pressed })
        if (event.changes.none { it.pressed }) break
    }

    if (isTap) {
        if (fingers == 1) onTap(first.position) else onMultiFingerTap(fingers)
    }
}
