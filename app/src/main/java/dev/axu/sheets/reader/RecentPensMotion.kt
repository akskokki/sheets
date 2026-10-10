package dev.axu.sheets.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.axu.sheets.ink.Pen
import dev.axu.sheets.reader.RecentPenMoves.Arriving
import dev.axu.sheets.reader.RecentPenMoves.Leaving
import dev.axu.sheets.ui.Button
import dev.axu.sheets.ui.ButtonSize

/** A pen used before, to switch back to in one tap, getting there as [move] says. */
@Composable
fun RecentPenButton(
    pen: Pen,
    move: RecentPenMoves.Move?,
    elapsed: () -> Float,
    vertical: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick,
        Modifier
            .then(
                if (vertical) {
                    Modifier.size(
                        ButtonSize,
                        RecentPenLength,
                    )
                } else {
                    Modifier.size(RecentPenLength, ButtonSize)
                },
            )
            .semantics { contentDescription = "Switch to ${pen.color.name}, ${pen.width.name}" },
    ) {
        val line = Modifier.size(32.dp, 20.dp)
        val old = move?.old
        if (old != null && move.leaves != Leaving.Onward) {
            // Pushed out by the pen sliding in, it goes on ahead of it, as if one row moved along.
            val pushed = move.leaves == Leaving.Away && move.arrives == Arriving.SlidesIn
            val toward = if (move.leaves == Leaving.ToPen) -1f else 1f
            PenLine(
                Color(old.color.argb),
                old.width,
                line.graphicsLayer {
                    if (pushed) slideOut(elapsed(), vertical) else fadeOut(elapsed(), toward, vertical)
                },
            )
        }
        PenLine(
            Color(pen.color.argb),
            pen.width,
            when (move?.arrives) {
                null -> line
                Arriving.FadesIn -> line.graphicsLayer { fadeIn(elapsed(), vertical) }
                Arriving.SlidesIn -> line.graphicsLayer { slideIn(elapsed(), vertical) }
            },
        )
    }
}

/** How the recent pens moved when last switched, and how long ago in milliseconds. */
class RecentPensMotion(val moves: List<RecentPenMoves.Move?>, val elapsed: Animatable<Float, AnimationVector1D>)

/** Plays [RecentPenMoves] whenever [recents] change. */
@Composable
fun rememberRecentPensMotion(recents: List<Pen>, current: Pen): RecentPensMotion {
    val shown = remember { arrayOf(recents) }
    val motion = remember(recents) {
        RecentPensMotion(RecentPenMoves.between(shown[0], recents, current), Animatable(0f))
    }
    SideEffect { shown[0] = recents }
    LaunchedEffect(motion) {
        motion.elapsed.animateTo(SHUFFLE_MILLIS.toFloat(), tween(SHUFFLE_MILLIS, easing = LinearEasing))
    }
    return motion
}

/*
 * Recent pens that change place fade out together, nudged the way they go: toward the pen if now in
 * use, otherwise away. Then the new ones fade in, nudged from the pen's side, as the one just left
 * comes from there. A pen moving on to the next place slides there instead, throughout, and one it
 * pushes out slides on with it.
 */

private fun GraphicsLayerScope.fadeOut(elapsed: Float, toward: Float, vertical: Boolean) {
    val f = fadingOut(elapsed)
    alpha = 1f - f
    shift(toward * ShuffleNudge.toPx() * f, vertical)
}

/** Moves on in step with the pen sliding into its place, fading as the others do. */
private fun GraphicsLayerScope.slideOut(elapsed: Float, vertical: Boolean) {
    alpha = 1f - fadingOut(elapsed)
    shift(slid(elapsed), vertical)
}

private fun GraphicsLayerScope.fadeIn(elapsed: Float, vertical: Boolean) {
    val f = LinearOutSlowInEasing.transform(((elapsed - FADE_OUT_MILLIS) / FADE_IN_MILLIS).coerceIn(0f, 1f))
    alpha = f
    shift(-ShuffleNudge.toPx() * (1f - f), vertical)
}

/** From the place before, in one smooth sweep over the whole shuffle. */
private fun GraphicsLayerScope.slideIn(elapsed: Float, vertical: Boolean) {
    shift(slid(elapsed) - PlaceToPlace.toPx(), vertical)
}

private fun fadingOut(elapsed: Float) = FastOutLinearInEasing.transform((elapsed / FADE_OUT_MILLIS).coerceIn(0f, 1f))

/** How far a sliding pen has gone toward the next place. */
private fun GraphicsLayerScope.slid(elapsed: Float) =
    PlaceToPlace.toPx() * FastOutSlowInEasing.transform((elapsed / SHUFFLE_MILLIS).coerceIn(0f, 1f))

private fun GraphicsLayerScope.shift(by: Float, vertical: Boolean) {
    if (vertical) translationY = by else translationX = by
}

private const val FADE_OUT_MILLIS = 320
private const val FADE_IN_MILLIS = 400
private const val SHUFFLE_MILLIS = FADE_OUT_MILLIS + FADE_IN_MILLIS
private val ShuffleNudge = 14.dp

/** Along the row of tools; across it, a recent pen is as wide as every button. */
private val RecentPenLength = 52.dp

/** From one recent pen's place to the next, side by side in their row. */
private val PlaceToPlace = RecentPenLength + ToolSpacing
