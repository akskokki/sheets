package dev.axu.sheets.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.axu.sheets.R
import dev.axu.sheets.ink.PenSettings
import dev.axu.sheets.ink.PenWidth

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
                RecentPenButton(recent, motion.moves.getOrNull(i), { motion.elapsed.value }, vertical) {
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
    val open = menu.isOpen
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "arrow")
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
        modifier = (if (vertical) Modifier.size(48.dp, 56.dp) else Modifier.height(44.dp))
            .semantics { stateDescription = if (open) "Menu open" else "Menu closed" },
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

private val SeparatorColor = Color(0xFFD5D8DD)
