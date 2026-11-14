package com.music.bitchord.ui.performance

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AppStyle
import com.music.bitchord.data.settings.CustomizationSettings
import java.util.Locale

/**
 * The frames of one stretch of time on one page, counted.
 *
 * A frame is late when it took longer than the display gave it. Each late
 * frame's time is kept apart by where it went, so a line of the log says not
 * only that a page stuttered but which part of the work was slow: the wait
 * for the app's thread to be free (something else was running on it), the
 * app's thread itself (composition, layout, drawing), the render thread, or
 * the GPU.
 */
class FrameTally {
    var frames = 0
        private set
    var late = 0
        private set

    /** Late by more than a whole frame: the ones that are seen. */
    var slow = 0
        private set
    var frozen = 0
        private set
    var worstNanos = 0L
        private set

    /**
     * The least any frame of the stretch was given. Android gives a frame
     * longer once the frames before it have fallen behind, so the least is the
     * one that says what the display asks for.
     */
    private var budgetNanos = 0L
    private var lateWaitNanos = 0L
    private var lateUiNanos = 0L
    private var lateRenderNanos = 0L
    private var lateGpuNanos = 0L

    fun add(totalNanos: Long, budgetNanos: Long, waitNanos: Long, uiNanos: Long, renderNanos: Long, gpuNanos: Long) {
        frames++
        if (budgetNanos > 0L && (this.budgetNanos == 0L || budgetNanos < this.budgetNanos)) this.budgetNanos = budgetNanos
        if (totalNanos > worstNanos) worstNanos = totalNanos
        if (totalNanos >= FROZEN_NANOS) frozen++
        if (budgetNanos <= 0L || totalNanos <= budgetNanos) return
        late++
        if (totalNanos > budgetNanos * 2) slow++
        lateWaitNanos += waitNanos
        lateUiNanos += uiNanos
        lateRenderNanos += renderNanos
        lateGpuNanos += gpuNanos
    }

    fun reset() {
        frames = 0
        late = 0
        slow = 0
        frozen = 0
        worstNanos = 0L
        budgetNanos = 0L
        lateWaitNanos = 0L
        lateUiNanos = 0L
        lateRenderNanos = 0L
        lateGpuNanos = 0L
    }

    /**
     * One line for the log, or null when there was no frame to speak of.
     * [spanNanos] is the time from the stretch's first frame to its last.
     */
    fun summary(spanNanos: Long = 0L): String? {
        if (frames == 0) return null
        // A rate only means something over a stretch long enough to have one.
        val rate = if (spanNanos >= MIN_RATE_SPAN_NANOS) {
            " (${"%.0f".format(Locale.ROOT, (frames - 1) * 1_000_000_000.0 / spanNanos)} a second)"
        } else {
            ""
        }
        val head = "$frames frames$rate, worst ${millis(worstNanos)}, budget ${millis(budgetNanos)}"
        if (late == 0) return "$head, none late"
        val share = late * 100.0 / frames
        return "$head, $late late (${"%.1f".format(Locale.ROOT, share)}%), " +
            "$slow over twice the budget, $frozen frozen; " +
            "a late frame took on average ${millis(lateWaitNanos / late)} waiting for the app thread, " +
            "${millis(lateUiNanos / late)} on the app thread, " +
            "${millis(lateRenderNanos / late)} on the render thread, ${millis(lateGpuNanos / late)} on the GPU"
    }

    private fun millis(nanos: Long): String = "%.1fms".format(Locale.ROOT, nanos / 1_000_000.0)

    private companion object {
        /** What Android itself calls a frozen frame. */
        const val FROZEN_NANOS = 700_000_000L
        const val MIN_RATE_SPAN_NANOS = 250_000_000L
    }
}

/**
 * Frame times, written to a log session while one is recording.
 *
 * A report of stutter says nothing about where or why. With logging on
 * (Settings, Advanced, Enable logging), this writes a line for each stretch of
 * frames: the page they were on, how many were late, and where a late frame's
 * time went, and a second line for how evenly its frames came (see
 * [FramePacing]). A touch that moved has a line with each of its first
 * frames (see [TouchStart]). While frames are drawn, [AppThreadWatch] also writes what the
 * app's thread was in the middle of each time it was busy for longer than a
 * few frames. Nothing is measured, and no listener is attached, with logging
 * off.
 */
object FrameLog {
    private const val TAG = "Frames"

    /** A stretch is written once it is this long, or once the screen has been still for [IDLE_MS]. */
    private const val STRETCH_MS = 5_000L
    private const val IDLE_MS = 1_000L

    /** A single frame at least this long gets its own line. */
    private const val SLOW_FRAME_NANOS = 40_000_000L

    /**
     * The least time between two memory lines. A line is written once the
     * screen has gone still: reading the memory can hold the app up for a
     * moment, and a stop made by the log itself would be read as the app's.
     *
     * Some screens are never still (the ambient background moves for as long
     * as it is up), and a log of them had no memory line at all. So a page
     * that has had frames for a whole stretch gets its line with the screen
     * moving, this long after the last one, and the line says how it was
     * read. Never as the page changes: that is the moment a log is read for.
     */
    private const val MEMORY_GAP_MS = 10_000L

    /** The page on screen, as the app names it. Set by the app; read on the log's own thread. */
    @Volatile
    var page: String = ""

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val touchStart = TouchStart()
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var watched = java.lang.ref.WeakReference<Window>(null)

    /** Whether this run of the app has had its first frame written. */
    private var launchWritten = false

    /** Whether this run of the app has said how much of its code is compiled. */
    private var compiledWritten = false

    /**
     * Starts counting [window]'s frames. Safe to call before the window is on
     * screen: the count then starts with its first frame, which is how a log
     * left on over a launch gets the launch.
     */
    fun start(window: Window) {
        if (listener != null) {
            if (watched.get() === window) return
            // A window that went without saying so: the activity was rebuilt.
            stop()
        }
        val thread = HandlerThread("FrameLog").also { it.start() }
        val handler = Handler(thread.looper)
        val tally = FrameTally()
        val pacing = FramePacing()
        var stretchPage = ""
        var stretchStart = 0L
        // Before Android 12 a frame does not say how long it was given, so the
        // display's rate stands in. Read at the first frame: before the window
        // is on screen it has no display to ask.
        var displayBudget = 0L

        // When the stretch's last frame came in, so that the still second
        // which ends a stretch is not counted as part of it.
        var stretchEnd = 0L

        fun write() {
            val span = stretchEnd - stretchStart
            val line = tally.summary(span * 1_000_000L) ?: return
            TrackLog.toSession('I', TAG, "$stretchPage, ${"%.1f".format(Locale.ROOT, span / 1000.0)}s: $line")
            tally.reset()
            pacing.summary()?.let { TrackLog.toSession('I', TAG, "$stretchPage pacing: $it") }
            pacing.reset()
        }
        val startedAt = SystemClock.elapsedRealtime()
        var memoryAt = 0L

        fun writeMemory(moving: Boolean) {
            val now = SystemClock.elapsedRealtime()
            // With the screen moving, not in a launch's first seconds either.
            val last = if (moving) maxOf(memoryAt, startedAt) else memoryAt
            if (last != 0L && now - last < MEMORY_GAP_MS) return
            memoryAt = now
            val reading = MemoryLog.read() ?: return
            TrackLog.toSession(
                'I',
                TAG,
                MemoryLog.line(
                    page.ifEmpty { "launch" },
                    reading,
                    homeKept = AppSettings.keepHome.value,
                    moving = moving,
                ),
            )
        }
        val idle = Runnable {
            write()
            writeTouch()
            writeMemory(moving = false)
        }

        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val now = SystemClock.elapsedRealtime()
            AppThreadWatch.lastFrameAt = SystemClock.uptimeMillis()
            val totalNanos = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            // The time the frame stood waiting because the app's thread was
            // busy with something that was not this frame.
            val waitNanos = metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)
            val inputNanos = metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION)
            // Compose runs its recompositions here, on the frame clock.
            val animationNanos = metrics.getMetric(FrameMetrics.ANIMATION_DURATION)
            val layoutNanos = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
            val drawNanos = metrics.getMetric(FrameMetrics.DRAW_DURATION)
            val uiNanos = inputNanos + animationNanos + layoutNanos + drawNanos
            val renderNanos = metrics.getMetric(FrameMetrics.SYNC_DURATION) +
                metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) +
                metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
            if (displayBudget == 0L) {
                val refreshRate = window.decorView.display?.refreshRate?.takeIf { it > 0f } ?: 60f
                displayBudget = (1_000_000_000.0 / refreshRate).toLong()
                TrackLog.toSession('I', TAG, "display ${refreshRate}Hz${arrLine(window)}")
            }
            // A window's first frame builds everything. It is not stutter, so
            // it is kept out of the count and written on its own line.
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) {
                val sinceStart = if (launchWritten) {
                    ""
                } else {
                    launchWritten = true
                    ", ${now - Process.getStartElapsedRealtime()}ms after the app's process started"
                }
                TrackLog.toSession(
                    'I',
                    TAG,
                    "first frame of the window: ${millis(totalNanos)}, " +
                        "${millis(uiNanos)} on the app thread, ${millis(renderNanos)} on the render thread$sinceStart",
                )
                return@OnFrameMetricsAvailableListener
            }
            val current = page.ifEmpty { "launch" }
            if (tally.frames > 0 && (current != stretchPage || now - stretchStart >= STRETCH_MS)) {
                val samePage = current == stretchPage
                write()
                if (samePage) writeMemory(moving = true)
            }
            if (tally.frames == 0) {
                stretchPage = current
                stretchStart = now
            }
            stretchEnd = now
            val hasDeadline = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val gpuNanos = if (hasDeadline) metrics.getMetric(FrameMetrics.GPU_DURATION) else 0L
            tally.add(
                totalNanos = totalNanos,
                budgetNanos = if (hasDeadline) metrics.getMetric(FrameMetrics.DEADLINE) else displayBudget,
                waitNanos = waitNanos,
                uiNanos = uiNanos,
                renderNanos = renderNanos,
                gpuNanos = gpuNanos,
            )
            val vsyncNanos = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP)
            pacing.add(vsyncNanos, totalNanos)
            val touchOver = touchStart.frame(
                vsyncNanos = vsyncNanos,
                totalNanos = totalNanos,
                budgetNanos = if (hasDeadline) metrics.getMetric(FrameMetrics.DEADLINE) else displayBudget,
                inputNanos = inputNanos,
                animationNanos = animationNanos,
                layoutNanos = layoutNanos,
                drawNanos = drawNanos,
                renderNanos = renderNanos,
                gpuNanos = gpuNanos,
            )
            if (touchOver) writeTouch()
            // A frame this long is seen as a stop. It gets a line of its own,
            // at the time it happened, so the lines around it in the log say
            // what the app was doing.
            if (totalNanos >= SLOW_FRAME_NANOS) {
                val began = (System.nanoTime() - metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)) / 1_000_000L
                TrackLog.toSession(
                    'I',
                    TAG,
                    "slow frame on $current: ${millis(totalNanos)}, began ${began}ms before this line; " +
                        "${millis(waitNanos)} waiting for the app thread, " +
                        "app thread ${millis(uiNanos)} (input ${millis(inputNanos)}, " +
                        "animation and composition ${millis(animationNanos)}, " +
                        "layout ${millis(layoutNanos)}, draw ${millis(drawNanos)}), " +
                        "${millis(renderNanos)} on the render thread, ${millis(gpuNanos)} on the GPU",
                )
            }
            handler.removeCallbacks(idle)
            handler.postDelayed(idle, IDLE_MS)
        }
        window.addOnFrameMetricsAvailableListener(listener, handler)
        this.thread = thread
        this.handler = handler
        this.listener = listener
        watched = java.lang.ref.WeakReference(window)
        TrackLog.toSession('I', TAG, "frame times on; ${effectsLine()}")
        if (!compiledWritten) {
            compiledWritten = true
            val apk = window.context.applicationInfo?.sourceDir
            // Read off the app's thread: it is a file of some thousands of lines.
            val context = window.context.applicationContext
            handler.post {
                CompiledCode.line(apk)?.let { TrackLog.toSession('I', TAG, it) }
                MemoryLog.deviceLine(context)?.let { TrackLog.toSession('I', TAG, it) }
            }
        }
        AppThreadWatch.start()
    }

    private fun millis(nanos: Long): String = "%.1fms".format(Locale.ROOT, nanos / 1_000_000.0)

    /**
     * A touch on the window, as the activity is given it. With a log
     * recording, the first frames of a touch that moves get a line: see
     * [TouchStart]. This is on the app's thread and in the path of every
     * touch, so it keeps two numbers and reads nothing; the display is asked
     * what it is doing on the log's own thread.
     */
    fun touch(event: MotionEvent) {
        val handler = handler ?: return
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStart.down(event.eventTime * NANOS_PER_MILLI, page.ifEmpty { "launch" })
                handler.post { touchStart.displayAtDown(displayNow()) }
            }
            MotionEvent.ACTION_MOVE -> touchStart.move(event.eventTime * NANOS_PER_MILLI, event.historySize + 1)
        }
    }

    private const val NANOS_PER_MILLI = 1_000_000L

    /**
     * Something the app did just now that a touch's first frames may have
     * paid for. It is shown in the touch's line at the frame it fell in.
     */
    fun mark(what: String) {
        if (handler == null) return
        touchStart.mark(System.nanoTime(), what)
    }

    private fun writeTouch() {
        touchStart.summary(displayNow())?.let { TrackLog.toSession('I', TAG, it) }
    }

    /** The display's mode and rate at this moment, in a few words. */
    private fun displayNow(): String {
        val display = watched.get()?.decorView?.display ?: return "unknown"
        return "mode #${display.mode?.modeId} ${"%.1f".format(Locale.ROOT, display.refreshRate)}Hz"
    }

    /**
     * Whether the display takes a rate for each frame (Android's adaptive
     * refresh rate, from Android 16). Asked by name: the answer is for the
     * log only, and an Android without the question has no answer to give.
     */
    private fun arrLine(window: Window): String {
        val display = window.decorView.display ?: return ""
        val answer = runCatching { display.javaClass.getMethod("hasArrSupport").invoke(display) }.getOrNull()
            ?: return ""
        return ", adaptive refresh rate support: $answer"
    }

    /**
     * A line about what the app did to a page, between the frame lines: that
     * it was built, or kept and brought back. A slow frame beside one of these
     * says which it was for.
     */
    fun note(message: String) {
        if (listener == null) return
        TrackLog.toSession('I', TAG, message)
    }

    /** Stops counting [window]'s frames. A window that is not the one being counted is left alone. */
    fun stop(window: Window) {
        if (watched.get() === window) stop()
    }

    private fun stop() {
        val listener = listener ?: return
        AppThreadWatch.stop()
        watched.get()?.let { runCatching { it.removeOnFrameMetricsAvailableListener(listener) } }
        watched = java.lang.ref.WeakReference<Window>(null)
        thread?.quitSafely()
        handler = null
        this.listener = null
        thread = null
    }

    /** The settings that decide how much each frame has to draw. */
    private fun effectsLine(): String {
        val style = AppStyle.of(AppSettings.liquidGlass.value, AppSettings.reduceDynamicBlur.value)
        return "performance mode=${AppSettings.performanceMode.value}, " +
            "keep Home loaded=${AppSettings.keepHome.value}, " +
            "app style=$style, reduce animations=${AppSettings.reduceAnimation.value}, " +
            "high refresh rate=${AppSettings.highPerformanceMode.value}, " +
            "ambient background=${CustomizationSettings.ambientBackground.value}, " +
            "dial tile style=${CustomizationSettings.dialTileStyle.value}, " +
            "mixes dial=${CustomizationSettings.mixesDial.value}, " +
            "lyrics blur=${AppSettings.lyricsBlur.value}, canvas=${AppSettings.animatedCanvas.value}"
    }
}
