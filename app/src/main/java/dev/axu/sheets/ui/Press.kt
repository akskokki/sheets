package dev.axu.sheets.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The feedback for everything tappable: pressed, it sinks into the surface ([Recess]), all the way
 * however brief the tap, then slowly rises back. Before Android 13, which can't run the shader, a
 * soft fill shows instead. Pen hovers do nothing, as the pen hovers whenever it's near the screen.
 */
object Press : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        SinkNode(interactionSource, held = false)

    override fun equals(other: Any?) = other === this

    override fun hashCode() = System.identityHashCode(this)
}

/** Sinks this in as [Press] does for what [presses] reports, and keeps it sunk while [held], like a selected tool. */
fun Modifier.sinks(presses: InteractionSource?, held: Boolean): Modifier = this then SinkElement(presses, held)

private data class SinkElement(val presses: InteractionSource?, val held: Boolean) : ModifierNodeElement<SinkNode>() {
    override fun create() = SinkNode(presses, held)

    override fun update(node: SinkNode) = node.update(presses, held)
}

private class SinkNode(private var presses: InteractionSource?, private var held: Boolean) :
    Modifier.Node(),
    DrawModifierNode {
    private val pressed = Animatable(0f)
    private val holding = Animatable(if (held) 1f else 0f)
    private val recess = recess()
    private var layer: GraphicsLayer? = null
    private var following: Job? = null

    override fun onAttach() = follow()

    fun update(presses: InteractionSource?, held: Boolean) {
        if (held != this.held) {
            this.held = held
            coroutineScope.launch { holding.animateTo(if (held) 1f else 0f, if (held) SinkIn else RiseOut) }
        }
        if (presses != this.presses) {
            this.presses = presses
            following?.cancel()
            follow()
        }
    }

    private fun follow() {
        val presses = presses ?: return
        following = coroutineScope.launch {
            var sinking: Job? = null
            presses.interactions.collect {
                when (it) {
                    is PressInteraction.Press -> sinking = launch { pressed.animateTo(1f, SinkIn) }

                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        val sunk = sinking
                        launch {
                            sunk?.join()
                            pressed.animateTo(0f, RiseOut)
                        }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val depth = max(pressed.value, holding.value)
        if (depth == 0f) return drawContent()
        if (recess == null) {
            drawRoundRect(PressColor, cornerRadius = CornerRadius(PressCorner.toPx()), alpha = depth)
            return drawContent()
        }
        // The effect can't draw past its layer, so the layer reaches past the edges, for marks that
        // move beyond them, like a recent pen sliding in.
        val room = Overflow.toPx().roundToInt()
        val layer = layer ?: requireGraphicsContext().createGraphicsLayer().also { layer = it }
        layer.topLeft = IntOffset(-room, -room)
        layer.renderEffect = recess.effect(size, room.toFloat(), density, depth)
        layer.record(IntSize(size.width.roundToInt() + 2 * room, size.height.roundToInt() + 2 * room)) {
            translate(room.toFloat(), room.toFloat()) { this@draw.drawContent() }
        }
        drawLayer(layer)
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
    }
}

/** The rounded corners of what's pressed in, and of what's shaped like it. */
val PressCorner = 12.dp
private val Overflow = 64.dp
private const val SINK_MILLIS = 80
private const val RISE_MILLIS = 1200
private val SinkIn = tween<Float>(SINK_MILLIS, easing = EaseOut)
private val RiseOut = tween<Float>(RISE_MILLIS, easing = EaseOut)

/** Behind whatever is pressed in before Android 13, and the menu choice a drag is over. */
val PressColor = Color(0xFFE9ECF0)
