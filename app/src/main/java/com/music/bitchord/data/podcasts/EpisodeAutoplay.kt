package com.music.bitchord.data.podcasts

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.EpisodeInfo
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.Song

/**
 * What Autoplay plays after a podcast episode: the show's next episode (the
 * one that came out after it), then the one after that; when the show has no
 * newer episode, episodes of the shows YouTube Music recommends, one for each
 * show. Never songs.
 */
object EpisodeAutoplay {

    /** Up to [limit] episodes to follow [seed], none of them already in [existing]. */
    suspend fun tracks(existing: List<Song>, seed: Song, limit: Int): List<Song> {
        if (limit <= 0) return emptyList()
        val queued = existing.mapTo(HashSet()) { it.videoId } + seed.videoId
        val showId = EpisodeShows.idOf(seed)
        val showEpisodes = showId?.let { YtMusicRepository.browseSongs(it).getOrNull()?.songs }.orEmpty()
        val next = EpisodeOrder.newerThan(seed.videoId, showEpisodes)
            .filter { it.videoId !in queued }
            .map { song -> song.copy(episode = (song.episode ?: EpisodeInfo()).copy(showId = showId)) }
        if (next.isNotEmpty()) return next.take(limit)
        val shelves = YtMusicRepository.podcastsHome().getOrNull()?.shelves.orEmpty() +
            YtMusicRepository.podcastsLanding().getOrNull().orEmpty()
        return EpisodeOrder.recommended(shelves, seed, queued, limit)
    }
}

/** The order rules of [EpisodeAutoplay]. Pure. */
object EpisodeOrder {

    /**
     * The episodes that came out after [videoId], the next one first.
     * [newestFirst] is a show's list as YouTube Music gives it, newest at the
     * top. Empty when [videoId] is the newest, or is not in the list.
     */
    fun newerThan(videoId: String, newestFirst: List<Song>): List<Song> {
        val at = newestFirst.indexOfFirst { it.videoId == videoId }
        if (at <= 0) return emptyList()
        return newestFirst.subList(0, at).asReversed().toList()
    }

    /**
     * Recommended episodes from the podcast feed's [shelves], in shelf order:
     * one for each show, none of [seed]'s own show, none in [queued].
     */
    fun recommended(shelves: List<HomeShelf>, seed: Song, queued: Set<String>, limit: Int): List<Song> {
        val seedShow = PodcastSources.key(seed.artist)
        val shows = HashSet<String>()
        return shelves.asSequence()
            .flatMap { it.items.asSequence() }
            // An episode card has a play id and a length; a show card has neither.
            .filter { it.videoId != null && it.durationText != null }
            .filter { it.videoId !in queued }
            .filter { item ->
                val show = PodcastSources.key(item.subtitle)
                (show.isEmpty() || show != seedShow) && (show.isEmpty() || shows.add(show))
            }
            .distinctBy { it.videoId }
            .take(limit)
            .map { item ->
                Song(
                    videoId = item.videoId!!,
                    title = item.title,
                    artist = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl,
                    durationText = item.durationText,
                    episode = EpisodeInfo(),
                )
            }
            .toList()
    }
}
