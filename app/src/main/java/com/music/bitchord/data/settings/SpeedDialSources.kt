package com.music.bitchord.data.settings

import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem

/**
 * Where the speed dial's tiles after the pins come from: what was played
 * lately, or YouTube Music's own speed dial (the default). The pickers show
 * them in this order.
 */
enum class SpeedDialSource { HISTORY, YOUTUBE }

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
    /**
     * Home's shelves as YouTube titles them, in the languages the app already
     * recognises them in.
     */
    private val LISTEN_AGAIN_TITLES = setOf("listen again", "nghe lại", "もう一度聴く")
    private val FORGOTTEN_TITLES = setOf(
        "forgotten favorites", "forgotten favourites", "giai điệu quen thuộc", "よく聴いたお気に入りの曲",
    )
    private val QUICK_PICKS_TITLES = setOf("quick picks", "lựa chọn nhanh", "chọn nhanh đài phát", "クイック ミックス")

    fun isListenAgainShelf(title: String): Boolean = title.trim().lowercase() in LISTEN_AGAIN_TITLES
    fun isForgottenShelf(title: String): Boolean = title.trim().lowercase() in FORGOTTEN_TITLES
    fun isQuickPicksShelf(title: String): Boolean = title.trim().lowercase() in QUICK_PICKS_TITLES

    /**
     * The tiles for [source].
     *
     * [history] is the dial as History builds it (recently played first).
     *
     * For YouTube, [official] is YouTube Music's own Speed dial shelf, taken
     * whole and in its order. When it can't fill [size] tiles, the rest is
     * filled the way YouTube builds that shelf, from what the listener plays
     * most: [listenAgain] (YouTube's own ranking of the listener's plays),
     * then [recents] (what was played lately, as albums and playlists), then
     * [forgotten] (old favourites), then [quickPicks] (songs picked for the
     * listener), and [history] last.
     */
    fun pick(
        source: SpeedDialSource,
        history: List<ShelfItem>,
        official: List<ShelfItem> = emptyList(),
        listenAgain: List<ShelfItem> = emptyList(),
        recents: List<ShelfItem> = emptyList(),
        forgotten: List<ShelfItem> = emptyList(),
        quickPicks: List<ShelfItem> = emptyList(),
        size: Int,
    ): DialPicks {
        if (source == SpeedDialSource.HISTORY) return DialPicks(history, emptySet())
        val dial = ArrayList<ShelfItem>()
        val seen = HashSet<String>()
        // YouTube's own dial whole; the fill stops at the dial's size.
        official.forEach { item -> item.speedDialId?.let { if (seen.add(it)) dial += item } }
        listOf(listenAgain, recents, forgotten, quickPicks, history).forEach { pool ->
            pool.forEach { item ->
                val id = item.speedDialId ?: return@forEach
                if (dial.size < size && seen.add(id)) dial += item
            }
        }
        return DialPicks(dial, emptySet())
    }
}
