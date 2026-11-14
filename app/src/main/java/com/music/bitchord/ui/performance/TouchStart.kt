package com.music.bitchord.ui.performance

import java.util.Locale

/**
 * The first frames of a touch that moved, one by one.
 *
 * A recording of the screen showed a swipe's first 50 to 60 ms move unevenly:
 * a frame with no movement, then one with twice the movement, three or four
 * times over, and then every frame the same. A stretch's lines cannot say
 * why, they are counts over five seconds. This keeps each frame of the first
 * [WINDOW_NANOS] after a finger comes down: the time since the frame before
 * it, how long it took, and the app thread's part of that where it is most of
 * a beat. With that, the three things it could be read
 * differently:
 *
 *  - the app's frames are slow as a scroll starts: frames marked late, with
 *    gaps of two beats, and the line says where a late frame's time went;
 *  - the display is changing its rate (a phone that rests at 120 Hz and goes
 *    to 165 Hz for a touch): the gaps change from one beat to another, with
 *    no frame late, and the display's mode differs from the touch to the end;
 *  - the touch itself comes in slowly at first: every frame on time and in
 *    step, and fewer touch samples than frames.
 *
 * The app can also [mark] a moment of its own in those frames (the bottom bar
 * changing its form as the page starts to move), and the line shows which
 * frame it fell in: a run of late frames straight after a mark is that
 * change's cost.
 *
 * A touch that never moved (a tap) writes nothing.
 *
 * The touch comes from the app's thread and the frames from the log's, so
 * every entry is synchronized. Nothing here reads the display or the clock.
 */
class TouchStart {
    private var downNanos = 0L
    private var page = ""
    private var displayAtDown = ""
    private var events = 0
    private var samples = 0
    private var firstMoveNanos = 0L

    private val gaps = LongArray(MAX_FRAMES)
    private val totals = LongArray(MAX_FRAMES)
    private val apps = LongArray(MAX_FRAMES)
    private val lates = BooleanArray(MAX_FRAMES)
    private val vsyncs = LongArray(MAX_FRAMES)
    private val markNanos = LongArray(MAX_MARKS)
    private val markNames = arrayOfNulls<String>(MAX_MARKS)
    private var marks = 0
    private var frames = 0
    private var moveFrame = -1
    private var lastVsyncNanos = 0L

    private var lateInput = 0L
    private var lateAnimation = 0L
    private var lateLayout = 0L
    private var lateDraw = 0L
    private var lateRender = 0L
    private var lateGpu = 0L

    /** A finger came down at [nanos], with [page] on the screen. */
    @Synchronized
    fun down(nanos: Long, page: String) {
        downNanos = nanos
        this.page = page
        displayAtDown = ""
        events = 0
        samples = 0
        firstMoveNanos = 0L
        frames = 0
        marks = 0
        moveFrame = -1
        lateInput = 0L
        lateAnimation = 0L
        lateLayout = 0L
        lateDraw = 0L
        lateRender = 0L
        lateGpu = 0L
    }

    /** What the display was doing at the touch, read off the app's thread a moment after it. */
    @Synchronized
    fun displayAtDown(display: String) {
        if (downNanos != 0L) displayAtDown = display
    }

    /** The finger moved: one event at [nanos], carrying [samples] readings of the touch. */
    @Synchronized
    fun move(nanos: Long, samples: Int) {
        if (downNanos == 0L || nanos - downNanos > WINDOW_NANOS) return
        if (events == 0) firstMoveNanos = nanos
        events++
        this.samples += samples
    }

    /** Something the app did at [nanos], to be shown at the frame it fell in. */
    @Synchronized
    fun mark(nanos: Long, what: String) {
        if (downNanos == 0L || nanos < downNanos || nanos - downNanos > WINDOW_NANOS || marks == MAX_MARKS) return
        markNanos[marks] = nanos
        markNames[marks] = what
        marks++
    }

    /**
     * A frame. True once the touch's first frames are all in and [summary] is
     * due: this frame is the first one past them.
     */
    @Synchronized
    fun frame(
        vsyncNanos: Long,
        totalNanos: Long,
        budgetNanos: Long,
        inputNanos: Long,
        animationNanos: Long,
        layoutNanos: Long,
        drawNanos: Long,
        renderNanos: Long,
        gpuNanos: Long,
    ): Boolean {
        val before = lastVsyncNanos
        lastVsyncNanos = vsyncNanos
        if (downNanos == 0L || vsyncNanos < downNanos) return false
        if (vsyncNanos - downNanos > WINDOW_NANOS) return true
        if (frames == MAX_FRAMES) return false
        if (moveFrame < 0 && firstMoveNanos != 0L && vsyncNanos >= firstMoveNanos) moveFrame = frames
        val late = budgetNanos > 0L && totalNanos > budgetNanos
        gaps[frames] = if (before == 0L) 0L else vsyncNanos - before
        totals[frames] = totalNanos
        apps[frames] = inputNanos + animationNanos + layoutNanos + drawNanos
        lates[frames] = late
        vsyncs[frames] = vsyncNanos
        frames++
        if (late) {
            lateInput += inputNanos
            lateAnimation += animationNanos
            lateLayout += layoutNanos
            lateDraw += drawNanos
            lateRender += renderNanos
            lateGpu += gpuNanos
        }
        return false
    }

    /**
     * The touch's line for the log, with [displayNow] for what the display is
     * doing at its end, and the touch closed. Null for no touch, a touch that
     * did not move, or one with too few frames to say anything.
     */
    @Synchronized
    fun summary(displayNow: String): String? {
        if (downNanos == 0L) return null
        val down = downNanos
        downNanos = 0L
        if (events == 0 || frames < MIN_FRAMES) return null

        var beat = Long.MAX_VALUE
        for (i in 0 until frames) if (gaps[i] in SHORTEST_GAP_NANOS until beat) beat = gaps[i]
        var late = 0
        var missed = 0
        val each = StringBuilder()
        for (i in 0 until frames) {
            if (lates[i]) late++
            val rest = gaps[i] > REST_NANOS
            // Not the first: the frame before it was before the touch.
            if (i > 0 && beat != Long.MAX_VALUE && !rest && gaps[i] >= beat * MISSED_BEATS) missed++
            if (i == moveFrame) each.append(" |")
            for (m in 0 until marks) {
                // The frame the mark fell in: the last one that began before it.
                val inThis = vsyncs[i] <= markNanos[m] && (i == frames - 1 || vsyncs[i + 1] > markNanos[m])
                val beforeAll = i == 0 && markNanos[m] < vsyncs[0]
                if (inThis || beforeAll) each.append(" [").append(markNames[m]).append(']')
            }
            each.append(' ')
            each.append(
                when {
                    gaps[i] == 0L -> "-"
                    rest -> "rest"
                    else -> millis(gaps[i], 1)
                },
            )
            each.append('/').append(millis(totals[i], 1))
            // A frame the app's thread held for most of a beat or more: with
            // this the line says whether a slow frame was the app's own work.
            if (apps[i] >= APP_SHOWN_NANOS) each.append("(app ").append(millis(apps[i], 1)).append(')')
            if (lates[i]) each.append('*')
        }
        val display = when {
            displayAtDown.isEmpty() -> "display $displayNow after ${WINDOW_NANOS / 1_000_000}ms"
            displayAtDown == displayNow -> "display $displayNow at the touch and after ${WINDOW_NANOS / 1_000_000}ms"
            else -> "display $displayAtDown at the touch, $displayNow after ${WINDOW_NANOS / 1_000_000}ms"
        }
        val lateSplit = if (late == 0) {
            ""
        } else {
            val ui = lateInput + lateAnimation + lateLayout + lateDraw
            "; a late frame took on average ${millis(ui / late, 1)}ms on the app thread " +
                "(input ${millis(lateInput / late, 1)}, animation and composition ${millis(lateAnimation / late, 1)}, " +
                "layout ${millis(lateLayout / late, 1)}, draw ${millis(lateDraw / late, 1)}), " +
                "${millis(lateRender / late, 1)}ms on the render thread, ${millis(lateGpu / late, 1)}ms on the GPU"
        }
        val beatText = if (beat == Long.MAX_VALUE) "" else " (beat ${millis(beat, 2)}ms)"
        return "touch on $page: $display; $samples touch samples in $events events, " +
            "the first ${millis(firstMoveNanos - down, 1)}ms after the touch; " +
            "$frames frames in ${WINDOW_NANOS / 1_000_000}ms, $late late, $missed after a missed beat$beatText$lateSplit; " +
            "frames as gap/time in ms, (app) the app thread's part of it from ${APP_SHOWN_NANOS / 1_000_000}ms, * late, " +
            "| the finger's first move, [] what the app did in the frame after it:$each"
    }

    private fun millis(nanos: Long, places: Int): String = "%.${places}f".format(Locale.ROOT, nanos / 1_000_000.0)

    private companion object {
        const val WINDOW_NANOS = 400_000_000L

        /** 400 ms at 165 frames a second is 66. */
        const val MAX_FRAMES = 72
        const val MIN_FRAMES = 3
        const val MAX_MARKS = 4
        const val SHORTEST_GAP_NANOS = 2_000_000L
        const val REST_NANOS = 100_000_000L
        const val MISSED_BEATS = 1.6
        const val APP_SHOWN_NANOS = 5_000_000L
    }
}
