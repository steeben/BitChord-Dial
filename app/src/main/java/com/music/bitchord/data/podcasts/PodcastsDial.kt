package com.music.bitchord.data.podcasts

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.ShelfLayout
import com.music.bitchord.data.model.isPodcastShow
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.data.settings.speedDialId

/**
 * What YouTube Music has for the Podcasts page: the shows for its dial after
 * the pins ([picks]), and the shelves under the dial ([shelves]).
 */
data class PodcastsFeed(
    val picks: List<ShelfItem>,
    val shelves: List<HomeShelf>,
)

/**
 * The Podcasts page's dial: the shows pinned to it, then the shows YouTube
 * Music recommends. Those are the ones it picked for the listener ("Shows
 * for you", on Home under its Podcasts chip, which is not in every answer),
 * then the Podcasts landing page's: the shows to start with, the popular
 * ones, and the ones it lists by category.
 */
object PodcastsDial {

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
     * The feed from the two pages it is read from. The shelves under the dial
     * are the shelves of shows, [home]'s first, each title once: the landing
     * page repeats "Popular shows", which Home's podcasts page ends with.
     */
    fun feed(home: List<HomeShelf>, landing: List<HomeShelf>): PodcastsFeed = PodcastsFeed(
        picks = picks(home, landing),
        shelves = (showShelves(home) + showShelves(landing)).distinctBy { it.title.trim().lowercase() },
    )

    /**
     * The dial's tiles: [pins] first, then [picks], without what was taken
     * off the dial ([hidden]), up to the size of a dial.
     */
    fun tiles(pins: List<ShelfItem>, picks: List<ShelfItem>, hidden: Set<String>): List<ShelfItem> =
        (pins + picks)
            .filter { it.isPodcastShow && it.speedDialId !in hidden }
            .distinctBy { it.speedDialId }
            .take(CustomizationSettings.SPEED_DIAL_SIZE)

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
}
