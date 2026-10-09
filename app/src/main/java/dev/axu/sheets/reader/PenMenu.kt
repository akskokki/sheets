package dev.axu.sheets.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.axu.sheets.ink.PenSettings
import dev.axu.sheets.ink.PenWidth
import dev.axu.sheets.ink.Pens
import kotlin.math.max
import kotlin.math.roundToInt

/** Whether the pen menu is open, and where it opens from. */
@Stable
class PenMenuState(private val onOpen: () -> Unit = {}, private val onClose: () -> Unit = {}) {
    var isOpen by mutableStateOf(false)
        private set

    fun open() {
        if (isOpen) return
        isOpen = true
        onOpen()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        choices.clear()
        onClose()
    }

    fun toggle() = if (isOpen) close() else open()

    /** The pen button's bounds, in root coordinates. */
    var anchor by mutableStateOf(Rect.Zero)

    /** The toolbar's bounds, in root coordinates; the menu opens just past its edge. */
    var toolbar by mutableStateOf(Rect.Zero)

    /** The choice a drag from the pen button is over, which lifting there would pick. */
    var highlighted: Any? by mutableStateOf(null)
        private set

    private class Choice(val bounds: Rect, val select: () -> Unit)

    // Where the open menu's choices are, for picking by dragging from the pen button, which keeps
    // the pointer from reaching them.
    private val choices = mutableMapOf<Any, Choice>()

    internal fun onChoicePositioned(key: Any, bounds: Rect, select: () -> Unit) {
        choices[key] = Choice(bounds, select)
    }

    internal fun onDrag(point: Offset?) {
        highlighted = point?.let { choiceAt(it) }
    }

    /** Picks the choice under [point], if any; returns whether there was one. */
    internal fun onLift(point: Offset): Boolean {
        highlighted = null
        val key = choiceAt(point) ?: return false
        choices[key]?.select?.invoke()
        return true
    }

    private fun choiceAt(point: Offset): Any? = choices.entries.firstOrNull { point in it.value.bounds }?.key
}

/**
 * Makes this the pen button: pressing it opens the menu, and pressing it again closes it. Pressing
 * and dragging into the menu picks the choice lifted over, leaving the menu open for more. While the
 * pen isn't [isSelected], a tap only calls [onSelect]. The gesture keeps the first [isSelected] and
 * [onSelect] it's given, so they must read the current state.
 */
fun Modifier.opensPenMenu(
    menu: PenMenuState,
    isSelected: () -> Boolean = { true },
    onSelect: () -> Unit = {},
): Modifier = this
    .onGloballyPositioned { menu.anchor = it.boundsInRoot() }
    .pointerInput(menu) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val selected = isSelected()
            val wasOpen = menu.isOpen
            if (selected) menu.open()
            var dragging = false
            try {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                    val point = menu.anchor.topLeft + change.position
                    if (change.pressed) {
                        change.consume()
                        if (selected) menu.onDrag(point.takeIf { dragging })
                        continue
                    }
                    when {
                        !selected -> if (!dragging) onSelect()
                        dragging -> menu.onLift(point)
                        wasOpen -> menu.close()
                    }
                    break
                }
            } finally {
                menu.onDrag(null)
            }
        }
    }
    .semantics {
        role = Role.Button
        onClick {
            if (isSelected()) menu.toggle() else onSelect()
            true
        }
    }

/** Makes this a choice in the pen menu, which a drag from the pen button can pick. */
fun Modifier.penMenuChoice(menu: PenMenuState, key: Any, onSelect: () -> Unit): Modifier =
    onGloballyPositioned { menu.onChoicePositioned(key, it.boundsInRoot(), onSelect) }

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
                PenMenuPanel(
                    pen,
                    state,
                    Modifier.onGloballyPositioned {
                        inkTargets.onExclusionPositioned(PenMenuKey, it)
                    },
                )
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
                    state.close()
                }
            },
    )
}

private val PenMenuKey = Any()
private val MenuGap = 8.dp

/** From the top of the menu to the middle of its first row: padding plus half a choice. */
private val MenuFirstRowCenter = 30.dp
private const val MENU_ANIMATION_MILLIS = 150

/** Color swatches above width choices. */
@Composable
fun PenMenuPanel(pen: PenSettings, menu: PenMenuState, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 6.dp) {
        Column(Modifier.width(IntrinsicSize.Min).padding(8.dp)) {
            Row {
                for (color in Pens.colors) {
                    Choice(
                        menu,
                        key = color,
                        selected = pen.color == color,
                        description = "${color.name} pen",
                        onSelect = { pen.select(color) },
                        shape = CircleShape,
                    ) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(color.argb)))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), color = DividerColor)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                for (width in PenWidth.entries) {
                    Choice(
                        menu,
                        key = width,
                        selected = pen.width == width,
                        description = "${width.name} line",
                        onSelect = { pen.select(width) },
                        shape = RoundedCornerShape(50),
                        width = 72.dp,
                    ) {
                        PenLine(Color(pen.color.argb), width, Modifier.size(44.dp, 20.dp))
                    }
                }
            }
        }
    }
}

/** A 44dp tall touch target with a ring around it when selected. */
@Composable
private fun Choice(
    menu: PenMenuState,
    key: Any,
    selected: Boolean,
    description: String,
    onSelect: () -> Unit,
    shape: Shape,
    width: Dp = 44.dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(width, 44.dp)
            .penMenuChoice(menu, key, onSelect)
            .clip(shape)
            .then(if (menu.highlighted == key) Modifier.background(HighlightColor) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width - 10.dp, 34.dp)
                .then(if (selected) Modifier.border(2.dp, Color(0xFF1F2937), shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

private val DividerColor = Color(0xFFE6E6E3)
private val HighlightColor = Color(0xFFE3E8F2)
