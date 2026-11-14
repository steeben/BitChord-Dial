package com.music.bitchord.ui.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import java.util.Locale

/**
 * How much memory the app holds, for the frame log.
 *
 * Kept on purpose (Home built while another page is up, a picture cache),
 * memory is what a smooth page is paid for with, and a phone with little of
 * it closes the app in the background sooner. A line of this beside the frame
 * lines says what a setting costs on the phone the log came from.
 */
object MemoryLog {
    /** One reading, in kilobytes, split the way Android's own memory summary is. */
    class Reading(
        val totalKb: Int,
        val javaKb: Int,
        val nativeKb: Int,
        /** Pictures and layers held for drawing: where a kept page's pictures are counted. */
        val graphicsKb: Int,
        val codeKb: Int,
    ) {
        val otherKb: Int get() = (totalKb - javaKb - nativeKb - graphicsKb - codeKb).coerceAtLeast(0)
    }

    /**
     * Reads the app's memory. Not for the app's thread, and not while frames
     * are drawn: Android walks every mapping of the process to answer.
     */
    fun read(): Reading? = runCatching {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        fun stat(name: String) = info.getMemoryStat(name)?.toIntOrNull() ?: 0
        Reading(
            totalKb = stat("summary.total-pss"),
            javaKb = stat("summary.java-heap"),
            nativeKb = stat("summary.native-heap"),
            graphicsKb = stat("summary.graphics"),
            codeKb = stat("summary.code"),
        ).takeIf { it.totalKb > 0 }
    }.getOrNull()

    /**
     * The line for [reading], taken on [page]. [moving]: it was read while
     * frames were drawn, so a stop on the lines next to it may be the read's.
     */
    fun line(page: String, reading: Reading, homeKept: Boolean, moving: Boolean = false): String =
        "memory on $page: ${megabytes(reading.totalKb)} " +
            "(java ${megabytes(reading.javaKb)}, native ${megabytes(reading.nativeKb)}, " +
            "graphics ${megabytes(reading.graphicsKb)}, code ${megabytes(reading.codeKb)}, " +
            "other ${megabytes(reading.otherKb)}); keep Home loaded=$homeKept" +
            if (moving) "; read while the screen moved" else ""

    /** What the phone has, once a log: the frame of reference for every memory line after it. */
    fun deviceLine(context: Context): String? = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val device = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        deviceLine(
            totalBytes = device.totalMem,
            freeBytes = device.availMem,
            lowRam = manager.isLowRamDevice,
            heapLimitMb = manager.memoryClass,
        )
    }.getOrNull()

    fun deviceLine(totalBytes: Long, freeBytes: Long, lowRam: Boolean, heapLimitMb: Int): String =
        "device memory ${gigabytes(totalBytes)}, ${gigabytes(freeBytes)} free, " +
            "low memory device=$lowRam, app heap limit ${heapLimitMb}MB"

    private fun megabytes(kb: Int): String = "${(kb + 512) / 1024}MB"

    private fun gigabytes(bytes: Long): String = "%.1fGB".format(Locale.ROOT, bytes / (1024.0 * 1024.0 * 1024.0))
}
