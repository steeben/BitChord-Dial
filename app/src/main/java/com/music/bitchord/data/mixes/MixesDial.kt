package com.music.bitchord.data.mixes

import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.spotify.SPOTIFY_PAGE_PREFIX
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.data.spotify.SpotifyPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import kotlin.random.Random

/**
 * The Mixes Dial: a page to the left of the speed dial's first page, of mixes
 * dealt at random. YouTube Music's come from Home's feed, its "Mixed for
 * you" page and its "From the community" shelf; Spotify's, while an account
 * is signed in, are the playlists Spotify made for it (Daily Mixes, Discover
 * Weekly and the like) that are in its library, and those on its home page's
 * "Your top mixes" shelf. The two are dealt in turn, so neither fills the
 * page alone. Up to [DECADES_LIMIT] playlists from Explore's "Decades"
 * category are dealt in turn with them. What room is left goes to YouTube
 * Music's "Trending community playlists". A tile from either community shelf
 * shows its playlist's name ([named]): a mix's art carries its name, a
 * listener's playlist's does not.
 *
 * Only one mix of a kind is dealt: one "Feel Good Mix" whichever app it is
 * from, and one of "Chill Mix 01" and "Chill Mix 02"; see [mixType].
 *
 * Sleep mixes are dealt only at night, between [SLEEP_FROM] and [SLEEP_UNTIL]
 * by the phone's clock; see [isSleepTime].
 */
object MixesDial {

    private const val TAG = "MixesDial"

    /** One dial page at its largest (3 × 4); a 3 × 3 dial leaves the last row off. */
    const val SIZE = 12

    private const val SPOTIFY_TIMEOUT_MS = 15_000L

    /** Home's shelves of other listeners' playlists, by title: the first is dealt with the mixes, the second only fills what is left. */
    const val FROM_THE_COMMUNITY = "From the community"
    const val TRENDING_COMMUNITY = "Trending community playlists"

    /**
     * The tiles (by id) that show their name on the page: those of the last
     * deal that are from [FROM_THE_COMMUNITY] or [TRENDING_COMMUNITY].
     */
    val named = MutableStateFlow<Set<String>>(emptySet())

    /** The ids of the playlists on the community's shelves among [shelves]: see [named]. */
    fun communityIds(shelves: List<HomeShelf>): Set<String> =
        (shelfPlaylists(shelves, FROM_THE_COMMUNITY) + shelfPlaylists(shelves, TRENDING_COMMUNITY)).mapTo(HashSet(), ::key)

    /** How many more pages of Home are read looking for [FROM_THE_COMMUNITY], when the pages Home has loaded lack it. */
    private const val COMMUNITY_PAGES = 4

    /** Explore's category of playlists by decade ('80s, '90s and so on), by its button's title. */
    const val DECADES = "Decades"

    /** How many of the [DECADES] category's playlists one deal may hold. */
    const val DECADES_LIMIT = 3

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

    /** When sleep mixes start being dealt, and when they stop, by the phone's clock. */
    val SLEEP_FROM: LocalTime = LocalTime.of(21, 30)
    val SLEEP_UNTIL: LocalTime = LocalTime.of(4, 0)

    /** Whether [time] is in the night hours sleep mixes are dealt in: 9:30pm to 4:00am. */
    fun isSleepTime(time: LocalTime = LocalTime.now()): Boolean = !time.isBefore(SLEEP_FROM) || !time.isAfter(SLEEP_UNTIL)

    /** A mix for falling asleep to, going by its name: "Sleep Mix", "Deep Sleep", "Sleepy Mix". */
    fun isSleepMix(item: ShelfItem): Boolean = item.title.contains("sleep", ignoreCase = true)

    /**
     * A fresh deal: YouTube Music's mixes and, with [spotify], Spotify's,
     * shuffled together. Sleep mixes are left out unless it is [sleepTime].
     */
    suspend fun deal(
        homeShelves: List<HomeShelf>,
        spotify: Boolean,
        hidden: Set<String>,
        random: Random = Random(System.nanoTime()),
        sleepTime: Boolean = isSleepTime(),
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
        val community = communityShelves(homeShelves)
        val fromCommunity = shelfPlaylists(community, FROM_THE_COMMUNITY)
        val trending = shelfPlaylists(community, TRENDING_COMMUNITY)
        val decades = decadesPlaylists()
        val dealt: (ShelfItem) -> Boolean = { key(it) !in hidden && (sleepTime || !isSleepMix(it)) }
        pick(
            youTube = (fromHome + mixedForYou + fromCommunity).filter(dealt),
            spotify = fromSpotify.await().filter(dealt),
            random = random,
            filler = trending.filter(dealt),
            decades = decades.filter(dealt),
        ).also {
            named.value = communityIds(community)
            Log.d(TAG, "dealt ${it.size} mixes (community ${fromCommunity.size}, trending ${trending.size}, decades ${decades.size})")
        }
    }

    /**
     * Up to [limit] mixes: each source shuffled, then taken from in turn, so
     * the page has both while both have some. Duplicates go, and so does a
     * second mix of a kind already dealt ([mixType]). [decades] takes its
     * turn with the two sources until [decadesLimit] of its playlists are
     * dealt. [filler] is only dealt from once both sources have run out.
     */
    fun pick(
        youTube: List<ShelfItem>,
        spotify: List<ShelfItem>,
        random: Random,
        limit: Int = SIZE,
        filler: List<ShelfItem> = emptyList(),
        decades: List<ShelfItem> = emptyList(),
        decadesLimit: Int = DECADES_LIMIT,
    ): List<ShelfItem> {
        val yt = youTube.distinctBy(::key).shuffled(random)
        val sp = spotify.distinctBy(::key).shuffled(random)
        val dealt = ArrayList<ShelfItem>(limit)
        val ids = HashSet<String>()
        val types = HashSet<String>()
        // Whether [item] is new to the page, by id and by kind; noted as dealt when it is.
        fun fresh(item: ShelfItem): Boolean {
            val type = mixType(item.title)
            if (key(item) in ids || type in types) return false
            ids += key(item)
            types += type
            return true
        }
        // Which source leads is chosen at random too.
        val sources = if (random.nextBoolean()) listOf(yt, sp) else listOf(sp, yt)
        // The decades' playlists, and where in each round their turn comes: before, between or after the two.
        val byDecade = if (decadesLimit > 0) decades.distinctBy(::key).shuffled(random).iterator() else emptyList<ShelfItem>().iterator()
        val decadesTurn = if (byDecade.hasNext()) random.nextInt(sources.size + 1) else -1
        var decadesDealt = 0
        // One of the decades' playlists, passing over any already on the page; a turn is not lost to those.
        fun dealDecade() {
            while (dealt.size < limit && decadesDealt < decadesLimit && byDecade.hasNext()) {
                val item = byDecade.next()
                if (fresh(item)) {
                    dealt += item
                    decadesDealt++
                    return
                }
            }
        }
        var i = 0
        while (dealt.size < limit && (i < maxOf(yt.size, sp.size) || (decadesDealt < decadesLimit && byDecade.hasNext()))) {
            for (turn in 0..sources.size) {
                if (turn == decadesTurn) dealDecade()
                val item = sources.getOrNull(turn)?.getOrNull(i) ?: continue
                if (dealt.size < limit && fresh(item)) dealt += item
            }
            i++
        }
        for (item in filler.shuffled(random)) {
            if (dealt.size >= limit) break
            if (fresh(item)) dealt += item
        }
        return dealt
    }

    /**
     * The kind of mix a title names: the title without case, spacing,
     * punctuation or a number at its end. "Chill Mix 01" and "Chill Mix 02"
     * are one kind, as are "Sleep Supermix" and "Sleep super mix", "My Mix 1"
     * and "My Mix 4", and "Your Top Songs 2023" and "Your Top Songs 2022".
     */
    fun mixType(title: String): String {
        val words = title.lowercase().split(NOT_A_WORD).filter { it.isNotEmpty() }
        return words.dropLastWhile { word -> word.all(Char::isDigit) }.joinToString("")
            .ifEmpty { words.joinToString("").ifEmpty { title.trim().lowercase() } }
    }

    private val NOT_A_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** A playlist of any kind, as the community shelves hold: not a song, album or artist. */
    fun isYouTubePlaylist(item: ShelfItem): Boolean {
        if (item.videoId != null) return false
        val id = item.browseId?.removePrefix("VL") ?: return false
        return id.startsWith("PL") || id.startsWith("RD") || id.startsWith("OLAK")
    }

    /** The playlists of the shelf called [title] among [shelves]. */
    fun shelfPlaylists(shelves: List<HomeShelf>, title: String): List<ShelfItem> =
        shelves.filter { it.title.trim().equals(title, ignoreCase = true) }
            .flatMap { it.items }
            .filter(::isYouTubePlaylist)

    /**
     * Home's shelves to look for the community's playlists in. They sit far
     * down Home, usually past what it has loaded by the time the mixes are
     * dealt, so when [loaded] has no "From the community" the feed is read
     * from the top, a few pages on, until it turns up.
     */
    private suspend fun communityShelves(loaded: List<HomeShelf>): List<HomeShelf> {
        if (shelfPlaylists(loaded, FROM_THE_COMMUNITY).isNotEmpty()) return loaded
        val read = ArrayList<HomeShelf>()
        try {
            var feed = YtMusicRepository.home().getOrNull()
            var pages = 0
            while (feed != null) {
                read += feed.shelves
                if (shelfPlaylists(read, FROM_THE_COMMUNITY).isNotEmpty()) break
                val more = feed.continuation?.takeIf { pages++ < COMMUNITY_PAGES } ?: break
                feed = YtMusicRepository.moreHome(more).getOrNull()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Community playlists unavailable: ${error.message}")
        }
        return loaded + read
    }

    /** The [DECADES] category's place on Explore, kept once found: its browse id and params. */
    @Volatile
    private var decadesCategory: Pair<String, String?>? = null

    /** The [DECADES] button among Explore's [sections], whichever group it is in. */
    fun decadesCategory(sections: List<MoodGenreSection>): MoodGenre? =
        sections.flatMap { it.items }.firstOrNull { it.title.trim().equals(DECADES, ignoreCase = true) }

    /** Every playlist of Explore's [DECADES] category, across its shelves; none when it can't be read. */
    private suspend fun decadesPlaylists(): List<ShelfItem> = try {
        val category = decadesCategory
            ?: decadesCategory(YtMusicRepository.moodAndGenres().getOrNull().orEmpty())
                ?.let { it.browseId to it.params }
                ?.also { decadesCategory = it }
        if (category == null) {
            Log.w(TAG, "Explore has no $DECADES category")
            emptyList()
        } else {
            YtMusicRepository.moodGenreShelves(category.first, category.second).getOrNull().orEmpty()
                .flatMap { it.items }
                .filter(::isYouTubePlaylist)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(TAG, "Decades playlists unavailable: ${error.message}")
        emptyList()
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

    /** The library's mixes and the home page's "Your top mixes", each read on its own so one failing leaves the other. */
    private suspend fun spotifyMixes(): List<ShelfItem> = coroutineScope {
        val library = async { spotifyRead("library") { SpotifyLibrary.playlists().filter(::isSpotifyMix) } }
        val topMixes = async { spotifyRead("top mixes") { SpotifyLibrary.topMixes() } }
        (library.await() + topMixes.await()).map(::tile)
    }

    private suspend fun spotifyRead(what: String, read: suspend () -> List<SpotifyPlaylist>): List<SpotifyPlaylist> = try {
        withTimeoutOrNull(SPOTIFY_TIMEOUT_MS) { read() }.orEmpty()
            .also { Log.d(TAG, "Spotify $what: ${it.size}") }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(TAG, "Spotify $what unavailable: ${error.message}")
        emptyList()
    }

    /** A tile's id on the dials, the same one "Remove from dial" keeps. */
    private fun key(item: ShelfItem): String = item.videoId ?: item.browseId ?: item.title
}
