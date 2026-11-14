package com.music.bitchord.data.podcasts

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.data.spotify.SpotifyLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Where the podcast search looks. */
enum class PodcastSearchSource { YOUTUBE, SPOTIFY, LIBRARY }

/** What a podcast search found: shows, then episodes. Spotify gives shows only. */
data class PodcastHits(val shows: List<ShelfItem>, val episodes: List<Song>) {
    val isEmpty: Boolean get() = shows.isEmpty() && episodes.isEmpty()
}

/**
 * The search the Podcasts tab opens: podcast shows and episodes only, never
 * songs, from YouTube Music, from Spotify (its shows, found on YouTube
 * Music by name) or from the listener's own shows.
 *
 * The search field is the app's one search field; while [active] is set,
 * what is typed in it comes here and not to the song search.
 */
object PodcastSearch {

    private const val TAG = "PodcastSearch"

    /** Spotify shows looked for on YouTube Music; each costs a search. */
    private const val MAX_SPOTIFY_SHOWS = 10

    private const val CONCURRENT_SEARCHES = 4

    private val _active = MutableStateFlow(false)
    /** Set while the search was opened from the Podcasts tab. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _source = MutableStateFlow(PodcastSearchSource.YOUTUBE)
    val source: StateFlow<PodcastSearchSource> = _source.asStateFlow()

    private val _results = MutableStateFlow<UiState<PodcastHits>?>(null)
    /** Null while nothing is searched. */
    val results: StateFlow<UiState<PodcastHits>?> = _results.asStateFlow()

    private var query = ""
    private var job: Job? = null

    /** The listener's own shows and saved episodes, read once for each opening of the search. */
    private var library: PodcastHits? = null

    /** Whether a YouTube Music account is signed in, for the library's lists. */
    private var signedIn = false

    /** Opens the search for podcasts ([on]) or for music. A new opening starts empty. */
    fun setActive(on: Boolean, signedIn: Boolean) {
        _active.value = on
        this.signedIn = signedIn
        reset()
        library = null
    }

    fun setSource(scope: CoroutineScope, value: PodcastSearchSource) {
        if (_source.value == value) return
        _source.value = value
        // What is in the field is asked of the new source at once.
        if (query.isNotBlank()) submit(scope, query) else reset()
    }

    /** The field changed. The library is searched as the listener types; the others when the search is sent. */
    fun onQuery(scope: CoroutineScope, value: String) {
        query = value
        if (value.isBlank()) {
            reset()
            return
        }
        if (_source.value == PodcastSearchSource.LIBRARY) submit(scope, value)
    }

    fun submit(scope: CoroutineScope, value: String) {
        query = value
        val q = value.trim()
        job?.cancel()
        if (q.isEmpty()) {
            _results.value = null
            return
        }
        val source = _source.value
        if (source == PodcastSearchSource.SPOTIFY && AppSettings.spotifySpdcToken.value.isBlank()) {
            _results.value = UiState.Error(SPOTIFY_SIGNED_OUT)
            return
        }
        // The library filters what it has at once; only its first reading waits.
        if (source != PodcastSearchSource.LIBRARY || library == null) _results.value = UiState.Loading
        job = scope.launch {
            val hits = try {
                when (source) {
                    PodcastSearchSource.YOUTUBE -> youtube(q)
                    PodcastSearchSource.SPOTIFY -> spotify(q)
                    PodcastSearchSource.LIBRARY -> filter(ownShows(), q)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "podcast search failed: ${error.message}")
                _results.value = UiState.Error(error.message ?: "")
                return@launch
            }
            _results.value = UiState.Success(hits)
        }
    }

    private fun reset() {
        job?.cancel()
        query = ""
        _results.value = null
    }

    private suspend fun youtube(q: String): PodcastHits = coroutineScope {
        val shows = async { YtMusicRepository.searchShows(q) }
        val episodes = async { YtMusicRepository.searchEpisodes(q) }
        val showList = shows.await()
        val episodeList = episodes.await()
        // Nothing at all and a failure: say so, not "no results".
        if (showList.isFailure && episodeList.isFailure) throw showList.exceptionOrNull()!!
        PodcastHits(showList.getOrNull().orEmpty(), episodeList.getOrNull().orEmpty())
    }

    /** Spotify's shows for [q], each found on YouTube Music by its name; a show not there is left out. */
    private suspend fun spotify(q: String): PodcastHits = coroutineScope {
        val gate = Semaphore(CONCURRENT_SEARCHES)
        val found = SpotifyLibrary.searchShows(q).take(MAX_SPOTIFY_SHOWS).map { show ->
            async {
                gate.withPermit {
                    val results = YtMusicRepository.searchShows(show.name).getOrNull().orEmpty()
                    PodcastSources.match(show.name, results)?.let { match ->
                        // Spotify's cover and publisher, where YouTube's row has none.
                        match.copy(
                            subtitle = match.subtitle.ifBlank { show.publisher.orEmpty() },
                            thumbnailUrl = match.thumbnailUrl ?: show.imageUrl,
                        )
                    }
                }
            }
        }.awaitAll().filterNotNull().distinctBy { it.browseId }
        PodcastHits(found, emptyList())
    }

    /** The shows the account follows and the shows pinned to the Podcasts dial; the account's "Episodes for later". */
    private suspend fun ownShows(): PodcastHits {
        library?.let { return it }
        val followed = if (signedIn) YtMusicRepository.followedShows().getOrNull().orEmpty() else emptyList()
        val pinned = CustomizationSettings.podcastDialPins.value.filter { it.browseId != null && it.videoId == null }
        val later = if (signedIn) {
            YtMusicRepository.browseSongs(EPISODES_FOR_LATER).getOrNull()?.songs.orEmpty()
        } else emptyList()
        return PodcastHits((followed + pinned).distinctBy { it.browseId }, later).also { library = it }
    }

    /** The library's shows and episodes whose name, show or publisher has every word of [q] in it. */
    internal fun filter(all: PodcastHits, q: String): PodcastHits {
        val words = q.lowercase().split(' ').filter { it.isNotBlank() }
        fun matches(vararg fields: String?): Boolean {
            val text = fields.filterNotNull().joinToString(" ").lowercase()
            return words.all { it in text }
        }
        return PodcastHits(
            shows = all.shows.filter { matches(it.title, it.subtitle) },
            episodes = all.episodes.filter { matches(it.title, it.artist) },
        )
    }

    /** YouTube Music's "Episodes for later" list. */
    private const val EPISODES_FOR_LATER = "VLSE"

    /** Shown in place of results; the screen puts the text in the app's language. */
    const val SPOTIFY_SIGNED_OUT = "spotify-signed-out"
}
