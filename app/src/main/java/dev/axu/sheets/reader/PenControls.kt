package dev.axu.sheets.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.axu.sheets.R
import dev.axu.sheets.ink.PenSettings
import dev.axu.sheets.ink.PenWidth

/**
 * The toolbar's drawing tools: the pen, optionally the ones used before it to switch back to, and
 * the eraser, in a row or (for the side rail) a column. The selected tool is raised.
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
    ToolbarButtonGroup(vertical = vertical, spacing = ToolSpacing) {
        PenButton(pen, menu, selected = !erasing, onSelect = { onErasingChange(false) }, vertical)
        if (showRecents) {
            val motion = rememberRecentPensMotion(pen.recents, pen.pen)
            pen.recents.forEachIndexed { i, recent ->
                RecentPenButton(recent, motion.moves.getOrNull(i), { motion.elapsed.value }, vertical) {
                    menu.close()
                    pen.switchTo(recent)
                    onErasingChange(false)
                }
            }
        }
        ToolbarDivider()
        EraserButton(
            selected = erasing,
            onChange = {
                menu.close()
                onErasingChange(it)
            },
        )
    }
}

/**
 * The pen in use, drawn as a short line; pressing it opens [menu] to change its color and width.
 * While another tool is [selected], touching it only switches back to the pen.
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
    val open = menu.isOpen
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(ARROW_MILLIS, easing = EaseOut), label = "arrow")
    val isSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val presses = remember { MutableInteractionSource() }
    val content: @Composable () -> Unit = {
        WipingPenLine(Color(pen.color.argb), pen.width, Modifier.size(40.dp, 24.dp))
        Icon(
            painterResource(R.drawable.ic_expand),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .rotate(turn + if (vertical) -90f else 0f),
        )
    }
    ToolbarButton(
        rest = if (selected) ButtonHeight.Raised else ButtonHeight.Flush,
        presses,
        (if (vertical) Modifier.size(44.dp, 56.dp) else Modifier.height(44.dp))
            .opensPenMenu(menu, presses, isSelected = { isSelected }, onSelect = { select() })
            .semantics {
                contentDescription = "Pen: ${pen.color.name}, ${pen.width.name}"
                this.selected = selected
                stateDescription = if (open) "Menu open" else "Menu closed"
            },
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

/** Erases whole strokes the pen touches, until switched off by pressing it again. */
@Composable
private fun EraserButton(selected: Boolean, onChange: (Boolean) -> Unit) {
    val isSelected by rememberUpdatedState(selected)
    val change by rememberUpdatedState(onChange)
    val presses = remember { MutableInteractionSource() }
    ToolbarButton(
        rest = if (selected) ButtonHeight.Raised else ButtonHeight.Flush,
        presses,
        Modifier
            .size(44.dp)
            .toolPress(presses, isSelected = { isSelected }, onSelect = { change(true) }, onClick = { change(false) })
            .semantics {
                contentDescription = "Eraser"
                this.selected = selected
            },
    ) {
        Icon(painterResource(R.drawable.ic_eraser), contentDescription = null, Modifier.size(22.dp))
    }
}

/** A short wavy line in a pen's color and width, standing in for the pen. */
@Composable
fun PenLine(color: Color, width: PenWidth, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawPenLine(SolidColor(color), width) }
}

/**
 * A [PenLine] that, when the pen changes, wipes across from the left to the new color and width,
 * with a soft edge, so the change shows without the line moving.
 */
@Composable
private fun WipingPenLine(color: Color, width: PenWidth, modifier: Modifier = Modifier) {
    val line = PenLook(color, width)
    var shown by remember { mutableStateOf(line) }
    var wipedOver by remember { mutableStateOf<PenLook?>(null) }
    val wipe = remember { Animatable(1f) }
    LaunchedEffect(line) {
        if (line == shown) return@LaunchedEffect
        // A change mid-wipe wipes over the line it was wiping in.
        wipedOver = shown
        shown = line
        wipe.snapTo(0f)
        wipe.animateTo(1f, tween(WIPE_MILLIS, easing = EaseOut))
        wipedOver = null
    }
    Canvas(modifier) {
        val old = wipedOver
        if (old == null) {
            drawPenLine(SolidColor(shown.color), shown.width)
            return@Canvas
        }
        // The edge travels from just before the line to just past it.
        val edge = WipeEdge.toPx()
        val at = -edge + (size.width + 2 * edge) * wipe.value
        val gone = Brush.horizontalGradient(
            listOf(old.color.copy(alpha = 0f), old.color),
            startX = at - edge,
            endX = at,
        )
        val come = Brush.horizontalGradient(
            listOf(shown.color, shown.color.copy(alpha = 0f)),
            startX = at - edge,
            endX = at,
        )
        drawPenLine(gone, old.width)
        drawPenLine(come, shown.width)
    }
}

private data class PenLook(val color: Color, val width: PenWidth)

private fun DrawScope.drawPenLine(brush: Brush, width: PenWidth) {
    // Drawn in a 40x24 box, scaled to fit.
    val x = size.width / 40f
    val y = size.height / 24f
    val path = Path().apply {
        moveTo(3 * x, 17 * y)
        cubicTo(9 * x, 4 * y, 15 * x, 4 * y, 19 * x, 12 * y)
        cubicTo(23 * x, 20 * y, 29 * x, 21 * y, 37 * x, 7 * y)
    }
    drawPath(path, brush, style = Stroke((width.size * LINE_WIDTH_PER_POINT).dp.toPx(), cap = StrokeCap.Round))
}

/** Lines are drawn in proportion to the pen's width. */
private const val LINE_WIDTH_PER_POINT = 2f
private const val ARROW_MILLIS = 400
private const val WIPE_MILLIS = 440

/** How soft the edge of a [WipingPenLine]'s wipe is. */
private val WipeEdge = 8.dp
