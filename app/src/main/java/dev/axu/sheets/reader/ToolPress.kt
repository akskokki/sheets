package dev.axu.sheets.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Makes this a tool's button. Touching it while another tool is selected selects it at once; once
 * it was selected, releasing it does [onClick], unless the press slides off. Either way the press
 * is reported to [presses]. The gesture keeps the first [isSelected], [onSelect] and [onClick] it's
 * given, so they must read the current state.
 */
fun Modifier.toolPress(
    presses: MutableInteractionSource,
    isSelected: () -> Boolean,
    onSelect: () -> Unit,
    onClick: () -> Unit,
): Modifier = this
    .pointerInput(presses) {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            val press = PressInteraction.Press(down.position)
            presses.tryEmit(press)
            val wasSelected = isSelected()
            if (!wasSelected) onSelect()
            val up = waitForUpOrCancellation()
            if (up == null) {
                presses.tryEmit(PressInteraction.Cancel(press))
            } else {
                up.consume()
                presses.tryEmit(PressInteraction.Release(press))
                if (wasSelected) onClick()
            }
        }
    }
    .semantics {
        role = Role.Button
        onClick {
            if (isSelected()) onClick() else onSelect()
            true
        }
    }
