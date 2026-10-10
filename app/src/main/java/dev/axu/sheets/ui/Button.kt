package dev.axu.sheets.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * Where a button's face sits. Raised, it shows its side below; pressed, the wall of its hole above;
 * flush, neither.
 */
enum class ButtonHeight(internal val lift: Float) { Raised(1f), Flush(0f), Pressed(-1f) }

/** Where a button sits in a [ButtonGroup], which decides its shape and the room around its icon. */
enum class ButtonPosition { Alone, First, Middle, Last }

/** Does [onClick] when released, unless the press slides off first. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    rest: ButtonHeight = ButtonHeight.Raised,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val presses = remember { MutableInteractionSource() }
    Button(
        rest,
        presses,
        modifier.clickable(presses, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        enabled,
        shape,
        content,
    )
}

/**
 * Every button in the app. Its face rests at [rest] and sinks while [presses] says it's pressed,
 * all the way even for a brief tap;
 * what pressing it does is up to a gesture in [modifier], which reports to [presses]. Unless
 * [enabled], it lies flush with its icon faded.
 */
@Composable
fun Button(
    rest: ButtonHeight,
    presses: InteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit,
) {
    val pressed by presses.collectIsPressedAsState()
    // However short the tap, a press sinks all the way before the face comes back up.
    var sinking by remember { mutableStateOf(false) }
    val height = when {
        sinking -> ButtonHeight.Pressed
        !enabled -> ButtonHeight.Flush
        pressed -> ButtonHeight.Pressed
        else -> rest
    }
    // Pressing is quick; everything else settles more slowly.
    val millis = if (height == ButtonHeight.Pressed) PRESS_MILLIS else SETTLE_MILLIS
    val lift by animateFloatAsState(height.lift, tween(millis, easing = EaseOut), label = "lift")
    LaunchedEffect(presses) {
        presses.interactions.filterIsInstance<PressInteraction.Press>().collectLatest {
            sinking = true
            try {
                snapshotFlow { lift }.first { it == ButtonHeight.Pressed.lift }
            } finally {
                sinking = false
            }
        }
    }
    val face by animateColorAsState(height.face, tween(millis, easing = EaseOut), label = "face")
    val inkAlpha by animateFloatAsState(
        if (enabled) 1f else DISABLED_ALPHA,
        tween(SETTLE_MILLIS, easing = EaseOut),
        label = "ink",
    )
    Box(
        modifier.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawBehind {
                // The edge fills the button's footprint, and what the face uncovers of it is the
                // button's side or wall. Flush, it's hidden, so antialiasing leaves no fringe.
                drawOutline(outline, SheetsColors.Edge, alpha = abs(lift).coerceAtMost(1f))
                translate(top = -lift * Travel.toPx()) { drawOutline(outline, face) }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.graphicsLayer {
                translationY = -lift * Travel.toPx()
                alpha = inkAlpha
            },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides SheetsColors.Ink, content = content)
        }
    }
}

/**
 * A button showing [icon], described by [label]. On its own it's round; in a [ButtonGroup], its
 * [position] rounds the group's ends and leaves the icon a little more room beside them.
 */
@Composable
fun IconButton(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    position: ButtonPosition = ButtonPosition.Alone,
) {
    val vertical = LocalGroupVertical.current
    // A rounded end looks lighter than the flat middle of a group, so it gets more room.
    val before = if (position == ButtonPosition.First) EndRoom else InnerRoom
    val after = if (position == ButtonPosition.Last) EndRoom else InnerRoom
    val length = before + IconSize + after
    val nudge = (before - after) / 2
    val size = when {
        position == ButtonPosition.Alone -> Modifier.size(ButtonSize)
        vertical -> Modifier.size(ButtonSize, length)
        else -> Modifier.size(length, ButtonSize)
    }
    Button(onClick, modifier.then(size), enabled, shape = position.shape(vertical)) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier
                .size(IconSize)
                .then(if (vertical) Modifier.offset(y = nudge) else Modifier.offset(x = nudge)),
        )
    }
}

/**
 * Buttons side by side, or one above the other if [vertical], in a well they rise from. Buttons that
 * act together, like undo and redo, sit closest.
 */
@Composable
fun ButtonGroup(
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    spacing: Dp = 2.dp,
    content: @Composable () -> Unit,
) {
    val well = modifier
        .clip(CircleShape)
        .background(SheetsColors.Group)
        .padding(4.dp)
    CompositionLocalProvider(LocalGroupVertical provides vertical) {
        if (vertical) {
            Column(well, Arrangement.spacedBy(spacing), Alignment.CenterHorizontally) { content() }
        } else {
            Row(well, Arrangement.spacedBy(spacing), Alignment.CenterVertically) { content() }
        }
    }
}

/** A line between buttons in a [ButtonGroup], across it. */
@Composable
fun ButtonDivider() {
    val size = if (LocalGroupVertical.current) Modifier.size(24.dp, 1.dp) else Modifier.size(1.dp, 24.dp)
    Box(size.background(SheetsColors.Divider))
}

private val LocalGroupVertical = staticCompositionLocalOf { false }

private val ButtonHeight.face
    get() = when (this) {
        ButtonHeight.Raised -> SheetsColors.Raised
        ButtonHeight.Flush -> SheetsColors.Group
        ButtonHeight.Pressed -> SheetsColors.Pressed
    }

private fun ButtonPosition.shape(vertical: Boolean): Shape = when (this) {
    ButtonPosition.Alone -> CircleShape

    ButtonPosition.Middle -> RoundedCornerShape(0)

    ButtonPosition.First -> if (vertical) {
        RoundedCornerShape(topStartPercent = 50, topEndPercent = 50)
    } else {
        RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    }

    ButtonPosition.Last -> if (vertical) {
        RoundedCornerShape(bottomStartPercent = 50, bottomEndPercent = 50)
    } else {
        RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
    }
}

/** How far a face rises or sinks, and so how much of the edge shows. */
private val Travel = 1.dp

/** Across a button, and along a round one. */
private val ButtonSize = 44.dp
private val IconSize = 22.dp
private val EndRoom = 15.dp
private val InnerRoom = 13.dp
private const val DISABLED_ALPHA = 0.38f
private const val PRESS_MILLIS = 120
private const val SETTLE_MILLIS = 440

/** CSS's ease-out, for buttons and what they move. Nothing overshoots, as that would lift a face off its edge. */
val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
