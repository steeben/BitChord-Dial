package com.music.bitchord.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Music videos standing behind the song that replaced them, kept so their
 * picture can still be the player's Canvas.
 *
 * Tapping a music video plays its official song instead when one is found
 * (the higher quality audio), and until now the video's picture went with
 * it. Keyed by the song's video id, so whichever surface draws the Canvas
 * can ask "is there a music video for what's playing?" without knowing how
 * it got there. The player only falls back to it when the song has no Canvas
 * of its own — see NowPlayingScreen.
 *
 * Scoped to this app session, as the song-version conversion is.
 */
object VideoCanvases {
    private val _pictures = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Song video id → the music video whose picture can be its Canvas. */
    val pictures: StateFlow<Map<String, String>> = _pictures.asStateFlow()

    fun pictureFor(songVideoId: String): String? = _pictures.value[songVideoId]

    fun link(songVideoId: String, musicVideoId: String) {
        if (songVideoId == musicVideoId) return
        if (_pictures.value[songVideoId] == musicVideoId) return
        _pictures.value += songVideoId to musicVideoId
    }
}
