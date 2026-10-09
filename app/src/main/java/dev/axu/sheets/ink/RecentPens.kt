package dev.axu.sheets.ink

/** A color and width to write with. */
data class Pen(val color: PenColor, val width: PenWidth)

/** The pens used before the current one, most recent first, kept within reach to switch back to. */
object RecentPens {
    const val COUNT = 2

    /** [recents] after switching from [previous] to [current]. */
    fun afterSwitch(previous: Pen, current: Pen, recents: List<Pen>): List<Pen> =
        (listOf(previous) + recents).filter { it != current }.distinct().take(COUNT)

    /** How the pen leaving a place among the recent ones goes. */
    enum class Leaving {
        /** Into the pen button, now that it's the pen in use. */
        ToPen,

        /** Away from the pen button, out of the recent ones. */
        Away,

        /** On to the next place, where it [Arriving.SlidesIn]. */
        Onward,
    }

    /** How the pen arriving at a place among the recent ones comes. */
    enum class Arriving {
        /** From the pen button's side, as the pen just left does. */
        FadesIn,

        /** From the place before, where it went [Leaving.Onward]. */
        SlidesIn,
    }

    /** A place among the recent ones that now shows [arriving] instead of [leaving]. */
    data class Change(val leaving: Pen?, val left: Leaving?, val arriving: Pen, val arrived: Arriving)

    /**
     * What changes at each place when the recent pens go from [before] to [after], with [current]
     * now in use; null where nothing does.
     */
    fun changes(before: List<Pen>, after: List<Pen>, current: Pen): List<Change?> = after.mapIndexed { i, new ->
        val old = before.getOrNull(i)
        if (old == new) return@mapIndexed null
        val left = when {
            old == null -> null
            after.getOrNull(i + 1) == old -> Leaving.Onward
            old == current -> Leaving.ToPen
            else -> Leaving.Away
        }
        val arrived = if (before.getOrNull(i - 1) == new) Arriving.SlidesIn else Arriving.FadesIn
        Change(old, left, new, arrived)
    }
}
