package com.music.bitchord.playback

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.sources.SourceStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * The tracks the listener sent to one particular addon or source by hand,
 * from the player's fix menu — because the one the app chose plays the wrong
 * audio.
 *
 * The other half of [OriginalVersion], which pins a track to YouTube's own
 * upload: this pins it to a named source instead. Every place that would
 * otherwise race the sources for a YouTube track asks only the pinned one,
 * and falls back to YouTube when it can't serve the track.
 *
 * Kept for good, as a revert is.
 */
object SourcePins {

    private lateinit var prefs: SharedPreferences

    private val _pins = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Track → the [com.music.bitchord.data.sources.MusicSource.configId] it is pinned to. */
    val pins: StateFlow<Map<String, String>> = _pins.asStateFlow()

    /**
     * Streams the fix menu already found while showing which sources have the
     * track, handed to the service so it doesn't search again. Taken once.
     */
    private val offered = ConcurrentHashMap<String, SourceStream>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _pins.value = prefs.getString(KEY_PINS, null)
            ?.split('\n')
            ?.mapNotNull { line ->
                val split = line.indexOf('=')
                if (split <= 0) null else line.substring(0, split) to line.substring(split + 1)
            }
            ?.toMap()
            ?: emptyMap()
    }

    fun of(videoId: String?): String? = videoId?.let { _pins.value[it] }

    fun pin(videoId: String, configId: String) {
        if (videoId.isBlank() || _pins.value[videoId] == configId) return
        var next = _pins.value - videoId + (videoId to configId)
        while (next.size > MAX_PINNED) next = next - next.keys.first()
        write(next)
    }

    fun unpin(videoId: String) {
        if (videoId !in _pins.value) return
        write(_pins.value - videoId)
    }

    fun offer(videoId: String, configId: String, stream: SourceStream) {
        offered["$videoId>$configId"] = stream
    }

    fun takeOffered(videoId: String, configId: String): SourceStream? = offered.remove("$videoId>$configId")

    private fun write(pins: Map<String, String>) {
        _pins.value = pins
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_PINS, pins.entries.joinToString("\n") { "${it.key}=${it.value}" }).apply()
    }

    private const val MAX_PINNED = 500
    private const val PREFS_NAME = "bitchord_source_pins"
    private const val KEY_PINS = "pins"
}
