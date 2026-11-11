package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Music videos standing behind a song, kept so their picture can be the
 * player's Canvas.
 *
 * Tapping a music video plays its official song instead when one is found
 * (the higher quality audio), and the video's picture stays as its Canvas.
 * A song started as a song gets one too: [findFor] looks up its music video
 * once the player knows the song has no Canvas of its own. Keyed by the
 * song's video id, so whichever surface draws the Canvas can ask "is there a
 * music video for what's playing?" without knowing how it got there. The
 * player only falls back to it when the song has no Canvas of its own — see
 * NowPlayingScreen. Only a video that calls itself a music video, a lyric
 * video, a visualizer or the like is taken by [findFor]: see [MusicVideoTerms].
 *
 * A song whose own id is a YouTube video (YouTube files some uploads as songs
 * in one place and videos in another) is linked to itself: its own picture
 * is its Canvas.
 *
 * Scoped to this app session, as the song-version conversion is. A video the
 * listener picks by hand is kept for good in [CanvasAudioFixes] instead, and
 * outranks anything here.
 */
object VideoCanvases {
    private val _pictures = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Song video id → the music video whose picture can be its Canvas. */
    val pictures: StateFlow<Map<String, String>> = _pictures.asStateFlow()

    /** Song → the music video it was converted from, when it was. */
    private val sources = ConcurrentHashMap<String, String>()

    /** Songs already looked up this session, found or not. */
    private val looked = ConcurrentHashMap.newKeySet<String>()

    fun pictureFor(songVideoId: String): String? =
        CanvasAudioFixes.pairedVideo(songVideoId) ?: _pictures.value[songVideoId]

    /** The video [songVideoId] was converted from or found to have, not counting a hand-picked one. */
    fun foundFor(songVideoId: String): String? = _pictures.value[songVideoId]

    /** The music video [songVideoId] was played in place of, when it was. */
    fun sourceOf(songVideoId: String): String? = sources[songVideoId]

    /** [songVideoId] replaced the music video [musicVideoId]: its picture is the song's Canvas. */
    fun link(songVideoId: String, musicVideoId: String) {
        if (songVideoId != musicVideoId && musicVideoId.isNotBlank()) sources[songVideoId] = musicVideoId
        linkFound(songVideoId, musicVideoId)
    }

    /**
     * A link kept from an earlier session — the music video saved with a
     * downloaded song as its Canvas — so it shows offline, where [findFor]
     * cannot look it up again.
     */
    fun restore(songVideoId: String, musicVideoId: String) = linkFound(songVideoId, musicVideoId)

    private fun linkFound(songVideoId: String, musicVideoId: String) {
        if (songVideoId.isBlank() || musicVideoId.isBlank()) return
        if (_pictures.value[songVideoId] == musicVideoId) return
        _pictures.value += songVideoId to musicVideoId
    }

    /**
     * Finds a music video for [song] to stand in as its Canvas, once per
     * session. YouTube's own Song/Video pairing first, then a search — the
     * same lookup the player's Video switch makes. A song whose own id is a
     * video is linked to itself.
     *
     * Never throws; a miss is the normal answer.
     */
    suspend fun findFor(song: Song) {
        val id = song.videoId
        if (id.isBlank() || song.isVideo || song.localUri != null) return
        if (pictureFor(id) != null) return
        if (!looked.add(id)) return
        runCatching {
            val pair = YtMusicRepository.versionPair(id)
            if (pair?.isVideo == true) {
                if (!saysItIsAVideo(song.title, id)) {
                    TrackLog.d("Canvas", "'${song.title}' is a video itself, but not one that calls itself a music video: no Canvas from it", id)
                    return
                }
                TrackLog.d("Canvas", "'${song.title}' is a video itself: its own picture is the Canvas", id)
                linkFound(id, id)
                return
            }
            val video = YtMusicRepository.resolveVideo(song) ?: return
            if (!saysItIsAVideo(video.title, video.videoId)) {
                TrackLog.d("Canvas", "'${video.title}' (${video.videoId}) does not call itself a music video: not used as the Canvas", id)
                return
            }
            TrackLog.d("Canvas", "'${song.title}' has no Canvas: its music video ${video.videoId} stands in", id)
            linkFound(id, video.videoId)
        }.onFailure { TrackLog.w("Canvas", "music video lookup failed: ${it.message}", id) }
    }

    /**
     * Whether the upload [videoId] calls itself a music video, a lyric video,
     * a visualizer or the like — see [MusicVideoTerms]. Its title first, which
     * is already in hand; its description is fetched only when the title
     * does not say. Only a video found by looking is asked: one the listener
     * tapped, or picked by hand from the ⋯ menu, is theirs to choose.
     */
    private suspend fun saysItIsAVideo(title: String?, videoId: String): Boolean {
        if (MusicVideoTerms.inTitle(title)) return true
        return MusicVideoTerms.inDescription(SongInfoRepository.description(videoId))
    }

    /** Lets [songVideoId] be looked up again — after the listener undoes their fixes. */
    fun forget(songVideoId: String) {
        looked.remove(songVideoId)
    }
}
