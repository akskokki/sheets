package dev.axu.sheets.reader

import androidx.compose.animation.core.Animatable
import androidx.ink.strokes.Stroke

/** Ink that was just scratched out or tidied up, faded out on screen so it's clear what went. */
class Erasure(val page: Int, val strokes: List<Stroke>) {
    val alpha = Animatable(1f)
}
