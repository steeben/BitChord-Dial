package com.music.bitchord.data

import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import java.util.Locale

/**
 * What of one artist is on this device: the files and downloads crediting
 * them, and the albums those are off. For an artist page's Local toggle.
 */
data class ArtistOnDevice(
    val songs: List<Song>,
    /** Cards for [DeviceArtists.albums], each opening a page of its device tracks. */
    val albums: List<ShelfItem> = emptyList(),
)

/**
 * Finding an artist among the device's own songs.
 *
 * An artist page from YouTube Music lists that artist's popular tracks and
 * releases, so narrowing it to what is on the device found only the few of
 * those that were downloads: a file is never one of YouTube Music's tracks,
 * so none ever showed. This looks the artist up on the device instead, the
 * way the library's Artists tab groups it.
 */
object DeviceArtists {
    /**
     * The songs among [device] by the artist [name]: credited by name, split
     * the way [LibraryArtists] reads a credit, or tied to [browseId] by id (a
     * download, or a file linked to YouTube Music; see [LocalLinks]). The same
     * song twice, a download and a file, is kept once.
     */
    fun songs(device: List<Song>, browseId: String?, name: String): List<Song> {
        val key = LibraryArtists.key(name)
        val known = LibraryArtists.knownArtists(device.map { it.artist }) + key
        val found = ArrayList<Song>()
        device.distinctBy { it.videoId }.forEach { song ->
            if (!song.credits(browseId, key, known)) return@forEach
            if (found.none { LibraryDuplicates.sameSong(it, song) }) found += song
        }
        return found.sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    /**
     * The albums [songs] are off that are [name]'s own, each with every track
     * of it on [device], in the order the device lists them. An album that is
     * someone else's (a compilation, a guest spot) stays out: its song is in
     * [songs] all the same.
     */
    fun albums(device: List<Song>, songs: List<Song>, name: String): List<Pair<String, List<Song>>> {
        val key = LibraryArtists.key(name)
        val known = LibraryArtists.knownArtists(device.map { it.artist }) + key
        val wanted = songs.mapNotNullTo(HashSet()) { it.albumKey() }
        if (wanted.isEmpty()) return emptyList()
        return device
            .distinctBy { it.videoId }
            .filter { it.albumKey() in wanted }
            .groupBy { it.albumKey()!! }
            .values
            .filter { tracks -> LibraryArtists.key(LibraryArtists.albumArtist(tracks, known)) == key }
            .map { tracks -> tracks.first().albumName!!.trim() to tracks }
            .sortedBy { (album, _) -> album.lowercase(Locale.ROOT) }
    }

    private fun Song.albumKey(): String? = albumName?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)

    private fun Song.credits(browseId: String?, key: String, known: Set<String>): Boolean =
        (browseId != null && (artistId == browseId || artists.any { it.browseId == browseId })) ||
            artists.any { LibraryArtists.key(it.name) == key } ||
            LibraryArtists.split(artist, known).any { LibraryArtists.key(it) == key }
}
