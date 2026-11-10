package com.music.bitchord.data.spotify

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.Http
import com.music.bitchord.data.canvas.SpotifyToken
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * The playlist holds [AppSettings.spotifyPlayedKeep] songs at most: after a
 * song is added, the oldest past that are taken out.
 */
object SpotifyPlayedSync {

    private const val TAG = "SpotifyPlayedSync"
    private const val PREFS = "spotify_played_playlist"
    private const val KEY_PLAYLIST = "playlist_id"
    private const val KEY_ADDED = "added"

    /** Each song in the playlist as "uri<tab>key", so one taken out can be added again when next played. */
    private const val KEY_URI_KEYS = "uri_keys"

    /** The most pages of old songs taken out after one play: a lowered keep is caught up over a few. */
    private const val MAX_TRIM_ROUNDS = 10

    /** Songs remembered as already handled; past this the oldest half is forgotten. */
    private const val MAX_REMEMBERED = 4000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val jsonType = "application/json".toMediaType()

    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
                .onFailure { Log.w(TAG, "add threw: ${it.message}") }
        }
    }

    private suspend fun add(store: SharedPreferences, key: String, song: Song) {
        val added = store.getStringSet(KEY_ADDED, emptySet()).orEmpty()
        if (key in added) return
        val token = SpotifyToken.accessToken() ?: return
        val headers = SpotifyLikeSync.headers(token)
        val uri = SpotifyLikeSync.findTrack(song.title, song.artist, token)
        if (uri == null) {
            // Not on Spotify (or not surely the same song): don't ask again.
            remember(store, added, key)
            return
        }
        val playlist = playlistId(store, headers) ?: return
        when (val code = addTrack(playlist, uri, headers)) {
            in 200..299 -> {
                remember(store, added, key)
                store.edit()
                    .putStringSet(KEY_URI_KEYS, store.getStringSet(KEY_URI_KEYS, emptySet()).orEmpty() + "$uri\t$key")
                    .apply()
                Log.d(TAG, "added '${song.title}' to ${SpotifyPlayedList.NAME}")
                trim(store, playlist, headers)
            }
            // The playlist was deleted on Spotify: make a new one next time.
            404 -> store.edit().remove(KEY_PLAYLIST).apply()
            else -> Log.w(TAG, "adding '${song.title}' failed, http $code")
        }
    }

    private fun remember(store: SharedPreferences, added: Set<String>, key: String) {
        val kept = if (added.size >= MAX_REMEMBERED) added.drop(added.size / 2).toSet() else added
        store.edit().putStringSet(KEY_ADDED, kept + key).apply()
    }

    /** Takes the oldest songs out, a page at a time, while the playlist holds more than the keep. */
    private fun trim(store: SharedPreferences, playlistId: String, headers: Map<String, String>) {
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

    /** The playlist's id: the one saved, the one already on the account, or a new one. */
    private suspend fun playlistId(store: SharedPreferences, headers: Map<String, String>): String? {
        store.getString(KEY_PLAYLIST, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val id = runCatching { SpotifyLibrary.playlistIdNamed(SpotifyPlayedList.NAME) }.getOrNull()
            ?: create(headers)
            ?: return null
        store.edit().putString(KEY_PLAYLIST, id).apply()
        return id
    }

    private fun create(headers: Map<String, String>): String? {
        val (code, body) = post(SpotifyPlayedList.CREATE_URL, SpotifyPlayedList.createBody(), headers)
        val id = body?.let(SpotifyPlayedList::createdId)
        if (id == null) Log.w(TAG, "creating ${SpotifyPlayedList.NAME} failed, http $code")
        return id
    }

    /** The status of adding [uri]; a 404 only once every address has said so. */
    private fun addTrack(playlistId: String, uri: String, headers: Map<String, String>): Int {
        var last = -1
        for (url in SpotifyPlayedList.addUrls(playlistId)) {
            last = post(url, SpotifyPlayedList.addBody(uri), headers).first
            if (last != 404 && last != 405) return last
        }
        return last
    }

    private fun post(url: String, body: String, headers: Map<String, String>): Pair<Int, String?> =
        send("POST", url, body, headers)

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
