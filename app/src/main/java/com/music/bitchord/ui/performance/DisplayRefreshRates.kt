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
