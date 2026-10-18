package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.durationMillis
import java.text.Normalizer
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.abs

/**
 * Spotting the same recording reached two ways — a track in the YouTube Music
 * library that is also an MP3 on the device, say — so the All library can show
 * it once.
 *
 * Two songs are the same when their titles match once the noise is taken out
 * (case, punctuation, accents, "(Official Audio)" and the like, a featured
 * credit), their credits share an artist in any order ("Electric Youth,
 * College" and "College, Electric Youth"), and their lengths are within
 * [DURATION_TOLERANCE_MS] of each other. A song with no known length matches
 * on the names alone.
 *
 * A matched set shows once, with YouTube Music's details and playing the copy
 * on the device, unless the listener said otherwise for that set — see
 * [DuplicateChoices].
 */
object LibraryDuplicates {
    /**
     * Loose on purpose: a device's own idea of an MP3's length is often a few
     * seconds out, and a single and its album cut differ by a breath. What it
     * still keeps apart is a same-named song that is plainly another one — an
     * artist's two "Intro"s, a long video cut.
     */
    private const val DURATION_TOLERANCE_MS = 20_000L

    /** Bracketed asides that say nothing about which recording it is. */
    private val NOISE = Regex(
        "[(\\[][^)\\]]*(official|audio|video|lyric|visuali[sz]er|explicit|clean|remaster|hd|hq|4k|mv|m/v)[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE,
    )
    private val FEATURING = Regex("\\s[(\\[]?(feat\\.?|ft\\.?|featuring)\\s.*$", RegexOption.IGNORE_CASE)
    private val ARTIST_SPLIT = Regex("\\s*(,|&|;|/|\\sx\\s|\\sand\\s|\\sfeat\\.?\\s|\\sft\\.?\\s|\\sfeaturing\\s)\\s*", RegexOption.IGNORE_CASE)
    private val MARKS = Regex("\\p{M}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(NOT_WORD, "")

    fun titleKey(title: String): String {
        val stripped = title.replace(NOISE, " ").replace(FEATURING, "")
        return fold(stripped).ifEmpty { fold(title) }
    }

    /** The first-credited artist, folded. */
    fun artistKey(artist: String): String {
        val lead = artist.removeSuffix(" - Topic").split(ARTIST_SPLIT).firstOrNull().orEmpty()
        return fold(lead).ifEmpty { fold(artist) }
    }

    /**
     * Every artist [artist] credits, folded, plus the credit whole. Split
     * eagerly — "Simon & Garfunkel" becomes two names — which is harmless here,
     * where it only ever decides whether two credits share someone.
     */
    fun artistKeys(artist: String): Set<String> {
        val credit = artist.removeSuffix(" - Topic")
        val keys = HashSet<String>()
        credit.split(ARTIST_SPLIT).forEach { name ->
            val key = fold(name)
            if (key.isNotEmpty()) keys += key
        }
        val whole = fold(credit)
        if (whole.isNotEmpty()) keys += whole
        if (keys.isEmpty()) keys += credit.trim().lowercase(Locale.ROOT)
        return keys
    }

    /** Whether [a] and [b] are the same recording by the rules above. */
    fun sameSong(a: Song, b: Song): Boolean {
        if (titleKey(a.title) != titleKey(b.title)) return false
        val others = artistKeys(b.artist)
        if (artistKeys(a.artist).none { it in others }) return false
        return closeInLength(a, b)
    }

    private fun closeInLength(a: Song, b: Song): Boolean {
        val first = a.durationMillis()
        val second = b.durationMillis()
        return first <= 0L || second <= 0L || abs(first - second) <= DURATION_TOLERANCE_MS
    }

    /**
     * Every set of copies of one song in [songs], each in the order [songs]
     * lists them. Songs with no other copy are in none.
     */
    fun detect(songs: List<Song>): List<DuplicateGroup> {
        class Seen(val song: Song, val artists: Set<String>)
        val byTitle = LinkedHashMap<String, MutableList<MutableList<Seen>>>()
        songs.forEach { song ->
            val seen = Seen(song, artistKeys(song.artist))
            val sets = byTitle.getOrPut(titleKey(song.title)) { mutableListOf() }
            val home = sets.firstOrNull { set ->
                set.any { other -> other.artists.any { it in seen.artists } && closeInLength(other.song, song) }
            }
            if (home != null) home += seen else sets += mutableListOf(seen)
        }
        return byTitle.values.flatMap { sets ->
            sets.filter { it.size > 1 }.map { set -> DuplicateGroup(set.map { it.song }) }
        }
    }

    /**
     * This list with each set of copies standing as one song, where the first
     * copy of it was; see [DuplicateGroup.shown] for which details and which
     * file. A set the listener asked to keep apart stays as it is.
     */
    fun List<Song>.withDuplicatesMerged(
        choices: DuplicateChoices = DuplicateChoices(),
        /** [detect] of this list, when the caller has it already. */
        groups: List<DuplicateGroup> = detect(this),
        pickedArt: (Song) -> String? = { null },
    ): List<Song> {
        if (groups.isEmpty()) return this
        // By identity: two copies can be equal as data, and only one of them stands.
        val standIns = IdentityHashMap<Song, Song?>()
        groups.forEach { group ->
            if (group.key in choices.keptApart) return@forEach
            val shown = group.shown(choices, pickedArt)
            group.members.forEachIndexed { index, song -> standIns[song] = if (index == 0) shown else null }
        }
        return mapNotNull { song -> if (standIns.containsKey(song)) standIns[song] else song }
    }
}

/** A track with a YouTube identity of its own: a stream, or a download of one. */
internal fun Song.hasYouTubeId(): Boolean =
    videoId.length == 11 && videoId.none { it == ':' || it == '/' }

/**
 * The copies of one song the library holds, as [LibraryDuplicates.detect]
 * found them, in listing order.
 */
class DuplicateGroup(val members: List<Song>) {
    /** Names this set across launches, for the choices made about it. */
    val key: String = members.map { it.videoId }.sorted().joinToString("|")

    /** A copy that plays from the device: a file of the listener's own or a download. */
    val deviceCopy: Song? = members.firstOrNull { it.localUri != null }

    /** A copy that streams. */
    val streamCopy: Song? = members.firstOrNull { it.localUri == null }

    /** The copy whose details are YouTube Music's — its stream, else a download of it. */
    val youTubeCopy: Song? = members.firstOrNull { it.localUri == null && it.hasYouTubeId() }
        ?: members.firstOrNull { it.hasYouTubeId() }

    /** A file the listener put on the device themselves, with its own tags. */
    val fileCopy: Song? = members.firstOrNull { it.localUri != null && !it.hasYouTubeId() }

    /** Whether there is a choice of what plays: a copy on the device and one that streams. */
    val canChoosePlayback: Boolean get() = deviceCopy != null && streamCopy != null

    /** Whether there is a choice of whose details show: YouTube Music's or the file's tags. */
    val canChooseDetails: Boolean get() = youTubeCopy != null && fileCopy != null

    fun playsStream(choices: DuplicateChoices): Boolean = key in choices.playStream && streamCopy != null

    fun showsFileDetails(choices: DuplicateChoices): Boolean = key in choices.fileDetails && fileCopy != null

    /**
     * The one song this set stands as: YouTube Music's title, artist and
     * artwork, playing the copy on the device — or the other way round on
     * either count, where [choices] say so. A cover the listener picked for
     * the file's album ([pickedArt]) still wins over YouTube Music's art.
     */
    fun shown(choices: DuplicateChoices, pickedArt: (Song) -> String? = { null }): Song {
        val details = (if (showsFileDetails(choices)) fileCopy else youTubeCopy) ?: members.first()
        val player = (if (playsStream(choices)) streamCopy else deviceCopy) ?: members.first()
        return when {
            details === player -> details
            // A stream's details over a file keep the stream's own id, as a
            // download does, so radio and the rest of what a YouTube Music
            // track can do still work while the file is what plays.
            player.localUri != null && details.localUri == null -> details.copy(
                thumbnailUrl = pickedArt(player) ?: details.thumbnailUrl,
                localUri = player.localUri,
                localPath = player.localPath,
                localDateAddedSeconds = player.localDateAddedSeconds,
                localDateModifiedSeconds = player.localDateModifiedSeconds,
                downloadFormat = player.downloadFormat,
                durationText = player.durationText ?: details.durationText,
            )
            else -> player.copy(
                title = details.title,
                artist = details.artist,
                artists = details.artists.ifEmpty { player.artists },
                artistId = details.artistId ?: player.artistId,
                thumbnailUrl = details.thumbnailUrl ?: player.thumbnailUrl,
                albumId = details.albumId ?: player.albumId,
                albumName = details.albumName ?: player.albumName,
                isExplicit = details.isExplicit ?: player.isExplicit,
            )
        }
    }
}

/**
 * What the listener decided about particular sets of copies, each by its
 * [DuplicateGroup.key]. Left empty, every set shows once, with YouTube Music's
 * details, playing from the device.
 */
data class DuplicateChoices(
    /** Sets shown as separate songs. */
    val keptApart: Set<String> = emptySet(),
    /** Sets that stream rather than play the copy on the device. */
    val playStream: Set<String> = emptySet(),
    /** Sets that show the file's own tags rather than YouTube Music's details. */
    val fileDetails: Set<String> = emptySet(),
)
