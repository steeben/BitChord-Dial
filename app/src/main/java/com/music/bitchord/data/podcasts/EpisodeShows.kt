package com.music.bitchord.data.podcasts

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.PODCAST_SHOW_PREFIX
import com.music.bitchord.data.model.Song

/** Finds the show a podcast episode is of. */
object EpisodeShows {

    /** Shows already found by name, so the same name is not searched twice. */
    private val byName = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * The browse id ("MPSP…") of the show [song] is an episode of: what its
     * row said, or the show page it was played from, or else the show
     * YouTube Music finds by the show's name. Null when none of these gives one.
     */
    suspend fun idOf(song: Song): String? {
        knownIdOf(song)?.let { return it }
        val name = song.artist.trim().takeIf { it.isNotEmpty() } ?: return null
        byName[name]?.let { return it }
        val found = YtMusicRepository.searchShows(name).getOrNull().orEmpty()
        val id = PodcastSources.match(name, found)?.browseId ?: return null
        byName[name] = id
        return id
    }

    /** The show id [song] carries itself, with no request. */
    fun knownIdOf(song: Song): String? =
        listOf(song.episode?.showId, song.playbackSourceId, song.artistId)
            .firstOrNull { it?.startsWith(PODCAST_SHOW_PREFIX) == true }
}
