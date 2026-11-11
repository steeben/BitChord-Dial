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
import kotlinx.serialization.json.putJsonObject

/**
 * "Played in Dial": a private playlist on the listener's Spotify account that the songs played
 * here are added to, so Spotify's recommendations have something to learn this listening from.
 *
 * Spotify takes no listening history from outside its own apps, and reporting plays that never
 * happened there is what it bans accounts for. Adding a song to a playlist is something every
 * Spotify client does, and playlists are one of the things its recommendations read.
 *
 * There are two ways in, and the web player's own is tried first: the public Web API
 * (`api.spotify.com/v1`) is meant for registered developer apps and turns the web player's
 * sign-in away much of the time, where the routes the web player itself uses to make a
 * playlist and add to it take that sign-in as a matter of course.
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

    // ---- The web player's own route -------------------------------------

    /** Where the web player makes a playlist; it answers with the new playlist's uri. */
    const val WEB_CREATE_URL = "https://spclient.wg.spotify.com/playlist/v2/playlist"

    const val PROFILE_OPERATION = "profileAttributes"
    const val ADD_OPERATION = "addToPlaylist"
    const val REMOVE_OPERATION = "removeFromPlaylist"

    /** The last hashes known for the three; the live ones are read off the web player's scripts first. */
    const val KNOWN_PROFILE_HASH = "53bcb064f6cd18c23f752bc324a791194d20df612d8e1239c735144ab0399ced"
    const val KNOWN_ADD_HASH = "47c69e71df79e3c80e4af7e7a9a727d82565bb20ae20dc820d6bc6f94def482d"
    const val KNOWN_REMOVE_HASH = "47c69e71df79e3c80e4af7e7a9a727d82565bb20ae20dc820d6bc6f94def482d"

    /** Where a new playlist is put into [username]'s library; one made and not put there is an orphan. */
    fun rootlistUrl(username: String): String =
        "https://spclient.wg.spotify.com/playlist/v2/user/${escape(username)}/rootlist/changes"

    /** The body that makes the playlist. It carries only the name: that is all the web player sends. */
    fun webCreateBody(): String = buildJsonObject {
        putJsonArray("ops") {
            add(buildJsonObject {
                put("kind", "UPDATE_LIST_ATTRIBUTES")
                putJsonObject("updateListAttributes") {
                    putJsonObject("newAttributes") { putJsonObject("values") { put("name", NAME) } }
                }
            })
        }
    }.toString()

    /** The new playlist's id out of the answer to [webCreateBody], which names it as a uri. */
    fun webCreatedId(body: String): String? =
        text(parse(body), "uri")?.removePrefix("spotify:playlist:")?.takeIf { it.isNotBlank() && ':' !in it }

    /** The body that puts the playlist [playlistId] at the top of the library, stamped [nowMs]. */
    fun rootlistAddBody(playlistId: String, nowMs: Long): String = buildJsonObject {
        putJsonArray("deltas") {
            add(buildJsonObject {
                putJsonArray("ops") {
                    add(buildJsonObject {
                        put("kind", "ADD")
                        putJsonObject("add") {
                            putJsonArray("items") {
                                add(buildJsonObject {
                                    put("uri", "spotify:playlist:$playlistId")
                                    putJsonObject("attributes") { put("timestamp", nowMs.toString()) }
                                })
                            }
                            put("addFirst", true)
                        }
                    })
                }
                putJsonObject("info") { putJsonObject("source") { put("client", "WEBPLAYER") } }
            })
        }
    }.toString()

    /** Whether the library took the change: it answers with its new revision. */
    fun rootlistChanged(body: String): Boolean = parse(body)?.get("revision").let { it != null && it !is JsonNull }

    /** The body of a Pathfinder query that takes no variables, such as [PROFILE_OPERATION]. */
    fun queryBody(operation: String, hash: String): String = pathfinderBody(operation, hash) {}

    /** The account's username out of the answer to [PROFILE_OPERATION]. */
    fun username(body: String): String? =
        text(((parse(body)?.get("data") as? JsonObject)?.get("me") as? JsonObject)?.get("profile") as? JsonObject, "username")

    /** The body that adds [trackUri] to the end of the playlist. */
    fun webAddBody(playlistId: String, trackUri: String, hash: String): String =
        pathfinderBody(ADD_OPERATION, hash) {
            putJsonArray("playlistItemUris") { add(JsonPrimitive(trackUri)) }
            put("playlistUri", "spotify:playlist:$playlistId")
            putJsonObject("newPosition") {
                put("moveType", "BOTTOM_OF_PLAYLIST")
                put("fromUid", JsonNull)
            }
        }

    /** The body that takes the entries [uids] out of the playlist; an entry's uid is its place, not its song. */
    fun webRemoveBody(playlistId: String, uids: List<String>, hash: String): String =
        pathfinderBody(REMOVE_OPERATION, hash) {
            putJsonArray("uids") { uids.forEach { add(JsonPrimitive(it)) } }
            put("playlistUri", "spotify:playlist:$playlistId")
        }

    /** What a 2xx answer to a change said. */
    sealed interface Answer {
        data object Done : Answer

        /** It didn't go through; [staleHash] when looking the operation's hash up again is worth a try. */
        data class Refused(val reason: String, val staleHash: Boolean = false) : Answer
    }

    fun webAdded(body: String): Answer = changed(body, "addItemsToPlaylist", "AddItemsToPlaylistPayload")

    fun webRemoved(body: String): Answer = changed(body, "removeItemsFromPlaylist", "RemoveItemsFromPlaylistPayload")

    /** A change went through when its [field] came back as [payload]; anything else is Spotify saying why not. */
    private fun changed(body: String, field: String, payload: String): Answer {
        val root = parse(body) ?: return Answer.Refused("not JSON")
        val errors = (root["errors"] as? JsonArray)?.mapNotNull { text(it as? JsonObject, "message") }.orEmpty()
        if (errors.isNotEmpty()) {
            return Answer.Refused(
                errors.joinToString("; "),
                staleHash = errors.any { it.contains("PersistedQueryNotFound", ignoreCase = true) },
            )
        }
        val result = (root["data"] as? JsonObject)?.get(field) as? JsonObject ?: return Answer.Refused("no $field")
        val type = text(result, "__typename")
        return if (type == payload) Answer.Done else Answer.Refused(type ?: "no type")
    }

    private fun pathfinderBody(
        operation: String,
        hash: String,
        variables: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): String = buildJsonObject {
        putJsonObject("variables", variables)
        put("operationName", operation)
        putJsonObject("extensions") {
            putJsonObject("persistedQuery") {
                put("version", 1)
                put("sha256Hash", hash)
            }
        }
    }.toString()

    private fun parse(body: String): JsonObject? = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun text(from: JsonObject?, key: String): String? =
        (from?.get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }

    /** [value] as one piece of an address. */
    private fun escape(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    // ---- What happened last ---------------------------------------------

    /** The outcome of the last song handled, kept so the settings can say whether this is working. */
    data class Status(val kind: Kind, val detail: String, val atMs: Long) {
        enum class Kind { ADDED, NO_MATCH, FAILED }

        fun encode(): String = "${kind.name}\t$atMs\t$detail"

        companion object {
            fun decode(saved: String?): Status? {
                val parts = saved?.split('\t', limit = 3) ?: return null
                if (parts.size != 3) return null
                val kind = Kind.entries.firstOrNull { it.name == parts[0] } ?: return null
                return Status(kind, parts[2], parts[1].toLongOrNull() ?: return null)
            }
        }
    }
}
