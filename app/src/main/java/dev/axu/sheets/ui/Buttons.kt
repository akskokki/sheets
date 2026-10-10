package dev.axu.sheets.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/** Does [onClick] when released, unless the press slides off first. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    selection: Color = Ink,
    content: @Composable () -> Unit,
) {
    val presses = remember { MutableInteractionSource() }
    Button(
        presses,
        modifier.clickable(presses, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        enabled,
        selected,
        selection,
        content = content,
    )
}

/**
 * Every button in the app: a mark, such as an icon, a line or a word, with nothing around it. What
 * pressing it does is up to a gesture in [modifier], which reports to [presses], and the theme's
 * [Press] shows it. When [selected], a short bar in [selection] sits under it, centered
 * on [barCenter] from its start if that's given, to line up with its mark rather than with all of
 * it. Unless [enabled], its mark fades, the only thing in the app that does.
 */
@Composable
fun Button(
    presses: InteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    selection: Color = Ink,
    barCenter: Dp = Dp.Unspecified,
    content: @Composable () -> Unit,
) {
    val alpha by animateFloatAsState(
        if (enabled) 1f else DISABLED_ALPHA,
        tween(SETTLE_MILLIS, easing = EaseOut),
        label = "enabled",
    )
    val bar by animateFloatAsState(
        if (selected) 1f else 0f,
        tween(SELECTION_MILLIS, easing = EmphasizedDecelerate),
        label = "selected",
    )
    val barColor by animateColorAsState(selection, tween(SETTLE_MILLIS, easing = EaseOut), label = "selection")
    Box(
        modifier
            .indication(presses, LocalIndication.current)
            .drawWithContent {
                drawContent()
                if (bar == 0f) return@drawWithContent
                // It grows from the middle as it fades in.
                val width = SelectionBarWidth.toPx() * (MIN_BAR_SCALE + (1f - MIN_BAR_SCALE) * bar)
                val height = SelectionBarHeight.toPx()
                drawRoundRect(
                    barColor,
                    topLeft = Offset(
                        (if (barCenter.isSpecified) barCenter.toPx() else size.width / 2) - width / 2,
                        size.height - SelectionBarInset.toPx() - height,
                    ),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(height / 2),
                    alpha = (bar * 2).coerceAtMost(1f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { this.alpha = alpha }, contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides Ink, content = content)
        }
    }
}

/** A button showing [icon], described by [label]. */
@Composable
fun IconButton(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick, modifier.size(ButtonSize), enabled) {
        Icon(icon, contentDescription = label, Modifier.size(IconSize))
    }
}

/** A button reading [text], optionally after an [icon], in the accent color that marks words to tap. */
@Composable
fun TextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Painter? = null) {
    val accent = MaterialTheme.colorScheme.primary
    Button(onClick, modifier.heightIn(min = ButtonSize)) {
        CompositionLocalProvider(LocalContentColor provides accent) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Across a button, and the least a touch target is. */
val ButtonSize = 48.dp
private val IconSize = 22.dp
private val SelectionBarWidth = 20.dp
private val SelectionBarHeight = 3.dp
private val SelectionBarInset = 3.dp
private const val MIN_BAR_SCALE = 0.3f
private const val DISABLED_ALPHA = 0.38f
private const val SETTLE_MILLIS = 400
private const val SELECTION_MILLIS = 440

/** Icons and lines on buttons, and the eraser's selection bar. */
val Ink = Color(0xFF1F1F1F)

/** CSS's ease-out, for buttons and what they move. */
val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EmphasizedDecelerate = CubicBezierEasing(0.2f, 0f, 0f, 1f)
