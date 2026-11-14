package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher
import kotlin.math.abs

/**
 * Whether a YouTube video is the artist's own upload of a song.
 *
 * A song's music video can stand in as its Canvas (see [VideoCanvases]), and
 * plenty of artists upload one without calling it a music video, or under a
 * title no title match can read: "ATLAS - Patterns ft. @rino @aquoferne" on
 * the channel "Pretty Patterns" is "ATLAS (feat. aquoferne & Davis West)" by
 * Pretty Patterns, RINO. Three things together say it is the song's video
 * all the same: it runs as long as the song does, give or take
 * [DURATION_WINDOW_SEC]; the channel it is on carries the name of one of the
 * song's artists; and its title has the song's title in it.
 *
 * The length is what keeps a still-cover loop, a live take or an extended
 * cut out, and the title what keeps out another song of the artist's that
 * happens to run as long. An upload that says it is only the audio, or one
 * on an auto-generated "- Topic" channel, is a still cover and is left out.
 */
object ArtistUploads {
    /** How far the video's length may be from the song's. */
    const val DURATION_WINDOW_SEC = 2

    /** The best of [candidates] that is [song]'s artist's own upload of it: the nearest in length. */
    fun best(candidates: List<Song>, song: Song): Song? {
        val wanted = TrackMatcher.secondsOf(song.durationText) ?: return null
        return candidates
            .filter { matches(song, it) }
            .minByOrNull { abs(wanted - (TrackMatcher.secondsOf(it.durationText) ?: Int.MAX_VALUE)) }
    }

    /** Whether [video] is [song]'s artist's own upload of it. */
    fun matches(song: Song, video: Song): Boolean {
        val wanted = TrackMatcher.secondsOf(song.durationText) ?: return false
        val got = TrackMatcher.secondsOf(video.durationText) ?: return false
        if (abs(wanted - got) > DURATION_WINDOW_SEC) return false
        if (!isBy(video.artist, song.artist)) return false
        if (audioOnly.containsMatchIn(video.title) && !MusicVideoTerms.inTitle(video.title)) return false
        return titleIsIn(song, video.title)
    }

    /**
     * Whether the channel [uploader] is one of the artists in [credit].
     *
     * Channel words that are not the name are set aside first — "Official",
     * "VEVO", "Music", "TV" — and spacing and punctuation with them, so
     * "PrettyPatternsVEVO", "Pretty Patterns Official" and "Pretty Patterns"
     * are one name. A name of six letters or more may be one letter off,
     * which is what an accent a channel leaves out comes to ("Beyoncé" /
     * "BeyonceVEVO").
     */
    fun isBy(uploader: String, credit: String): Boolean {
        if (topicChannel.containsMatchIn(uploader)) return false
        val channel = nameKey(uploader)
        if (channel.isEmpty()) return false
        val artists = TrackMatcher.artistNames(credit).map { nameKey(it.joinToString(" ")) } + nameKey(credit)
        return artists.any { it.isNotEmpty() && closeTo(channel, it) }
    }

    /** A name as it is compared: lower case, letters and digits only, channel words dropped. */
    internal fun nameKey(name: String): String {
        val words = TrackMatcher.normalize(name)
            .split(notNameChars)
            .filter { it.isNotEmpty() }
        val kept = words.filter { it !in CHANNEL_WORDS }.ifEmpty { words }
        var key = kept.joinToString("")
        // "TaylorSwiftVEVO", "ArtistOfficial": the channel word run into the name.
        CHANNEL_SUFFIXES.forEach { suffix ->
            if (key.length > suffix.length && key.endsWith(suffix)) key = key.removeSuffix(suffix)
        }
        return key
    }

    private fun closeTo(a: String, b: String): Boolean {
        if (a == b) return true
        if (minOf(a.length, b.length) < CLOSE_MIN_LENGTH || abs(a.length - b.length) > 1) return false
        // One letter added, dropped or changed.
        val (short, long) = if (a.length <= b.length) a to b else b to a
        val at = short.indices.firstOrNull { short[it] != long[it] } ?: return true
        return if (short.length == long.length) short.substring(at + 1) == long.substring(at + 1)
        else short.substring(at) == long.substring(at + 1)
    }

    private fun titleIsIn(song: Song, videoTitle: String): Boolean {
        val core = TrackMatcher.parseTitle(song.title, song.artist).core.replace(notNameChars, "")
        if (core.isEmpty()) return false
        return TrackMatcher.normalize(videoTitle).replace(notNameChars, "").contains(core)
    }

    private val notNameChars = Regex("""[^\p{L}\p{N}]+""")
    private val audioOnly = Regex("""\baudio\b""", RegexOption.IGNORE_CASE)
    private val topicChannel = Regex("""\s-\s+topic\s*$""", RegexOption.IGNORE_CASE)

    private val CHANNEL_WORDS = setOf("official", "vevo", "topic", "music", "channel", "tv", "videos", "video")
    private val CHANNEL_SUFFIXES = listOf("vevo", "official")
    private const val CLOSE_MIN_LENGTH = 6
}
