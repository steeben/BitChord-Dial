package com.music.bitchord.data.spotify

import com.music.bitchord.data.canvas.normalizeForMatch
import com.music.bitchord.data.canvas.splitArtists
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The HTTP-free half of mirroring a YouTube like onto the listener's Spotify
 * Liked Songs: finding the same recording among Spotify's search results, and
 * the web player's own `addToLibrary` / `removeFromLibrary` mutations.
 *
 * A wrong match here doesn't just show the wrong clip the way a Canvas miss
 * does, it puts a song the listener never liked into their library. So unlike
 * the Canvas search, which trusts Spotify's top hit, every candidate's title
 * and artists are checked, and nothing is saved when none of them match.
 */
object SpotifyLikeMirror {

    const val ADD_OPERATION = "addToLibrary"
    const val REMOVE_OPERATION = "removeFromLibrary"

    /**
     * The last hashes known for the two mutations. The live ones are read off
     * the web player's scripts first (see `SpotifyCanvasQuery.QueryHashes`);
     * these only stand in when that lookup can't be made.
     */
    const val KNOWN_ADD_HASH = "656c491c3f65d9d08d259be6632f4ef1931540ebcf766488ed17f63e1f8daf6b"
    const val KNOWN_REMOVE_HASH = "1103bfd4b9d80275950bff95ef6d41a02cec3357e8f7ecd8974528043739677c"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Candidate(val uri: String, val title: String, val artists: List<String>)

    /** The JSON body for a Pathfinder v2 `POST` of [operation] on [trackUri]. */
    fun mutationBody(operation: String, trackUri: String, hash: String): String = buildJsonObject {
        put("operationName", operation)
        putJsonObject("variables") {
            putJsonArray("libraryItemUris") { add(JsonPrimitive(trackUri)) }
        }
        putJsonObject("extensions") {
            putJsonObject("persistedQuery") {
                put("version", 1)
                put("sha256Hash", hash)
            }
        }
    }.toString()

    sealed interface MutationAnswer {
        data object Done : MutationAnswer
        data class Failed(val reason: String, val staleHash: Boolean = false) : MutationAnswer
    }

    /** Whether a 2xx answer to [mutationBody] actually went through. */
    fun parseMutation(body: String): MutationAnswer {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return MutationAnswer.Failed("not JSON")
        val errors = (root["errors"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.text("message") }
            .orEmpty()
        if (errors.isNotEmpty()) {
            return MutationAnswer.Failed(
                errors.joinToString("; "),
                staleHash = errors.any { it.contains("PersistedQueryNotFound", ignoreCase = true) },
            )
        }
        if (root["data"] !is JsonObject) return MutationAnswer.Failed("no data")
        return MutationAnswer.Done
    }

    /** Tracks out of the web player's `searchTracks` answer, in Spotify's order. */
    fun pathfinderCandidates(body: String): List<Candidate> {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        val items = root.obj("data")?.obj("searchV2")?.obj("tracksV2")?.get("items") as? JsonArray
            ?: return emptyList()
        return items.mapNotNull { entry ->
            val data = (entry as? JsonObject)?.obj("item")?.obj("data") ?: return@mapNotNull null
            val uri = data.text("uri") ?: data.text("id")?.let { "spotify:track:$it" } ?: return@mapNotNull null
            val title = data.text("name") ?: return@mapNotNull null
            val artists = (data.obj("artists")?.get("items") as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.obj("profile")?.text("name") }
                .orEmpty()
            Candidate(uri, title, artists)
        }
    }

    /** Tracks out of the REST `/v1/search?type=track` answer. */
    fun restCandidates(body: String): List<Candidate> {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        val items = root.obj("tracks")?.get("items") as? JsonArray ?: return emptyList()
        return items.mapNotNull { entry ->
            val track = entry as? JsonObject ?: return@mapNotNull null
            val uri = track.text("uri") ?: return@mapNotNull null
            val title = track.text("name") ?: return@mapNotNull null
            val artists = (track["artists"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.text("name") }
                .orEmpty()
            Candidate(uri, title, artists)
        }
    }

    /**
     * The first candidate that is the same song by the same artist, or null.
     *
     * Titles are compared with the decoration both services hang off them
     * stripped ("(feat. X)", "- Remastered 2011", "[Official Video]"), and the
     * credit has to share at least the lead artist: YouTube often credits only
     * the lead where Spotify lists everyone, and the other way round.
     */
    fun bestMatch(candidates: List<Candidate>, title: String, artist: String): Candidate? {
        val wantTitle = coreTitle(title)
        val wantArtists = splitArtists(artist)
        if (wantTitle.isBlank() || wantArtists.isEmpty()) return null
        return candidates.firstOrNull { candidate ->
            if (coreTitle(candidate.title) != wantTitle) return@firstOrNull false
            val credited = candidate.artists.flatMap(::splitArtists)
            credited.isNotEmpty() && wantArtists.any { it in credited }
        }
    }

    /** `spotify:track:<id>` → `<id>`, for the REST fallback. */
    fun trackId(uri: String): String? =
        uri.removePrefix("spotify:track:").takeIf { it != uri && it.isNotBlank() }

    /** A title with its bracketed and dash-separated decoration taken off. */
    internal fun coreTitle(raw: String): String =
        raw.replace(BRACKETED, " ")
            .replace(DASH_SUFFIX, "")
            .normalizeForMatch()

    private val BRACKETED = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val DASH_SUFFIX = Regex("""\s+[-–—]\s+.*$""")

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
}
