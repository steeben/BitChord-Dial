package com.music.bitchord.playback.audio

import androidx.media3.common.C
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong

/**
 * Splices a [SpliceFeed] into one player's audio, in its [PrecisionAudioSink],
 * by timestamp: from the first block after [arm], every frame the decoder
 * hands the sink is replaced by the feed's frame for the same moment of the
 * song. One output, one clock, nothing restarted — the new copy takes the old
 * one's place sample for sample, after a crossing too short to hear.
 *
 * Before that it can [requestCapture] a stretch of the player's own audio, as
 * it reaches the sink, so the service can measure the feed against exactly
 * what is being played rather than against a decode of the file that may
 * handle the codec's priming differently.
 *
 * Shared between the app thread (requests) and the audio thread (everything
 * in [onBlock]); state crossing between them is volatile.
 */
class SinkSplice {

    /** The song time (µs) up to which the sink has processed audio; written by the sink. */
    @Volatile var processedMediaUs: Long = C.TIME_UNSET
        private set
    @Volatile var sampleRate: Int = 0
        private set
    @Volatile var channels: Int = 0
        private set

    // ---- Capture ----------------------------------------------------------

    @Volatile private var captureBuffer: FloatArray? = null
    @Volatile private var captured = 0
    @Volatile var captureStartUs: Long = C.TIME_UNSET
        private set
    @Volatile var captureDone = false
        private set

    /** App thread. Records the next [frames] frames the sink processes, as mono. */
    fun requestCapture(frames: Int) {
        captureDone = false
        captured = 0
        captureStartUs = C.TIME_UNSET
        captureBuffer = FloatArray(frames)
    }

    /** The captured mono audio, once [captureDone]. */
    fun capture(): FloatArray? = if (captureDone) captureBuffer else null

    // ---- Splice -----------------------------------------------------------

    @Volatile private var feed: SpliceFeed? = null
    @Volatile private var deltaUs = 0.0
    @Volatile var state = IDLE
        private set
    @Volatile var splicedAtUs: Long = C.TIME_UNSET
        private set
    @Volatile var underrunFrames = 0L
        private set

    private var cursor = 0L
    private var fadeDone = 0
    private var fadeFrames = 0
    private var scratch = FloatArray(0)

    /**
     * App thread. Splices [source] in from the next block, where its time is
     * the song time plus [offsetUs].
     */
    fun arm(source: SpliceFeed, offsetUs: Double) {
        scratch = FloatArray(MAX_BLOCK_FRAMES * source.channels)
        deltaUs = offsetUs
        underrunFrames = 0
        splicedAtUs = C.TIME_UNSET
        feed = source
        state = ARMED
    }

    /** Any thread. Stops splicing; the player's own audio carries on from the next block. */
    fun end() {
        val f = feed
        feed = null
        if (state == ARMED || state == ACTIVE) state = ENDED
        f?.release()
    }

    /** Sink. A flush or a reset: anything in flight belongs to the playback just abandoned. */
    internal fun onDiscontinuity() {
        if (!captureDone) captureBuffer = null
        end()
    }

    /**
     * Audio thread, once per block, between decode and the DSP chain. [mediaUs]
     * is the song time of the block's first frame.
     */
    internal fun onBlock(block: AudioBlock, mediaUs: Long, rate: Int) {
        val frames = block.frameCount
        val ch = block.channelCount
        sampleRate = rate
        channels = ch
        if (frames <= 0 || rate <= 0) return
        processedMediaUs = mediaUs + frames * 1_000_000L / rate

        captureBuffer?.let { buffer ->
            if (!captureDone) {
                if (captured == 0) captureStartUs = mediaUs
                val n = minOf(frames, buffer.size - captured)
                for (f in 0 until n) {
                    var sum = 0f
                    for (c in 0 until ch) sum += block.samples[f * ch + c]
                    buffer[captured + f] = sum / ch
                }
                captured += n
                if (captured >= buffer.size) captureDone = true
            }
        }

        val source = feed ?: return
        if (source.channels != ch || source.rate != rate) {
            end()
            return
        }
        if (state == ARMED) {
            val first = source.firstFrameUs
            if (first == C.TIME_UNSET) return
            cursor = ((mediaUs + deltaUs - first) * rate / 1_000_000.0).roundToLong()
            fadeFrames = (rate * FADE_SECONDS).toInt().coerceAtLeast(1)
            fadeDone = 0
            splicedAtUs = mediaUs
            state = ACTIVE
        }
        if (state != ACTIVE) return
        var done = 0
        while (done < frames) {
            val chunk = minOf(frames - done, MAX_BLOCK_FRAMES)
            val got = source.read(cursor, scratch, 0, chunk)
            for (f in 0 until got) {
                val base = (done + f) * ch
                if (fadeDone < fadeFrames) {
                    val g = (0.5 - 0.5 * cos(PI * fadeDone / fadeFrames)).toFloat()
                    for (c in 0 until ch) {
                        val own = block.samples[base + c]
                        block.samples[base + c] = own + (scratch[f * ch + c] - own) * g
                    }
                    fadeDone++
                } else {
                    System.arraycopy(scratch, f * ch, block.samples, base, ch)
                }
            }
            // Frames the feed did not have yet keep the player's own audio,
            // which is the same moment of the same song.
            underrunFrames += (chunk - got)
            cursor += chunk
            done += chunk
        }
        source.releaseUpTo(cursor)
    }

    companion object {
        const val IDLE = 0
        const val ARMED = 1
        const val ACTIVE = 2
        const val ENDED = 3
        private const val MAX_BLOCK_FRAMES = 4096
        private const val FADE_SECONDS = 0.02
    }
}
