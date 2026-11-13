package com.music.bitchord.ui.performance

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.FrameMetrics
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
 * only that a page stuttered but which part of the work was slow: the app's
 * own thread (composition, layout, drawing), the render thread, or the GPU.
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
    private var budgetNanos = 0L
    private var lateUiNanos = 0L
    private var lateRenderNanos = 0L
    private var lateGpuNanos = 0L

    fun add(totalNanos: Long, budgetNanos: Long, uiNanos: Long, renderNanos: Long, gpuNanos: Long) {
        frames++
        this.budgetNanos = budgetNanos
        if (totalNanos > worstNanos) worstNanos = totalNanos
        if (totalNanos >= FROZEN_NANOS) frozen++
        if (budgetNanos <= 0L || totalNanos <= budgetNanos) return
        late++
        if (totalNanos > budgetNanos * 2) slow++
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
        lateUiNanos = 0L
        lateRenderNanos = 0L
        lateGpuNanos = 0L
    }

    /** One line for the log, or null when there was no frame to speak of. */
    fun summary(): String? {
        if (frames == 0) return null
        val head = "$frames frames, worst ${millis(worstNanos)}, budget ${millis(budgetNanos)}"
        if (late == 0) return "$head, none late"
        val share = late * 100.0 / frames
        return "$head, $late late (${"%.1f".format(Locale.ROOT, share)}%), " +
            "$slow over twice the budget, $frozen frozen; " +
            "a late frame took on average ${millis(lateUiNanos / late)} on the app thread, " +
            "${millis(lateRenderNanos / late)} on the render thread, ${millis(lateGpuNanos / late)} on the GPU"
    }

    private fun millis(nanos: Long): String = "%.1fms".format(Locale.ROOT, nanos / 1_000_000.0)

    private companion object {
        /** What Android itself calls a frozen frame. */
        const val FROZEN_NANOS = 700_000_000L
    }
}

/**
 * Frame times, written to a log session while one is recording.
 *
 * A report of stutter says nothing about where or why. With logging on
 * (Settings, Advanced, Enable logging), this writes a line for each stretch of
 * frames: the page they were on, how many were late, and where a late frame's
 * time went. Nothing is measured, and no listener is attached, with logging
 * off.
 */
object FrameLog {
    private const val TAG = "Frames"

    /** A stretch is written once it is this long, or once the screen has been still for [IDLE_MS]. */
    private const val STRETCH_MS = 5_000L
    private const val IDLE_MS = 1_000L

    /** The page on screen, as the app names it. Set by the app; read on the log's own thread. */
    @Volatile
    var page: String = ""

    private var thread: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var watched = java.lang.ref.WeakReference<Window>(null)

    /** Whether this run of the app has had its first frame written. */
    private var launchWritten = false

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
        var stretchPage = ""
        var stretchStart = 0L
        // Before Android 12 a frame does not say how long it was given, so the
        // display's rate stands in. Read at the first frame: before the window
        // is on screen it has no display to ask.
        var displayBudget = 0L

        fun write() {
            val line = tally.summary() ?: return
            val seconds = (SystemClock.elapsedRealtime() - stretchStart) / 1000.0
            TrackLog.toSession('I', TAG, "$stretchPage, ${"%.1f".format(Locale.ROOT, seconds)}s: $line")
            tally.reset()
        }
        val idle = Runnable { write() }

        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val now = SystemClock.elapsedRealtime()
            val uiNanos = metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) +
                metrics.getMetric(FrameMetrics.ANIMATION_DURATION) +
                metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) +
                metrics.getMetric(FrameMetrics.DRAW_DURATION)
            val renderNanos = metrics.getMetric(FrameMetrics.SYNC_DURATION) +
                metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION) +
                metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)
            if (displayBudget == 0L) {
                val refreshRate = window.decorView.display?.refreshRate?.takeIf { it > 0f } ?: 60f
                displayBudget = (1_000_000_000.0 / refreshRate).toLong()
                TrackLog.toSession('I', TAG, "display ${refreshRate}Hz")
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
                    "first frame of the window: ${millis(metrics.getMetric(FrameMetrics.TOTAL_DURATION))}, " +
                        "${millis(uiNanos)} on the app thread, ${millis(renderNanos)} on the render thread$sinceStart",
                )
                return@OnFrameMetricsAvailableListener
            }
            val current = page.ifEmpty { "launch" }
            if (tally.frames > 0 && (current != stretchPage || now - stretchStart >= STRETCH_MS)) write()
            if (tally.frames == 0) {
                stretchPage = current
                stretchStart = now
            }
            val hasDeadline = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            tally.add(
                totalNanos = metrics.getMetric(FrameMetrics.TOTAL_DURATION),
                budgetNanos = if (hasDeadline) metrics.getMetric(FrameMetrics.DEADLINE) else displayBudget,
                uiNanos = uiNanos,
                renderNanos = renderNanos,
                gpuNanos = if (hasDeadline) metrics.getMetric(FrameMetrics.GPU_DURATION) else 0L,
            )
            handler.removeCallbacks(idle)
            handler.postDelayed(idle, IDLE_MS)
        }
        window.addOnFrameMetricsAvailableListener(listener, handler)
        this.thread = thread
        this.listener = listener
        watched = java.lang.ref.WeakReference(window)
        TrackLog.toSession('I', TAG, "frame times on; ${effectsLine()}")
    }

    private fun millis(nanos: Long): String = "%.1fms".format(Locale.ROOT, nanos / 1_000_000.0)

    /** Stops counting [window]'s frames. A window that is not the one being counted is left alone. */
    fun stop(window: Window) {
        if (watched.get() === window) stop()
    }

    private fun stop() {
        val listener = listener ?: return
        watched.get()?.let { runCatching { it.removeOnFrameMetricsAvailableListener(listener) } }
        watched = java.lang.ref.WeakReference<Window>(null)
        thread?.quitSafely()
        this.listener = null
        thread = null
    }

    /** The settings that decide how much each frame has to draw. */
    private fun effectsLine(): String {
        val style = AppStyle.of(AppSettings.liquidGlass.value, AppSettings.reduceDynamicBlur.value)
        return "performance mode=${AppSettings.performanceMode.value}, " +
            "app style=$style, reduce animations=${AppSettings.reduceAnimation.value}, " +
            "high refresh rate=${AppSettings.highPerformanceMode.value}, " +
            "ambient background=${CustomizationSettings.ambientBackground.value}, " +
            "dial tile style=${CustomizationSettings.dialTileStyle.value}, " +
            "mixes dial=${CustomizationSettings.mixesDial.value}, " +
            "lyrics blur=${AppSettings.lyricsBlur.value}, canvas=${AppSettings.animatedCanvas.value}"
    }
}
