package dev.axu.sheets.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.axu.sheets.R
import dev.axu.sheets.ink.Pen
import dev.axu.sheets.ink.PenSettings
import dev.axu.sheets.ink.PenWidth
import dev.axu.sheets.ink.Pens

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
            for (recent in pen.recents) {
                RecentPenButton(recent) {
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
        gesture = { interactions ->
            opensPenMenu(menu, interactions, isSelected = { isSelected }, onSelect = { select() })
        },
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
private fun RecentPenButton(pen: Pen, onClick: () -> Unit) {
    ToolButton(
        selected = false,
        description = "Switch to ${pen.color.name}, ${pen.width.name}",
        gesture = { toolClick(onClick) },
        modifier = Modifier.size(48.dp, 44.dp),
    ) {
        PenLine(Color(pen.color.argb), pen.width, Modifier.size(32.dp, 20.dp))
    }
}

/** Erases whole strokes the pen touches, until switched off. */
@Composable
private fun EraserButton(selected: Boolean, onClick: () -> Unit) {
    ToolButton(
        selected,
        description = "Eraser",
        gesture = { toolClick(onClick) },
        modifier = Modifier.size(44.dp),
    ) {
        Icon(painterResource(R.drawable.ic_eraser), contentDescription = null, Modifier.size(22.dp))
    }
}

/**
 * Every button among the drawing tools, so that they all look and respond alike. Nothing animates:
 * the selected tool stands out, switching over instantly, and a press shows only while held.
 *
 * What a press does is up to [gesture], which reports the presses that deserve feedback to the
 * interaction source it's given, rather than showing any of its own. Switching tools doesn't: the
 * switch is the response. The gesture mustn't depend on whether the tool is selected, so a press
 * that selects it is still handled by the same gesture when it ends.
 */
@Composable
private fun ToolButton(
    selected: Boolean,
    description: String,
    gesture: Modifier.(MutableInteractionSource) -> Modifier,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    Box(
        modifier
            .drawBehind {
                val corners = CornerRadius(size.minDimension / 2)
                if (selected) {
                    drawRoundRect(SelectedEdgeColor, Offset(0f, SelectedEdgeOffset.toPx()), size, corners)
                    drawRoundRect(Color.White, cornerRadius = corners)
                }
                if (pressed) drawRoundRect(PressedColor, cornerRadius = corners)
            }
            .gesture(interactions)
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
private val PressedColor = Color(0x1A000000)

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
