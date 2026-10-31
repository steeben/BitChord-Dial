package com.music.bitchord.data.settings

import android.content.SharedPreferences
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * What this device has played lately, for the speed dial's recents.
 *
 * Recently played is YouTube's history, which only ever hears of tracks
 * streamed from YouTube Music: a file from the library, a share or a download
 * folder never reaches it, so the dial went on showing YouTube plays as if
 * the library had never been touched. The tracks YouTube can't know of are
 * kept here, and every track's start time too, so they can be slotted into
 * YouTube's list where they were played.
 */
object DevicePlays {
    private var prefs: SharedPreferences? = null

    /** When each track last started on this device, oldest first. */
    private val times = LinkedHashMap<String, Long>()

    /** The tracks YouTube's history won't have, oldest first. */
    private val songs = LinkedHashMap<String, Song>()

    /**
     * Every track this device started lately, YouTube's included, oldest
     * first, as a dial tile would show it. YouTube's history alone left the
     * dial stale whenever it lagged, failed to load or never heard of a play,
     * and only twenty rows of it reach Home; this is the dial's own record.
     */
    private val recent = LinkedHashMap<String, ShelfItem>()

    /** Moves whenever a track YouTube won't hear of starts, so the dial is built again. */
    val version = MutableStateFlow(0)

    fun init(context: android.content.Context) {
        val p = context.getSharedPreferences("bitchord_device_plays", android.content.Context.MODE_PRIVATE)
        prefs = p
        synchronized(this) {
            times.clear()
            songs.clear()
            recent.clear()
            runCatching {
                val saved = JSONObject(p.getString(KEY, null) ?: return@runCatching)
                val started = saved.optJSONArray("times") ?: JSONArray()
                for (i in 0 until started.length()) {
                    val pair = started.getJSONArray(i)
                    times[pair.getString(0)] = pair.getLong(1)
                }
                val tracks = saved.optJSONArray("songs") ?: JSONArray()
                for (i in 0 until tracks.length()) {
                    val o = tracks.getJSONObject(i)
                    val id = o.getString("id")
                    songs[id] = Song(
                        videoId = id,
                        title = o.optString("title"),
                        artist = o.optString("artist"),
                        thumbnailUrl = o.optStringOrNull("thumb"),
                        albumName = o.optStringOrNull("album"),
                        localUri = o.optStringOrNull("uri"),
                        localPath = o.optStringOrNull("path"),
                    )
                }
                val played = saved.optJSONArray("recent") ?: JSONArray()
                for (i in 0 until played.length()) {
                    val o = played.getJSONObject(i)
                    val id = o.getString("id")
                    recent[id] = ShelfItem(
                        title = o.optString("title"),
                        subtitle = o.optString("artist"),
                        thumbnailUrl = o.optStringOrNull("thumb"),
                        videoId = id,
                        browseId = null,
                        isVideo = o.optBoolean("video", false),
                    )
                }
                // Saved before every play was kept: the library's plays carry over.
                if (!saved.has("recent")) {
                    songs.values.forEach { song ->
                        recent[song.videoId] = ShelfItem(song.title, song.artist, song.thumbnailUrl, song.videoId, null)
                    }
                }
            }
        }
    }

    /** A track has started playing on this device. */
    fun onTrackStarted(song: Song) {
        val id = song.videoId.takeIf { it.isNotBlank() } ?: return
        val offYouTube = song.localUri != null || song.localPath != null || !YOUTUBE_ID.matches(id)
        synchronized(this) {
            times.remove(id)
            times[id] = System.currentTimeMillis()
            while (times.size > MAX_TIMES) times.remove(times.keys.first())
            if (song.title.isNotBlank()) {
                recent.remove(id)
                recent[id] = ShelfItem(song.title, song.artist, song.thumbnailUrl, id, null, isVideo = song.isVideo)
                while (recent.size > MAX_RECENT) recent.remove(recent.keys.first())
            }
            if (offYouTube) {
                songs.remove(id)
                songs[id] = Song(
                    videoId = id,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                    albumName = song.albumName,
                    localUri = song.localUri,
                    localPath = song.localPath,
                )
                while (songs.size > MAX_SONGS) songs.remove(songs.keys.first())
            }
            save()
        }
        // Every start, not only the library's: the dial leads with what was
        // played last, and this is what keeps it so between Home's fetches.
        version.value++
    }

    /** The saved track behind a dial tile played off YouTube, so it plays from its file again. */
    @Synchronized
    fun song(videoId: String?): Song? = videoId?.let { songs[it] }

    /**
     * YouTube's Recently played with this device's own plays slotted in by
     * when they were played. A YouTube row this device never played
     * (from another phone, or from before these were kept) takes the time of
     * the nearest row around it that it did, so YouTube's own order holds.
     */
    @Synchronized
    fun withDevicePlays(recents: List<ShelfItem>): List<ShelfItem> {
        if (recent.isEmpty()) return recents
        val known = recents.map { item -> item.videoId?.let(times::get) }
        // Rows ahead of the first known one take its time; with none known at
        // all, YouTube's rows are taken as older than anything played here.
        var floor = known.firstOrNull { it != null } ?: 0L
        val stamped = LongArray(recents.size) { i ->
            floor = minOf(floor, known[i] ?: floor)
            floor
        }
        val local = recent.values.reversed().map { item -> item to (times[item.videoId] ?: 0L) }
        val merged = ArrayList<ShelfItem>(recents.size + local.size)
        var i = 0
        var j = 0
        while (i < recents.size || j < local.size) {
            val takeLocal = j < local.size && (i >= recents.size || local[j].second > stamped[i])
            if (takeLocal) merged += local[j++].first else merged += recents[i++]
        }
        return merged
    }

    private fun save() {
        val started = JSONArray()
        times.forEach { (id, at) -> started.put(JSONArray().put(id).put(at)) }
        val tracks = JSONArray()
        songs.values.forEach { song ->
            tracks.put(
                JSONObject()
                    .put("id", song.videoId)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("thumb", song.thumbnailUrl ?: JSONObject.NULL)
                    .put("album", song.albumName ?: JSONObject.NULL)
                    .put("uri", song.localUri ?: JSONObject.NULL)
                    .put("path", song.localPath ?: JSONObject.NULL),
            )
        }
        val played = JSONArray()
        recent.values.forEach { item ->
            played.put(
                JSONObject()
                    .put("id", item.videoId)
                    .put("title", item.title)
                    .put("artist", item.subtitle)
                    .put("thumb", item.thumbnailUrl ?: JSONObject.NULL)
                    .put("video", item.isVideo),
            )
        }
        prefs?.edit()?.putString(
            KEY,
            JSONObject().put("times", started).put("songs", tracks).put("recent", played).toString(),
        )?.apply()
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private const val KEY = "device_plays"

    /** Well past what Recently played holds, so its oldest rows are still timed. */
    private const val MAX_TIMES = 400

    /** More than the dial has room for. */
    private const val MAX_SONGS = 60

    /** Enough that, folded into their albums and playlists, they still fill the dial. */
    private const val MAX_RECENT = 120

    private val YOUTUBE_ID = Regex("""[A-Za-z0-9_-]{11}""")
}
