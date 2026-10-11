package dev.axu.sheets.reader

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import dev.axu.sheets.ui.Button
import dev.axu.sheets.ui.ButtonSize
import dev.axu.sheets.ui.EaseOut
import dev.axu.sheets.ui.Ink
import dev.axu.sheets.ui.sinks

/**
 * The toolbar's drawing tools: the pen, optionally the ones used before it to switch back to, and
 * the eraser, in a row or (for the side rail) a column. The selected tool stays pressed in.
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
    ToolbarRow(vertical) {
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
        // Room between the pens and the eraser, which isn't one.
        Spacer(Modifier.size(ToolSpacing * 2))
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
 * The pen in use, drawn as a short line, with an arrow beside it; pressing it opens [menu] to change
 * its color and width. While another tool is [selected], touching it only switches back to the pen.
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
    // Pens tried in the menu show at once. Changed with the menu closed, by a recent pen, the line
    // crossfades.
    val line: @Composable () -> Unit = {
        Crossfade(
            pen.color to pen.width,
            animationSpec = if (open) snap() else tween(CROSSFADE_MILLIS, easing = EaseOut),
            label = "pen",
        ) { (color, width) ->
            PenLine(Color(color.argb), width, Modifier.size(LineWidth, LineHeight))
        }
    }
    val arrow: @Composable () -> Unit = {
        Icon(
            painterResource(R.drawable.ic_expand),
            contentDescription = null,
            tint = Ink,
            modifier = Modifier
                .size(18.dp)
                .rotate(turn + if (vertical) -90f else 0f),
        )
    }
    Box(
        (if (vertical) Modifier.width(ButtonSize) else Modifier.height(ButtonSize))
            .opensPenMenu(menu, presses, isSelected = { isSelected }, onSelect = { select() })
            .semantics {
                contentDescription = "Pen: ${pen.color.name}, ${pen.width.name}"
                this.selected = selected
                stateDescription = if (open) "Menu open" else "Menu closed"
            },
        contentAlignment = Alignment.Center,
    ) {
        // Only the pen itself sinks in, pressed or selected, not the arrow beside it, which opens the
        // menu.
        val well = Modifier.sinks(presses, held = selected)
        if (vertical) {
            Column(Modifier.padding(bottom = ArrowMargin), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(well.size(ButtonSize, LineHeight + LineMargin * 2), contentAlignment = Alignment.Center) { line() }
                arrow()
            }
        } else {
            Row(Modifier.padding(end = ArrowMargin), verticalAlignment = Alignment.CenterVertically) {
                Box(well.size(LineWidth + LineMargin * 2, ButtonSize), contentAlignment = Alignment.Center) { line() }
                arrow()
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
    Button(
        presses,
        Modifier
            .size(ButtonSize)
            .toolPress(presses, isSelected = { isSelected }, onSelect = { change(true) }, onClick = { change(false) })
            .semantics {
                contentDescription = "Eraser"
                this.selected = selected
            },
        selected = selected,
    ) {
        Icon(painterResource(R.drawable.ic_eraser), contentDescription = null, Modifier.size(22.dp))
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
private val LineWidth = 40.dp
private val LineHeight = 24.dp
private val LineMargin = 8.dp
private val ArrowMargin = 6.dp
private const val ARROW_MILLIS = 400
private const val CROSSFADE_MILLIS = 240
