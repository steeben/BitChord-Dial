package com.music.bitchord.ui.screens

import java.util.Collections
import java.util.WeakHashMap

/**
 * How a playlist, album or artist page brings in what is under its header:
 * one thing after another from the top down, each easing up into place as it
 * fades in, rather than the whole list arriving at once.
 *
 * Everything on the page takes a slot, counted down from the top. Only the
 * first [SLOTS] play, which is more than a screenful; whatever is further
 * down is simply there by the time it is scrolled to.
 *
 * Kept free of Compose so the rules can be tested on their own.
 */
internal object PageEntrance {
    /** The gap between one slot starting and the next. */
    const val STEP_MS = 65L

    /** How long one slot takes to settle. */
    const val DURATION_MS = 520

    /** How many slots from the top play. */
    const val SLOTS = 12

    /**
     * How long after the entrance starts a newly drawn item may still join
     * it. Past this it is drawn in place: it came from a scroll, a filter or
     * a sort, not from the page arriving.
     */
    const val JOIN_WINDOW_MS = 600L

    /** How long slot [slot] waits before it starts. */
    fun delayMs(slot: Int): Long = slot.coerceIn(0, SLOTS - 1) * STEP_MS

    /**
     * Whether an item in [slot], first drawn at [nowMs], plays the entrance
     * that started at [startedAtMs] (null when this showing has none).
     */
    fun plays(slot: Int, startedAtMs: Long?, nowMs: Long): Boolean =
        startedAtMs != null && slot in 0 until SLOTS && nowMs - startedAtMs in 0 until JOIN_WINDOW_MS

    /** From the first slot starting to the last one settling. */
    val totalMs: Long get() = delayMs(SLOTS - 1) + DURATION_MS

    private val shown = FirstShowings()

    /**
     * True the first time it is asked about [page], the thing that stands for
     * one open page for as long as it stays open (its list state). Coming back
     * to the page, refreshing it or recomposing it asks again and gets false,
     * so the entrance plays once per opening.
     */
    fun claim(page: Any): Boolean = shown.claim(page)
}

/**
 * Remembers which owners have been seen, without keeping them alive: an owner
 * that is let go of is forgotten with it.
 */
internal class FirstShowings {
    private val seen: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())

    /** True only the first time [owner] is claimed. */
    @Synchronized
    fun claim(owner: Any): Boolean = seen.add(owner)
}
