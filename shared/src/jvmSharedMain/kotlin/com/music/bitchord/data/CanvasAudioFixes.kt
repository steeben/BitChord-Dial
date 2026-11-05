package com.music.bitchord.data

import com.music.bitchord.data.model.Song
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
        /** Tracks whose Canvas is a music video the listener searched for and picked, by track. */
        val pairedVideo: Map<String, String> = emptyMap(),
        /**
         * The song the listener picked as the right audio, by the id it replaces:
         * a music video whose matched song was the wrong one, or a song itself.
         */
        val songPicks: Map<String, SongPick> = emptyMap(),
        /** Tracks that show their album's animated cover as the Canvas. */
        val albumArtCanvas: Set<String> = emptySet(),
        /** The lyrics' text the listener picked for a track while its album art is behind them. */
        val lyricInkArt: Map<String, LyricInk> = emptyMap(),
        /** The same pick for while a Canvas plays behind them. */
        val lyricInkCanvas: Map<String, LyricInk> = emptyMap(),
    ) {
        fun touches(videoId: String): Boolean =
            videoId in keepVideoAudio || videoId in hiddenCanvas || videoId in hiddenVideoCanvas ||
                videoId in pairedVideo || videoId in songPicks || videoId in albumArtCanvas ||
                syncOffsets.keys.any { it.startsWith("$videoId>") || it.endsWith(">$videoId") }
    }

    /** Enough of a [Song] to play it again after a restart. */
    data class SongPick(
        val videoId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String? = null,
        val durationText: String? = null,
        val albumName: String? = null,
        val albumId: String? = null,
        val artistId: String? = null,
    ) {
        fun toSong(): Song = Song(
            videoId = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumbnailUrl,
            durationText = durationText,
            artistId = artistId,
            albumId = albumId,
            albumName = albumName,
        )

        companion object {
            fun of(song: Song) = SongPick(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                durationText = song.durationText,
                albumName = song.albumName,
                albumId = song.albumId,
                artistId = song.artistId,
            )
        }
    }

    /** Light or dark lyrics text, picked by hand for one song. No pick means the player decides. */
    enum class LyricInk { LIGHT, DARK }

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

    /** The music video the listener picked as [songVideoId]'s Canvas, if they picked one. */
    fun pairedVideo(songVideoId: String): String? = _state.value.pairedVideo[songVideoId]

    /** The song the listener picked to play in place of [videoId], if they picked one. */
    fun songPick(videoId: String): Song? = _state.value.songPicks[videoId]?.toSong()

    fun showsAlbumArt(songVideoId: String): Boolean = songVideoId in _state.value.albumArtCanvas

    /**
     * Makes [musicVideoId] [songVideoId]'s Canvas: it outranks a looked-up clip
     * and any video found on its own, and undoes an earlier "doesn't match".
     */
    fun pairVideo(songVideoId: String, musicVideoId: String) = update {
        it.copy(
            pairedVideo = it.pairedVideo + (songVideoId to musicVideoId),
            hiddenVideoCanvas = it.hiddenVideoCanvas - songVideoId,
            albumArtCanvas = it.albumArtCanvas - songVideoId,
        )
    }

    /** Plays [song] wherever [replacing] would have played, from now on. */
    fun pickSong(replacing: String, song: Song) {
        if (replacing == song.videoId) return
        // A picked song is the answer to "the audio is wrong", so it replaces
        // "play the video's own audio" if that was the answer before.
        update {
            it.copy(
                songPicks = it.songPicks + (replacing to SongPick.of(song)),
                keepVideoAudio = it.keepVideoAudio - replacing,
            )
        }
    }

    /** Shows the album's animated cover as [songVideoId]'s Canvas. */
    fun useAlbumArt(songVideoId: String) = update {
        it.copy(
            albumArtCanvas = it.albumArtCanvas + songVideoId,
            hiddenCanvas = it.hiddenCanvas - songVideoId,
            pairedVideo = it.pairedVideo - songVideoId,
        )
    }

    /** The lyrics text picked for [songVideoId] over its album art ([overCanvas] false) or its Canvas. */
    fun lyricInk(songVideoId: String, overCanvas: Boolean): LyricInk? =
        lyricInkOf(_state.value, songVideoId, overCanvas)

    /** Picks [songVideoId]'s lyrics text over its album art or its Canvas; null goes back to automatic. */
    fun setLyricInk(songVideoId: String, overCanvas: Boolean, ink: LyricInk?) = update { state ->
        val current = if (overCanvas) state.lyricInkCanvas else state.lyricInkArt
        val next = if (ink == null) current - songVideoId else current + (songVideoId to ink)
        if (overCanvas) state.copy(lyricInkCanvas = next) else state.copy(lyricInkArt = next)
    }

    fun lyricInkOf(state: State, songVideoId: String, overCanvas: Boolean): LyricInk? =
        (if (overCanvas) state.lyricInkCanvas else state.lyricInkArt)[songVideoId]

    fun setSyncOffset(musicVideoId: String, songVideoId: String, offsetMs: Long) =
        update { it.copy(syncOffsets = it.syncOffsets + (key(musicVideoId, songVideoId) to offsetMs)) }

    /** Forgets every answer that names any of [videoIds]. */
    fun reset(vararg videoIds: String) = update { state ->
        val ids = videoIds.toSet()
        state.copy(
            keepVideoAudio = state.keepVideoAudio - ids,
            hiddenCanvas = state.hiddenCanvas - ids,
            hiddenVideoCanvas = state.hiddenVideoCanvas - ids,
            pairedVideo = state.pairedVideo - ids,
            songPicks = state.songPicks - ids,
            albumArtCanvas = state.albumArtCanvas - ids,
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
