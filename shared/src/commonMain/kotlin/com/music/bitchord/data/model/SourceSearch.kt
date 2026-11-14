package com.music.bitchord.data.model

/**
 * The top result a source names itself (Spotify gives one): the place of a
 * song in the source's songs, or one of its artist / album / playlist rows.
 */
data class SearchTopPick(val songIndex: Int = -1, val item: BrowseItem? = null)

/**
 * The Spotify, Library and Addons searches drawn as the YouTube search is: a
 * top result card and filter tabs. Those sources give all their hits at one
 * time, so the tabs and the card are worked out here from the hits.
 */
object SourceSearch {
    /** Rows of each kind on the All tab when the tabs give the full lists. */
    const val PREVIEW_ROWS = 5

    private val KINDS = listOf(
        SearchFilter.ARTISTS to BrowseType.ARTIST,
        SearchFilter.ALBUMS to BrowseType.ALBUM,
        SearchFilter.PLAYLISTS to BrowseType.PLAYLIST,
    )

    /** The kind of rows a tab lists; null for All, Songs and Videos. */
    fun browseType(filter: SearchFilter): BrowseType? = KINDS.firstOrNull { it.first == filter }?.second

    /**
     * The tabs for these hits, in the YouTube search's order. Only a kind
     * that has hits gets a tab. Hits of one kind need no tabs, so the list
     * is then empty.
     */
    fun filters(songs: List<Song>, browse: List<BrowseItem>): List<SearchFilter> {
        val kinds = buildList {
            if (songs.isNotEmpty()) add(SearchFilter.SONGS)
            KINDS.forEach { (filter, type) -> if (browse.any { it.type == type }) add(filter) }
        }
        if (kinds.size < 2) return emptyList()
        return SearchFilter.entries.filter { it == SearchFilter.ALL || it in kinds }
    }

    /**
     * The hit for the top result card: the one whose name agrees best with
     * [query]. For equal names an artist comes before a song, a song before
     * an album, and an album before a playlist; then the source's own order
     * decides. [pick] is the source's own top result, used when it is given
     * and is among the hits.
     */
    fun topResult(
        query: String,
        songs: List<Song>,
        browse: List<BrowseItem>,
        pick: SearchTopPick? = null,
    ): SearchResult? {
        pick?.item?.let { item -> if (item in browse) return SearchResult.Browse(item) }
        pick?.let { songs.getOrNull(it.songIndex) }?.let { return SearchResult.TopTrack(it) }
        val term = fold(query)
        if (term.isEmpty()) return null
        var best: SearchResult? = null
        var bestScore = -1
        fun offer(score: Int, hit: SearchResult) {
            if (score > bestScore) {
                bestScore = score
                best = hit
            }
        }
        browse.filter { it.type == BrowseType.ARTIST }.forEach { offer(nameScore(it.title, term), SearchResult.Browse(it)) }
        songs.forEach { offer(nameScore(it.title, term), SearchResult.TopTrack(it)) }
        browse.filter { it.type == BrowseType.ALBUM }.forEach { offer(nameScore(it.title, term), SearchResult.Browse(it)) }
        browse.filter { it.type == BrowseType.PLAYLIST }.forEach { offer(nameScore(it.title, term), SearchResult.Browse(it)) }
        // No name agrees (the hits matched on an album or artist field): the
        // first song is the best the source gave.
        if (bestScore == 0) songs.firstOrNull()?.let { return SearchResult.TopTrack(it) }
        return best
    }

    /**
     * The YouTube Music album for a Spotify album: the first of [candidates]
     * with the same name, else the first whose name starts with it or that it
     * starts with ("Album (Deluxe)"). Null when none agrees, so no other
     * album opens in its place.
     */
    fun matchAlbum(title: String, candidates: List<BrowseItem>): BrowseItem? {
        val wanted = fold(title)
        if (wanted.isEmpty()) return null
        val albums = candidates.filter { it.type == BrowseType.ALBUM }
        return albums.firstOrNull { fold(it.title) == wanted }
            ?: albums.firstOrNull { fold(it.title).let { name -> name.startsWith(wanted) || wanted.startsWith(name) } }
    }

    /** 4 the same name, 3 starts with it, 2 a word starts with it, 1 has it, 0 none. */
    private fun nameScore(name: String, term: String): Int {
        val folded = fold(name)
        return when {
            folded == term -> 4
            folded.startsWith(term) -> 3
            folded.contains(" $term") -> 2
            folded.contains(term) -> 1
            else -> 0
        }
    }

    /** Lower case, letters and digits only, one space between words. */
    private fun fold(text: String): String =
        text.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
}
