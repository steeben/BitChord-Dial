package com.music.bitchord.data

import android.content.Context
import org.json.JSONObject

/**
 * Keeps [SongMetadataFixes] on the device, so a file or upload renamed once
 * stays renamed across restarts and updates.
 */
object SongMetadataFixStore {
    private const val PREFS = "song_metadata_fixes"
    private const val FIXES = "fixes"

    fun install(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        SongMetadataFixes.load(prefs.getStringSet(FIXES, null).orEmpty().mapNotNull(::read).toMap())
        SongMetadataFixes.persist = { fixes ->
            prefs.edit().putStringSet(FIXES, fixes.map { (key, fix) -> write(key, fix) }.toSet()).apply()
        }
    }

    private fun write(key: String, fix: SongMetadataFixes.Fix): String = JSONObject()
        .put("for", key)
        .put("details", fix.details.toJson())
        .put("original", fix.original.toJson())
        .toString()

    private fun read(entry: String): Pair<String, SongMetadataFixes.Fix>? = runCatching {
        val o = JSONObject(entry)
        o.getString("for") to SongMetadataFixes.Fix(
            details = o.getJSONObject("details").toDetails(),
            original = o.getJSONObject("original").toDetails(),
        )
    }.getOrNull()

    private fun SongMetadataFixes.Details.toJson() = JSONObject()
        .put("title", title)
        .put("artist", artist)
        .putOpt("album", albumName)
        .putOpt("albumId", albumId)
        .putOpt("artistId", artistId)
        .putOpt("thumb", thumbnailUrl)
        .putOpt("match", matchedVideoId)

    private fun JSONObject.toDetails() = SongMetadataFixes.Details(
        title = getString("title"),
        artist = getString("artist"),
        albumName = optText("album"),
        albumId = optText("albumId"),
        artistId = optText("artistId"),
        thumbnailUrl = optText("thumb"),
        matchedVideoId = optText("match"),
    )

    private fun JSONObject.optText(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null
}
