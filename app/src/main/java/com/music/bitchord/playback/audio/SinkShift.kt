package com.music.bitchord.playback.audio

import java.util.concurrent.atomic.AtomicLong

/**
 * A sample-exact nudge forward for one player's audio, asked for on the app
 * thread and carried out by its [PrecisionAudioSink] on the audio thread.
 *
 * Exists for the blended quality upgrade, which has to line a silent stand-by
 * player up against the playing one to within a few milliseconds before
 * crossing them over. Neither of the tools a player offers can do that: a seek
 * flushes the sink, throws its AudioTrack away and resumes after a delay that
 * is itself tens of milliseconds and different every time, and a speed change
 * reconfigures the track the same way. Both were tried, and both left the two
 * copies further apart than they started.
 *
 * Dropping frames at the sink's input moves the audio by exactly that many
 * frames with nothing flushed and no track rebuilt. The sink's own clock does
 * not see frames it was never handed, so [appliedUs] is added back onto the
 * position it reports — the player's idea of where it is stays true to what
 * is coming out of it. Both are cleared by a flush, after which the position
 * comes off the seek again.
 */
class SinkShift {
    private val requestedUs = AtomicLong(0L)

    /** How far forward the audio has been moved since the last flush, in media microseconds. */
    @Volatile
    var appliedUs: Long = 0L
        internal set

    /** Moves the audio [us] forward, from the next buffer the sink is handed. App thread. */
    fun skipAhead(us: Long) {
        if (us > 0L) requestedUs.addAndGet(us)
    }

    /** Whether a requested skip is still waiting for audio to drop. */
    val isPending: Boolean get() = requestedUs.get() > 0L

    internal fun takeRequestUs(): Long = requestedUs.getAndSet(0L)

    internal fun clear() {
        requestedUs.set(0L)
        appliedUs = 0L
    }
}
