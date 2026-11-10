package com.music.bitchord.data.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
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

    /** How many songs the playlist may hold, to choose from, and the choice until one is made. */
    val KEEP_OPTIONS = listOf(100, 200, 500, 1000)
    const val DEFAULT_KEEP = 500

    /** The most songs read, or taken out, in one request. */
    const val PAGE = 50

    /** [saved] if it is one of [KEEP_OPTIONS], else [DEFAULT_KEEP]. */
    fun keepOrDefault(saved: Int): Int = if (saved in KEEP_OPTIONS) saved else DEFAULT_KEEP

    /** How many of the oldest songs to take out of a playlist holding [total], to keep [keep]; at most [PAGE]. */
    fun excess(total: Int, keep: Int): Int = (total - keep).coerceIn(0, PAGE)

    /** The playlist's oldest songs, a page of them: new songs are added at the end, so the oldest lead. */
    fun oldestUrl(itemsUrl: String): String = "$itemsUrl?offset=0&limit=$PAGE"

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

    /** How many songs the playlist holds, out of the answer to listing it. */
    fun total(body: String): Int? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        return (root["total"] as? JsonPrimitive)?.intOrNull
    }

    /**
     * The first [count] songs' uris out of the answer to listing the playlist. Each entry's
     * song is under "track", or "item" where the address is the newer one.
     */
    fun oldestUris(body: String, count: Int): List<String> {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        val items = root["items"] as? JsonArray ?: return emptyList()
        return items.asSequence()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { entry -> (entry["track"] as? JsonObject) ?: (entry["item"] as? JsonObject) }
            .mapNotNull { track -> (track["uri"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull }
            .filter { it.isNotBlank() }
            .take(count.coerceAtLeast(0))
            .toList()
    }

    /** The body that takes [uris] out of the playlist at [url]: the songs go by the name the address has. */
    fun removeBody(url: String, uris: List<String>): String = buildJsonObject {
        putJsonArray(if (url.endsWith("/items")) "items" else "tracks") {
            uris.forEach { uri -> add(buildJsonObject { put("uri", uri) }) }
        }
    }.toString()

    /** [playlists] as the app lists them, without this one. */
    fun withoutPlayedList(playlists: List<SpotifyPlaylist>): List<SpotifyPlaylist> =
        playlists.filterNot { it.name == NAME }
}
