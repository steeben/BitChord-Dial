package com.music.bitchord.ui.performance

import android.view.Display
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_PERFORMANCE_REFRESH_RATE = 50
private const val MAX_REASONABLE_REFRESH_RATE = 240

/** Refresh rates the current display can actually select, normalized for UI labels. */
fun Display?.supportedPerformanceRefreshRates(): List<Int> {
    val fallback = this?.refreshRate?.roundToInt()?.coerceAtLeast(MIN_PERFORMANCE_REFRESH_RATE) ?: 60
    return this?.supportedModes
        ?.asSequence()
        ?.map { it.refreshRate.roundToInt() }
        ?.filter { it in MIN_PERFORMANCE_REFRESH_RATE..MAX_REASONABLE_REFRESH_RATE }
        ?.distinct()
        ?.sorted()
        ?.toList()
        ?.ifEmpty { listOf(fallback) }
        ?: listOf(fallback)
}

/**
 * The display mode running at [refreshRate] and at the resolution in use now,
 * or 0 (no preference) when the display has none.
 */
fun Display?.performanceModeId(refreshRate: Int): Int {
    val display = this ?: return 0
    val current = display.mode ?: return 0
    return display.supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .filter { it.refreshRate.roundToInt() == refreshRate }
        .maxByOrNull { it.refreshRate }
        ?.modeId
        ?: 0
}

/** Maps a saved preference to a real mode, including preferences restored on another device. */
fun Display?.resolvePerformanceRefreshRate(preferred: Int): Int =
    supportedPerformanceRefreshRates().minBy { abs(it - preferred) }

/**
 * What the display offers and what it is doing, for a log session: every
 * mode with its id, size and rate, then the mode and rate in use. Written
 * when high performance mode is applied and again a moment later, so a log
 * shows whether the phone took the request or kept its own ceiling.
 */
fun Display?.describeForLog(): String {
    val display = this ?: return "no display"
    val modes = display.supportedModes.joinToString("; ") { mode ->
        val alternatives = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            mode.alternativeRefreshRates.joinToString(",") { "%.1f".format(java.util.Locale.ROOT, it) }
        } else {
            ""
        }
        "#${mode.modeId} ${mode.physicalWidth}x${mode.physicalHeight}@" +
            "%.2f".format(java.util.Locale.ROOT, mode.refreshRate) +
            if (alternatives.isEmpty()) "" else " alt[$alternatives]"
    }
    val current = display.mode
    return "current mode #${current?.modeId} ${current?.physicalWidth}x${current?.physicalHeight}, " +
        "running at ${"%.2f".format(java.util.Locale.ROOT, display.refreshRate)}Hz; modes: $modes"
}
