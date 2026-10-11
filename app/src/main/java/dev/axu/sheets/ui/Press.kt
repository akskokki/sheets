package dev.axu.sheets.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The feedback for everything tappable: pressed, it shows pressed in, a soft fill behind it, in full
 * however brief the tap. Pen hovers light nothing up, as the pen hovers whenever it's near the
 * screen.
 */
object Press : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource)

    override fun equals(other: Any?) = other === this

    override fun hashCode() = System.identityHashCode(this)
}

private class PressNode(private val presses: InteractionSource) :
    Modifier.Node(),
    DrawModifierNode {
    private val depth = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var sinking: Job? = null
            presses.interactions.collect {
                when (it) {
                    is PressInteraction.Press -> sinking = launch { depth.animateTo(1f, PressIn) }

                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        val sunk = sinking
                        launch {
                            sunk?.join()
                            depth.animateTo(0f, PressOut)
                        }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawPressed(depth.value)
        drawContent()
    }
}

/** Shows [content] pressed in for as long as it's [held], like a selected tool. */
@Composable
fun Held(held: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val depth by animateFloatAsState(if (held) 1f else 0f, if (held) PressIn else PressOut, label = "held")
    Box(modifier.drawBehind { drawPressed(depth) }, contentAlignment = Alignment.Center) { content() }
}

private fun DrawScope.drawPressed(depth: Float) {
    if (depth > 0f) drawRoundRect(PressColor, cornerRadius = CornerRadius(PressCorner.toPx()), alpha = depth)
}

/** The rounded corners of what's pressed in, and of what's shaped like it. */
val PressCorner = 12.dp
private const val PRESS_MILLIS = 80
private const val RELEASE_MILLIS = 1200
private val PressIn = tween<Float>(PRESS_MILLIS, easing = EaseOut)
private val PressOut = tween<Float>(RELEASE_MILLIS, easing = EaseOut)

/** Behind whatever is pressed in, and the menu choice a drag is over. */
val PressColor = Color(0xFFE9ECF0)
