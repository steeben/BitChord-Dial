package com.music.bitchord.ui.player

import kotlin.math.abs

/**
 * Tells a music video that is really one picture held still (an upload of
 * the song over its album art) from one that moves, by comparing a few tiny
 * thumbnails of what is already on screen.
 *
 * Each sample is the frame scaled down to [WIDTH]×[HEIGHT] and reduced to
 * brightness, which irons out compression noise: a re-encoded still image
 * differs from itself by a level or two, a cut or a moving camera by far
 * more over a good share of the picture. A video is still only when every
 * sample matches the one before it across the whole probe; one sample that
 * moved settles it the other way, so a real video is usually known after the
 * second look.
 */
internal object StillPicture {
    const val WIDTH = 48
    const val HEIGHT = 27

    /** A pixel whose brightness moved by more than this (of 255) changed. */
    const val PIXEL_THRESHOLD = 12

    /** More than this share of pixels changing is a picture that moved. */
    const val MOVED_FRACTION = 0.004f

    /** Samples taken before a video is called still. */
    const val SAMPLES = 5

    /** Video time between samples. */
    const val EVERY_MS = 3_000L

    /** Video time after the first frame before the first sample: past a fade-in. */
    const val FIRST_AFTER_MS = 2_000L

    /** Brightness, 0–255, of each ARGB pixel. */
    fun luma(argb: IntArray): ByteArray = ByteArray(argb.size) { i ->
        val p = argb[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        ((r * 77 + g * 150 + b * 29) shr 8).toByte()
    }

    /** The share of pixels whose brightness differs by more than [PIXEL_THRESHOLD]. */
    fun changedFraction(a: ByteArray, b: ByteArray): Float {
        if (a.size != b.size || a.isEmpty()) return 1f
        var changed = 0
        for (i in a.indices) {
            val d = abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
            if (d > PIXEL_THRESHOLD) changed++
        }
        return changed.toFloat() / a.size
    }

    fun moved(a: ByteArray, b: ByteArray): Boolean = changedFraction(a, b) > MOVED_FRACTION
}
