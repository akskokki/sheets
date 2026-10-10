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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.axu.sheets.R
import dev.axu.sheets.ink.PenSettings
import dev.axu.sheets.ink.PenWidth
import dev.axu.sheets.ui.Button
import dev.axu.sheets.ui.ButtonSize
import dev.axu.sheets.ui.EaseOut

/**
 * The toolbar's drawing tools: the pen, optionally the ones used before it to switch back to, and
 * the eraser, in a row or (for the side rail) a column. A bar marks the selected tool.
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
 * The pen in use, drawn as a short line, its bar in its ink; pressing it opens [menu] to change its
 * color and width. While another tool is [selected], touching it only switches back to the pen.
 */
@Composable
private fun PenButton(
    pen: PenSettings,
    menu: PenMenuState,
    selected: Boolean,
    onSelect: () -> Unit,
    vertical: Boolean,
) {
    // The arrow points to where the menu opens, and back once it's open. It shows only while
    // pressing opens the menu: while erasing, pressing picks the pen up instead.
    val open = menu.isOpen
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(ARROW_MILLIS, easing = EaseOut), label = "arrow")
    val arrow by animateFloatAsState(
        if (selected) 1f else 0f,
        tween(ARROW_FADE_MILLIS, easing = EaseOut),
        label = "arrow",
    )
    val isSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val presses = remember { MutableInteractionSource() }
    val ink = Color(pen.color.argb)
    val content: @Composable () -> Unit = {
        // Pens tried in the menu show at once. Changed with the menu closed, by a recent pen, the line
        // crossfades.
        Crossfade(
            pen.color to pen.width,
            animationSpec = if (open) snap() else tween(CROSSFADE_MILLIS, easing = EaseOut),
            label = "pen",
        ) { (color, width) ->
            PenLine(Color(color.argb), width, Modifier.size(LineWidth, 24.dp))
        }
        Icon(
            painterResource(R.drawable.ic_expand),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .rotate(turn + if (vertical) -90f else 0f)
                .graphicsLayer { alpha = arrow },
        )
    }
    Button(
        presses,
        (if (vertical) Modifier.size(ButtonSize, 60.dp) else Modifier.height(ButtonSize))
            .opensPenMenu(menu, presses, isSelected = { isSelected }, onSelect = { select() })
            .semantics {
                contentDescription = "Pen: ${pen.color.name}, ${pen.width.name}"
                this.selected = selected
                stateDescription = if (open) "Menu open" else "Menu closed"
            },
        selected = selected,
        selection = ink,
        // Under the line, with the arrow off to its side; in the rail, the line is centered anyway.
        barCenter = if (vertical) Dp.Unspecified else LineStart + LineWidth / 2,
    ) {
        if (vertical) {
            Column(Modifier.padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
        } else {
            Row(Modifier.padding(start = LineStart, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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
private val LineStart = 12.dp
private val LineWidth = 40.dp
private const val ARROW_MILLIS = 400
private const val ARROW_FADE_MILLIS = 320
private const val CROSSFADE_MILLIS = 240
