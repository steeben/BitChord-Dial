package com.music.bitchord.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * AI Mix: the queue played as a DJ set of 30-second samples.
 *
 * Each track plays one [SAMPLE_MS] window, its hook rather than its intro,
 * and Automix blends it into the next track's window — see
 * [CrossfadeController], which owns the timing. Nothing about the queue
 * itself changes: the tracks are the same items, so a sample can be skipped,
 * liked or queued like any other row, and the mix ends the moment something
 * else is played.
 *
 * Every menu that starts a mix starts a queue first (a radio, an album, a
 * playlist), and that queue is built asynchronously. So a menu [arm]s the mix
 * and the next queue to start takes it up in [onNewQueue]; a queue started
 * any other way ends it. The player's own menu has its queue already and
 * calls [startNow].
 *
 * Process-wide: the UI and the playback service share a process, the same as
 * [BoundedContextRequests].
 */
object AiMix {
    /** How long each track plays, its blend into the next included. */
    const val SAMPLE_MS = 30_000L

    /** How long two samples overlap. A third of a sample reads as a mix, not a skip. */
    const val BLEND_MS = 8_000L

    /** How long an [arm] waits for its queue before it lapses. */
    private const val ARM_TIMEOUT_MS = 30_000L

    /** How close together two queue starts count as the same queue. */
    private const val SAME_QUEUE_MS = 5_000L

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    @Volatile private var armedAt = 0L

    /** When the running mix took up its queue; see [onNewQueue]. */
    @Volatile private var startedAt = 0L

    /** Where each track's sample starts, in ms, fixed once chosen so the window can't drift while it plays. */
    private val windows = ConcurrentHashMap<String, Long>()

    /** The next queue to start plays as a mix. */
    fun arm() {
        armedAt = SystemClock.elapsedRealtime()
    }

    /**
     * Called as any queue starts. An armed mix begins with it; otherwise the
     * listener chose something else, and a mix already running ends.
     */
    fun onNewQueue() {
        val now = SystemClock.elapsedRealtime()
        if (armedAt != 0L && now - armedAt <= ARM_TIMEOUT_MS) {
            armedAt = 0L
            startedAt = now
            windows.clear()
            SampleWindows.clear()
            _active.value = true
            return
        }
        armedAt = 0L
        // A radio clears the old queue and then plays the new one, two starts
        // for one queue: the second must not end the mix the first began.
        if (_active.value && now - startedAt <= SAME_QUEUE_MS) return
        windows.clear()
        SampleWindows.clear()
        _active.value = false
    }

    /** Mixes the queue already playing, the current track's sample starting where it stands. */
    fun startNow(currentMediaId: String?, positionMs: Long) {
        armedAt = 0L
        windows.clear()
        SampleWindows.clear()
        if (currentMediaId != null) windows[currentMediaId] = positionMs.coerceAtLeast(0L)
        _active.value = true
    }

    fun stop() {
        armedAt = 0L
        windows.clear()
        SampleWindows.clear()
        _active.value = false
    }

    /**
     * Where [mediaId]'s sample starts. [choose] is asked once, and only while
     * the track's length is known: a guess made without it would be fixed in
     * place for good.
     */
    fun windowStart(mediaId: String, durationMs: Long, choose: () -> Long): Long {
        windows[mediaId]?.let { return it }
        if (durationMs <= 0L) return 0L
        return choose().also { windows[mediaId] = it }
    }
}
