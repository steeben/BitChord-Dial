package com.music.bitchord.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.data.CanvasAudioFixes
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.smart.AutomixAnalysisSource
import com.music.bitchord.playback.smart.VersionAudioAligner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where a music video standing in as a song's Canvas has to sit to match the
 * song: the shift between the video's own audio and the song's, measured the
 * same way a version switch is — see [VersionAudioAligner].
 *
 * Offsets read `song time = video time + offset`, so the picture is shown at
 * `song position − offset`.
 */
@UnstableApi
object CanvasPictureSync {
    private const val HEAD_TIMEOUT_MS = 20_000L
    private const val MEASURE_TIMEOUT_MS = 30_000L

    /** The shift known for this pair right now: the listener's fix first, then a measurement. */
    fun knownOffsetMs(pictureId: String, songId: String): Long? =
        CanvasAudioFixes.syncOffset(pictureId, songId)
            ?: VersionAudioAligner.getCachedOffsetMs(pictureId, songId)

    /**
     * Measures the pair, fetching both openings first. [force] throws away
     * an earlier measurement, for when the listener says it came out wrong.
     */
    suspend fun measure(context: Context, pictureId: String, song: Song, force: Boolean = false): Long? {
        if (force) VersionAudioAligner.forget(pictureId, song.videoId)
        val picture = Song(videoId = pictureId, title = song.title, artist = song.artist, thumbnailUrl = null)
        return withContext(Dispatchers.IO) {
            val heads = listOf(picture, song).filter { it.localUri == null }.map { track ->
                async {
                    AudioCache.awaitAnalysisHead(
                        Uri.parse(AutomixAnalysisSource.opusUri(track.videoId)),
                        HEAD_TIMEOUT_MS,
                    )
                }
            }
            heads.forEach { it.await() }
            val measured = withTimeoutOrNull(MEASURE_TIMEOUT_MS) {
                VersionAudioAligner.findOffsetMs(context, picture, song)
            }
            TrackLog.d(
                "MusicVideo",
                "canvas picture $pictureId measured against the song: ${measured ?: "nothing"}",
                song.videoId,
            )
            measured
        }
    }
}
