package com.music.bitchord.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the listener told the player about a song whose Canvas and audio
 * don't belong together, kept for good so the same song never needs fixing
 * twice. Offered from the player's ⋯ menu.
 *
 * Every set is keyed by a video id: the music video for [keepVideoAudio], the
 * playing track for the other two.
 *
 * The app loads the stored answers at start and saves each change through
 * [persist]; nothing here knows where they live.
 */
object CanvasAudioFixes {
    data class State(
        /** Music videos whose matched "official song" is a different recording: play the video's own sound. */
        val keepVideoAudio: Set<String> = emptySet(),
        /** Tracks whose looked-up Canvas (Spotify, Apple…) is the wrong clip. */
        val hiddenCanvas: Set<String> = emptySet(),
        /** Tracks whose music video picture is the wrong one to stand in as the Canvas. */
        val hiddenVideoCanvas: Set<String> = emptySet(),
        /** Measured picture-to-audio shifts the listener asked for, by "video>audio". */
        val syncOffsets: Map<String, Long> = emptyMap(),
    ) {
        fun touches(videoId: String): Boolean =
            videoId in keepVideoAudio || videoId in hiddenCanvas || videoId in hiddenVideoCanvas ||
                syncOffsets.keys.any { it.startsWith("$videoId>") || it.endsWith(">$videoId") }
    }

    /** What the Canvas on screen is, so the menu can offer the fix that fits it. */
    enum class Shown { NONE, CANVAS, VIDEO_PICTURE }

    data class Showing(val songVideoId: String, val kind: Shown, val pictureVideoId: String? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Published by the player as it draws, read by the ⋯ menu. */
    val showing = MutableStateFlow<Showing?>(null)

    /** Where changes are written; set by the app at start. */
    @Volatile
    var persist: ((State) -> Unit)? = null

    fun load(state: State) {
        _state.value = state
    }

    fun keepsVideoAudio(musicVideoId: String): Boolean = musicVideoId in _state.value.keepVideoAudio

    fun syncOffset(musicVideoId: String, songVideoId: String): Long? =
        _state.value.syncOffsets[key(musicVideoId, songVideoId)]

    fun keepVideoAudio(musicVideoId: String) = update { it.copy(keepVideoAudio = it.keepVideoAudio + musicVideoId) }

    fun hideCanvas(songVideoId: String) = update { it.copy(hiddenCanvas = it.hiddenCanvas + songVideoId) }

    fun hideVideoCanvas(songVideoId: String) =
        update { it.copy(hiddenVideoCanvas = it.hiddenVideoCanvas + songVideoId) }

    fun setSyncOffset(musicVideoId: String, songVideoId: String, offsetMs: Long) =
        update { it.copy(syncOffsets = it.syncOffsets + (key(musicVideoId, songVideoId) to offsetMs)) }

    /** Forgets every answer that names any of [videoIds]. */
    fun reset(vararg videoIds: String) = update { state ->
        val ids = videoIds.toSet()
        state.copy(
            keepVideoAudio = state.keepVideoAudio - ids,
            hiddenCanvas = state.hiddenCanvas - ids,
            hiddenVideoCanvas = state.hiddenVideoCanvas - ids,
            syncOffsets = state.syncOffsets.filterKeys { key ->
                ids.none { key.startsWith("$it>") || key.endsWith(">$it") }
            },
        )
    }

    private fun key(musicVideoId: String, songVideoId: String) = "$musicVideoId>$songVideoId"

    private inline fun update(change: (State) -> State) {
        val next = change(_state.value)
        if (next == _state.value) return
        _state.value = next
        persist?.invoke(next)
    }
}
