package dev.axu.sheets.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.axu.sheets.R
import dev.axu.sheets.ink.Pen
import dev.axu.sheets.ink.PenSettings
import dev.axu.sheets.ink.PenWidth
import dev.axu.sheets.ink.Pens
import dev.axu.sheets.ink.RecentPens
import dev.axu.sheets.ink.RecentPens.Arriving
import dev.axu.sheets.ink.RecentPens.Leaving
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/**
 * The toolbar's drawing tools: the pen, optionally the ones used before it to switch back to, and
 * the eraser, in a row or (for the side rail) a column.
 */
@Composable
fun DrawingTools(
    pen: PenSettings,
    menu: PenMenuState,
    showRecents: Boolean,
    erasing: Boolean,
    onErasingChange: (Boolean) -> Unit,
    vertical: Boolean,
) {
    ToolGroup(vertical) {
        PenButton(pen, menu, selected = !erasing, onSelect = { onErasingChange(false) }, vertical)
        if (showRecents) {
            val motion = rememberRecentPensMotion(pen.recents, pen.pen)
            pen.recents.forEachIndexed { i, recent ->
                RecentPenButton(recent, motion.changes.getOrNull(i), { motion.elapsed.value }, vertical) {
                    menu.close()
                    pen.switchTo(recent)
                    onErasingChange(false)
                }
            }
        }
        Box(
            Modifier.background(
                SeparatorColor,
            ).then(if (vertical) Modifier.size(24.dp, 1.dp) else Modifier.size(1.dp, 24.dp)),
        )
        EraserButton(selected = erasing) {
            menu.close()
            onErasingChange(!erasing)
        }
    }
}

/** Tools on a shared background, the selected one on a chip that slides over to the next one chosen. */
@Composable
private fun ToolGroup(vertical: Boolean, content: @Composable () -> Unit) {
    val selection = remember { ToolSelection() }
    val chip = remember { mutableStateOf<Animatable<Rect, AnimationVector4D>?>(null) }
    LaunchedEffect(selection) {
        snapshotFlow { selection.target }.filterNotNull().collectLatest { target ->
            // Where the toolbar first appears, the chip is already in place.
            val current =
                chip.value ?: return@collectLatest run { chip.value = Animatable(target, Rect.VectorConverter) }
            current.animateTo(target, tween(SELECT_MILLIS, easing = SelectEasing))
        }
    }
    val modifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(ToolGroupColor)
        .onPlaced { selection.group = it }
        .drawBehind {
            val rect = chip.value?.value ?: selection.target ?: return@drawBehind
            val corners = CornerRadius(rect.minDimension / 2)
            drawRoundRect(SelectedEdgeColor, rect.topLeft + Offset(0f, SelectedEdgeOffset.toPx()), rect.size, corners)
            drawRoundRect(Color.White, rect.topLeft, rect.size, corners)
        }
        .padding(4.dp)
    CompositionLocalProvider(LocalToolSelection provides selection) {
        if (vertical) {
            Column(modifier, Arrangement.spacedBy(ToolSpacing), Alignment.CenterHorizontally) { content() }
        } else {
            Row(modifier, Arrangement.spacedBy(ToolSpacing), Alignment.CenterVertically) { content() }
        }
    }
}

/** Where in its [ToolGroup] the selected tool is. */
private class ToolSelection {
    var group: LayoutCoordinates? = null
    var target: Rect? by mutableStateOf(null)
        private set

    fun moveTo(tool: LayoutCoordinates) {
        val group = group?.takeIf { it.isAttached } ?: return
        if (tool.isAttached) target = group.localBoundingBoxOf(tool, clipBounds = false)
    }
}

private val LocalToolSelection = staticCompositionLocalOf<ToolSelection?> { null }

/**
 * The pen in use, drawn as a short line; tapping it opens [menu] to change its color and width. While
 * another tool is [selected], tapping it only switches back to the pen.
 */
@Composable
private fun PenButton(
    pen: PenSettings,
    menu: PenMenuState,
    selected: Boolean,
    onSelect: () -> Unit,
    vertical: Boolean,
) {
    // The arrow points to where the menu opens, and back once it's open.
    val turn by animateFloatAsState(if (menu.isOpen) 180f else 0f, label = "arrow")
    val isSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val content: @Composable () -> Unit = {
        PenLine(Color(pen.color.argb), pen.width, Modifier.size(40.dp, 24.dp))
        Icon(
            painterResource(R.drawable.ic_expand),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .rotate(turn + if (vertical) -90f else 0f),
        )
    }
    ToolButton(
        selected,
        description = "Pen: ${pen.color.name}, ${pen.width.name}",
        gesture = Modifier.opensPenMenu(menu, isSelected = { isSelected }, onSelect = { select() }),
        modifier = if (vertical) Modifier.size(48.dp, 56.dp) else Modifier.height(44.dp),
    ) {
        if (vertical) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
        } else {
            Row(Modifier.padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                content()
            }
        }
    }
}

/** A pen used before, to switch back to in one tap. */
@Composable
private fun RecentPenButton(
    pen: Pen,
    change: RecentPens.Change?,
    elapsed: () -> Float,
    vertical: Boolean,
    onClick: () -> Unit,
) {
    ToolButton(
        selected = false,
        description = "Switch to ${pen.color.name}, ${pen.width.name}",
        gesture = Modifier.toolClick(onClick),
        modifier = Modifier.size(RecentPenSize),
    ) {
        val line = Modifier.size(32.dp, 20.dp)
        val leaving = change?.leaving
        if (leaving != null && change.left != Leaving.Onward) {
            val toward = if (change.left == Leaving.ToPen) -1f else 1f
            PenLine(
                Color(leaving.color.argb),
                leaving.width,
                line.graphicsLayer {
                    fadeOut(elapsed(), toward, vertical)
                },
            )
        }
        PenLine(
            Color(pen.color.argb),
            pen.width,
            when (change?.arrived) {
                null -> line
                Arriving.FadesIn -> line.graphicsLayer { fadeIn(elapsed(), vertical) }
                Arriving.SlidesIn -> line.graphicsLayer { slideIn(elapsed(), vertical) }
            },
        )
    }
}

/** How the recent pens changed when last switched, and how long ago in milliseconds. */
private class RecentPensMotion(val changes: List<RecentPens.Change?>, val elapsed: Animatable<Float, AnimationVector1D>)

/** Plays [RecentPens.changes] whenever [recents] change. */
@Composable
private fun rememberRecentPensMotion(recents: List<Pen>, current: Pen): RecentPensMotion {
    val shown = remember { arrayOf(recents) }
    val motion = remember(recents) {
        RecentPensMotion(RecentPens.changes(shown[0], recents, current), Animatable(0f)).also { shown[0] = recents }
    }
    LaunchedEffect(motion) {
        motion.elapsed.animateTo(SHUFFLE_MILLIS.toFloat(), tween(SHUFFLE_MILLIS, easing = LinearEasing))
    }
    return motion
}

/*
 * Recent pens that change place fade out together, nudged the way they go: toward the pen if now in
 * use, otherwise away. Then the new ones fade in, nudged from the pen's side, as the one just left
 * comes from there. A pen moving on to the next place slides there instead, throughout.
 */

private fun GraphicsLayerScope.fadeOut(elapsed: Float, toward: Float, vertical: Boolean) {
    val f = FastOutLinearInEasing.transform((elapsed / FADE_OUT_MILLIS).coerceIn(0f, 1f))
    alpha = 1f - f
    shift(toward * ShuffleNudge.toPx() * f, vertical)
}

private fun GraphicsLayerScope.fadeIn(elapsed: Float, vertical: Boolean) {
    val f = LinearOutSlowInEasing.transform(((elapsed - FADE_OUT_MILLIS) / FADE_IN_MILLIS).coerceIn(0f, 1f))
    alpha = f
    shift(-ShuffleNudge.toPx() * (1f - f), vertical)
}

/** Speeds up to halfway while the others fade out, and settles as they fade in. */
private fun GraphicsLayerScope.slideIn(elapsed: Float, vertical: Boolean) {
    val half = ((if (vertical) RecentPenSize.height else RecentPenSize.width) + ToolSpacing).toPx() / 2
    val toGo = if (elapsed < FADE_OUT_MILLIS) {
        half * (2f - FastOutLinearInEasing.transform(elapsed / FADE_OUT_MILLIS))
    } else {
        half * (1f - LinearOutSlowInEasing.transform(((elapsed - FADE_OUT_MILLIS) / FADE_IN_MILLIS).coerceAtMost(1f)))
    }
    shift(-toGo, vertical)
}

private fun GraphicsLayerScope.shift(by: Float, vertical: Boolean) {
    if (vertical) translationY = by else translationX = by
}

// Designed to be seen with Android's animation speed at 0.5x, which halves them.
private const val FADE_OUT_MILLIS = 320
private const val FADE_IN_MILLIS = 400
private const val SHUFFLE_MILLIS = FADE_OUT_MILLIS + FADE_IN_MILLIS
private val ShuffleNudge = 14.dp
private val RecentPenSize = DpSize(48.dp, 44.dp)
private val ToolSpacing = 4.dp

/** Erases whole strokes the pen touches, until switched off. */
@Composable
private fun EraserButton(selected: Boolean, onClick: () -> Unit) {
    ToolButton(
        selected,
        description = "Eraser",
        gesture = Modifier.toolClick(onClick),
        modifier = Modifier.size(44.dp),
    ) {
        Icon(painterResource(R.drawable.ic_eraser), contentDescription = null, Modifier.size(22.dp))
    }
}

/**
 * Every button among the drawing tools, so that they all look and respond alike: the selected tool
 * sits on its group's chip, and a press shows nothing, as switching tools is the response.
 *
 * What a press does is up to [gesture]. It mustn't depend on whether the tool is selected, so a
 * press that selects it is still handled by the same gesture when it ends.
 */
@Composable
private fun ToolButton(
    selected: Boolean,
    description: String,
    modifier: Modifier,
    gesture: Modifier,
    content: @Composable () -> Unit,
) {
    val selection = LocalToolSelection.current
    val isSelected by rememberUpdatedState(selected)
    var placed by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(selected) {
        if (selected) placed?.let { selection?.moveTo(it) }
    }
    Box(
        modifier
            .onGloballyPositioned {
                placed = it
                if (isSelected) selection?.moveTo(it)
            }
            .then(gesture)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** A tool's gesture for a plain tap, which switches tools and so shows no press. */
private fun Modifier.toolClick(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClick = onClick)

private val ToolShape = RoundedCornerShape(50)
private val SelectedEdgeColor = Color(0x2E000000)
private val SelectedEdgeOffset = 1.dp

// Designed to be seen with Android's animation speed at 0.5x, which halves it.
private const val SELECT_MILLIS = 320
private val SelectEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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

/** A short wavy line in a pen's color and width, standing in for the pen. */
@Composable
fun PenLine(color: Color, width: PenWidth, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        // Drawn in a 40x24 box, scaled to fit.
        val x = size.width / 40f
        val y = size.height / 24f
        val path = Path().apply {
            moveTo(3 * x, 17 * y)
            cubicTo(9 * x, 4 * y, 15 * x, 4 * y, 19 * x, 12 * y)
            cubicTo(23 * x, 20 * y, 29 * x, 21 * y, 37 * x, 7 * y)
        }
        drawPath(path, color, style = Stroke((width.size * LINE_WIDTH_PER_POINT).dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Lines are drawn in proportion to the pen's width. */
private const val LINE_WIDTH_PER_POINT = 2f

private val ToolGroupColor = Color(0xFFEEF0F3)
private val DividerColor = Color(0xFFE6E6E3)
private val SeparatorColor = Color(0xFFD5D8DD)
private val HighlightColor = Color(0xFFE3E8F2)

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
