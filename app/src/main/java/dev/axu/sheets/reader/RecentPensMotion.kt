package dev.axu.sheets.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.axu.sheets.ink.Pen
import dev.axu.sheets.reader.RecentPenMoves.Arriving
import dev.axu.sheets.reader.RecentPenMoves.Leaving

/** A pen used before, to switch back to in one tap, getting there as [move] says. */
@Composable
fun RecentPenButton(
    pen: Pen,
    move: RecentPenMoves.Move?,
    elapsed: () -> Float,
    vertical: Boolean,
    onClick: () -> Unit,
) {
    ToolButton(
        selected = false,
        description = "Switch to ${pen.color.name}, ${pen.width.name}",
        gesture = Modifier.toolClick(onClick),
        modifier = Modifier.size(RecentPenSize),
    ) {
        val line = Modifier.size(32.dp, 20.dp)
        val old = move?.old
        if (old != null && move.leaves != Leaving.Onward) {
            val toward = if (move.leaves == Leaving.ToPen) -1f else 1f
            PenLine(
                Color(old.color.argb),
                old.width,
                line.graphicsLayer {
                    fadeOut(elapsed(), toward, vertical)
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
 * comes from there. A pen moving on to the next place slides there instead, throughout.
 */

private fun GraphicsLayerScope.fadeOut(elapsed: Float, toward: Float, vertical: Boolean) {
    val f = FastOutLinearInEasing.transform((elapsed / FADE_OUT_MILLIS).coerceIn(0f, 1f))
    alpha = 1f - f
    shift(toward * ShuffleNudge.toPx() * f, vertical)
}

private fun GraphicsLayerScope.fadeIn(elapsed: Float, vertical: Boolean) {
    val f = LinearOutSlowInEasing.transform(((elapsed - FADE_OUT_MILLIS) / FADE_IN_MILLIS).coerceIn(0f, 1f))
    alpha = f
    shift(-ShuffleNudge.toPx() * (1f - f), vertical)
}

/** Speeds up to halfway while the others fade out, and settles as they fade in. */
private fun GraphicsLayerScope.slideIn(elapsed: Float, vertical: Boolean) {
    // From the place before, side by side in the ToolGroup.
    val half = ((if (vertical) RecentPenSize.height else RecentPenSize.width) + ToolSpacing).toPx() / 2
    val toGo = if (elapsed < FADE_OUT_MILLIS) {
        half * (2f - FastOutLinearInEasing.transform(elapsed / FADE_OUT_MILLIS))
    } else {
        half * (1f - LinearOutSlowInEasing.transform(((elapsed - FADE_OUT_MILLIS) / FADE_IN_MILLIS).coerceAtMost(1f)))
    }
    shift(-toGo, vertical)
}

private fun GraphicsLayerScope.shift(by: Float, vertical: Boolean) {
    if (vertical) translationY = by else translationX = by
}

private const val FADE_OUT_MILLIS = 320
private const val FADE_IN_MILLIS = 400
private const val SHUFFLE_MILLIS = FADE_OUT_MILLIS + FADE_IN_MILLIS
private val ShuffleNudge = 14.dp
private val RecentPenSize = DpSize(48.dp, 44.dp)
