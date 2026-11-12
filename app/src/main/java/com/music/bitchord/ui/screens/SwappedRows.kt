package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.isMatchPending

/**
 * A Spotify playlist's rows go up as Spotify's own tracks and are swapped,
 * one at a time, for their YouTube Music matches. What is here keeps a
 * swapped row the same row to the page, so the match takes over in place
 * (its spinner fading into the track's length) instead of the row being
 * taken down and put up again.
 */

/**
 * The list key of the row at [index] of a page's running order.
 *
 * A page whose rows are swapped in place ([swapsInPlace]) keys them by where
 * they stand alone: keyed by the song as well, a match would be a new row,
 * drawn from scratch where the old one was.
 */
internal fun songRowKey(index: Int, videoId: String, swapsInPlace: Boolean): String =
    if (swapsInPlace) "row_$index" else "${index}_$videoId"

/**
 * The artwork each row of a page was first shown with while it waited on its
 * match, kept for as long as the page is up.
 *
 * The match brings a picture of its own from another address. Changing to it
 * would blank the row's artwork until that one had loaded, on every row in
 * turn, for what is nearly always the same sleeve.
 */
internal class HeldRowArt {
    private val held = HashMap<Int, String>()

    /**
     * The artwork to draw for the songs in [songs], the page's running order
     * as it stands now, by video id. Only rows that have been seen waiting on
     * a match are in it.
     */
    @Synchronized
    fun over(songs: List<Song>): Map<String, String> {
        songs.forEachIndexed { index, song ->
            if (song.isMatchPending) song.thumbnailUrl?.let { held[index] = it }
        }
        if (held.isEmpty()) return emptyMap()
        val art = HashMap<String, String>()
        songs.forEachIndexed { index, song -> held[index]?.let { art[song.videoId] = it } }
        return art
    }
}
