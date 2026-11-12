package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import java.text.Normalizer
import java.util.Locale

/**
 * Reading a track's artist credit as the artists it names.
 *
 * A file tagged "Porter Robinson, Imaginary Cities" is a Porter Robinson
 * track and an Imaginary Cities track, not the work of a third artist with
 * that whole string for a name — which is what grouping the library by the
 * raw tag made of it, splitting an album across as many "artists" as it had
 * collaborations.
 *
 * Commas, semicolons, a spaced slash and a featuring credit always separate
 * artists, apart from the few real names written with a comma
 * ([KEEP_WHOLE]: "Tyler, The Creator", "Earth, Wind & Fire"). "&", "and",
 * " x ", "+" and "vs." are part of too many names ("Simon & Garfunkel",
 * "Florence + the Machine") to be read as separators on sight, so they only
 * split a credit when every side of them is an artist the library already
 * knows on their own — see [knownArtists].
 *
 * Nothing here touches [LibraryDuplicates], whose lead-artist key is what the
 * All library matches copies of a song by.
 */
object LibraryArtists {
    // Declared ahead of [KEEP_WHOLE], whose initialiser folds through [key].
    private val MARKS = Regex("\\p{M}+")
    private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

    /** Real names that a comma would otherwise cut in two. Matched folded. */
    private val KEEP_WHOLE = listOf(
        "Tyler, The Creator",
        "Earth, Wind & Fire",
        "Crosby, Stills, Nash & Young",
        "Crosby, Stills & Nash",
        "Emerson, Lake & Palmer",
        "Peter, Paul and Mary",
        "Blood, Sweat & Tears",
        "Black Country, New Road",
        "Now, Now",
        "Me, Myself and I",
        "Hello, Dolly",
        "Lily, Rosemary and the Jack of Hearts",
    ).mapTo(HashSet()) { key(it) }

    /** "(feat. B)" and "[ft. B]": a credit in brackets, read as a separated one. */
    private val BRACKETED_FEATURE = Regex("\\s*[(\\[]\\s*(?:feat\\.?|ft\\.?|featuring)\\s+([^)\\]]*)[)\\]]", RegexOption.IGNORE_CASE)
    private val FEATURE = Regex("\\s+(?:feat\\.?|ft\\.?|featuring)\\s+", RegexOption.IGNORE_CASE)

    /** Always a separator. A comma between digits is a number ("10,000 Maniacs"). */
    private val HARD = Regex("\\s*(?:(?<!\\d),|,(?!\\d)|;|，|；|、|\\s/\\s|／)\\s*")

    /** A separator only when every side is an artist of its own. */
    private val SOFT = Regex("\\s+(?:&|＆|\\+|x|×|and|vs\\.?)\\s+", RegexOption.IGNORE_CASE)

    /** One spelling of a name for comparing: no case, accents or punctuation. */
    fun key(name: String): String =
        Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace(NOT_WORD, "")
            .ifEmpty { name.trim().lowercase(Locale.ROOT) }

    /** The names [credit] states before any "&"-style split. */
    private fun hardSplit(credit: String): List<String> {
        val text = credit.trim()
            .removeSuffix(" - Topic")
            .replace(BRACKETED_FEATURE) { ", " + it.groupValues[1] }
            .replace(FEATURE, ", ")
        val tokens = text.split(HARD).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.size < 2) return tokens
        // Put a comma-written name back together, longest match first.
        val out = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            var taken = 1
            for (end in tokens.size downTo i + 2) {
                if (key(tokens.subList(i, end).joinToString("")) in KEEP_WHOLE) {
                    taken = end - i
                    break
                }
            }
            out += tokens.subList(i, i + taken).joinToString(", ")
            i += taken
        }
        return out
    }

    /**
     * Every name the library credits on its own somewhere, by [key] — what
     * decides whether "A & B" is two artists ([split]).
     */
    fun knownArtists(credits: Iterable<String>): Set<String> =
        credits.flatMapTo(HashSet()) { credit -> hardSplit(credit).map(::key) }

    /** The artists [credit] names, in order and once each. */
    fun split(credit: String, known: Set<String> = emptySet()): List<String> {
        val names = hardSplit(credit).flatMap { name ->
            val parts = name.split(SOFT).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size > 1 && parts.all { key(it) in known }) parts else listOf(name)
        }
        return names.distinctBy(::key).ifEmpty { listOf(credit.trim()) }
    }

    /**
     * [songs] by each artist they credit, in the order the songs come: a
     * collaboration is on every one of its artists' lists. The name each list
     * goes by is the spelling it was first met in.
     */
    fun group(songs: List<Song>): List<Pair<String, List<Song>>> {
        val known = knownArtists(songs.map { it.artist })
        val names = LinkedHashMap<String, String>()
        val lists = LinkedHashMap<String, MutableList<Song>>()
        songs.forEach { song ->
            split(song.artist, known).forEach { name ->
                val k = key(name)
                names.getOrPut(k) { name }
                lists.getOrPut(k) { mutableListOf() } += song
            }
        }
        return lists.map { (k, tracks) -> names.getValue(k) to tracks }
    }

    /**
     * The artists among [songs] whose name has [query] in it, for the Search
     * tab's Library source: each with every track that credits them, as
     * [group] lists them. Names that start with the query come first, then
     * the rest, both in name order. Empty for a blank query.
     */
    fun search(songs: List<Song>, query: String): List<Pair<String, List<Song>>> {
        val term = query.trim()
        if (term.isEmpty()) return emptyList()
        return group(songs)
            .filter { (name, _) -> name.contains(term, ignoreCase = true) }
            .sortedWith(
                compareBy<Pair<String, List<Song>>> { (name, _) -> !name.startsWith(term, ignoreCase = true) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first },
            )
    }

    /**
     * Who an album is by: the artist credited on most of its [tracks], so a
     * guest on one song doesn't take the release over. Ties go to whoever is
     * named first on the first track.
     */
    fun albumArtist(tracks: List<Song>, known: Set<String> = knownArtists(tracks.map { it.artist })): String {
        val counts = LinkedHashMap<String, Int>()
        val names = HashMap<String, String>()
        tracks.forEach { song ->
            split(song.artist, known).forEach { name ->
                val k = key(name)
                names.getOrPut(k) { name }
                counts[k] = (counts[k] ?: 0) + 1
            }
        }
        val best = counts.maxByOrNull { it.value }?.key ?: return tracks.firstOrNull()?.artist.orEmpty()
        return names.getValue(best)
    }
}
