package com.music.bitchord.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The part of a track AI Mix plays, in the track's own milliseconds. */
data class SampleWindow(val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs
}

/**
 * The windows AI Mix is playing, by media id, so the player's seek bar and
 * times can describe the sample rather than the whole song. Empty when no mix
 * is running. Written by the playback side, read by the player screen.
 */
object SampleWindows {
    private val _windows = MutableStateFlow<Map<String, SampleWindow>>(emptyMap())
    val windows: StateFlow<Map<String, SampleWindow>> = _windows.asStateFlow()

    fun put(mediaId: String, window: SampleWindow) {
        if (window.lengthMs <= 0L) return
        _windows.update { if (it[mediaId] == window) it else it + (mediaId to window) }
    }

    fun clear() {
        _windows.value = emptyMap()
    }
}
