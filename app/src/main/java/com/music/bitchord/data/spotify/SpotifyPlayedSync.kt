package com.music.bitchord.data.spotify

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.canvas.CANVAS_UA
import com.music.bitchord.data.canvas.SpotifyCanvasQuery
import com.music.bitchord.data.canvas.SpotifyToken
import com.music.bitchord.data.canvas.canvasGet
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Keeps the listener's "Played in Dial" playlist on Spotify: each song that
 * counts as a play here is found on Spotify and added to it, once.
 *
 * See [SpotifyPlayedList] for why a playlist and not listening history. Only
 * while [AppSettings.spotifyPlayedPlaylist] is on and a Spotify account is
 * signed in. Best-effort and in the background: nothing here can hold up or
 * fail playback, and a song with no confident match on Spotify is left out
 * rather than guessed at.
 *
 * Each step goes the web player's own way first and the public Web API's
 * second, since the sign-in here is the web player's and the Web API often
 * turns it away. What became of the last song is kept in [status], for the
 * settings to show: release builds have no log to read it from.
 *
 * The playlist holds [AppSettings.spotifyPlayedKeep] songs at most: after a
 * song is added, the oldest past that are taken out.
 */
object SpotifyPlayedSync {

    private const val TAG = "SpotifyPlayedSync"
    private const val PREFS = "spotify_played_playlist"
    private const val KEY_PLAYLIST = "playlist_id"
    private const val KEY_ADDED = "added"
    private const val KEY_STATUS = "status"

    /** Each song in the playlist as "uri<tab>key", so one taken out can be added again when next played. */
    private const val KEY_URI_KEYS = "uri_keys"

    /** The most pages of old songs taken out after one play: a lowered keep is caught up over a few. */
    private const val MAX_TRIM_ROUNDS = 10

    /** Songs remembered as already handled; past this the oldest half is forgotten. */
    private const val MAX_REMEMBERED = 4000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val jsonType = "application/json".toMediaType()

    private val scriptFetch: (String) -> String? = { url -> canvasGet(url, mapOf("User-Agent" to CANVAS_UA)) }
    private val addHash = hashes(SpotifyPlayedList.ADD_OPERATION, SpotifyPlayedList.KNOWN_ADD_HASH)
    private val removeHash = hashes(SpotifyPlayedList.REMOVE_OPERATION, SpotifyPlayedList.KNOWN_REMOVE_HASH)
    private val profileHash = hashes(SpotifyPlayedList.PROFILE_OPERATION, SpotifyPlayedList.KNOWN_PROFILE_HASH)

    private fun hashes(operation: String, fallback: String) =
        SpotifyCanvasQuery.QueryHashes(fetch = scriptFetch, operation = operation, fallback = fallback)

    @Volatile private var prefs: SharedPreferences? = null

    private val _status = MutableStateFlow<SpotifyPlayedList.Status?>(null)

    /** What became of the last song handled; null until there has been one. */
    val status: StateFlow<SpotifyPlayedList.Status?> = _status

    fun init(context: Context) {
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _status.value = SpotifyPlayedList.Status.decode(store.getString(KEY_STATUS, null))
    }

    /** [song] has just counted as a play. */
    fun onPlayed(song: Song) {
        if (!AppSettings.spotifyPlayedPlaylist.value) return
        if (AppSettings.spotifySpdcToken.value.isBlank()) return
        if (song.title.isBlank() || song.artist.isBlank()) return
        val store = prefs ?: return
        val key = SpotifyRadio.songKey(song.title, song.artist)
        scope.launch {
            runCatching { lock.withLock { add(store, key, song) } }
                .onFailure {
                    Log.w(TAG, "add threw: ${it.message}")
                    failed(store, "'${song.title}': ${it.message ?: it.javaClass.simpleName}")
                }
        }
    }

    private suspend fun add(store: SharedPreferences, key: String, song: Song) {
        val added = store.getStringSet(KEY_ADDED, emptySet()).orEmpty()
        if (key in added) return
        val token = SpotifyToken.accessToken()
            ?: return failed(store, "the Spotify sign-in could not be renewed")
        val headers = SpotifyLikeSync.headers(token)
        val found = SpotifyLikeSync.search(song.title, song.artist, token)
        val uri = found.uri
        if (uri == null) {
            if (!found.answered) {
                // Spotify never said whether it has the song: ask again next time it is played.
                return failed(store, "Spotify would not search for '${song.title}'")
            }
            // Not on Spotify (or not surely the same song): don't ask again.
            remember(store, added, key)
            return report(store, SpotifyPlayedList.Status.Kind.NO_MATCH, song.title)
        }
        val playlist = playlistId(store, headers) ?: return
        val problem = addTrack(playlist, uri, headers)
        if (problem != null) {
            failed(store, "adding '${song.title}': $problem")
            forgetIfGone(store, playlist)
            return
        }
        remember(store, added, key)
        store.edit()
            .putStringSet(KEY_URI_KEYS, store.getStringSet(KEY_URI_KEYS, emptySet()).orEmpty() + "$uri\t$key")
            .apply()
        Log.d(TAG, "added '${song.title}' to ${SpotifyPlayedList.NAME}")
        report(store, SpotifyPlayedList.Status.Kind.ADDED, song.title)
        if (!trimViaWeb(store, playlist, headers)) trimViaRest(store, playlist, headers)
    }

    private fun failed(store: SharedPreferences, detail: String) {
        Log.w(TAG, detail)
        report(store, SpotifyPlayedList.Status.Kind.FAILED, detail)
    }

    private fun report(store: SharedPreferences, kind: SpotifyPlayedList.Status.Kind, detail: String) {
        val status = SpotifyPlayedList.Status(kind, detail, System.currentTimeMillis())
        _status.value = status
        store.edit().putString(KEY_STATUS, status.encode()).apply()
    }

    private fun remember(store: SharedPreferences, added: Set<String>, key: String) {
        val kept = if (added.size >= MAX_REMEMBERED) added.drop(added.size / 2).toSet() else added
        store.edit().putStringSet(KEY_ADDED, kept + key).apply()
    }

    // ---- Adding ---------------------------------------------------------

    /** Null once [uri] is in the playlist, else what each way in said. */
    private fun addTrack(playlistId: String, uri: String, headers: Map<String, String>): String? {
        val web = addViaWeb(playlistId, uri, headers) ?: return null
        val rest = addViaRest(playlistId, uri, headers)
        if (rest in 200..299) return null
        return "web player: $web; Web API: http $rest"
    }

    private fun addViaWeb(playlistId: String, uri: String, headers: Map<String, String>, isRetry: Boolean = false): String? {
        if ("Client-Token" !in headers) return "no client token"
        val hash = addHash.hash(forceRefresh = isRetry)
        val (code, body) = pathfinder(SpotifyPlayedList.webAddBody(playlistId, uri, hash), headers)
        if (body == null) return "http $code"
        return when (val answer = SpotifyPlayedList.webAdded(body)) {
            SpotifyPlayedList.Answer.Done -> null
            is SpotifyPlayedList.Answer.Refused ->
                if (answer.staleHash && !isRetry) addViaWeb(playlistId, uri, headers, isRetry = true) else answer.reason
        }
    }

    /** The status of adding [uri]; a 404 only once every address has said so. */
    private fun addViaRest(playlistId: String, uri: String, headers: Map<String, String>): Int {
        var last = -1
        for (url in SpotifyPlayedList.addUrls(playlistId)) {
            last = send("POST", url, SpotifyPlayedList.addBody(uri), headers).first
            if (last != 404 && last != 405) return last
        }
        return last
    }

    /**
     * After a song could not be added: if the library can be read and the
     * playlist is no longer in it, it was deleted on Spotify, so the next
     * play finds or makes one afresh.
     */
    private suspend fun forgetIfGone(store: SharedPreferences, playlistId: String) {
        val listed = runCatching { SpotifyLibrary.playlistIdNamed(SpotifyPlayedList.NAME) }
        if (listed.isSuccess && listed.getOrNull() != playlistId) store.edit().remove(KEY_PLAYLIST).apply()
    }

    // ---- Keeping it to size ---------------------------------------------

    /**
     * Takes the oldest songs out, a page at a time, while the playlist holds
     * more than the keep. False when the web player's way could not do it.
     */
    private suspend fun trimViaWeb(store: SharedPreferences, playlistId: String, headers: Map<String, String>): Boolean {
        val keep = AppSettings.spotifyPlayedKeep.value
        repeat(MAX_TRIM_ROUNDS) {
            val (total, entries) = runCatching { SpotifyLibrary.playlistHead(playlistId, SpotifyPlayedList.PAGE) }
                .getOrNull() ?: return false
            val over = SpotifyPlayedList.excess(total, keep)
            if (over == 0) return true
            val oldest = entries.take(over)
            if (oldest.isEmpty()) return false
            val problem = removeViaWeb(playlistId, oldest.map { it.first }, headers)
            if (problem != null) {
                Log.w(TAG, "trimming ${SpotifyPlayedList.NAME} the web player's way failed: $problem")
                return false
            }
            forget(store, oldest.map { it.second })
            Log.d(TAG, "took ${oldest.size} of $total out of ${SpotifyPlayedList.NAME} to keep $keep")
        }
        return true
    }

    private fun removeViaWeb(playlistId: String, uids: List<String>, headers: Map<String, String>, isRetry: Boolean = false): String? {
        if ("Client-Token" !in headers) return "no client token"
        val hash = removeHash.hash(forceRefresh = isRetry)
        val (code, body) = pathfinder(SpotifyPlayedList.webRemoveBody(playlistId, uids, hash), headers)
        if (body == null) return "http $code"
        return when (val answer = SpotifyPlayedList.webRemoved(body)) {
            SpotifyPlayedList.Answer.Done -> null
            is SpotifyPlayedList.Answer.Refused ->
                if (answer.staleHash && !isRetry) removeViaWeb(playlistId, uids, headers, isRetry = true) else answer.reason
        }
    }

    private fun trimViaRest(store: SharedPreferences, playlistId: String, headers: Map<String, String>) {
        val keep = AppSettings.spotifyPlayedKeep.value
        repeat(MAX_TRIM_ROUNDS) {
            val (url, body) = oldest(playlistId, headers) ?: return
            val total = SpotifyPlayedList.total(body) ?: return
            val over = SpotifyPlayedList.excess(total, keep)
            if (over == 0) return
            val uris = SpotifyPlayedList.oldestUris(body, over)
            if (uris.isEmpty()) return
            val code = send("DELETE", url, SpotifyPlayedList.removeBody(url, uris), headers).first
            if (code !in 200..299) {
                Log.w(TAG, "trimming ${SpotifyPlayedList.NAME} failed, http $code")
                return
            }
            forget(store, uris)
            Log.d(TAG, "took ${uris.size} of $total out of ${SpotifyPlayedList.NAME} to keep $keep")
        }
    }

    /** The playlist's address that answered, and its oldest songs as it listed them. */
    private fun oldest(playlistId: String, headers: Map<String, String>): Pair<String, String>? {
        for (url in SpotifyPlayedList.addUrls(playlistId)) {
            val body = send("GET", SpotifyPlayedList.oldestUrl(url), null, headers).second
            if (body != null) return url to body
        }
        return null
    }

    /** Songs taken out are no longer remembered as added, so playing one again adds it back. */
    private fun forget(store: SharedPreferences, uris: List<String>) {
        val gone = uris.toSet()
        val entries = store.getStringSet(KEY_URI_KEYS, emptySet()).orEmpty()
        val dropped = entries.filter { it.substringBefore('\t') in gone }.toSet()
        if (dropped.isEmpty()) return
        val keys = dropped.map { it.substringAfter('\t') }.toSet()
        store.edit()
            .putStringSet(KEY_URI_KEYS, entries - dropped)
            .putStringSet(KEY_ADDED, store.getStringSet(KEY_ADDED, emptySet()).orEmpty() - keys)
            .apply()
    }

    // ---- The playlist itself --------------------------------------------

    /** The playlist's id: the one saved, the one already on the account, or a new one. */
    private suspend fun playlistId(store: SharedPreferences, headers: Map<String, String>): String? {
        store.getString(KEY_PLAYLIST, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val id = runCatching { SpotifyLibrary.playlistIdNamed(SpotifyPlayedList.NAME) }.getOrNull()
            ?: create(store, headers)
            ?: return null
        store.edit().putString(KEY_PLAYLIST, id).apply()
        return id
    }

    private fun create(store: SharedPreferences, headers: Map<String, String>): String? {
        val (webId, web) = createViaWeb(headers)
        if (webId != null) return webId
        val (code, body) = send("POST", SpotifyPlayedList.CREATE_URL, SpotifyPlayedList.createBody(), headers)
        val id = body?.let(SpotifyPlayedList::createdId)
        if (id == null) failed(store, "creating the playlist: web player: $web; Web API: http $code")
        return id
    }

    /**
     * The new playlist's id, or why there isn't one. Making a playlist the
     * web player's way takes two steps, making it and putting it in the
     * library, and it only counts once both are done.
     */
    private fun createViaWeb(headers: Map<String, String>): Pair<String?, String> {
        val user = username(headers) ?: return null to "no username"
        val (code, body) = send("POST", SpotifyPlayedList.WEB_CREATE_URL, SpotifyPlayedList.webCreateBody(), headers)
        val id = body?.let(SpotifyPlayedList::webCreatedId) ?: return null to "http $code"
        val (listCode, listed) = send(
            "POST",
            SpotifyPlayedList.rootlistUrl(user),
            SpotifyPlayedList.rootlistAddBody(id, System.currentTimeMillis()),
            headers,
        )
        if (listed == null || !SpotifyPlayedList.rootlistChanged(listed)) return null to "library http $listCode"
        return id to ""
    }

    private fun username(headers: Map<String, String>, isRetry: Boolean = false): String? {
        if ("Client-Token" !in headers) return null
        val hash = profileHash.hash(forceRefresh = isRetry)
        val body = pathfinder(SpotifyPlayedList.queryBody(SpotifyPlayedList.PROFILE_OPERATION, hash), headers).second
        val name = body?.let(SpotifyPlayedList::username)
        return if (name == null && !isRetry) username(headers, isRetry = true) else name
    }

    // ---- HTTP -----------------------------------------------------------

    private fun pathfinder(body: String, headers: Map<String, String>): Pair<Int, String?> =
        send("POST", SpotifyCanvasQuery.ENDPOINT, body, headers + ("App-platform" to "WebPlayer"))

    /** The status of a request and, when it went through, what it answered. */
    private fun send(method: String, url: String, body: String?, headers: Map<String, String>): Pair<Int, String?> {
        val request = Request.Builder()
            .url(url)
            .method(method, body?.toByteArray(Charsets.UTF_8)?.toRequestBody(jsonType))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return runCatching {
            Http.client.newCall(request).execute().use { response ->
                response.code to if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrDefault(-1 to null)
    }
}
