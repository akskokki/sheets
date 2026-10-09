package dev.axu.sheets.reader

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Tools on a shared background, the selected one on a chip that slides over to the next one chosen. */
@Composable
fun ToolGroup(vertical: Boolean, content: @Composable () -> Unit) {
    val selection = remember { ToolSelection() }
    val chip = selection.target?.let {
        animateRectAsState(it, tween(SELECT_MILLIS, easing = SelectEasing), label = "chip")
    }
    val modifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(ToolGroupColor)
        .onPlaced { selection.group = it }
        .drawBehind {
            // Until the next composition starts animating it, the chip is where the tools were placed.
            val rect = chip?.value ?: selection.target ?: return@drawBehind
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
 * Every button among the drawing tools, so that they all look and respond alike: the selected tool
 * sits on its group's chip, and a press shows nothing, as switching tools is the response.
 *
 * What a press does is up to [gesture]. It mustn't depend on whether the tool is selected, so a
 * press that selects it is still handled by the same gesture when it ends.
 */
@Composable
fun ToolButton(
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
            .onPlaced {
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
fun Modifier.toolClick(onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)

/** Between tools in a [ToolGroup]. */
val ToolSpacing = 4.dp
private val ToolGroupColor = Color(0xFFEEF0F3)
private val SelectedEdgeColor = Color(0x2E000000)
private val SelectedEdgeOffset = 1.dp
private const val SELECT_MILLIS = 320
private val SelectEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
