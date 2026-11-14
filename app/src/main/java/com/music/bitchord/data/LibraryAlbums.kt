package com.music.bitchord.data

import com.music.bitchord.data.model.Song

/** An album found among a list of songs: its name, who it is by, and its tracks. */
data class LibraryAlbum(val name: String, val artist: String, val tracks: List<Song>)

/**
 * The albums among a list of songs, for the Search tab's Library and Addons
 * sources. An album is the songs that have the same album tag; see
 * [LibraryArtists.albumArtist] for who it is by.
 */
object LibraryAlbums {
    /**
     * The albums among [songs] whose name has [query] in it. Names that start
     * with the query come first, then the rest, both in name order. Empty for
     * a blank query.
     */
    fun search(songs: List<Song>, query: String): List<LibraryAlbum> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        val known = LibraryArtists.knownArtists(songs.map { it.artist })
        return songs
            .filter { it.albumName?.contains(term, ignoreCase = true) == true }
            .groupBy { it.albumName!!.trim() }
            .map { (name, tracks) -> LibraryAlbum(name, LibraryArtists.albumArtist(tracks, known), tracks) }
            .sortedWith(
                compareBy<LibraryAlbum> { !it.name.startsWith(term, ignoreCase = true) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
    }
}
