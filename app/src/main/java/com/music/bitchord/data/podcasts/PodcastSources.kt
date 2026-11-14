package com.music.bitchord.data.podcasts

import com.music.bitchord.data.model.ShelfItem

/**
 * Whose recommended shows fill the Podcasts page's dial after the pins:
 * YouTube Music's (the default), Spotify's, or the two in turn.
 */
enum class PodcastSource {
    YOUTUBE, SPOTIFY, BOTH;

    /** This choice as it applies now: without a Spotify account it is YouTube Music. */
    fun effective(spotifyConnected: Boolean): PodcastSource = if (spotifyConnected) this else YOUTUBE

    /** Whether Spotify's shows are needed for this choice. */
    val usesSpotify: Boolean get() = this != YOUTUBE

    companion object {
        /** The sources a picker offers: Spotify's only while a Spotify account is signed in. */
        fun choices(spotifyConnected: Boolean): List<PodcastSource> =
            if (spotifyConnected) entries else listOf(YOUTUBE)
    }
}

/** Picking the dial's shows for each [PodcastSource], and finding Spotify's shows on YouTube Music. Pure. */
object PodcastSources {

    /**
     * The shows for the dial after the pins. [youtube] is YouTube Music's own
     * picks; [spotify] is Spotify's, already found on YouTube Music.
     * Spotify's alone are seldom enough for a dial, so with [PodcastSource.SPOTIFY]
     * YouTube Music's follow them; with [PodcastSource.BOTH] the two take
     * turns, Spotify's first. Each show once.
     */
    fun picks(youtube: List<ShelfItem>, spotify: List<ShelfItem>, source: PodcastSource): List<ShelfItem> =
        when (source) {
            PodcastSource.YOUTUBE -> youtube
            PodcastSource.SPOTIFY -> spotify + youtube
            PodcastSource.BOTH -> {
                val turns = ArrayList<ShelfItem>(youtube.size + spotify.size)
                for (i in 0 until maxOf(youtube.size, spotify.size)) {
                    spotify.getOrNull(i)?.let(turns::add)
                    youtube.getOrNull(i)?.let(turns::add)
                }
                turns
            }
        }.distinctBy { it.browseId }

    /**
     * The show among [results] (a YouTube Music search, best first) that is
     * the show called [name]: the first with the same name, or failing that
     * the first whose name begins with it or that it begins with (a show is
     * often "Name" in one place and "Name with Its Host" in the other). Null
     * when none is: a wrong show on the dial is worse than one show fewer.
     */
    fun match(name: String, results: List<ShelfItem>): ShelfItem? {
        val wanted = key(name)
        if (wanted.isEmpty()) return null
        val keyed = results.map { it to key(it.title) }
        return keyed.firstOrNull { (_, title) -> title == wanted }?.first
            ?: keyed.firstOrNull { (_, title) ->
                val shorter = minOf(title.length, wanted.length)
                shorter >= MIN_PREFIX && (title.startsWith(wanted) || wanted.startsWith(title))
            }?.first
    }

    /** A show's name as it is compared: its letters and digits, in lower case, without a leading "the". */
    internal fun key(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }.removePrefix("the")

    /** The fewest characters a name may have for "begins with" to count: "news" begins too many names. */
    private const val MIN_PREFIX = 8
}
