package com.music.bitchord.data.mixes

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.data.spotify.SpotifyPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * The Mixes Dial: a page to the left of the speed dial's first page, of mixes
 * dealt at random. YouTube Music's come from Home's feed and its "Mixed for
 * you" page; Spotify's, while an account is signed in, are the playlists
 * Spotify made for it (Daily Mixes, Discover Weekly and the like) that are in
 * its library. The two are dealt in turn, so neither fills the page alone.
 */
object MixesDial {

    private const val TAG = "MixesDial"

    /** One dial page at its largest (3 × 4); a 3 × 3 dial leaves the last row off. */
    const val SIZE = 12

    private const val SPOTIFY_TIMEOUT_MS = 15_000L

    /** Spotify's own playlists made for one listener, by name, besides those called a mix. */
    private val SPOTIFY_PERSONAL = setOf(
        "discover weekly",
        "release radar",
        "on repeat",
        "repeat rewind",
        "daylist",
        "your top songs",
        "time capsule",
    )

    /** A fresh deal: YouTube Music's mixes and, with [spotify], Spotify's, shuffled together. */
    suspend fun deal(
        homeShelves: List<HomeShelf>,
        spotify: Boolean,
        hidden: Set<String>,
        random: Random = Random(System.nanoTime()),
    ): List<ShelfItem> = coroutineScope {
        val fromSpotify = async { if (spotify) spotifyMixes() else emptyList() }
        val fromHome = homeShelves.flatMap { it.items }.filter(::isYouTubeMix)
        val mixedForYou = try {
            YtMusicRepository.mixedForYou().getOrNull().orEmpty().filter(::isYouTubeMix)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Mixed for you unavailable: ${error.message}")
            emptyList()
        }
        pick(
            youTube = (fromHome + mixedForYou).filterNot { key(it) in hidden },
            spotify = fromSpotify.await().filterNot { key(it) in hidden },
            random = random,
        ).also { Log.d(TAG, "dealt ${it.size} mixes") }
    }

    /**
     * Up to [limit] mixes: each source shuffled, then taken from in turn, so
     * the page has both while both have some. Duplicates go.
     */
    fun pick(youTube: List<ShelfItem>, spotify: List<ShelfItem>, random: Random, limit: Int = SIZE): List<ShelfItem> {
        val yt = youTube.distinctBy(::key).shuffled(random)
        val sp = spotify.distinctBy(::key).shuffled(random)
        val dealt = ArrayList<ShelfItem>(limit)
        val seen = HashSet<String>()
        // Which source leads is chosen at random too.
        val sources = if (random.nextBoolean()) listOf(yt, sp) else listOf(sp, yt)
        var i = 0
        while (dealt.size < limit && i < maxOf(yt.size, sp.size)) {
            for (source in sources) {
                val item = source.getOrNull(i) ?: continue
                if (dealt.size < limit && seen.add(key(item))) dealt += item
            }
            i++
        }
        return dealt
    }

    /**
     * A YouTube Music mix: the account's own (`RDTMAK…` playlists: My Mixes,
     * Supermix, Discover Mix, New Release Mix) or any other playlist that calls
     * itself one.
     */
    fun isYouTubeMix(item: ShelfItem): Boolean {
        if (item.videoId != null) return false
        val id = item.browseId?.removePrefix("VL") ?: return false
        if (id.startsWith("RDTMAK")) return true
        val playlist = id.startsWith("RD") || id.startsWith("PL") || id.startsWith("OLAK")
        return playlist && item.title.contains("mix", ignoreCase = true)
    }

    /** A Spotify playlist Spotify made for this listener, rather than an editorial one or the listener's own. */
    fun isSpotifyMix(playlist: SpotifyPlaylist): Boolean {
        if (playlist.id == SpotifyLibrary.LIKED_ID) return false
        if (!playlist.owner.equals("Spotify", ignoreCase = true)) return false
        val name = playlist.name.trim().lowercase()
        return "mix" in name || name in SPOTIFY_PERSONAL || SPOTIFY_PERSONAL.any { name.startsWith(it) }
    }

    /** A Spotify playlist as a dial tile, opened as its Spotify page is from the library. */
    fun tile(playlist: SpotifyPlaylist): ShelfItem = ShelfItem(
        title = playlist.name,
        subtitle = playlist.owner ?: "Spotify",
        thumbnailUrl = playlist.imageUrl,
        videoId = null,
        browseId = SPOTIFY_PAGE_PREFIX + playlist.id,
    )

    private suspend fun spotifyMixes(): List<ShelfItem> = try {
        withTimeoutOrNull(SPOTIFY_TIMEOUT_MS) {
            SpotifyLibrary.playlists().filter(::isSpotifyMix).map(::tile)
        }.orEmpty()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(TAG, "Spotify mixes unavailable: ${error.message}")
        emptyList()
    }

    /** A tile's id on the dials, the same one "Remove from dial" keeps. */
    private fun key(item: ShelfItem): String = item.videoId ?: item.browseId ?: item.title
}
