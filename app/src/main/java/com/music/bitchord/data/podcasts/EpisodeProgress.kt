package com.music.bitchord.data.podcasts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * How much of each podcast episode was played in this app, for the bar along
 * the foot of its row on the show's page.
 *
 * YouTube Music says how much of an episode the account has played, but only
 * for an account that is signed in with its history on, and only as of when
 * the page was read. This is the app's own count, which is there for a guest
 * and moves while an episode plays. A row shows the larger of the two.
 *
 * Only episodes are counted: the ones a show's page listed, or a podcast
 * shelf played from ([know]), and the ones counted before. One whole percent
 * is one write, so an episode is at most a hundred small writes, to a file of
 * its own. The file is not read at launch: a show's page or the first thing
 * played reads it.
 */
object EpisodeProgress {

    /** The most episodes kept; past it, the ones played longest ago go. */
    internal const val MAX_KEPT = 500

    /** How many go at once when there are too many, so that it is not one for each new episode. */
    internal const val DROPPED_AT_ONCE = 100

    /** From here on an episode is played to its end: the rest is its credits. */
    internal const val PLAYED_OUT = 97

    private var context: Context? = null
    private var prefs: SharedPreferences? = null
    private val lock = Any()

    /** The episodes known in this run of the app that have no count yet. */
    private val known = HashSet<String>()

    /** Each counted episode's packed percent and time; see [pack]. */
    private val stored = HashMap<String, Long>()

    private val _played = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Percent played, 1 to 100, by video id. An episode that was not started is not in it. */
    val played: StateFlow<Map<String, Int>> = _played.asStateFlow()

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    /** These are episodes: what is played of them from now on is counted. Reads nothing. */
    fun know(ids: Collection<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) { known.addAll(ids) }
    }

    /** Reads what was counted before, for a page that shows it. Off the main thread. */
    suspend fun load() = withContext(Dispatchers.IO) {
        synchronized(lock) { open() }
    }

    /**
     * The player is [positionMs] into [videoId], which is [durationMs] long.
     * Called every few seconds while anything plays, so it does nothing for
     * what is not an episode, and nothing until the whole percent changes.
     */
    fun onSample(videoId: String, positionMs: Long, durationMs: Long, nowMs: Long = System.currentTimeMillis()) {
        val percent = percentOf(positionMs, durationMs) ?: return
        synchronized(lock) {
            // Read at the first sample at the latest: the file holds the
            // episodes counted before, which this may be one of.
            open()
            if (videoId !in known && videoId !in stored) return
            val before = stored[videoId]?.let(::percentIn) ?: 0
            // A second listen does not take the bar back to its start.
            if (percent <= before) return
            val packed = pack(percent, nowMs)
            stored[videoId] = packed
            val editor = prefs?.edit()?.putLong(videoId, packed)
            if (stored.size > MAX_KEPT) {
                oldest(stored, DROPPED_AT_ONCE).forEach { id ->
                    stored.remove(id)
                    editor?.remove(id)
                }
            }
            editor?.apply()
            _played.value = stored.mapValues { percentIn(it.value) }
        }
    }

    /** Reads the file, once. Called with [lock] held. */
    private fun open() {
        if (prefs != null) return
        val opened = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        prefs = opened
        opened.all.forEach { (id, value) -> if (value is Long) stored[id] = value }
        _played.value = stored.mapValues { percentIn(it.value) }
    }

    /**
     * [positionMs] of [durationMs] as a whole percent, 1 to 100, or null when
     * there is nothing to count yet (no length, or less than one percent in).
     */
    internal fun percentOf(positionMs: Long, durationMs: Long): Int? {
        if (durationMs <= 0L || positionMs <= 0L) return null
        val percent = (positionMs * 100 / durationMs).toInt().coerceIn(0, 100)
        return when {
            percent >= PLAYED_OUT -> 100
            percent < 1 -> null
            else -> percent
        }
    }

    /** A percent and when it was counted (to the minute), as the one number that is kept. */
    internal fun pack(percent: Int, nowMs: Long): Long = (nowMs / 60_000L) * 128L + percent.coerceIn(0, 100)

    internal fun percentIn(packed: Long): Int = (packed % 128L).toInt()

    /** The [count] ids of [entries] that were counted longest ago. */
    internal fun oldest(entries: Map<String, Long>, count: Int): List<String> =
        entries.entries.sortedBy { it.value / 128L }.take(count).map { it.key }

    /** What the row shows: the larger of YouTube's percent and this app's. */
    fun shown(fromYouTube: Int, fromApp: Int?): Int = maxOf(fromYouTube, fromApp ?: 0).coerceIn(0, 100)

    private const val PREFS_NAME = "episode_progress"
}
