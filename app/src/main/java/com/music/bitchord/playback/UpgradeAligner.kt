package com.music.bitchord.playback

import android.net.Uri
import com.music.bitchord.playback.smart.AudioDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * How far apart two copies of the same recording sit in time, measured off
 * the audio itself rather than assumed to be zero.
 *
 * A quality upgrade swaps one master for another from a different store:
 * YouTube's Opus for Qobuz's FLAC, say. They are the same music, but not
 * necessarily on the same clock. One store trims a few milliseconds of lead-in
 * the other keeps, an encoder pads its first frame, and the runtimes disagree
 * by a fraction of a second that rounds to "2:50" on one and "2:51" on the
 * other. A swap that assumes position 4470ms means the same note in both lands
 * that difference off, and a blend across two copies tens of milliseconds apart
 * is heard as a flam: the same beat struck twice.
 *
 * So a few seconds of each are decoded off the cache, right where the swap is
 * about to happen, and cross-correlated. Both decodes go through the same
 * [AudioDecoder] path, so whatever it does with a codec's priming does to both.
 */
object UpgradeAligner {

    /** One measurement: target time = source time + [offsetMs], with [confidence] in 0..1. */
    class Offset(val offsetMs: Double, val confidence: Double)

    /** Rate the two decodes are brought down to before correlating: 0.25ms resolution, cheap to search. */
    private const val WORK_RATE = 4_000.0

    /** How much of the playing copy is matched against the other. */
    private const val WINDOW_MS = 3_000L

    /** How far either way the other copy is searched for it. */
    private const val SEARCH_MS = 250L

    /** Below this the two windows were not recognisably the same music. */
    private const val MIN_CONFIDENCE = 0.6

    /**
     * Where [targetUri]'s audio sits against [sourceUri]'s around [atMs] in
     * the source, or null when either copy could not be read there or the two
     * did not match well enough to trust.
     */
    suspend fun measure(sourceUri: Uri, targetUri: Uri, atMs: Long): Offset? = withContext(Dispatchers.IO) {
        val start = atMs / 1000.0
        val end = (atMs + WINDOW_MS) / 1000.0
        val pad = SEARCH_MS / 1000.0
        val (source, target) = coroutineScope {
            val s = async { decode(sourceUri, start, end) }
            val t = async { decode(targetUri, (start - pad).coerceAtLeast(0.0), end + pad) }
            s.await() to t.await()
        }
        if (source == null || target == null) return@withContext null
        val (a, aStart) = source
        val (b, bStart) = target
        val maxLag = (SEARCH_MS / 1000.0 * WORK_RATE).toInt()
        // Lag L lines a[i] up with b[i + L - base]: `base` is how far into b
        // the window a starts, so L = 0 is "same timestamp in both".
        val base = ((aStart - bStart) * WORK_RATE).roundToLong().toInt()
        val n = a.size
        var bestLag = 0
        var best = -1.0
        val scores = DoubleArray(2 * maxLag + 1)
        var energyA = 0.0
        for (v in a) energyA += v * v
        if (energyA < 1e-6) return@withContext null
        for (lag in -maxLag..maxLag) {
            val shift = base + lag
            val from = max(0, -shift)
            val to = min(n, b.size - shift)
            if (to - from < n * 3 / 4) continue
            var dot = 0.0
            var energyB = 0.0
            for (i in from until to) {
                val y = b[i + shift]
                dot += a[i] * y
                energyB += y * y
            }
            if (energyB < 1e-9) continue
            val r = dot / sqrt(energyA * energyB)
            scores[lag + maxLag] = r
            if (r > best) {
                best = r
                bestLag = lag
            }
        }
        if (best < MIN_CONFIDENCE) return@withContext null
        // A peak on the edge of the search is a lag past it, not a measurement.
        if (bestLag == -maxLag || bestLag == maxLag) return@withContext null
        // Parabolic interpolation between the neighbours for a sub-sample peak.
        val l = scores[bestLag + maxLag - 1]
        val c = scores[bestLag + maxLag]
        val r = scores[bestLag + maxLag + 1]
        val denom = l - 2 * c + r
        val fraction = if (denom < 0) (0.5 * (l - r) / denom).coerceIn(-0.5, 0.5) else 0.0
        Offset(offsetMs = (bestLag + fraction) / WORK_RATE * 1000.0, confidence = best)
    }

    /** Mono at [WORK_RATE], paired with the real start time in seconds. */
    private fun decode(uri: Uri, startSec: Double, endSec: Double): Pair<FloatArray, Double>? {
        val source = AudioCache.headMediaDataSource(uri) ?: return null
        try {
            val (pcm, realStart) = AudioDecoder.decodeRegion(source, startSec, endSec) ?: return null
            if (pcm.samples.isEmpty() || pcm.sampleRate <= 0) return null
            return downsample(pcm.samples, pcm.sampleRate) to realStart
        } finally {
            runCatching { source.close() }
        }
    }

    /** Box-averaged down to [WORK_RATE]; plenty of low-pass for a correlation. */
    private fun downsample(samples: FloatArray, rate: Double): FloatArray {
        val step = rate / WORK_RATE
        val count = (samples.size / step).toInt()
        val out = FloatArray(count)
        for (k in 0 until count) {
            val from = (k * step).toInt()
            val to = min(samples.size, max(from + 1, ((k + 1) * step).toInt()))
            var sum = 0f
            for (i in from until to) sum += samples[i]
            out[k] = sum / (to - from)
        }
        return out
    }
}
