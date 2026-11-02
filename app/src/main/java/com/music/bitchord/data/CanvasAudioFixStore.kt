package com.music.bitchord.data

import android.content.Context

/**
 * Keeps [CanvasAudioFixes] on the device, so a song fixed once stays fixed
 * across restarts and updates.
 */
object CanvasAudioFixStore {
    private const val PREFS = "canvas_audio_fixes"
    private const val KEEP_VIDEO_AUDIO = "keep_video_audio"
    private const val HIDDEN_CANVAS = "hidden_canvas"
    private const val HIDDEN_VIDEO_CANVAS = "hidden_video_canvas"
    private const val SYNC_OFFSETS = "sync_offsets"

    fun install(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        CanvasAudioFixes.load(
            CanvasAudioFixes.State(
                keepVideoAudio = prefs.getStringSet(KEEP_VIDEO_AUDIO, null).orEmpty().toSet(),
                hiddenCanvas = prefs.getStringSet(HIDDEN_CANVAS, null).orEmpty().toSet(),
                hiddenVideoCanvas = prefs.getStringSet(HIDDEN_VIDEO_CANVAS, null).orEmpty().toSet(),
                syncOffsets = prefs.getStringSet(SYNC_OFFSETS, null).orEmpty().mapNotNull { entry ->
                    val split = entry.lastIndexOf('=')
                    if (split <= 0) return@mapNotNull null
                    val offset = entry.substring(split + 1).toLongOrNull() ?: return@mapNotNull null
                    entry.substring(0, split) to offset
                }.toMap(),
            ),
        )
        CanvasAudioFixes.persist = { state ->
            prefs.edit()
                .putStringSet(KEEP_VIDEO_AUDIO, state.keepVideoAudio.toSet())
                .putStringSet(HIDDEN_CANVAS, state.hiddenCanvas.toSet())
                .putStringSet(HIDDEN_VIDEO_CANVAS, state.hiddenVideoCanvas.toSet())
                .putStringSet(SYNC_OFFSETS, state.syncOffsets.map { (key, value) -> "$key=$value" }.toSet())
                .apply()
        }
    }
}
