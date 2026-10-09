package dev.axu.sheets.reader

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.axu.sheets.R
import dev.axu.sheets.ink.Pen
import dev.axu.sheets.ink.PenSettings
import dev.axu.sheets.ink.PenWidth
import dev.axu.sheets.ink.Pens

/**
 * The toolbar's drawing tools: the pen, and optionally the ones used before it to switch back to, in
 * a row or (for the side rail) a column.
 */
@Composable
fun DrawingTools(pen: PenSettings, menu: PenMenuState, showRecents: Boolean, vertical: Boolean) {
    ToolGroup(vertical) {
        PenButton(pen, menu, vertical)
        if (showRecents) {
            for (recent in pen.recents) {
                RecentPenButton(recent) {
                    menu.close()
                    pen.switchTo(recent)
                }
            }
        }
    }
}

/** Tools on a shared background. */
@Composable
private fun ToolGroup(vertical: Boolean, content: @Composable () -> Unit) {
    val modifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(ToolGroupColor)
        .padding(4.dp)
    if (vertical) {
        Column(modifier, Arrangement.spacedBy(4.dp), Alignment.CenterHorizontally) { content() }
    } else {
        Row(modifier, Arrangement.spacedBy(4.dp), Alignment.CenterVertically) { content() }
    }
}

/** The pen in use, drawn as a short line; tapping it opens [menu] to change its color and width. */
@Composable
private fun PenButton(pen: PenSettings, menu: PenMenuState, vertical: Boolean) {
    // The arrow points to where the menu opens, and back once it's open.
    val turn by animateFloatAsState(if (menu.isOpen) 180f else 0f, label = "arrow")
    val modifier = Modifier
        .shadow(1.dp, CircleShape)
        .clip(RoundedCornerShape(50))
        .background(Color.White)
        .opensPenMenu(menu)
        .semantics { contentDescription = "Pen: ${pen.color.name}, ${pen.width.name}" }
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
    if (vertical) {
        Column(modifier.size(48.dp, 56.dp), Arrangement.Center, Alignment.CenterHorizontally) { content() }
    } else {
        Row(
            modifier.height(44.dp).padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

/** A pen used before, to switch back to in one tap. */
@Composable
private fun RecentPenButton(pen: Pen, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp, 44.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Switch to ${pen.color.name}, ${pen.width.name}" },
        contentAlignment = Alignment.Center,
    ) {
        PenLine(Color(pen.color.argb), pen.width, Modifier.size(32.dp, 20.dp))
    }
}

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
