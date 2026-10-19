package com.music.bitchord.data

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * A file on the device tied to its track on YouTube Music, so the file can
 * open its artist and album there: the song menu's Open artist and Open
 * album, and holding the cover in the player.
 *
 * A file's tags carry names, never YouTube Music's ids, so nothing on the
 * device could ever lead there before. A link is made three ways:
 * - from the All library, where a file and a YouTube Music track were found
 *   to be the same song ([learn]) — no network at all;
 * - by looking the file up ([resolve]) the first time its menu or the player
 *   needs it, accepted only when [TrackMatcher] calls it the same recording;
 * - by hand, picking the track from a search ([candidates], [link]).
 *
 * Keyed by the file's content uri, which is this device's own, so links are
 * kept apart from the settings a backup carries.
 */
object LocalLinks {
    data class Link(
        val videoId: String,
        val artistId: String?,
        val albumId: String?,
        val artists: List<ArtistRef> = emptyList(),
    )

    private const val PREF_NAME = "local_links"
    private const val KEY_LINKS = "links"
    private const val CANDIDATES = 8

    private var prefs: SharedPreferences? = null

    private val _links = MutableStateFlow<Map<String, Link>>(emptyMap())

    /** A file's content uri to its link. */
    val links: StateFlow<Map<String, Link>> = _links.asStateFlow()

    /** Files looked up this run and not found, so a menu doesn't search again. */
    private val missed = ConcurrentHashMap.newKeySet<String>()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs = p
        _links.value = runCatching { read(p.getString(KEY_LINKS, null)) }.getOrDefault(emptyMap())
    }

    /** The key a file is linked by: its content uri. Null for anything that isn't a file of the listener's own. */
    fun keyOf(song: Song): String? = song.localUri?.takeUnless { song.hasYouTubeId() }

    fun linkFor(song: Song): Link? = keyOf(song)?.let { _links.value[it] }

    /** [song] carrying its link's artist and album, when it has one. */
    fun apply(song: Song): Song = linkFor(song)?.let { song.linkedTo(it) } ?: song

    /** Ties [file] to [track], replacing any link it had. */
    fun link(file: Song, track: Song) {
        val key = keyOf(file) ?: return
        missed -= key
        put(mapOf(key to track.toLink()))
    }

    fun unlink(file: Song) {
        val key = keyOf(file) ?: return
        if (key !in _links.value) return
        _links.value = _links.value - key
        persist()
    }

    /**
     * Links each file in [groups] to the YouTube Music copy of the same song,
     * where that copy says where its artist or album is. A file already
     * linked keeps its link: it may have been picked by hand.
     */
    fun learn(groups: List<DuplicateGroup>) {
        val current = _links.value
        val found = HashMap<String, Link>()
        groups.forEach { group ->
            val file = group.fileCopy ?: return@forEach
            val track = group.youTubeCopy ?: return@forEach
            val key = keyOf(file) ?: return@forEach
            if (key in current || (track.artistId == null && track.albumId == null)) return@forEach
            found[key] = track.toLink()
        }
        if (found.isNotEmpty()) put(found)
    }

    /**
     * [file]'s link, looking the file up on YouTube Music when it has none:
     * a search on its title and artist, held to [TrackMatcher]'s standard of
     * the same recording. Null when nothing passes, or the network is out.
     */
    suspend fun resolve(file: Song): Link? {
        linkFor(file)?.let { return it }
        val key = keyOf(file) ?: return null
        if (key in missed) return null
        val target = TrackMatcher.targetOf(file)
        for (query in TrackMatcher.queries(target).take(2)) {
            val result = YtMusicRepository.search(query, SearchFilter.SONGS)
            val candidates = result.getOrNull()?.tracks() ?: return null
            val match = TrackMatcher.best(candidates, target) ?: continue
            link(file, withIds(match))
            return linkFor(file)
        }
        missed += key
        return null
    }

    /** YouTube Music's songs for [file]'s title and artist, best first, for linking by hand. */
    suspend fun candidates(file: Song): Result<List<Song>> {
        val query = TrackMatcher.queries(TrackMatcher.targetOf(file)).firstOrNull()
            ?: return Result.success(emptyList())
        return YtMusicRepository.search(query, SearchFilter.SONGS).map { it.tracks().take(CANDIDATES) }
    }

    /** [track] with its artist and album ids, asking for them when the search row left them off. */
    suspend fun withIds(track: Song): Song {
        if (track.artistId != null && track.albumId != null) return track
        val links = YtMusicRepository.trackLinks(track.videoId).getOrNull() ?: return track
        return track.copy(
            artistId = track.artistId ?: links.artistId,
            albumId = track.albumId ?: links.albumId,
            albumName = track.albumName ?: links.albumName,
            artists = track.artists.ifEmpty { links.artists },
        )
    }

    private fun List<SearchResult>.tracks(): List<Song> = mapNotNull {
        when (it) {
            is SearchResult.Track -> it.song
            is SearchResult.TopTrack -> it.song
            is SearchResult.Browse -> null
        }
    }.distinctBy { it.videoId }

    private fun Song.toLink() = Link(videoId = videoId, artistId = artistId, albumId = albumId, artists = artists)

    private fun put(added: Map<String, Link>) {
        _links.value = _links.value + added
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        _links.value.forEach { (key, link) ->
            array.put(
                JSONObject()
                    .put("k", key)
                    .put("v", link.videoId)
                    .putOpt("ar", link.artistId)
                    .putOpt("al", link.albumId)
                    .put(
                        "as",
                        JSONArray().apply {
                            link.artists.forEach { put(JSONObject().put("n", it.name).putOpt("id", it.browseId)) }
                        },
                    ),
            )
        }
        prefs?.edit()?.putString(KEY_LINKS, array.toString())?.apply()
    }

    private fun read(raw: String?): Map<String, Link> {
        raw ?: return emptyMap()
        val array = JSONArray(raw)
        val out = HashMap<String, Link>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val artists = obj.optJSONArray("as")
            out[obj.getString("k")] = Link(
                videoId = obj.getString("v"),
                artistId = obj.optString("ar").takeIf { it.isNotEmpty() },
                albumId = obj.optString("al").takeIf { it.isNotEmpty() },
                artists = buildList<ArtistRef> {
                    if (artists != null) {
                        for (j in 0 until artists.length()) {
                            val artist = artists.getJSONObject(j)
                            add(ArtistRef(artist.getString("n"), artist.optString("id").takeIf { it.isNotEmpty() }))
                        }
                    }
                },
            )
        }
        return out
    }
}

/** This file with [link]'s artist and album, which is all a link lends it: its own tags stay. */
fun Song.linkedTo(link: LocalLinks.Link): Song = copy(
    artistId = link.artistId ?: artistId,
    albumId = link.albumId ?: albumId,
    artists = link.artists.ifEmpty { artists },
)
