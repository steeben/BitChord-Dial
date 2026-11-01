package com.music.bitchord.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Music videos the listener converted to audio when no song version of them
 * could be found.
 *
 * The track keeps playing as the same video upload: its audio is already the
 * audio, so nothing is re-streamed. What changes is that the picture is no
 * longer shown anywhere: the player, the mini player and the video engine all
 * treat the track as a song, and its thumbnail is cropped into the album art.
 *
 * Scoped to this app session, as the song-version conversion is.
 */
object AudioOnlyVideos {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    operator fun contains(videoId: String): Boolean = videoId in _ids.value

    fun add(videoId: String) {
        _ids.value += videoId
    }

    fun remove(videoId: String) {
        _ids.value -= videoId
    }
}
