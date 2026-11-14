package com.music.bitchord.data.settings

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem

/**
 * The artists' dial's tiles. Its own file, not MainActivity's, so the unit
 * tests can load it.
 */
object ArtistDialFill {
    /** The library shelves that hold artists, in the order the dial takes them. */
    private val LIBRARY_SHELVES = listOf("Subscriptions", "Artists")

    /**
     * The pins, then the artists played most in the app, then the account's
     * library artists, then the artists in Home's feed. Without the last two a
     * new install (no pins, nothing played yet) had no tiles, and a dial with
     * no tiles is not shown.
     */
    fun tiles(
        pins: List<ShelfItem>,
        mostPlayed: List<ShelfItem>,
        library: List<HomeShelf>,
        feed: List<HomeShelf>,
        hidden: Set<String>,
        size: Int,
    ): List<ShelfItem> {
        val fromLibrary = LIBRARY_SHELVES.flatMap { title ->
            library.filter { it.title.equals(title, ignoreCase = true) }.flatMap { it.items }
        }
        val fill = (fromLibrary + feed.flatMap { it.items }).mapNotNull(::asArtist)
        return (pins + mostPlayed + fill)
            .distinctBy { it.speedDialId }
            .filterNot { it.speedDialId in hidden }
            .take(size)
    }

    /**
     * [item] when it is an artist (a YouTube channel), else null. The library
     * gives some artists as their library page ("MPLA" + the channel id): the
     * dial opens the artist's own page.
     */
    private fun asArtist(item: ShelfItem): ShelfItem? {
        if (item.videoId != null) return null
        val id = item.browseId?.removePrefix("MPLA") ?: return null
        if (!id.startsWith("UC")) return null
        return if (id == item.browseId) item else item.copy(browseId = id)
    }
}
