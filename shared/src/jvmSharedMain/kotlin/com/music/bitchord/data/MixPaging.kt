package com.music.bitchord.data

import com.music.bitchord.data.model.Song

/**
 * Gives YouTube's endless mixes an end.
 *
 * My Supermix, My Mix 1–7, Discover Mix and the other radio-style `RD…`
 * playlists are not lists with a last row: every page comes with a token for
 * another, and YouTube keeps dealing songs for as long as it is asked, new
 * ones or the same ones under fresh row ids. Followed like an ordinary
 * playlist, the page grew past 5000 songs and never stopped. The official app
 * shows such a mix as a list of at most [MAX_SONGS], so that is where the
 * paging stops here too, or sooner when a page brings nothing new.
 *
 * Every loader (the open page, the playback service, the download sync) pages
 * by token alone, and two of them can ask for the same token. So what each
 * token has already listed is kept against the token itself, never changed
 * once written: asking twice gives the same answer twice.
 */
class MixPaging(private val maxSongs: Int = MAX_SONGS) {
    data class Page(val songs: List<Song>, val continuation: String?)

    private val lock = Any()

    /** Video ids listed before each token's page, for the tokens of mixes only. */
    private val listedBefore = object : LinkedHashMap<String, Set<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<String>>?) =
            size > TRACKED_TOKENS
    }

    /** The first page of [browseId], cut and tracked when it is an endless mix. */
    fun first(browseId: String, songs: List<Song>, continuation: String?): Page =
        if (isEndlessMix(browseId)) accept(emptySet(), songs, continuation) else Page(songs, continuation)

    /**
     * The page [token] led to. Left as it is unless the token belongs to a mix.
     *
     * [browseId] is the list being paged, where the caller knows it. A mix's
     * token this has no record of was saved by an earlier run of the app (the
     * playback service restores its queue's token), possibly thousands of
     * songs into a walk that had no end: that walk stops here.
     */
    fun next(token: String, songs: List<Song>, continuation: String?, browseId: String? = null): Page {
        val listed = synchronized(lock) { listedBefore[token] }
            ?: return if (isEndlessMix(browseId)) Page(emptyList(), null) else Page(songs, continuation)
        return accept(listed, songs, continuation)
    }

    private fun accept(listed: Set<String>, songs: List<Song>, continuation: String?): Page {
        val seen = HashSet(listed)
        // A mix never lists one song twice on purpose, so a repeat is told by
        // its video id: the row id is new every time YouTube deals it again.
        val added = songs.filter { seen.add(it.videoId) }.take((maxSongs - listed.size).coerceAtLeast(0))
        val total = listed.size + added.size
        val more = continuation?.takeIf { added.isNotEmpty() && total < maxSongs }
        if (more != null) {
            val ids = listed + added.map { it.videoId }
            synchronized(lock) { listedBefore[more] = ids }
        }
        return Page(added, more)
    }

    companion object {
        /** The longest list the official app shows for a mix. */
        const val MAX_SONGS = 100

        private const val TRACKED_TOKENS = 64

        /**
         * Whether [browseId] is a mix YouTube generates without end: any `RD…`
         * playlist except the `RDCLAK…` ones, which are editors' playlists
         * with a real last track.
         */
        fun isEndlessMix(browseId: String?): Boolean {
            val id = browseId?.removePrefix("VL") ?: return false
            return id.startsWith("RD") && !id.startsWith("RDCLAK")
        }
    }
}
