package com.music.bitchord.ui.screens

/**
 * How the search page brings in a list that has just arrived: one row after
 * another from the top down, each easing up into place as it fades in, the
 * way a playlist, album or artist page brings in what is under its header.
 *
 * A [Run] is one arrival: the results of a search, a tab or a source picked,
 * the rows under the keys while something is typed. Only rows that are new to
 * the list join it. A row that was there already stays where it is (or slides
 * to its new place), and one that is scrolled to later is simply there.
 *
 * Kept free of Compose so the rules can be tested on their own.
 */
object SearchEntrance {
    /**
     * How quickly an arrival plays.
     *
     * @property stepMs the gap between one row setting off and the next
     * @property durationMs how long one row takes to settle
     * @property riseDp how far below its place a row starts
     */
    enum class Pace(val stepMs: Long, val durationMs: Int, val riseDp: Float) {
        /** A list that was asked for: search results, a tab, a source. */
        ARRIVE(stepMs = 45, durationMs = 420, riseDp = 18f),

        /** Rows that change with each key: they must not keep the typing waiting. */
        TYPING(stepMs = 25, durationMs = 240, riseDp = 10f),
    }

    /** How many rows of one arrival play: more than a screenful. */
    const val SLOTS = 12

    /**
     * How long after an arrival a newly drawn row may still join it. Past
     * this it came from a scroll, not from the list arriving.
     */
    const val JOIN_WINDOW_MS = 600L

    /** One arrival, begun at [startedAtMs], and when each of its rows sets off. */
    class Run(val startedAtMs: Long, val pace: Pace) {
        private var joined = 0

        /**
         * When a row first drawn at [nowMs] sets off, or null when it has no
         * part in this arrival. Rows are drawn from the top down, so the
         * order they ask in is the order they come in: each one [Pace.stepMs]
         * after the one before, and never before it was drawn.
         */
        @Synchronized
        fun join(nowMs: Long): Long? {
            if (joined >= SLOTS || nowMs - startedAtMs !in 0 until JOIN_WINDOW_MS) return null
            return maxOf(nowMs, startedAtMs + joined * pace.stepMs).also { joined++ }
        }
    }

    /**
     * How far through its [durationMs] a row that set off at [startAtMs] is
     * at [nowMs], from 0 to 1 before any easing.
     */
    fun fraction(startAtMs: Long, durationMs: Int, nowMs: Long): Float =
        ((nowMs - startAtMs).toFloat() / durationMs).coerceIn(0f, 1f)

    /**
     * The key of each of [ids]' rows in a list: the id itself, and the id
     * with a count after it for a second and later row of the same id. A row
     * keeps its key when the rows above it come and go, so the list can slide
     * it to its new place; a key made from the row's index would make it a
     * new row each time.
     */
    fun rowKeys(prefix: String, ids: List<String>): List<String> {
        val seen = HashMap<String, Int>()
        return ids.map { id ->
            val count = (seen[id] ?: 0) + 1
            seen[id] = count
            if (count == 1) "$prefix$id" else "$prefix$id#$count"
        }
    }
}
