package com.music.bitchord.data.spotify

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.podcasts.PodcastSources
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The podcast shows Spotify recommends, as YouTube Music shows.
 *
 * Spotify only chooses, as it does for the speed dial: each show it names is
 * looked for on YouTube Music by its name, and a show that is not there is
 * left out. One search for each show, so the answer is kept for a while and
 * not asked for again each time the Podcasts page opens.
 *
 * Every step is best-effort: no session, a failed request or no show found
 * all answer with an empty list, and the dial keeps to YouTube Music's shows.
 */
object SpotifyPodcasts {

    private const val TAG = "SpotifyPodcasts"

    /** Most shows looked for; each costs a search. */
    private const val MAX_SHOWS = 18

    /** Searches run at once. */
    private const val CONCURRENT_SEARCHES = 4

    private const val TIMEOUT_MS = 25_000L

    /** How long an answer is kept. */
    private const val KEPT_MS = 30 * 60_000L

    private var kept: List<ShelfItem>? = null
    private var keptAt = 0L

    /** Which Spotify session [kept] was read with: another account's shows are not this one's. */
    private var keptFor = 0

    /**
     * Spotify's shows, found on YouTube Music, in Spotify's order. [fresh]
     * asks again whatever is kept (a pull on the page).
     */
    suspend fun shows(fresh: Boolean = false): List<ShelfItem> {
        val session = AppSettings.spotifySpdcToken.value
        if (session.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        val saved = kept
        if (!fresh && saved != null && keptFor == session.hashCode() && now - keptAt < KEPT_MS) return saved
        val found = try {
            withTimeoutOrNull(TIMEOUT_MS) { find() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Spotify shows unavailable: ${error.message}")
            null
        }
        // An answer that failed is not kept: the next opening asks again.
        if (found != null) {
            kept = found
            keptAt = now
            keptFor = session.hashCode()
        }
        return found.orEmpty()
    }

    private suspend fun find(): List<ShelfItem> = coroutineScope {
        val shows = SpotifyLibrary.recommendedShows().take(MAX_SHOWS)
        val gate = Semaphore(CONCURRENT_SEARCHES)
        val matched = shows.map { show ->
            async {
                gate.withPermit {
                    val results = YtMusicRepository.searchShows(show.name).getOrNull().orEmpty()
                    PodcastSources.match(show.name, results)
                }
            }
        }.awaitAll().filterNotNull().distinctBy { it.browseId }
        Log.d(TAG, "${matched.size} of ${shows.size} Spotify shows found on YouTube Music")
        matched
    }
}
