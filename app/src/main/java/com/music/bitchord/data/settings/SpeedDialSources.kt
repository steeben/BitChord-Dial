package com.music.bitchord.data.settings

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import kotlin.random.Random

/**
 * Where the speed dial's tiles after the pins come from: what was played
 * lately, a shuffle of the feed's picks for the listener, a blend of both, or
 * YouTube Music's own picks exactly as its Home gives them. New entries go at
 * the end: the pickers show them in this order.
 */
enum class SpeedDialSource { HISTORY, RECOMMENDS, MIX, YOUTUBE }

/** The dial's tiles past the pins, and which of them are new releases (for their tag). */
data class DialPicks(
    val items: List<ShelfItem>,
    val newReleaseIds: Set<String>,
)

/**
 * Picking the speed dial's tiles for each [SpeedDialSource]. Pure, so it can
 * be tested away from the app; pins, hidden tiles and the size limit are
 * applied after, by [CustomizationSettings.assembleDial].
 */
object SpeedDialPicker {
    /** The fewest and most new releases a Recommends or Mix dial carries. */
    const val MIN_NEW_RELEASES = 1
    const val MAX_NEW_RELEASES = 3

    /**
     * The Home shelves Recommends draws from, as YouTube titles them in the
     * languages the app already recognises them in. Recents is the app's own.
     */
    private val RECOMMEND_TITLES = setOf(
        "quick picks", "lựa chọn nhanh", "chọn nhanh đài phát", "クイック ミックス",
        "forgotten favorites", "forgotten favourites", "giai điệu quen thuộc", "よく聴いたお気に入りの曲",
        "listen again", "nghe lại", "もう一度聴く",
        "recents",
    )
    private val NEW_RELEASE_TITLES = setOf(
        "new releases", "bản phát hành mới", "最新リリース",
        "albums & singles", "new albums & singles",
    )

    fun isRecommendShelf(title: String): Boolean = title.trim().lowercase() in RECOMMEND_TITLES
    fun isNewReleaseShelf(title: String): Boolean = title.trim().lowercase() in NEW_RELEASE_TITLES

    /** The feed's new-release items, ahead of [extra] (FEmusic_new_releases's, fetched for the dial). */
    fun newReleasesOf(shelves: List<HomeShelf>, extra: List<ShelfItem>): List<ShelfItem> =
        (shelves.filter { isNewReleaseShelf(it.title) }.flatMap { it.items } + extra)
            .filter { it.speedDialId != null }
            .distinctBy { it.speedDialId }

    /**
     * The tiles for [source].
     *
     * [history] is the dial as History builds it (recently played first);
     * [recommends] the items of the shelves [isRecommendShelf] picks;
     * [newReleases] the pool the 1–3 tagged tiles are drawn from, placed
     * somewhere on the first page ([firstPage] tiles); [fill] tops the dial up
     * when the rest runs short (a guest's feed has few of those shelves).
     */
    fun pick(
        source: SpeedDialSource,
        history: List<ShelfItem>,
        recommends: List<ShelfItem>,
        newReleases: List<ShelfItem>,
        fill: List<ShelfItem>,
        firstPage: Int,
        size: Int,
        random: Random,
    ): DialPicks {
        if (source == SpeedDialSource.HISTORY) return DialPicks(history, emptySet())
        if (source == SpeedDialSource.YOUTUBE) return DialPicks(official(recommends, fill, size), emptySet())
        val releaseIds = newReleases.mapNotNullTo(HashSet()) { it.speedDialId }
        fun List<ShelfItem>.usable() = filter { it.speedDialId != null && it.speedDialId !in releaseIds }
            .distinctBy { it.speedDialId }
        val recs = recommends.usable().shuffled(random)
        val base = when (source) {
            SpeedDialSource.RECOMMENDS -> recs
            else -> {
                // Mix: the latest plays and the picks, dealt in at random.
                val played = history.usable().take(size).shuffled(random).toMutableList()
                val picked = recs.toMutableList()
                buildList {
                    while (played.isNotEmpty() || picked.isNotEmpty()) {
                        val fromPlayed = picked.isEmpty() || (played.isNotEmpty() && random.nextBoolean())
                        add(if (fromPlayed) played.removeAt(0) else picked.removeAt(0))
                    }
                }.distinctBy { it.speedDialId }
            }
        }
        val dial = base.toMutableList()
        val seen = dial.mapNotNullTo(HashSet()) { it.speedDialId }
        fill.usable().forEach { if (dial.size < size && seen.add(it.speedDialId!!)) dial += it }
        // The new releases: one to three, each somewhere on the first page.
        val count = random.nextInt(MIN_NEW_RELEASES, MAX_NEW_RELEASES + 1)
        val releases = newReleases.distinctBy { it.speedDialId }.shuffled(random).take(count)
        // Their final places, distinct and filled lowest first, so a later one
        // can't push an earlier one off the first page.
        val last = minOf(maxOf(firstPage, 1), dial.size + releases.size) - 1
        val spots = (0..last).shuffled(random).take(releases.size).sorted()
        val placed = releases.zip(spots)
        placed.forEach { (item, at) -> dial.add(at, item) }
        return DialPicks(dial, placed.mapNotNullTo(HashSet()) { it.first.speedDialId })
    }

    /**
     * YouTube Music's picks as its own app shows them: [recommends] in the
     * feed's order (Quick picks and the shelves beside it, as YouTube placed
     * them), unshuffled and with nothing added in, topped up from [fill] only
     * when the feed has too few.
     */
    private fun official(recommends: List<ShelfItem>, fill: List<ShelfItem>, size: Int): List<ShelfItem> {
        val dial = recommends.filter { it.speedDialId != null }.distinctBy { it.speedDialId }.toMutableList()
        val seen = dial.mapNotNullTo(HashSet()) { it.speedDialId }
        fill.forEach { item ->
            val id = item.speedDialId ?: return@forEach
            if (dial.size < size && seen.add(id)) dial += item
        }
        return dial
    }
}
