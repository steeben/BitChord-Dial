package com.music.bitchord.data.scrobbling

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToLong

class ScrobbleManager(
    private val scope: CoroutineScope,
    var minSongDuration: Int = 30,
    var scrobbleDelayPercent: Float = 0.5f,
    var scrobbleDelaySeconds: Int = 180,
) {
    private var scrobbleJob: Job? = null
    private var scrobbleRemainingMillis: Long = 0L
    private var scrobbleTimerStartedAt: Long = 0L
    private var songStartedAt: Long = 0L
    private var songStarted = false

    /** The playing song's length as [startScrobbleTimer] settled it, for the resume after a pause. */
    private var songDurationSeconds = 0

    /**
     * Scrobbles that never reached the server, oldest first, sent again behind
     * the next one that does. Only ones the network lost: a scrobble the
     * service itself refused would be refused again. Kept in memory, so a
     * process that dies with some waiting still loses them, as all of them
     * were lost before.
     */
    private val unsent = ArrayDeque<Unsent>()

    private class Unsent(
        val artist: String,
        val track: String,
        val album: String?,
        val durationSeconds: Int,
        val startedAt: Long,
    )

    var useNowPlaying = true
    var usePrimaryArtistOnly = false

    fun destroy() {
        scrobbleJob?.cancel()
        scrobbleRemainingMillis = 0L
        scrobbleTimerStartedAt = 0L
        songStartedAt = 0L
        songStarted = false
    }

    fun onSongStart(
        song: Song?,
        durationMs: Long? = null,
    ) {
        if (song == null) return
        songStartedAt = System.currentTimeMillis() / 1000
        songStarted = true
        startScrobbleTimer(song, durationMs)
        if (useNowPlaying) {
            updateNowPlaying(song)
        }
    }

    fun onSongResume(song: Song) {
        resumeScrobbleTimer(song)
    }

    fun onSongPause() {
        pauseScrobbleTimer()
    }

    fun onSongStop() {
        stopScrobbleTimer()
        songStarted = false
    }

    private fun startScrobbleTimer(
        song: Song,
        durationMs: Long? = null,
    ) {
        scrobbleJob?.cancel()
        val resolvedDurationSeconds = durationMs?.toInt()?.div(1000)
            ?: song.durationText?.let { parseDurationSeconds(it) }
            ?: return

        songDurationSeconds = resolvedDurationSeconds
        if (resolvedDurationSeconds <= minSongDuration) return

        val thresholdMs = (resolvedDurationSeconds * 1000L * scrobbleDelayPercent).roundToLong()
        scrobbleRemainingMillis = min(thresholdMs, scrobbleDelaySeconds * 1000L)

        if (scrobbleRemainingMillis <= 0) {
            scrobbleSong(song, resolvedDurationSeconds)
            return
        }
        scrobbleTimerStartedAt = System.currentTimeMillis()
        scrobbleJob =
            scope.launch {
                delay(scrobbleRemainingMillis)
                scrobbleSong(song, resolvedDurationSeconds)
                scrobbleJob = null
            }
    }

    private fun pauseScrobbleTimer() {
        scrobbleJob?.cancel()
        if (scrobbleTimerStartedAt != 0L) {
            val elapsed = System.currentTimeMillis() - scrobbleTimerStartedAt
            scrobbleRemainingMillis -= elapsed
            if (scrobbleRemainingMillis < 0) scrobbleRemainingMillis = 0
            scrobbleTimerStartedAt = 0L
        }
    }

    private fun resumeScrobbleTimer(song: Song) {
        if (scrobbleRemainingMillis <= 0) return
        scrobbleJob?.cancel()
        scrobbleTimerStartedAt = System.currentTimeMillis()
        scrobbleJob =
            scope.launch {
                delay(scrobbleRemainingMillis)
                // The length the timer was started with: the text alone is
                // missing for a device file, and that scrobbled a length of 0.
                val durationSeconds = songDurationSeconds.takeIf { it > 0 }
                    ?: song.durationText?.let { parseDurationSeconds(it) }
                    ?: 0
                scrobbleSong(song, durationSeconds)
                scrobbleJob = null
            }
    }

    private fun stopScrobbleTimer() {
        scrobbleJob?.cancel()
        scrobbleJob = null
        scrobbleRemainingMillis = 0
    }

    private fun scrobbleSong(song: Song, durationSeconds: Int) {
        val scrobble = Unsent(
            artist = song.artist.forScrobble(),
            track = song.title,
            album = song.albumName,
            durationSeconds = durationSeconds,
            startedAt = songStartedAt,
        )
        scope.launch {
            if (send(scrobble)) sendUnsent() else keep(scrobble)
        }
    }

    /** True when it arrived, or was refused for good; false when it is worth another try. */
    private suspend fun send(scrobble: Unsent): Boolean {
        val result = LastFM.scrobble(
            artist = scrobble.artist,
            track = scrobble.track,
            duration = scrobble.durationSeconds,
            timestamp = scrobble.startedAt,
            album = scrobble.album,
        )
        val failure = result.exceptionOrNull()
        if (failure == null) {
            Log.d(TAG, "Scrobbled: ${scrobble.track} by ${scrobble.artist}")
            return true
        }
        if (failure is CancellationException) throw failure
        Log.e(TAG, "Failed to scrobble: ${scrobble.track}", failure)
        // The service answered and said no, or there is no session to send
        // with: the same request would get the same answer.
        return when (failure) {
            is LastFM.LastFmException -> failure.code !in RETRYABLE_CODES && failure.code < 500
            is IllegalStateException, is IllegalArgumentException -> true
            else -> false
        }
    }

    private fun keep(scrobble: Unsent) {
        synchronized(unsent) {
            unsent.addLast(scrobble)
            while (unsent.size > MAX_UNSENT) unsent.removeFirst()
        }
    }

    /** The backlog, behind a scrobble that just got through; stops at the first that does not. */
    private suspend fun sendUnsent() {
        while (true) {
            val next = synchronized(unsent) { unsent.removeFirstOrNull() } ?: return
            if (!send(next)) {
                synchronized(unsent) { unsent.addFirst(next) }
                return
            }
        }
    }

    private fun updateNowPlaying(song: Song) {
        val scrobbleArtist = song.artist.forScrobble()
        scope.launch {
            LastFM
                .updateNowPlaying(
                    artist = scrobbleArtist,
                    track = song.title,
                    album = song.albumName,
                    duration = song.durationText?.let { parseDurationSeconds(it) },
                ).onSuccess {
                    Log.d(TAG, "Updated now playing: ${song.title}")
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to update now playing: ${song.title}", throwable)
                }
        }
    }

    fun onPlayerStateChanged(
        isPlaying: Boolean,
        song: Song?,
        durationMs: Long? = null,
    ) {
        if (song == null) return
        if (isPlaying) {
            if (!songStarted) {
                onSongStart(song, durationMs)
            } else {
                onSongResume(song)
            }
        } else {
            onSongPause()
        }
    }

    private fun parseDurationSeconds(text: String): Int = scrobbleDurationSeconds(text)

    private fun String.forScrobble(): String = if (usePrimaryArtistOnly) primaryArtist() else this

    companion object {
        private const val TAG = "ScrobbleManager"

        /** Last.fm takes fifty scrobbles in one request; no backlog is worth more than that. */
        private const val MAX_UNSENT = 50

        /** Last.fm's own "try again": service offline, temporarily unavailable, rate limited. */
        private val RETRYABLE_CODES = setOf(11, 16, 29)
    }
}

/**
 * "M:SS", "MM:SS" or "H:MM:SS" as seconds; 0 for anything else. Hours used to
 * read as 0 too, and a track that long was then too short to scrobble.
 */
internal fun scrobbleDurationSeconds(text: String): Int {
    val parts = text.trim().split(":").map { it.toIntOrNull() ?: return 0 }
    if (parts.size !in 2..3 || parts.any { it < 0 }) return 0
    return parts.fold(0) { total, part -> total * 60 + part }
}
