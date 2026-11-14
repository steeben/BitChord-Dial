package com.music.bitchord.data.podcasts

import com.music.bitchord.data.model.Song

/**
 * The episode filter on a show's page, in place of Shuffle: all episodes, or
 * only the ones not started, started and not finished, or finished.
 *
 * How much was played is the figure the row's bar shows: the larger of
 * YouTube's and the app's own (see [EpisodeProgress.shown]). An episode is
 * finished from [EpisodeProgress.PLAYED_OUT] percent: the rest is its credits.
 */
enum class EpisodeFilter {
    ALL,
    UNPLAYED,
    IN_PROGRESS,
    PLAYED,
    ;

    /** The filter a tap on the pill goes to. */
    fun next(): EpisodeFilter = entries[(ordinal + 1) % entries.size]

    /** Whether [percent] played is in this filter. */
    fun keeps(percent: Int): Boolean = when (this) {
        ALL -> true
        UNPLAYED -> percent <= 0
        IN_PROGRESS -> percent > 0 && percent < EpisodeProgress.PLAYED_OUT
        PLAYED -> percent >= EpisodeProgress.PLAYED_OUT
    }

    /**
     * The episodes of [songs] in this filter, in their order. [playedInApp] is
     * the app's own count by video id. A row that is not an episode stays.
     */
    fun apply(songs: List<Song>, playedInApp: Map<String, Int>): List<Song> {
        if (this == ALL) return songs
        return songs.filter { song ->
            val episode = song.episode ?: return@filter true
            keeps(EpisodeProgress.shown(episode.playedPercent, playedInApp[song.videoId]))
        }
    }
}
