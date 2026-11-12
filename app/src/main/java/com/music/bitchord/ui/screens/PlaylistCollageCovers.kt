package com.music.bitchord.ui.screens

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX
import kotlin.random.Random

/** Covers in a generated playlist image: one in front, the rest floating round it. */
internal const val COLLAGE_MAX_COVERS = 9

/**
 * Whether a playlist page with this id can have a generated image: one of
 * YouTube Music's (VL…) or one of Spotify's, a mix included. Liked music is
 * the caller's to add, as its ids differ by where it is read from.
 */
internal fun playlistTakesGeneratedImage(browseId: String): Boolean =
    browseId.startsWith("VL") || browseId.startsWith(SPOTIFY_PAGE_PREFIX)

/**
 * The covers a generated playlist image is made of, newest addition first.
 *
 * Which end of a playlist is newest: a dated row (a device track, a download)
 * says so itself; otherwise a liked list leads with its newest like, and any
 * other playlist is appended to, so its foot is the newest.
 *
 * No artwork, album or artist appears twice: a playlist of one album would
 * otherwise be the same sleeve nine times. If that leaves too few, artists
 * are allowed to repeat (with different albums), but artwork never is.
 *
 * With [shuffle], the songs are drawn in a random order instead, under the
 * same rules: the cover generator's "pick again".
 */
internal fun collageCovers(
    songs: List<Song>,
    newestFirst: Boolean,
    max: Int = COLLAGE_MAX_COVERS,
    shuffle: Random? = null,
): List<String> {
    val withArt = songs.filter { !it.thumbnailUrl.isNullOrBlank() }
    val ordered = if (shuffle != null) {
        withArt.shuffled(shuffle)
    } else if (withArt.any { it.localDateAddedSeconds != null }) {
        withArt.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<Song>> { it.value.localDateAddedSeconds ?: Long.MIN_VALUE }
                    .thenBy { if (newestFirst) it.index else -it.index },
            )
            .map { it.value }
    } else if (newestFirst) {
        withArt
    } else {
        withArt.asReversed()
    }

    val picked = ArrayList<Song>()
    val arts = HashSet<String>()
    val albums = HashSet<String>()
    val artists = HashSet<String>()
    fun take(strictArtist: Boolean) {
        for (song in ordered) {
            if (picked.size >= max) return
            val art = song.artKey() ?: continue
            val album = song.albumKey()
            val artist = song.artistKey()
            if (art in arts) continue
            if (album != null && album in albums) continue
            if (strictArtist && artist != null && artist in artists) continue
            picked += song
            arts += art
            album?.let { albums += it }
            artist?.let { artists += it }
        }
    }
    take(strictArtist = true)
    if (picked.size < max) take(strictArtist = false)
    return picked.mapNotNull { it.thumbnailUrl }
}

/** The artwork with its size hint taken out, so two sizes of one picture match. */
private fun Song.artKey(): String? = artworkKey(thumbnailUrl)

/** [url] with its size hint taken out, so two sizes of one picture match. */
internal fun artworkKey(url: String?): String? = url?.artworkAt(1)?.substringBefore('?')

private fun Song.albumKey(): String? =
    albumId?.takeIf { it.isNotBlank() } ?: albumName?.takeIf { it.isNotBlank() }?.lowercase()

private fun Song.artistKey(): String? =
    (artists.firstOrNull()?.name ?: artist).takeIf { it.isNotBlank() }?.trim()?.lowercase()
