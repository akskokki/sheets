package dev.axu.sheets.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The feedback for everything tappable. Pressed, it sinks into the surface, all the way however
 * brief the tap, and a wave spreads from where the finger landed ([LiquidPress]). Before Android 13,
 * which can't run the shader, a soft fill shows instead. Pen hovers light nothing up, as the pen
 * hovers whenever it's near the screen.
 */
object Press : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource)

    override fun equals(other: Any?) = other === this

    override fun hashCode() = System.identityHashCode(this)
}

/** Draws a press as an effect over what's pressed, where the system can run shaders. */
internal interface PressShader {
    /**
     * The effect for something of [size] px, drawn [room] px in from its layer's edge, [depth] pressed
     * in, with [waves] spreading.
     */
    fun effect(size: Size, room: Float, density: Float, depth: Float, waves: List<Wave>): RenderEffect
}

/** A wave spreading from [origin], in px from the pressed thing's corner, [age] ms ago. */
internal class Wave(val origin: Offset) {
    val age = Animatable(0f)
}

private class PressNode(private val presses: InteractionSource) :
    Modifier.Node(),
    DrawModifierNode {
    /** 0 at rest, 1 pressed all the way in. */
    private val depth = Animatable(0f)
    private val waves = mutableStateListOf<Wave>()
    private val shader = liquidPress()
    private var layer: GraphicsLayer? = null

    override fun onAttach() {
        coroutineScope.launch {
            var sinking: Job? = null
            presses.interactions.collect {
                when (it) {
                    is PressInteraction.Press -> {
                        sinking = launch { depth.animateTo(1f, tween(PRESS_MILLIS, easing = EaseOut)) }
                        if (shader != null) spread(Wave(it.pressPosition))
                    }

                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        val sunk = sinking
                        launch {
                            sunk?.join()
                            depth.animateTo(0f, tween(RELEASE_MILLIS, easing = EaseOut))
                        }
                    }
                }
            }
        }
    }

    private fun spread(wave: Wave) {
        if (waves.size == MAX_WAVES) waves.removeAt(0)
        waves += wave
        coroutineScope.launch {
            wave.age.animateTo(WAVE_MILLIS.toFloat(), tween(WAVE_MILLIS, easing = LinearEasing))
            waves -= wave
        }
    }

    override fun ContentDrawScope.draw() {
        val depth = depth.value
        if (depth == 0f && waves.isEmpty()) return drawContent()
        if (shader == null) {
            drawRoundRect(PressColor, cornerRadius = CornerRadius(PressCorner.toPx()), alpha = depth)
            return drawContent()
        }
        // The effect can't draw past its layer, so the layer reaches past the edges, for marks that
        // move beyond them, like a recent pen sliding in.
        val room = Overflow.toPx().roundToInt()
        val layer = layer ?: requireGraphicsContext().createGraphicsLayer().also { layer = it }
        layer.topLeft = IntOffset(-room, -room)
        layer.renderEffect = shader.effect(size, room.toFloat(), density, depth, waves)
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

/** The rounded corners of what's pressed, and of the waves' reach. */
internal val PressCorner = 12.dp
private val Overflow = 64.dp
private const val PRESS_MILLIS = 80
private const val RELEASE_MILLIS = 1200

/** A new press while this many waves still spread takes the place of the oldest. */
internal const val MAX_WAVES = 2

/** Behind whatever is pressed before Android 13, and the menu choice a drag is over. */
val PressColor = Color(0xFFE9ECF0)
