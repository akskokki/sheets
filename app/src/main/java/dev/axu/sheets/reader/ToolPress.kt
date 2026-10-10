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
import androidx.compose.ui.unit.dp

/**
 * Makes this a tool's button. Touching it while another tool is selected selects it at once, and
 * shows no press, as switching tools is the response. Once it's selected, pressing it reports to
 * [presses] and does [onClick] on release, unless the press slides off. The gesture keeps the first
 * [isSelected], [onSelect] and [onClick] it's given, so they must read the current state.
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
            if (!isSelected()) {
                onSelect()
                return@awaitEachGesture
            }
            val press = PressInteraction.Press(down.position)
            presses.tryEmit(press)
            val up = waitForUpOrCancellation()
            if (up == null) {
                presses.tryEmit(PressInteraction.Cancel(press))
            } else {
                up.consume()
                presses.tryEmit(PressInteraction.Release(press))
                onClick()
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

/** Between tools in a group. */
val ToolSpacing = 4.dp
