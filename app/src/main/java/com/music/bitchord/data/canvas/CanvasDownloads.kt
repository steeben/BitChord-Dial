package com.music.bitchord.data.canvas

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import androidx.media3.exoplayer.offline.ProgressiveDownloader
import com.music.bitchord.data.CanvasAudioFixes
import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.VideoCanvases
import com.music.bitchord.data.innertube.InnerTubeXResolver
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.ChunkedDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Canvases saved alongside downloaded songs — Storage's "Download canvases".
 *
 * A downloaded song plays offline, but its Canvas was always a lookup and a
 * stream: on a plane the player fell back to the still sleeve. With the switch
 * on, finishing a song's download also saves what the player would show
 * behind it: the looping clip (Apple's HLS, or a provider's MP4), or — when
 * the song has no clip of its own — the music video standing in as its Canvas.
 *
 * Kept in their own [SimpleCache] under the no-backup files directory with no
 * evictor, so nothing but deleting the song takes them out again. Playback
 * reads it ahead of everything else ([dataSourceFactory]), with the same cache
 * keys the player asks for, so a saved clip is never fetched again even online.
 */
@OptIn(UnstableApi::class)
object CanvasDownloads {

    private const val TAG = "CanvasDownloads"
    const val DIR = "canvas_downloads"
    private const val PREFS = "canvas_downloads"
    private const val KEY_INDEX = "index"

    /** A music video's picture is saved at this height: sharp on a phone, not a film's worth of bytes. */
    private const val VIDEO_HEIGHT = 720

    /** HLS clips: the tallest H.264 rendition up to this, which every phone decodes. */
    private const val CLIP_MAX_HEIGHT = 1080

    /** A catalogue track's "video" is its cover held still: nothing to save. */
    private const val MIN_VIDEO_ASPECT = 1.2f

    private const val VIDEO_CHUNK_BYTES = 1L * 1024 * 1024

    /** What was saved for one song. */
    @Serializable
    data class Stored(
        /** The URL the clip plays from — its cache key — or the video's last stream URL. */
        val url: String,
        /** The HLS variant saved, when the clip is a multivariant playlist. */
        val variant: Int? = null,
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val spotify: Boolean = false,
        /** Set when the Canvas is this music video's picture rather than a clip. */
        val musicVideoId: String? = null,
        val width: Int = 0,
        val height: Int = 0,
        /** Every cache key the download wrote, so deleting the song frees exactly those. */
        val keys: List<String> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val indexSerializer = MapSerializer(String.serializer(), Stored.serializer())

    private var prefs: SharedPreferences? = null
    private var cache: SimpleCache? = null

    private val _index = MutableStateFlow<Map<String, Stored>>(emptyMap())

    /** Song video id → what was saved for it. */
    val index: StateFlow<Map<String, Stored>> = _index.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** One download at a time: the keys a download wrote are read off the cache around it. */
    private val writing = Mutex()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val triedThisSession = ConcurrentHashMap.newKeySet<String>()

    /** Opened once per process, beside [CanvasCache.init]. */
    fun init(context: Context) {
        val app = context.applicationContext
        val store = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _index.value = runCatching {
            json.decodeFromString(indexSerializer, store.getString(KEY_INDEX, null) ?: "{}")
        }.getOrDefault(emptyMap())
        cache = runCatching {
            SimpleCache(File(app.noBackupFilesDir, DIR), NoOpCacheEvictor(), StandaloneDatabaseProvider(app))
        }.onFailure { TrackLog.w(TAG, "couldn't open the saved canvases: ${it.message}") }.getOrNull()
        // A saved music video can't be looked up offline, which is the only
        // time it is needed: tell the player about it now.
        _index.value.forEach { (song, stored) ->
            stored.musicVideoId?.let { VideoCanvases.restore(song, it) }
        }
    }

    // ── Playing ─────────────────────────────────────────────────────────────

    /** [upstream] read through the saved canvases first; never written to from here. */
    fun dataSourceFactory(upstream: DataSource.Factory): DataSource.Factory {
        val saved = cache ?: return upstream
        return CacheDataSource.Factory()
            .setCache(saved)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** The clip saved for [videoId], ready to mount, or null. */
    fun storedCanvas(videoId: String): CanvasArtwork? {
        val stored = _index.value[videoId] ?: return null
        if (stored.musicVideoId != null) return null
        return CanvasArtwork(
            url = stored.url,
            title = stored.title,
            artist = stored.artist,
            album = stored.album,
            source = if (stored.spotify) CanvasSource.SPOTIFY else CanvasSource.OTHER,
        )
    }

    /** Whether anything is saved as [videoId]'s Canvas. */
    fun has(videoId: String): Boolean = videoId in _index.value

    /** The HLS variant saved for a clip at [url], so the player asks for only what is on disk. */
    fun streamKeysFor(url: String): List<StreamKey> {
        val variant = _index.value.values.firstOrNull { it.url == url && it.musicVideoId == null }?.variant
            ?: return emptyList()
        return listOf(StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, variant))
    }

    /** A saved music video's picture: the media item to play it from the cache, and its aspect. */
    fun storedVideo(musicVideoId: String): Pair<MediaItem, Stored>? {
        if (cache == null) return null
        val stored = _index.value.values.firstOrNull { it.musicVideoId == musicVideoId } ?: return null
        val item = MediaItem.Builder()
            .setUri(stored.url)
            .setCustomCacheKey(videoKey(musicVideoId))
            .build()
        return item to stored
    }

    // ── Saving ──────────────────────────────────────────────────────────────

    /**
     * A song just finished downloading: save its Canvas too, when the switch
     * is on — filed under [song] and every id in [alsoAs] the same file was.
     */
    fun onSongDownloaded(song: Song, alsoAs: Collection<String> = emptyList()) {
        if (!AppSettings.downloadCanvases.value) return
        scope.launch { save(song, alsoAs) }
    }

    /**
     * A downloaded song is playing with nothing saved for it — downloaded
     * before the switch was on, or its music video was only found now. Saved
     * once per session at most, and only where downloads may spend data.
     */
    fun onDownloadedSongShown(song: Song) {
        if (!AppSettings.downloadCanvases.value || has(song.videoId)) return
        if (!AppSettings.downloadsAllowedNow) return
        if (!triedThisSession.add(song.videoId)) return
        scope.launch { save(song) }
    }

    private suspend fun save(song: Song, alsoAs: Collection<String> = emptyList()) {
        val id = song.videoId
        if (id.isBlank() || has(id) || cache == null || !inFlight.add(id)) return
        try {
            if (!AppSettings.downloadsAllowedNow) return
            val fixes = CanvasAudioFixes.state.value
            val paired = fixes.pairedVideo[id]
            val clip = when {
                paired != null || id in fixes.hiddenCanvas -> null
                id in fixes.albumArtCanvas -> CanvasRepository.albumCanvasFor(song) ?: CanvasRepository.canvasFor(song)
                else -> CanvasRepository.canvasFor(song)
            }?.takeIf { it.musicVideoId == null }
            val stored = (
                if (clip != null) {
                    saveClip(clip)
                } else {
                    val video = paired ?: VideoCanvases.pictureFor(id) ?: run {
                        VideoCanvases.findFor(song)
                        VideoCanvases.pictureFor(id)
                    }
                    video?.takeIf { paired != null || id !in fixes.hiddenVideoCanvas }?.let { saveVideo(it) }
                }
                ) ?: return
            val ids = (listOf(id) + alsoAs).filter { it.isNotBlank() }.distinct()
            _index.update { it + ids.associateWith { stored } }
            persist()
            stored.musicVideoId?.let { video -> ids.forEach { VideoCanvases.restore(it, video) } }
            TrackLog.d(TAG, "saved the Canvas for '${song.title}' (${stored.keys.size} parts)", id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TrackLog.w(TAG, "couldn't save the Canvas for '${song.title}': ${e.message}", id)
        } finally {
            inFlight.remove(id)
        }
    }

    /** The clip the phone player mounts — the 3:4 one where there is one — into the cache. */
    private suspend fun saveClip(clip: CanvasArtwork): Stored? {
        val url = clip.portraitUrl ?: clip.url
        val factory = writeFactory(OkHttpDataSource.Factory(Http.client)) ?: return null
        val hls = url.substringBefore('?').lowercase(Locale.ROOT).endsWith(".m3u8")
        val variant = if (hls) pickVariant(url) else null
        val keys = recordingKeys {
            if (hls) {
                val item = MediaItem.Builder().setUri(url).apply {
                    if (variant != null) {
                        setStreamKeys(listOf(StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, variant)))
                    }
                }.build()
                HlsDownloader(item, factory).download(null)
            } else {
                ProgressiveDownloader(MediaItem.fromUri(url), factory).download(null)
            }
        }
        return Stored(
            url = url,
            variant = variant,
            title = clip.title,
            artist = clip.artist,
            album = clip.album,
            spotify = clip.source == CanvasSource.SPOTIFY,
            keys = keys,
        )
    }

    /** A music video's picture, video only, as one progressive file under a key of its own. */
    private suspend fun saveVideo(musicVideoId: String): Stored? {
        _index.value.values.firstOrNull { it.musicVideoId == musicVideoId }?.let { return it }
        val stream = InnerTubeXResolver.extractVideo(musicVideoId, VIDEO_HEIGHT) ?: return null
        if (stream.aspect < MIN_VIDEO_ASPECT) return null
        val upstream = OkHttpDataSource.Factory(Http.client).setDefaultRequestProperties(stream.headers)
        val factory = writeFactory(ChunkedDataSource.Factory(upstream, VIDEO_CHUNK_BYTES)) ?: return null
        val keys = recordingKeys {
            val item = MediaItem.Builder()
                .setUri(stream.url)
                .setCustomCacheKey(videoKey(musicVideoId))
                .build()
            ProgressiveDownloader(item, factory).download(null)
        }
        return Stored(
            url = stream.url,
            musicVideoId = musicVideoId,
            width = stream.width,
            height = stream.height,
            keys = keys,
        )
    }

    private fun writeFactory(upstream: DataSource.Factory): CacheDataSource.Factory? {
        val saved = cache ?: return null
        return CacheDataSource.Factory()
            .setCache(saved)
            .setUpstreamDataSourceFactory(upstream)
    }

    /** Runs [download] alone, and returns the cache keys it added. */
    private suspend fun recordingKeys(download: () -> Unit): List<String> = writing.withLock {
        val saved = cache ?: return@withLock emptyList()
        val before = saved.keys.toSet()
        try {
            download()
        } catch (e: Exception) {
            // Half a clip is no use offline: take back whatever it wrote.
            (saved.keys - before).forEach { runCatching { saved.removeResource(it) } }
            throw e
        }
        (saved.keys - before).toList()
    }

    /**
     * The H.264 rendition of a multivariant playlist nearest [CLIP_MAX_HEIGHT]
     * without going over, or null for a playlist with a single rendition.
     */
    private fun pickVariant(url: String): Int? {
        val request = Request.Builder().url(url).build()
        val playlist = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            HlsPlaylistParser().parse(Uri.parse(url), body.byteStream())
        } as? HlsMultivariantPlaylist ?: return null
        val variants = playlist.variants.withIndex().toList()
        if (variants.size <= 1) return null
        val avc = variants.filter { it.value.format.codecs?.contains("avc1") == true }.ifEmpty { variants }
        val fitting = avc.filter { it.value.format.height in 1..CLIP_MAX_HEIGHT }
        val chosen = fitting.maxByOrNull { it.value.format.height * 100_000L + it.value.format.bitrate }
            ?: avc.minByOrNull { it.value.format.height }
        return chosen?.index
    }

    private fun videoKey(musicVideoId: String) = "music-video:$musicVideoId"

    // ── Forgetting ──────────────────────────────────────────────────────────

    /** [videoId]'s download was deleted: so is its Canvas, unless another download shares it. */
    fun forget(videoId: String) {
        val removed = _index.value[videoId] ?: return
        _index.update { it - videoId }
        persist()
        scope.launch {
            writing.withLock {
                val stillUsed = _index.value.values.flatMap { it.keys }.toSet()
                removed.keys.filter { it !in stillUsed }.forEach { key ->
                    runCatching { cache?.removeResource(key) }
                }
            }
        }
    }

    private fun persist() {
        prefs?.edit()?.putString(KEY_INDEX, json.encodeToString(indexSerializer, _index.value))?.apply()
    }
}
