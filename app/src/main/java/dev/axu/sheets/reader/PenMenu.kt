package dev.axu.sheets.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import dev.axu.sheets.ink.PenSettings
import kotlin.math.max
import kotlin.math.roundToInt

/** Whether the pen menu is open, and where it opens from. */
@Stable
class PenMenuState {
    var isOpen by mutableStateOf(false)

    /** The pen button's bounds, in root coordinates. */
    var anchor by mutableStateOf(Rect.Zero)

    /** The toolbar's bounds, in root coordinates; the menu opens just past its edge. */
    var toolbar by mutableStateOf(Rect.Zero)
}

/**
 * The pen menu, opening from the pen button: down from the top bar, or sideways from the side rail.
 * It floats above the reader rather than in the toolbar, which would clip it. The pen works it like
 * a finger, as it does the toolbar.
 */
@Composable
fun PenMenu(state: PenMenuState, pen: PenSettings, sideways: Boolean, inkTargets: PageInkTargets) {
    val enter = fadeIn(tween(MENU_ANIMATION_MILLIS)) + if (sideways) {
        expandHorizontally(tween(MENU_ANIMATION_MILLIS), expandFrom = Alignment.Start)
    } else {
        expandVertically(tween(MENU_ANIMATION_MILLIS), expandFrom = Alignment.Top)
    }
    val exit = fadeOut(tween(MENU_ANIMATION_MILLIS)) + if (sideways) {
        shrinkHorizontally(tween(MENU_ANIMATION_MILLIS), shrinkTowards = Alignment.Start)
    } else {
        shrinkVertically(tween(MENU_ANIMATION_MILLIS), shrinkTowards = Alignment.Top)
    }
    Layout(
        content = {
            AnimatedVisibility(state.isOpen, enter = enter, exit = exit) {
                DisposableEffect(Unit) { onDispose { inkTargets.onExclusionRemoved(PenMenuKey) } }
                PenMenuPanel(pen, Modifier.onGloballyPositioned { inkTargets.onExclusionPositioned(PenMenuKey, it) })
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val gap = MenuGap.toPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            val origin = coordinates?.positionInRoot() ?: Offset.Zero
            val anchor = state.anchor.translate(-origin)
            val toolbar = state.toolbar.translate(-origin)
            for (placeable in placeables) {
                val x = if (sideways) toolbar.right + gap else anchor.center.x - placeable.width / 2f
                // Sideways, the first row of choices lines up with the button.
                val y = if (sideways) anchor.center.y - MenuFirstRowCenter.toPx() else toolbar.bottom + gap
                placeable.place(
                    x.roundToInt().coerceIn(0, max(0, constraints.maxWidth - placeable.width)),
                    y.roundToInt().coerceIn(0, max(0, constraints.maxHeight - placeable.height)),
                )
            }
        }
    }
}

/** While the menu is open, a touch anywhere else only closes it, rather than also turning the page. */
@Composable
fun ClosePenMenuOnTouch(state: PenMenuState) {
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(state) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    state.isOpen = false
                }
            },
    )
}

private val PenMenuKey = Any()
private val MenuGap = 8.dp

/** From the top of the menu to the middle of its first row: padding plus half a choice. */
private val MenuFirstRowCenter = 30.dp
private const val MENU_ANIMATION_MILLIS = 150
