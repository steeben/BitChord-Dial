package com.music.bitchord.playback.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.media3.common.C
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A second copy of the playing track, decoded on its own thread into a ring of
 * float frames at the playing pipeline's rate and channel count, for
 * [SinkSplice] to splice into that pipeline sample for sample.
 *
 * Why decode it ourselves rather than on a second player: a player's audio
 * comes out of its own AudioTrack, and nothing Android reports says precisely
 * when. Two players lined up by their positions were heard tens of
 * milliseconds apart however level the positions read. Frames handed to the
 * *same* sink by timestamp have no such problem — there is one output, and
 * the new copy simply takes the old one's place in it.
 *
 * Frame `i` of the ring is the copy's own time [firstFrameUs] + i / [rate].
 * Decoding runs ahead until the ring is full and then waits for the reader to
 * [releaseUpTo] past what it has used.
 */
class SpliceFeed(
    private val openSource: () -> MediaDataSource?,
    /** Where in its own timeline the copy is wanted from. */
    private val startUs: Long,
    val rate: Int,
    val channels: Int,
    capacitySeconds: Int = 12,
) {
    private val capacityFrames = rate * capacitySeconds
    private val ring = FloatArray(capacityFrames * channels)

    @Volatile private var written = 0L
    @Volatile private var released = 0L
    @Volatile private var stopped = false

    /** The copy's own time of ring frame 0, once the first audio is decoded. */
    @Volatile var firstFrameUs: Long = C.TIME_UNSET
        private set

    @Volatile var failed: String? = null
        private set

    @Volatile var ended = false
        private set

    /** The decoder's output encoding, for the log. */
    @Volatile var outputEncoding = 0
        private set

    /** The copy's own rate, for the log. */
    @Volatile var sourceRate = 0
        private set

    val writtenFrames: Long get() = written

    private val thread = Thread(::run, "SpliceFeed").apply { isDaemon = true }

    fun start() = thread.start()

    /** Any thread. Stops decoding; the thread lets go of its decoder on its own. */
    fun release() {
        stopped = true
    }

    /**
     * Audio thread. Copies up to [frames] frames from ring frame [index] into
     * [dest], interleaved, and returns how many were there to copy — fewer
     * when decoding has not got that far, none when [index] has been
     * released or overwritten. Never blocks.
     */
    fun read(index: Long, dest: FloatArray, destOffset: Int, frames: Int): Int {
        val w = written
        if (index < released || index < w - capacityFrames || index >= w) return 0
        val n = min(frames.toLong(), w - index).toInt()
        var out = destOffset
        for (k in 0 until n) {
            val slot = ((index + k) % capacityFrames).toInt() * channels
            System.arraycopy(ring, slot, dest, out, channels)
            out += channels
        }
        return n
    }

    /** Any thread. Frames before [index] will not be asked for again. */
    fun releaseUpTo(index: Long) {
        if (index <= released) return
        released = index
    }

    /** A mono copy of [frames] ring frames from [fromIndex], or null if they are not all held. */
    fun mono(fromIndex: Long, frames: Int): FloatArray? {
        val w = written
        if (fromIndex < 0 || fromIndex < released || fromIndex < w - capacityFrames || fromIndex + frames > w) {
            return null
        }
        val out = FloatArray(frames)
        for (k in 0 until frames) {
            val slot = ((fromIndex + k) % capacityFrames).toInt() * channels
            var sum = 0f
            for (c in 0 until channels) sum += ring[slot + c]
            out[k] = sum / channels
        }
        return out
    }

    private fun run() {
        val source = try {
            openSource()
        } catch (e: Exception) {
            null
        }
        if (source == null) {
            failed = "no source"
            return
        }
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: run {
                failed = "no audio track"
                return
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: run {
                failed = "no mime"
                return
            }
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            // Whatever the decoder says it will hand out, read before the
            // first buffer rather than assumed: a float buffer read as 16-bit
            // is noise, and noise matches nothing.
            var encoding = codec.outputFormat.let {
                if (it.containsKey(MediaFormat.KEY_PCM_ENCODING)) it.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
            }
            var resampler: Resampler? = null
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var scratch = FloatArray(0)

            while (!stopped) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = codec.outputFormat
                    srcRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    srcChannels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    continue
                }
                if (outIndex < 0) continue
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                val buffer = codec.getOutputBuffer(outIndex)
                if (encoding != AudioFormat.ENCODING_PCM_16BIT && encoding != AudioFormat.ENCODING_PCM_FLOAT) {
                    failed = "decoder output encoding $encoding"
                    break
                }
                outputEncoding = encoding
                if (buffer != null && info.size > 0 && srcChannels > 0 && srcRate > 0) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    buffer.order(ByteOrder.nativeOrder())
                    val frames = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        info.size / (4 * srcChannels)
                    } else {
                        info.size / (2 * srcChannels)
                    }
                    if (scratch.size < frames * channels) scratch = FloatArray(frames * channels)
                    // Down to the pipeline's channel count as it is read.
                    if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        val floats = buffer.asFloatBuffer()
                        mapChannels(frames, srcChannels, scratch) { i -> floats.get(i) }
                    } else {
                        val shorts = buffer.asShortBuffer()
                        mapChannels(frames, srcChannels, scratch) { i -> shorts.get(i) / 32768f }
                    }
                    var skip = 0
                    if (firstFrameUs == C.TIME_UNSET) {
                        skip = max(0L, (startUs - info.presentationTimeUs) * srcRate / 1_000_000L)
                            .coerceAtMost(frames.toLong()).toInt()
                        if (skip < frames) {
                            firstFrameUs = info.presentationTimeUs + skip * 1_000_000L / srcRate
                            sourceRate = srcRate
                            if (srcRate != rate) resampler = Resampler(srcRate, rate, channels)
                        }
                    }
                    if (skip < frames) {
                        val r = resampler
                        if (r == null) {
                            write(scratch, skip * channels, frames - skip)
                        } else {
                            r.push(scratch, skip * channels, frames - skip) { data, offset, count -> write(data, offset, count) }
                        }
                    }
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (eos) {
                    ended = true
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "splice feed failed", e)
            failed = e.javaClass.simpleName
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
            runCatching { source.close() }
        }
    }

    private inline fun mapChannels(frames: Int, srcChannels: Int, out: FloatArray, sample: (Int) -> Float) {
        for (f in 0 until frames) {
            val base = f * srcChannels
            when {
                srcChannels == channels -> for (c in 0 until channels) out[f * channels + c] = sample(base + c)
                channels == 1 -> {
                    var sum = 0f
                    for (c in 0 until srcChannels) sum += sample(base + c)
                    out[f] = sum / srcChannels
                }
                srcChannels == 1 -> {
                    val v = sample(base)
                    for (c in 0 until channels) out[f * channels + c] = v
                }
                else -> for (c in 0 until channels) out[f * channels + c] = sample(base + min(c, srcChannels - 1))
            }
        }
    }

    /** Producer side. Blocks while the ring is full. */
    private fun write(data: FloatArray, offset: Int, frames: Int) {
        var done = 0
        while (done < frames && !stopped) {
            val space = capacityFrames - (written - released)
            if (space <= 0) {
                Thread.sleep(WAIT_MS)
                continue
            }
            val n = min(space, (frames - done).toLong()).toInt()
            for (k in 0 until n) {
                val slot = ((written + k) % capacityFrames).toInt() * channels
                System.arraycopy(data, offset + (done + k) * channels, ring, slot, channels)
            }
            written += n
            done += n
        }
    }

    /**
     * Windowed-sinc rate conversion with no delay: output frame k is the input
     * interpolated at exactly k * from / to, so the ring's time base survives
     * the conversion unchanged.
     */
    private class Resampler(from: Int, to: Int, private val channels: Int) {
        private val ratio = from.toDouble() / to
        private val cutoff = min(1.0, 1.0 / ratio)
        private val half = ceil(TAPS * max(1.0, ratio)).toInt()
        private val table = FloatArray(half * PHASES + 2).also { t ->
            for (i in t.indices) {
                val x = i.toDouble() / PHASES
                t[i] = if (x > half) {
                    0f
                } else {
                    val sinc = if (x == 0.0) 1.0 else sin(PI * cutoff * x) / (PI * cutoff * x)
                    val w = 0.42 + 0.5 * cos(PI * x / half) + 0.08 * cos(2 * PI * x / half)
                    (cutoff * sinc * w).toFloat()
                }
            }
        }
        private var buffer = FloatArray(16384 * channels)
        private var bufferFrames = 0
        /** Absolute input frame index of buffer[0]. */
        private var base = 0L
        private var nextOut = 0L
        private var out = FloatArray(4096 * channels)

        fun push(data: FloatArray, offset: Int, frames: Int, emit: (FloatArray, Int, Int) -> Unit) {
            if ((bufferFrames + frames) * channels > buffer.size) {
                buffer = buffer.copyOf(max(buffer.size * 2, (bufferFrames + frames) * channels))
            }
            System.arraycopy(data, offset, buffer, bufferFrames * channels, frames * channels)
            bufferFrames += frames
            var produced = 0
            while (true) {
                val x = nextOut * ratio
                val center = floor(x).toLong()
                if (center + half >= base + bufferFrames) break
                if (produced == out.size / channels) {
                    emit(out, 0, produced)
                    produced = 0
                }
                val frac = x - center
                for (c in 0 until channels) {
                    var acc = 0f
                    for (j in -half + 1..half) {
                        val idx = center + j
                        if (idx < base) continue
                        val d = kotlin.math.abs(j - frac) * PHASES
                        val i0 = d.toInt()
                        val weight = table[i0] + (table[i0 + 1] - table[i0]) * (d - i0).toFloat()
                        acc += buffer[((idx - base).toInt()) * channels + c] * weight
                    }
                    out[produced * channels + c] = acc
                }
                produced++
                nextOut++
            }
            if (produced > 0) emit(out, 0, produced)
            // Keep only what the next outputs can still reach.
            val keepFrom = floor(nextOut * ratio).toLong() - half
            val drop = (keepFrom - base).coerceIn(0L, bufferFrames.toLong()).toInt()
            if (drop > 0) {
                System.arraycopy(buffer, drop * channels, buffer, 0, (bufferFrames - drop) * channels)
                bufferFrames -= drop
                base += drop
            }
        }

        companion object {
            const val TAPS = 16
            const val PHASES = 256
        }
    }

    companion object {
        private const val TAG = "SpliceFeed"
        private const val TIMEOUT_US = 10_000L
        private const val WAIT_MS = 20L
    }
}
