package dev.axu.sheets.ink

/** A color and width to write with. */
data class Pen(val color: PenColor, val width: PenWidth)

/** The pens used before the current one, most recent first, kept within reach to switch back to. */
object RecentPens {
    const val COUNT = 2

    /** [recents] after switching from [previous] to [current]. */
    fun afterSwitch(previous: Pen, current: Pen, recents: List<Pen>): List<Pen> =
        (listOf(previous) + recents).filter { it != current }.distinct().take(COUNT)
}
