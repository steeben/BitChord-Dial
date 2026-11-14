package com.music.bitchord.ui.performance

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How evenly the frames of one stretch came, and how long they took.
 *
 * [FrameTally] counts the frames that were late. A screen can have almost
 * none of those and still not look steady: the eye follows a thing that moves
 * at 165 frames a second closely enough to see one refresh of the display go
 * by with no new frame. So this counts the time from each frame to the next,
 * against the stretch's own beat, which is the display's refresh while the
 * screen moves. A frame one beat after the one before is in step. One that
 * came two beats after left a refresh of the display with nothing new on it,
 * whatever the reason: the app was late to start the frame, or did not ask
 * for one.
 *
 * The frame times beside it say how much room there was: a median of 4 ms in
 * a budget of 10 is one thing, a 95th percentile of 9.8 is a screen that is
 * late whenever anything else happens.
 *
 * What this cannot see is a frame that came on time with the wrong thing in
 * it: a list that moved 7 ms of its fling in a 6 ms frame. Only a recording
 * of the screen shows that.
 */
class FramePacing {
    private val gaps = LongArray(CAPACITY)
    private var gapCount = 0
    private val totals = LongArray(CAPACITY)
    private var totalCount = 0
    private var lastVsyncNanos = 0L

    /**
     * A frame: [vsyncNanos] is the refresh of the display it was drawn for,
     * [totalNanos] how long it took from then to the screen.
     */
    fun add(vsyncNanos: Long, totalNanos: Long) {
        if (totalCount < CAPACITY) totals[totalCount++] = totalNanos
        val gap = vsyncNanos - lastVsyncNanos
        val first = lastVsyncNanos == 0L
        lastVsyncNanos = vsyncNanos
        // Longer than this and the screen was still in between. A frame that
        // itself took that long has a line of its own in the log.
        if (first || gap <= 0L || gap > REST_NANOS) return
        if (gapCount < CAPACITY) gaps[gapCount++] = gap
    }

    /** Starts a new stretch. The last frame is kept, so the first gap of the next one counts. */
    fun reset() {
        gapCount = 0
        totalCount = 0
    }

    /** One line for the log, or null for a stretch too short to have a beat. */
    fun summary(): String? {
        if (gapCount < MIN_GAPS) return null
        val beat = beat(need = maxOf(MIN_COMMON, gapCount / COMMON_SHARE)) ?: return null
        var inStep = 0
        var afterOne = 0
        var afterTwo = 0
        var afterMore = 0
        val off = LongArray(gapCount)
        var offCount = 0
        for (i in 0 until gapCount) {
            val beats = gaps[i].toDouble() / beat
            val whole = beats.roundToInt()
            when {
                whole < 1 || abs(beats - whole) > ON_BEAT -> off[offCount++] = gaps[i]
                whole == 1 -> inStep++
                whole == 2 -> afterOne++
                whole == 3 -> afterTwo++
                else -> afterMore++
            }
        }
        val offBeat = when (offCount) {
            0 -> ""
            else -> ", $offCount off the beat (most at ${millis(mostCommon(off, offCount), 2)})"
        }
        val sorted = totals.copyOf(totalCount).also { it.sort() }
        return "beat ${millis(beat, 2)}: $inStep frames in step, $afterOne after one missed beat, " +
            "$afterTwo after two, $afterMore after a longer gap$offBeat; " +
            "frame time median ${millis(sorted.rank(50).toDouble(), 1)}, " +
            "95% within ${millis(sorted.rank(95).toDouble(), 1)}, " +
            "99% within ${millis(sorted.rank(99).toDouble(), 1)}"
    }

    /**
     * The shortest time between frames that at least [need] of the gaps have:
     * the display's refresh, where the screen moved for long enough to show
     * it. The shortest and not the most common, because a clip that plays at
     * 30 a second on a still page has more gaps than a short scroll does.
     */
    private fun beat(need: Int): Double? {
        val counts = IntArray(BUCKETS + 2)
        for (i in 0 until gapCount) counts[bucket(gaps[i])]++
        // Two buckets at a time: a beat that falls on the line between two
        // would be split, and counted as neither.
        val at = (0..BUCKETS).firstOrNull { counts[it] + counts[it + 1] >= need } ?: return null
        return mean(gaps, gapCount, at)
    }

    private fun mostCommon(values: LongArray, count: Int): Double {
        val counts = IntArray(BUCKETS + 2)
        for (i in 0 until count) counts[bucket(values[i])]++
        val at = (0..BUCKETS).maxByOrNull { counts[it] + counts[it + 1] } ?: 0
        return mean(values, count, at)
    }

    /** The mean of the values in buckets [at] and [at] + 1. */
    private fun mean(values: LongArray, count: Int, at: Int): Double {
        var sum = 0L
        var n = 0
        for (i in 0 until count) {
            val b = bucket(values[i])
            if (b == at || b == at + 1) {
                sum += values[i]
                n++
            }
        }
        return if (n == 0) 0.0 else sum.toDouble() / n
    }

    private fun bucket(nanos: Long): Int = (nanos / BUCKET_NANOS).toInt().coerceIn(0, BUCKETS)

    /** The value that [percent] of a sorted array is at or under. */
    private fun LongArray.rank(percent: Int): Long =
        if (isEmpty()) 0L else this[((size * percent + 99) / 100 - 1).coerceIn(0, size - 1)]

    private fun millis(nanos: Double, places: Int): String = "%.${places}fms".format(Locale.ROOT, nanos / 1_000_000.0)

    private companion object {
        /** Five seconds at 165 frames a second is 825. */
        const val CAPACITY = 1024
        const val REST_NANOS = 100_000_000L
        const val BUCKET_NANOS = 250_000L
        const val BUCKETS = (REST_NANOS / BUCKET_NANOS).toInt()
        const val MIN_GAPS = 20

        /** A beat is one that this many gaps have, and one in [COMMON_SHARE] of them. */
        const val MIN_COMMON = 5
        const val COMMON_SHARE = 10

        /** How far from a whole number of beats a gap can be, in beats, and still be on it. */
        const val ON_BEAT = 0.15
    }
}
