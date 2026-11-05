package com.music.bitchord.data

import com.music.bitchord.data.innertube.UploadedTracks
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Title, artist, album and cover the listener took from a YouTube Music
 * search for a song of their own: a file on the device, or a track they
 * uploaded to their YouTube Music library. Both arrive with whatever their
 * tags said, often a file name and "Unknown Artist", and neither has a
 * catalogue entry that could correct them. Offered from Song fixes.
 *
 * A file is keyed by its content uri, an upload by its video id. [apply] is
 * called wherever such a song is built (the device library, the parsed
 * library rows, the player's queue items), so one pick renames it everywhere.
 *
 * The app loads the stored picks at start and saves each change through
 * [persist]; nothing here knows where they live.
 */
object SongMetadataFixes {
    /** The details a song shows. [matchedVideoId] is the catalogue track they came from. */
    data class Details(
        val title: String,
        val artist: String,
        val albumName: String? = null,
        val albumId: String? = null,
        val artistId: String? = null,
        val thumbnailUrl: String? = null,
        val matchedVideoId: String? = null,
    ) {
        companion object {
            fun of(song: Song) = Details(
                title = song.title,
                artist = song.artist,
                albumName = song.albumName,
                albumId = song.albumId,
                artistId = song.artistId,
                thumbnailUrl = song.thumbnailUrl,
                matchedVideoId = song.videoId,
            )
        }
    }

    /** A pick, and what the song showed before it, so it can be undone on a song already queued. */
    data class Fix(val details: Details, val original: Details)

    private val _fixes = MutableStateFlow<Map<String, Fix>>(emptyMap())
    val fixes: StateFlow<Map<String, Fix>> = _fixes.asStateFlow()

    /** Where changes are written; set by the app at start. */
    @Volatile
    var persist: ((Map<String, Fix>) -> Unit)? = null

    fun load(fixes: Map<String, Fix>) {
        _fixes.value = fixes
    }

    /**
     * The key [song] is fixed under, or null when it isn't a song of the
     * listener's own. A download of a YouTube track has a file too, but its
     * details are the catalogue's already.
     */
    fun keyOf(song: Song): String? {
        val file = song.localUri ?: song.videoId.takeIf { it.startsWith("content://") || it.startsWith("file://") }
        if (file != null && !song.hasYouTubeId()) return file
        return song.videoId.takeIf { UploadedTracks.contains(it) }
    }

    fun canFix(song: Song): Boolean = keyOf(song) != null

    fun fixOf(song: Song): Fix? {
        val map = _fixes.value
        if (map.isEmpty()) return null
        return song.localUri?.let(map::get) ?: map[song.videoId]
    }

    /** [song] showing the details picked for it, or [song] itself when there's no pick. */
    fun apply(song: Song): Song = fixOf(song)?.let { song.showing(it.details) } ?: song

    /**
     * Gives [song] the details of [match]. Returns [song] as it now reads, or
     * null when it isn't one of the listener's own.
     */
    fun fix(song: Song, match: Song): Song? {
        val key = keyOf(song) ?: return null
        val original = _fixes.value[key]?.original ?: Details.of(song).copy(matchedVideoId = null)
        val details = Details.of(match).let { d ->
            // A search row without a cover keeps the song's own.
            if (d.thumbnailUrl == null) d.copy(thumbnailUrl = original.thumbnailUrl) else d
        }
        update { it + (key to Fix(details, original)) }
        return song.showing(details)
    }

    /** Forgets [song]'s pick. Returns [song] as it read before it, or null when it had none. */
    fun restore(song: Song): Song? {
        val fix = fixOf(song) ?: return null
        update { map -> map.filterValues { it !== fix } }
        return song.showing(fix.original)
    }

    private fun Song.showing(details: Details): Song = copy(
        title = details.title,
        artist = details.artist,
        albumName = details.albumName,
        albumId = details.albumId,
        artistId = details.artistId,
        // The credits a row carried were for the old name.
        artists = emptyList(),
        thumbnailUrl = details.thumbnailUrl ?: thumbnailUrl,
    )

    private fun Song.hasYouTubeId(): Boolean =
        videoId.length == 11 && videoId.none { it == ':' || it == '/' }

    private fun update(change: (Map<String, Fix>) -> Map<String, Fix>) {
        val before = _fixes.value
        val after = change(before)
        if (after == before) return
        _fixes.value = after
        persist?.invoke(after)
    }
}
