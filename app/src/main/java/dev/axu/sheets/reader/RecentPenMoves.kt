package dev.axu.sheets.reader

import dev.axu.sheets.ink.Pen

/** How the recent pens beside the pen button move when they change. */
object RecentPenMoves {
    /** Where the pen leaving a place goes. */
    enum class Leaving {
        /** Into the pen button, now that it's the pen in use. */
        ToPen,

        /** Away from the pen button, out of the recent ones. */
        Away,

        /** On to the next place, where it [Arriving.SlidesIn]. */
        Onward,
    }

    /** Where the pen arriving at a place comes from. */
    enum class Arriving {
        /** From the pen button's side, as the pen just left does. */
        FadesIn,

        /** From the place before, where it went [Leaving.Onward]. */
        SlidesIn,
    }

    /** A place that now shows [new] instead of [old]. */
    data class Move(val old: Pen?, val leaves: Leaving?, val new: Pen, val arrives: Arriving)

    /**
     * What moves at each place when the recent pens go from [before] to [after], with [current] now
     * in use; null where nothing does.
     */
    fun between(before: List<Pen>, after: List<Pen>, current: Pen): List<Move?> = after.mapIndexed { i, new ->
        val old = before.getOrNull(i)
        if (old == new) return@mapIndexed null
        val leaves = when {
            old == null -> null
            after.getOrNull(i + 1) == old -> Leaving.Onward
            old == current -> Leaving.ToPen
            else -> Leaving.Away
        }
        val arrives = if (before.getOrNull(i - 1) == new) Arriving.SlidesIn else Arriving.FadesIn
        Move(old, leaves, new, arrives)
    }
}
