package com.music.bitchord.data

import android.content.Context
import org.json.JSONObject

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
    private const val PAIRED_VIDEO = "paired_video"
    private const val SONG_PICKS = "song_picks"
    private const val ALBUM_ART_CANVAS = "album_art_canvas"

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
                pairedVideo = prefs.getStringSet(PAIRED_VIDEO, null).orEmpty().mapNotNull { entry ->
                    val split = entry.indexOf('=')
                    if (split <= 0 || split == entry.lastIndex) null else entry.substring(0, split) to entry.substring(split + 1)
                }.toMap(),
                songPicks = prefs.getStringSet(SONG_PICKS, null).orEmpty().mapNotNull(::readPick).toMap(),
                albumArtCanvas = prefs.getStringSet(ALBUM_ART_CANVAS, null).orEmpty().toSet(),
            ),
        )
        CanvasAudioFixes.persist = { state ->
            prefs.edit()
                .putStringSet(KEEP_VIDEO_AUDIO, state.keepVideoAudio.toSet())
                .putStringSet(HIDDEN_CANVAS, state.hiddenCanvas.toSet())
                .putStringSet(HIDDEN_VIDEO_CANVAS, state.hiddenVideoCanvas.toSet())
                .putStringSet(SYNC_OFFSETS, state.syncOffsets.map { (key, value) -> "$key=$value" }.toSet())
                .putStringSet(PAIRED_VIDEO, state.pairedVideo.map { (key, value) -> "$key=$value" }.toSet())
                .putStringSet(SONG_PICKS, state.songPicks.map { (key, pick) -> writePick(key, pick) }.toSet())
                .putStringSet(ALBUM_ART_CANVAS, state.albumArtCanvas.toSet())
                .apply()
        }
    }

    private fun writePick(key: String, pick: CanvasAudioFixes.SongPick): String = JSONObject()
        .put("for", key)
        .put("id", pick.videoId)
        .put("title", pick.title)
        .put("artist", pick.artist)
        .putOpt("thumb", pick.thumbnailUrl)
        .putOpt("duration", pick.durationText)
        .putOpt("album", pick.albumName)
        .putOpt("albumId", pick.albumId)
        .putOpt("artistId", pick.artistId)
        .toString()

    private fun readPick(entry: String): Pair<String, CanvasAudioFixes.SongPick>? = runCatching {
        val o = JSONObject(entry)
        o.getString("for") to CanvasAudioFixes.SongPick(
            videoId = o.getString("id"),
            title = o.getString("title"),
            artist = o.getString("artist"),
            thumbnailUrl = o.optString("thumb").takeIf { o.has("thumb") },
            durationText = o.optString("duration").takeIf { o.has("duration") },
            albumName = o.optString("album").takeIf { o.has("album") },
            albumId = o.optString("albumId").takeIf { o.has("albumId") },
            artistId = o.optString("artistId").takeIf { o.has("artistId") },
        )
    }.getOrNull()
}
