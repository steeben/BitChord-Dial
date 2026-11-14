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
 * It plays once per opening and not at all with Reduce animations on; the
 * page checks that setting before asking for an entrance.
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

    /**
     * One playing of the entrance, begun at [startedAtMs], and when each of
     * its slots sets off.
     *
     * A slot keeps the time it was given however many times it is asked: the
     * row standing in it can be replaced while it is still coming in (a
     * Spotify track swapped for its YouTube Music match is a new row to the
     * list), and what replaces it carries on from where the first had got to
     * rather than starting over.
     *
     * The turns are counted from the first thing drawn, not from the top of
     * the page: on a page scrolled part of the way down before its songs
     * landed, the rows in view came in after a wait as long as if every row
     * above them were in view too, and the rows further down, past the last
     * slot, were there before them. That left a gap with rows under it.
     */
    class Run(val startedAtMs: Long) {
        private val starts = HashMap<Int, Long>()

        /** The slot of the first thing this entrance drew: the top of what it brings in. */
        private var top: Int? = null

        @Volatile
        private var hurriedAtMs: Long? = null

        /**
         * When the item in [slot], drawn at [nowMs], sets off, or null when
         * it has no part in this entrance and is simply there (see [plays]).
         * One first drawn after a scroll ([hurry]) is simply there.
         */
        @Synchronized
        fun startOf(slot: Int, nowMs: Long): Long? {
            starts[slot]?.let { return it }
            if (hurriedAtMs != null) return null
            if (!plays(0, startedAtMs, nowMs)) return null
            val first = top ?: slot.also { top = it }
            // One above the first (not seen so far) has no wait.
            val place = (slot - first).coerceAtLeast(0)
            if (!plays(place, startedAtMs, nowMs)) return null
            return (nowMs + delayMs(place)).also { starts[slot] = it }
        }

        /**
         * The list was scrolled at [nowMs], with the entrance still going.
         * Nothing waits for its turn any longer: what was waiting sets off
         * now (see [setOff]), and what the scroll brings into view is simply
         * there. Left to their turns, rows the scroll had carried up the
         * page were still to come in while the rows under them were there.
         */
        fun hurry(nowMs: Long) {
            if (hurriedAtMs == null) hurriedAtMs = nowMs
        }

        /** When a slot given [startAtMs] sets off, now that a scroll may have hurried it. */
        fun setOff(startAtMs: Long): Long {
            val hurried = hurriedAtMs
            return if (hurried != null && hurried < startAtMs) hurried else startAtMs
        }
    }

    /**
     * How far through its [DURATION_MS] a slot that sets off at [startAtMs]
     * is at [nowMs], from 0 to 1 before any easing. A slot with no start has
     * no part in the entrance and is all the way in.
     */
    fun fraction(startAtMs: Long?, nowMs: Long): Float {
        if (startAtMs == null) return 1f
        return ((nowMs - startAtMs).toFloat() / DURATION_MS).coerceIn(0f, 1f)
    }

    /** From the first slot starting to the last one settling. */
    val totalMs: Long get() = delayMs(SLOTS - 1) + DURATION_MS

    /** The most song rows a loading page holds a place for: about a screenful. */
    const val PLACEHOLDER_ROWS_MAX = 8

    /** How many it holds when nothing says how many songs are coming. */
    const val PLACEHOLDER_ROWS_UNKNOWN = 3

    /**
     * How many placeholder rows a loading playlist or album shows, given how
     * many songs it is [known] to hold (null when nothing says). Never none:
     * a page that is loading always shows that something is coming.
     */
    fun placeholderRows(known: Int?): Int =
        known?.coerceIn(1, PLACEHOLDER_ROWS_MAX) ?: PLACEHOLDER_ROWS_UNKNOWN

    private val countInSubtitle =
        Regex("""(\d[\d,.\u00A0 ]*)\s*(?:songs?|tracks?|videos?|episodes?)\b""", RegexOption.IGNORE_CASE)

    /**
     * The song count a page's subtitle states, as the card it was opened from
     * worded it ("Playlist • 12 songs"), or 1 for something billed as a
     * single. Null when the subtitle says neither.
     */
    fun trackCountIn(subtitle: String): Int? {
        countInSubtitle.find(subtitle)?.let { match ->
            match.groupValues[1].filter(Char::isDigit).toIntOrNull()?.let { return it }
        }
        val single = subtitle.split("•", "·").any { it.trim().equals("single", ignoreCase = true) }
        return if (single) 1 else null
    }

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
