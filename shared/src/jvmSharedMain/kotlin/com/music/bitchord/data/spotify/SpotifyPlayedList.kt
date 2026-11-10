package com.music.bitchord.data.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * "Played in Dial": a private playlist on the listener's Spotify account that the songs played
 * here are added to, so Spotify's recommendations have something to learn this listening from.
 *
 * Spotify takes no listening history from outside its own apps, and reporting plays that never
 * happened there is what it bans accounts for. Adding a song to a playlist is something every
 * Spotify client does, and playlists are one of the things its recommendations read.
 *
 * This is the HTTP-free half: the requests' addresses and bodies, and reading the answers.
 */
object SpotifyPlayedList {

    const val NAME = "Played in Dial"
    const val DESCRIPTION = "Songs played in Dial. Kept up to date by the app to help your recommendations."

    const val CREATE_URL = "https://api.spotify.com/v1/me/playlists"

    /** Where a playlist takes new tracks; the second is the older name for the first. */
    fun addUrls(playlistId: String): List<String> = listOf(
        "https://api.spotify.com/v1/playlists/$playlistId/items",
        "https://api.spotify.com/v1/playlists/$playlistId/tracks",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun createBody(): String = buildJsonObject {
        put("name", NAME)
        put("description", DESCRIPTION)
        put("public", false)
    }.toString()

    fun addBody(trackUri: String): String = buildJsonObject {
        putJsonArray("uris") { add(JsonPrimitive(trackUri)) }
    }.toString()

    /** The new playlist's id out of the answer to creating it. */
    fun createdId(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        return (root["id"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    /** [playlists] as the app lists them, without this one. */
    fun withoutPlayedList(playlists: List<SpotifyPlaylist>): List<SpotifyPlaylist> =
        playlists.filterNot { it.name == NAME }
}
