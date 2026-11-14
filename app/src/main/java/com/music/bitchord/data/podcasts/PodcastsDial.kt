package com.music.bitchord.data.podcasts

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.ShelfLayout
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.isPodcastShow
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.data.settings.speedDialId

/**
 * What YouTube Music has for the Podcasts page.
 *
 * @property picks The shows for the dial after the pins.
 * @property forYou The shows for the row of large cards under the dial:
 * "Shows for you" when YouTube has it or had it before, else the first shelf
 * of shows there is. Null when there is none.
 * @property shelves The shelves of Home's podcasts page read so far, in its
 * order: episodes by category, and now and then a shelf of shows.
 * @property landing The Podcasts landing page's shelves, which go after
 * [shelves] once those have ended: the popular episodes, and shows by category.
 * @property continuation The token for more of [shelves], null once they have ended.
 * @property pages How many pages of [shelves] were read.
 * @property fresh The shows the account follows whose newest episode was not
 * started when the feed was read, the newest episode's show first.
 */
data class PodcastsFeed(
    val picks: List<ShelfItem>,
    val forYou: HomeShelf?,
    val shelves: List<HomeShelf>,
    val landing: List<HomeShelf>,
    val continuation: String?,
    val pages: Int = 1,
    val fresh: List<FreshShow> = emptyList(),
)

/** A followed show with a new episode: its tile, and the episode ([episodeId] is its video id). */
data class FreshShow(val show: ShelfItem, val episodeId: String)

/**
 * The Podcasts page: its dial, and what is under it.
 *
 * The dial is the shows pinned to it, then the shows YouTube Music
 * recommends. Those are the ones it picked for the listener ("Shows for
 * you", on Home under its Podcasts chip, which is not in every answer), then
 * the Podcasts landing page's: the shows to start with, the popular ones, and
 * the ones it lists by category.
 *
 * Under the dial are the picked shows again as a row of large cards, then
 * Home's podcast shelves (episodes by category), read a page at a time as
 * the list is scrolled, then the landing page's shelves.
 */
object PodcastsDial {

    /** How many pages of Home's podcast shelves are read at most; each has about three shelves. */
    const val MAX_PAGES = 12

    /** The shelves of shows among [shelves], each with only its shows. */
    fun showShelves(shelves: List<HomeShelf>): List<HomeShelf> = shelves.mapNotNull { shelf ->
        if (shelf.layout == ShelfLayout.VIDEO) return@mapNotNull null
        val shows = shelf.items.filter { it.isPodcastShow }
        if (shows.isEmpty()) null else shelf.copy(items = shows)
    }

    /**
     * The shows for the dial after the pins: [home]'s (picked for the
     * listener) ahead of [landing]'s (the same for everyone), each show once.
     */
    fun picks(home: List<HomeShelf>, landing: List<HomeShelf>): List<ShelfItem> =
        (showShelves(home) + showShelves(landing))
            .flatMap { it.items }
            .distinctBy { it.browseId }

    /**
     * The feed from the first page of each of the two pages it is read from.
     * [savedForYou] is the "Shows for you" shelf as Home last had it, which
     * stands in when this answer of [home] has no shelf of shows.
     */
    fun feed(
        home: List<HomeShelf>,
        continuation: String?,
        landing: List<HomeShelf>,
        savedForYou: HomeShelf? = null,
        fresh: List<FreshShow> = emptyList(),
    ): PodcastsFeed {
        val fromHome = showShelves(home).firstOrNull()
        val forYou = fromHome
            ?: savedForYou?.let { showShelves(listOf(it)).firstOrNull() }
            ?: showShelves(landing).firstOrNull()
        return PodcastsFeed(
            // The saved shelf's shows are still the listener's own: ahead of the landing page's.
            picks = picks(home + listOfNotNull(forYou.takeIf { fromHome == null }), landing),
            forYou = forYou,
            shelves = playable(home),
            landing = playable(landing),
            continuation = continuation,
            fresh = fresh,
        )
    }

    /**
     * The followed shows with a new episode that was not started, from
     * YouTube Music's "New episodes" list ([episodes], newest first): one for
     * each show, by its newest episode there. The show's tile is the one the
     * library has for it ([followed]), else one of [known] (the dial's own
     * picks), else one made from the episode.
     */
    fun fresh(episodes: List<Song>, followed: List<ShelfItem>, known: List<ShelfItem> = emptyList()): List<FreshShow> {
        val tiles = HashMap<String, ShelfItem>()
        (followed + known).forEach { item ->
            val id = item.browseId
            if (id != null && item.isPodcastShow) tiles.putIfAbsent(id, item)
        }
        return episodes
            .mapNotNull { song -> song.episode?.showId?.let { showId -> showId to song } }
            // Only a show's newest episode says whether the show has a new one.
            .distinctBy { (showId, _) -> showId }
            .filter { (_, song) -> (song.episode?.playedPercent ?: 0) == 0 }
            .map { (showId, song) ->
                FreshShow(
                    show = tiles[showId] ?: ShelfItem(song.artist, "", song.thumbnailUrl, null, showId),
                    episodeId = song.videoId,
                )
            }
    }

    /**
     * The shows of [fresh] that still have their new episode to start:
     * without the ones whose episode was started in this app since
     * ([playedInApp], percent by video id).
     */
    fun freshShows(fresh: List<FreshShow>, playedInApp: Map<String, Int>): List<ShelfItem> =
        fresh.filter { (playedInApp[it.episodeId] ?: 0) == 0 }.map { it.show }

    /** [feed] with one more page of Home's podcast shelves; the paging stops at [MAX_PAGES]. */
    fun more(feed: PodcastsFeed, shelves: List<HomeShelf>, continuation: String?): PodcastsFeed {
        val pages = feed.pages + 1
        return feed.copy(
            shelves = feed.shelves + playable(shelves),
            continuation = continuation.takeIf { pages < MAX_PAGES },
            pages = pages,
        )
    }

    /** The shelves the page can draw: those of episodes as they are, and those of shows with only their shows. */
    private fun playable(shelves: List<HomeShelf>): List<HomeShelf> = shelves.mapNotNull { shelf ->
        if (shelf.layout == ShelfLayout.VIDEO) shelf.takeIf { it.items.isNotEmpty() } else showShelves(listOf(shelf)).firstOrNull()
    }

    /**
     * The dial's tiles: [pins] first, then the followed shows with a new
     * episode ([fresh]), then [picks], without what was taken off the dial
     * ([hidden]), up to the size of a dial. A pinned show stays where its pin
     * is.
     */
    fun tiles(
        pins: List<ShelfItem>,
        picks: List<ShelfItem>,
        hidden: Set<String>,
        fresh: List<ShelfItem> = emptyList(),
    ): List<ShelfItem> =
        (pins + fresh + picks)
            .filter { it.isPodcastShow && it.speedDialId !in hidden }
            .distinctBy { it.speedDialId }
            .take(CustomizationSettings.SPEED_DIAL_SIZE)

    /**
     * The shelves under the dial and the row of large cards: Home's, then the
     * landing page's once Home's have ended. Each title once (the landing
     * page repeats "Popular shows", which Home's podcasts page ends with),
     * and without the shelf that the large cards are.
     */
    fun shelvesUnder(feed: PodcastsFeed): List<HomeShelf> {
        val cards = feed.forYou?.title?.let(::titleKey)
        val listed = if (feed.continuation == null) feed.shelves + feed.landing else feed.shelves
        return listed.filter { titleKey(it.title) != cards }.distinctBy { titleKey(it.title) }
    }

    /**
     * The page as it is drawn: the dial as the shelf that leads it, under the
     * title the speed dial goes by so that it is drawn as one, then [shelves].
     * No dial shelf when there is nothing to put on it.
     */
    fun page(tiles: List<ShelfItem>, shelves: List<HomeShelf>): List<HomeShelf> =
        if (tiles.isEmpty()) {
            shelves
        } else {
            listOf(HomeShelf(CustomizationSettings.SPEED_DIAL_TITLE, tiles)) + shelves
        }

    private fun titleKey(title: String): String = title.trim().lowercase()
}
