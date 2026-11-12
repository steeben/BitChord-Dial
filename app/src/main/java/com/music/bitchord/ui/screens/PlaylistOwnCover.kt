package com.music.bitchord.ui.screens

import com.music.bitchord.data.CustomCovers
import kotlin.math.abs
import kotlin.math.min

/*
 * Telling a playlist's own picture (one its owner chose, a mix's artwork)
 * from a cover made out of its songs: the 2x2 grid of four sleeves, or one
 * sleeve on its own. Its own picture goes in front of the generated playlist
 * image; a cover made of sleeves would only repeat what floats round it.
 */

/** A decoded picture's pixels, row by row, as packed RGB. */
internal class CoverPixels(val width: Int, val height: Int, val rgb: IntArray) {
    fun at(x: Int, y: Int): Int = rgb[y * width + x]
}

/**
 * What a cover's address says by itself: true for a picture of the
 * playlist's own, false for one made of its songs, null when only the
 * picture can tell (see [coverIsOwnPicture]).
 */
internal fun playlistCoverIsOwnByAddress(url: String?): Boolean? {
    if (url.isNullOrBlank()) return false
    // One set on this device: the owner's choice, unless the app made it
    // out of the playlist's sleeves.
    if (!url.startsWith("http")) return CustomCovers.GENERATED_MARK !in url
    val host = url.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
    val path = url.substringAfter("://").substringAfter('/', "")
    return when {
        // A video's still: a playlist with no cover shows its first song's.
        host.endsWith("ytimg.com") -> if (path.startsWith("vi/") || path.startsWith("vi_webp/")) false else null
        host.endsWith("scdn.co") || host.endsWith("spotifycdn.com") -> when {
            host.startsWith("mosaic.") -> false
            // An album's sleeve, which a playlist of under four albums wears.
            "image/ab67616d" in path -> false
            else -> true
        }
        else -> null
    }
}

/**
 * Whether [cover] is a picture of the playlist's own, given the [sleeves] of
 * its first songs. It is not when two or more of its quarters are each one
 * of those sleeves (the default grid), when the whole of it is one of them,
 * or when it has the hard seams of a grid down and across its middle: the
 * grid's four can be sleeves from further down the list than those compared.
 *
 * Compared at a few cells a side, which is plenty to tell a sleeve from a
 * photo and cheap enough to run on every playlist opened.
 */
internal fun coverIsOwnPicture(cover: CoverPixels, sleeves: List<CoverPixels>): Boolean {
    // The middle square of whatever shape the cover comes in (an old
    // playlist's is a 4:3 video still).
    val side = min(cover.width, cover.height)
    if (side < 2 * SIG) return false
    val x0 = (cover.width - side) / 2
    val y0 = (cover.height - side) / 2
    val half = side / 2
    val known = sleeves.mapNotNull(::squareSignature)
    val quarters = (0 until 4).map { q -> signature(cover, x0 + (q % 2) * half, y0 + (q / 2) * half, half) }
    if (quarters.count { q -> known.any { distance(q, it) < MATCH_DISTANCE } } >= 2) return false
    val whole = signature(cover, x0, y0, side)
    if (known.any { distance(whole, it) < MATCH_DISTANCE }) return false
    return !hasGridSeams(cover, x0, y0, side)
}

/** A sleeve's middle square, as a video thumbnail's bars would otherwise skew it. */
private fun squareSignature(p: CoverPixels): IntArray? {
    val side = min(p.width, p.height)
    if (side < SIG) return null
    return signature(p, (p.width - side) / 2, (p.height - side) / 2, side)
}

/** The region averaged down to [SIG] x [SIG] RGB cells. */
private fun signature(p: CoverPixels, x0: Int, y0: Int, side: Int): IntArray {
    val out = IntArray(SIG * SIG * 3)
    for (cy in 0 until SIG) for (cx in 0 until SIG) {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        // Cell edges by proportion, so a side that isn't a multiple of SIG
        // is still covered edge to edge.
        for (y in y0 + cy * side / SIG until y0 + (cy + 1) * side / SIG) {
            for (x in x0 + cx * side / SIG until x0 + (cx + 1) * side / SIG) {
                val c = p.at(x, y)
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
            }
        }
        val i = (cy * SIG + cx) * 3
        if (n > 0) {
            out[i] = (r / n).toInt()
            out[i + 1] = (g / n).toInt()
            out[i + 2] = (b / n).toInt()
        }
    }
    return out
}

private fun distance(a: IntArray, b: IntArray): Float {
    var sum = 0L
    for (i in a.indices) sum += abs(a[i] - b[i])
    return sum.toFloat() / a.size
}

/**
 * A grid has hard edges down its middle and across it, far sharper than the
 * picture either side.
 */
private fun hasGridSeams(p: CoverPixels, x0: Int, y0: Int, side: Int): Boolean {
    if (side < 8) return false
    fun diff(a: Int, b: Int): Int =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
            abs((a and 0xFF) - (b and 0xFF))
    fun column(x: Int) = (0 until side).sumOf { diff(p.at(x0 + x - 1, y0 + it), p.at(x0 + x, y0 + it)) }.toFloat() / side
    fun row(y: Int) = (0 until side).sumOf { diff(p.at(x0 + it, y0 + y - 1), p.at(x0 + it, y0 + y)) }.toFloat() / side
    val usualCol = listOf(side / 4, side / 3, 2 * side / 3, 3 * side / 4).map(::column).average().toFloat()
    val usualRow = listOf(side / 4, side / 3, 2 * side / 3, 3 * side / 4).map(::row).average().toFloat()
    return column(side / 2) > usualCol * 3f + 12f && row(side / 2) > usualRow * 3f + 12f
}

private const val SIG = 8
private const val MATCH_DISTANCE = 26f
