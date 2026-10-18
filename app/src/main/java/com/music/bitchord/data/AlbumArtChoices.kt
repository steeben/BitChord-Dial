package com.music.bitchord.data

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.DetailPage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.ui.screens.LIBRARY_GROUP_PREFIX
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.FileSystem
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * The cover the user picked for an album out of the artwork its own files
 * carry, kept on this device only.
 *
 * Android files every track with one album name under one album id and hands
 * all of them the same picture — whichever file it read first — so an album
 * holding a single with its own sleeve can end up wearing the single's. The
 * files themselves still carry their own pictures; [candidates] reads them,
 * and the one picked stands for the album wherever the app draws the album
 * (its page, its card) and on every one of its songs that has no different
 * picture of its own, so the rest of the album stops wearing the single's
 * sleeve in the player and the queue. A song whose own file carries another
 * picture — the single — shows that one instead.
 *
 * Both are saved as files under the app's own storage, since the media
 * notification can't read a picture out of a track the way the app can.
 *
 * A device album has no id anything else knows it by, so it is keyed by its
 * name, which is also what the library groups it by. A YouTube Music album
 * is keyed by its browse id.
 */
object AlbumArtChoices {
    private const val PREF_NAME = "bitchord_album_art"
    private const val KEY_CHOICES = "choices_json"
    private const val KEY_PICKED = "picked_json"
    private const val KEY_SONGS = "songs_json"
    private const val KEY_SONG_PICKS = "song_picks_json"
    private const val KEY_SONG_PICKED = "song_picked_json"
    private const val DIR = "album_art"

    /** The scheme [TrackArtFetcher] answers: a track's own embedded picture. */
    const val TRACK_ART_SCHEME = "trackart"

    private var prefs: SharedPreferences? = null

    /** Album key (see [keyOf]) to the picked artwork's url. */
    private val _choices = MutableStateFlow<Map<String, String>>(emptyMap())
    val choices = _choices.asStateFlow()

    /** Album key to the candidate the pick was made from, to mark it in the picker. */
    private var picked: Map<String, String> = emptyMap()

    /** Album key to the art each of its device files was given: content uri to url. */
    private var albumSongs: Map<String, Map<String, String>> = emptyMap()

    /** One song's own pick, which wins over its album's: content uri to url. */
    private var songPicks: Map<String, String> = emptyMap()

    /** Content uri to the candidate that song's pick was made from. */
    private var songPicked: Map<String, String> = emptyMap()

    /** A device file's content uri to the art it shows — see [songArtFor]. */
    private val _songArt = MutableStateFlow<Map<String, String>>(emptyMap())
    val songArt = _songArt.asStateFlow()

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs = p
        runCatching { _choices.value = p.getString(KEY_CHOICES, null)?.let(::readMap).orEmpty() }
        runCatching { picked = p.getString(KEY_PICKED, null)?.let(::readMap).orEmpty() }
        runCatching {
            val obj = JSONObject(p.getString(KEY_SONGS, null) ?: return@runCatching)
            albumSongs = obj.keys().asSequence().associateWith { readMap(obj.getJSONObject(it).toString()) }
        }
        runCatching { songPicks = p.getString(KEY_SONG_PICKS, null)?.let(::readMap).orEmpty() }
        runCatching { songPicked = p.getString(KEY_SONG_PICKED, null)?.let(::readMap).orEmpty() }
        _songArt.value = mergedSongArt()
    }

    private fun mergedSongArt(): Map<String, String> =
        albumSongs.values.fold(emptyMap<String, String>()) { all, songs -> all + songs } + songPicks

    private fun readMap(raw: String): Map<String, String> {
        val obj = JSONObject(raw)
        return obj.keys().asSequence().associateWith { obj.getString(it) }
    }

    fun keyOf(browseId: String?, title: String): String =
        browseId?.takeUnless { it.isBlank() || it.startsWith(LIBRARY_GROUP_PREFIX) }
            ?: ("album:" + LibraryArtists.key(title))

    fun chosen(browseId: String?, title: String, choices: Map<String, String> = _choices.value): String? =
        choices[keyOf(browseId, title)]

    /** Which of the picker's candidates is the album's cover now. */
    fun pickedCandidate(browseId: String?, title: String): String? {
        val key = keyOf(browseId, title)
        return picked[key] ?: _choices.value[key]
    }

    /** The art a device file shows, when its album's cover was picked. */
    fun songArtFor(contentUri: String?): String? = contentUri?.let { _songArt.value[it] }

    /**
     * Makes [url], one of [candidates], [songs]' album's cover, and gives each
     * of its device files either that or, where the file carries a different
     * picture, its own.
     */
    suspend fun pick(context: Context, browseId: String?, title: String, url: String, songs: List<Song>) =
        withContext(Dispatchers.IO) {
            val key = keyOf(browseId, title)
            val chosenBytes = trackArtContentUri(url)?.let { embeddedPicture(context, it) }
            val chosenSha = chosenBytes?.let(::digest)
            val cover = chosenBytes?.let { save(context, it, chosenSha!!) } ?: url
            val perSong = LinkedHashMap<String, String>()
            songs.forEach { song ->
                val local = song.localUri?.takeIf { it.startsWith("content://") } ?: return@forEach
                val own = embeddedPicture(context, local)
                val ownSha = own?.let(::digest)
                perSong[local] = if (own != null && ownSha != chosenSha) save(context, own, ownSha!!) else cover
            }
            _choices.value = _choices.value + (key to cover)
            picked = picked + (key to url)
            albumSongs = albumSongs + (key to perSong)
            persist()
        }

    fun clear(browseId: String?, title: String) {
        val key = keyOf(browseId, title)
        _choices.value = _choices.value - key
        picked = picked - key
        albumSongs = albumSongs - key
        persist()
    }

    /** Which candidate this one song's art was picked from, or null if it wasn't. */
    fun pickedCandidateForSong(contentUri: String?): String? = contentUri?.let { songPicked[it] }

    /** Gives just [song] the cover [url], one of its album's [candidates]. */
    suspend fun pickForSong(context: Context, song: Song, url: String) = withContext(Dispatchers.IO) {
        val local = song.localUri?.takeIf { it.startsWith("content://") } ?: return@withContext
        val bytes = trackArtContentUri(url)?.let { embeddedPicture(context, it) }
        val art = bytes?.let { save(context, it, digest(it)) } ?: url
        songPicks = songPicks + (local to art)
        songPicked = songPicked + (local to url)
        persist()
    }

    /** Puts [song] back to whatever its album gives it. */
    fun clearSong(song: Song) {
        val local = song.localUri ?: return
        songPicks = songPicks - local
        songPicked = songPicked - local
        persist()
    }

    private fun persist() {
        _songArt.value = mergedSongArt()
        prefs?.edit()
            ?.putString(KEY_SONG_PICKS, JSONObject(songPicks).toString())
            ?.putString(KEY_SONG_PICKED, JSONObject(songPicked).toString())
            ?.putString(KEY_CHOICES, JSONObject(_choices.value).toString())
            ?.putString(KEY_PICKED, JSONObject(picked).toString())
            ?.putString(KEY_SONGS, JSONObject(albumSongs.mapValues { JSONObject(it.value) }).toString())
            ?.apply()
    }

    /** Writes a picture once, named by its content, and returns its `file://` url. */
    private fun save(context: Context, bytes: ByteArray, sha: String): String {
        val folder = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(folder, "$sha.img")
        if (!file.exists()) file.writeBytes(bytes)
        return Uri.fromFile(file).toString()
    }

    private fun trackArtContentUri(url: String): String? {
        val prefix = "$TRACK_ART_SCHEME://"
        return if (url.startsWith(prefix)) "content://" + url.removePrefix(prefix) else null
    }

    /** Whether [songs] could hold more than one cover worth choosing between. */
    fun worthOffering(songs: List<Song>): Boolean =
        songs.any { it.localUri?.startsWith("content://") == true } ||
            songs.mapNotNullTo(HashSet()) { it.thumbnailUrl }.size > 1

    /** The url that draws [song]'s own embedded picture, for a file on the device. */
    private fun trackArtUrl(contentUri: String): String =
        TRACK_ART_SCHEME + "://" + contentUri.removePrefix("content://")

    /**
     * Every different cover among [songs], in track order: the picture embedded
     * in each file on the device, told apart by content rather than by url (a
     * whole album of identical sleeves is one choice), and the artwork url of
     * anything that isn't a file with a picture in it.
     */
    suspend fun candidates(context: Context, songs: List<Song>): List<String> = withContext(Dispatchers.IO) {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        songs.forEach { song ->
            val local = song.localUri?.takeIf { it.startsWith("content://") }
            val bytes = local?.let { embeddedPicture(context, it) }
            if (local != null && bytes != null) {
                if (seen.add("sha:" + digest(bytes))) out += trackArtUrl(local)
                return@forEach
            }
            song.thumbnailUrl?.let { if (seen.add(it)) out += it }
        }
        out
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    fun embeddedPicture(context: Context, contentUri: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(contentUri))
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/** [this] song with the art its album's picked cover gave it, if any. */
fun Song.withChosenArt(songArt: Map<String, String>): Song =
    localUri?.let { songArt[it] }?.takeIf { it != thumbnailUrl }?.let { copy(thumbnailUrl = it) } ?: this

/**
 * [this] page with the album cover the user picked, if they picked one, and
 * its songs wearing the art that pick gave them — so a song played from the
 * page shows it in the player too.
 */
fun DetailPage.withChosenAlbumArt(
    choices: Map<String, String>,
    songArt: Map<String, String> = emptyMap(),
): DetailPage {
    if (choices.isEmpty()) return this
    val state = songs
    val withArt = if (songArt.isNotEmpty() && state is UiState.Success) {
        UiState.Success(state.data.map { it.withChosenArt(songArt) })
    } else {
        state
    }
    // A device album matched to YouTube Music opens under the YouTube id, so a
    // cover picked for it in the library (kept by name) counts there too.
    val own = if (type == BrowseType.ALBUM) {
        AlbumArtChoices.chosen(browseId, title, choices) ?: AlbumArtChoices.chosen(null, title, choices)
    } else {
        null
    }
    val albumCards = sections.map { shelf ->
        shelf.copy(
            items = shelf.items.map { item ->
                val id = item.browseId
                if (id != null && id.startsWith(LIBRARY_GROUP_PREFIX + "album:")) {
                    AlbumArtChoices.chosen(id, item.title, choices)?.let { item.copy(thumbnailUrl = it) } ?: item
                } else {
                    item
                }
            },
        )
    }
    return copy(thumbnailUrl = own ?: thumbnailUrl, sections = albumCards, songs = withArt)
}

/**
 * Draws a `trackart://` url — see [AlbumArtChoices] — as the picture embedded
 * in that one file, rather than the album-wide one Android files it under.
 */
class TrackArtFetcher private constructor(
    private val contentUri: String,
    private val context: Context,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = withContext(Dispatchers.IO) { AlbumArtChoices.embeddedPicture(context, contentUri) }
            ?: error("No picture in $contentUri")
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    /** Coil reads a string model as a [coil3.Uri] before asking any fetcher. */
    class Factory : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            from(data.toString(), options)
    }

    /** In case a string model ever reaches the fetchers as it is. */
    class StringFactory : Fetcher.Factory<String> {
        override fun create(data: String, options: Options, imageLoader: ImageLoader): Fetcher? =
            from(data, options)
    }

    private companion object {
        fun from(text: String, options: Options): Fetcher? {
            val prefix = "${AlbumArtChoices.TRACK_ART_SCHEME}://"
            if (!text.startsWith(prefix)) return null
            val rest = text.removePrefix(prefix)
            if (rest.isEmpty()) return null
            return TrackArtFetcher("content://$rest", options.context)
        }
    }
}
