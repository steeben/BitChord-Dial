package com.music.bitchord.data.spotify

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.canvas.CANVAS_UA
import com.music.bitchord.data.canvas.SpotifyCanvasQuery
import com.music.bitchord.data.canvas.SpotifyToken
import com.music.bitchord.data.canvas.canvasGet
import com.music.bitchord.data.canvas.canvasGetWithStatus
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Mirrors a heart tapped here onto the listener's Spotify Liked Songs, when
 * [AppSettings.spotifyLikeSync] is on.
 *
 * Rides the same web-player session as Spotify Canvas ([SpotifyToken], minted
 * from the `sp_dc` cookie), so it needs no Spotify developer app and no extra
 * sign-in; the web player's own token already carries the library scopes its
 * own heart button uses. The track is found with the web player's search and
 * saved with its own `addToLibrary` mutation, falling back to the REST
 * `/v1/me/tracks` endpoint when Pathfinder won't answer.
 *
 * Best-effort and fire-and-forget: the YouTube like has already gone through
 * by the time this runs, and nothing here can fail it or roll it back.
 */
object SpotifyLikeSync {

    private const val TAG = "SpotifyLikeSync"
    private const val PATHFINDER_V1 = "https://api-partner.spotify.com/pathfinder/v1/query"
    private const val SEARCH_HASH = "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428"
    private const val REST_SEARCH = "https://api.spotify.com/v1/search"
    private const val REST_SAVED_TRACKS = "https://api.spotify.com/v1/me/tracks"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scriptFetch: (String) -> String? = { url -> canvasGet(url, mapOf("User-Agent" to CANVAS_UA)) }
    private val addHash = SpotifyCanvasQuery.QueryHashes(
        fetch = scriptFetch,
        operation = SpotifyLikeMirror.ADD_OPERATION,
        fallback = SpotifyLikeMirror.KNOWN_ADD_HASH,
    )
    private val removeHash = SpotifyCanvasQuery.QueryHashes(
        fetch = scriptFetch,
        operation = SpotifyLikeMirror.REMOVE_OPERATION,
        fallback = SpotifyLikeMirror.KNOWN_REMOVE_HASH,
    )

    /**
     * Likes ([liked] true) or un-likes [title] by [artist] on Spotify, in the
     * background. A no-op while the setting is off or no cookie is set.
     */
    fun mirror(title: String?, artist: String?, liked: Boolean) {
        if (!AppSettings.spotifyLikeSync.value) return
        if (AppSettings.spotifySpdcToken.value.isBlank()) return
        if (title.isNullOrBlank() || artist.isNullOrBlank()) return
        scope.launch { runCatching { apply(title, artist, liked) }.onFailure { Log.w(TAG, "mirror threw: ${it.message}") } }
    }

    private suspend fun apply(title: String, artist: String, liked: Boolean) {
        val token = SpotifyToken.accessToken()
        if (token == null) {
            Log.w(TAG, "no Spotify token; '$title' not mirrored")
            return
        }
        withContext(Dispatchers.IO) {
            val uri = findTrack(title, artist, token)
            if (uri == null) {
                Log.d(TAG, "no Spotify match for '$title' by '$artist'; not mirrored")
                return@withContext
            }
            val done = mutateViaPathfinder(uri, liked, token) || mutateViaRest(uri, liked, token)
            Log.d(TAG, "${if (liked) "like" else "unlike"} of $uri ('$title') ${if (done) "mirrored" else "failed"}")
        }
    }

    private fun headers(token: String): Map<String, String> {
        val headers = mutableMapOf(
            "Authorization" to "Bearer $token",
            "User-Agent" to CANVAS_UA,
            "Accept" to "application/json",
        )
        SpotifyToken.clientToken()?.let { headers["Client-Token"] = it }
        return headers
    }

    // ---- Finding the track ---------------------------------------------

    private fun findTrack(title: String, artist: String, token: String): String? =
        searchViaPathfinder(title, artist, token) ?: searchViaRest(title, artist, token)

    private fun searchViaPathfinder(title: String, artist: String, token: String): String? {
        val headers = headers(token)
        if ("Client-Token" !in headers) return null
        val variables = buildJsonObject {
            put("searchTerm", "$title $artist")
            put("offset", 0)
            put("limit", 10)
            put("numberOfTopResults", 5)
            put("includeAudiobooks", false)
            put("includePreReleases", false)
        }.toString()
        val extensions = buildJsonObject {
            putJsonObject("persistedQuery") {
                put("version", 1)
                put("sha256Hash", SEARCH_HASH)
            }
        }.toString()
        val url = PATHFINDER_V1.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "searchTracks")
            .addQueryParameter("variables", variables)
            .addQueryParameter("extensions", extensions)
            .build().toString()
        val (code, body) = canvasGetWithStatus(url, headers + ("App-platform" to "WebPlayer"))
        if (body == null) {
            Log.w(TAG, "pathfinder search failed, http $code")
            return null
        }
        return SpotifyLikeMirror.bestMatch(SpotifyLikeMirror.pathfinderCandidates(body), title, artist)?.uri
    }

    private fun searchViaRest(title: String, artist: String, token: String): String? {
        val url = REST_SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("q", "$title $artist")
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", "10")
            .build().toString()
        val (code, body) = canvasGetWithStatus(url, headers(token))
        if (body == null) {
            Log.w(TAG, "rest search failed, http $code")
            return null
        }
        return SpotifyLikeMirror.bestMatch(SpotifyLikeMirror.restCandidates(body), title, artist)?.uri
    }

    // ---- Saving / removing ---------------------------------------------

    private fun mutateViaPathfinder(uri: String, liked: Boolean, token: String, isRetry: Boolean = false): Boolean {
        val headers = headers(token)
        if ("Client-Token" !in headers) return false
        val operation = if (liked) SpotifyLikeMirror.ADD_OPERATION else SpotifyLikeMirror.REMOVE_OPERATION
        val hash = (if (liked) addHash else removeHash).hash(forceRefresh = isRetry)
        val request = Request.Builder()
            .url(SpotifyCanvasQuery.ENDPOINT)
            .post(
                SpotifyLikeMirror.mutationBody(operation, uri, hash)
                    .toByteArray(Charsets.UTF_8)
                    .toRequestBody("application/json".toMediaType()),
            )
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .header("App-platform", "WebPlayer")
            .build()
        val (code, body) = runCatching {
            Http.client.newCall(request).execute().use { response ->
                response.code to if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrDefault(-1 to null)
        if (body == null) {
            Log.w(TAG, "pathfinder $operation failed, http $code")
            return false
        }
        return when (val answer = SpotifyLikeMirror.parseMutation(body)) {
            SpotifyLikeMirror.MutationAnswer.Done -> true
            is SpotifyLikeMirror.MutationAnswer.Failed -> {
                Log.w(TAG, "pathfinder $operation refused: ${answer.reason}")
                if (answer.staleHash && !isRetry) mutateViaPathfinder(uri, liked, token, isRetry = true) else false
            }
        }
    }

    private fun mutateViaRest(uri: String, liked: Boolean, token: String): Boolean {
        val id = SpotifyLikeMirror.trackId(uri) ?: return false
        val url = REST_SAVED_TRACKS.toHttpUrl().newBuilder().addQueryParameter("ids", id).build()
        val request = Request.Builder()
            .url(url)
            .apply { headers(token).forEach { (name, value) -> header(name, value) } }
            .apply {
                val empty = ByteArray(0).toRequestBody(null)
                if (liked) put(empty) else delete(empty)
            }
            .build()
        val code = runCatching {
            Http.client.newCall(request).execute().use { it.code }
        }.getOrDefault(-1)
        if (code !in 200..299) Log.w(TAG, "rest ${if (liked) "save" else "remove"} failed, http $code")
        return code in 200..299
    }
}
