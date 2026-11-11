package com.music.bitchord.data.mixes

import kotlin.math.ln
import kotlin.random.Random

/**
 * The genres a listener listens to, for the Mixes Dial to favour: a mix that
 * names a genre they play comes up more often, and one that names only genres
 * they don't ("'00s Country" for someone who never plays country) is passed
 * over. A mix that names no genre ("My Mix 1", "'80s Hits", "Chill Mix") is
 * neither.
 *
 * Genres are counted in families, so "Trap" listening speaks for a "Hip-Hop
 * Mix" and "Classic Rock" is rock: see [FAMILIES].
 *
 * [shares] is each family's share of the listening, 0 to 1. Empty means the
 * taste is not known, and then no mix is favoured or passed over.
 */
class MixTaste(private val shares: Map<String, Double>) {

    val known: Boolean get() = shares.isNotEmpty()

    /** Whether [family] is one the listener plays. */
    fun listens(family: String): Boolean = family in shares

    /**
     * Whether a mix called [title] is in genres the listener doesn't play:
     * it names at least one, and none of those it names is theirs. Never so
     * while the taste is not [known].
     */
    fun misses(title: String): Boolean {
        if (!known) return false
        val named = genresIn(title)
        return named.isNotEmpty() && named.none(::listens)
    }

    /**
     * How much more or less often than a plain mix (1.0) one called [title]
     * should come up: more for a genre the listener plays, the more they play
     * it; much less for one they don't.
     */
    fun weight(title: String): Double {
        if (!known) return 1.0
        val named = genresIn(title)
        if (named.isEmpty()) return 1.0
        val share = named.maxOf { shares[it] ?: -1.0 }
        return if (share < 0) MISSED_WEIGHT else LISTENED_WEIGHT + LISTENED_SHARE_WEIGHT * share
    }

    /**
     * [items] in a random order that leans by [weight]: every order is
     * possible, and a heavier item is likelier to be near the front.
     */
    fun <T> shuffled(items: List<T>, random: Random, title: (T) -> String): List<T> {
        if (!known) return items.shuffled(random)
        // An exponential clock per item, running faster the heavier it is; the order they ring in.
        return items.map { it to -ln(1.0 - random.nextDouble()) / weight(title(it)) }
            .sortedBy { it.second }
            .map { it.first }
    }

    companion object {

        /** No taste known: nothing favoured, nothing passed over. */
        val UNKNOWN = MixTaste(emptyMap())

        /** A family counts as listened to from this share of the listening up. */
        const val LISTENED_SHARE = 0.02

        /** Listening below this much, in all, says too little to pass mixes over on. */
        const val ENOUGH_LISTENING_MS = 60 * 60 * 1000L

        /** Made-for-you mixes naming this many families between them are a taste on their own. */
        const val ENOUGH_MIX_FAMILIES = 2

        private const val MISSED_WEIGHT = 0.25
        private const val LISTENED_WEIGHT = 1.5
        private const val LISTENED_SHARE_WEIGHT = 3.0

        /**
         * The taste shown by [listened] (time listened per genre, by the
         * genre's name, as the listening stats chart them) and by [madeForYou]
         * (the names of the mixes the music services made for this listener: a
         * "Hip-Hop Mix" among them says hip-hop is played).
         *
         * [UNKNOWN] when there is too little of either to go on: under
         * [ENOUGH_LISTENING_MS] of charted listening, and fewer than
         * [ENOUGH_MIX_FAMILIES] families named by the mixes.
         */
        fun of(listened: Map<String, Long>, madeForYou: List<String> = emptyList()): MixTaste {
            val ms = HashMap<String, Long>()
            for ((genre, time) in listened) {
                if (time <= 0) continue
                for (family in genresIn(genre)) ms[family] = (ms[family] ?: 0L) + time
            }
            // Shares are of all the charted time, genres outside every family included.
            val total = listened.values.filter { it > 0 }.sum()
            val shares = HashMap<String, Double>()
            if (total >= ENOUGH_LISTENING_MS) {
                for ((family, time) in ms) {
                    val share = (time.toDouble() / total).coerceAtMost(1.0)
                    if (share >= LISTENED_SHARE) shares[family] = share
                }
            }
            val fromMixes = madeForYou.flatMapTo(HashSet(), ::genresIn)
            if (shares.isEmpty() && fromMixes.size < ENOUGH_MIX_FAMILIES) return UNKNOWN
            for (family in fromMixes) shares[family] = maxOf(shares[family] ?: 0.0, LISTENED_SHARE)
            return MixTaste(shares)
        }

        /**
         * The genre families [text] names: a mix's title ("'00s Country",
         * "Hip-Hop/Rap Mix") or a genre's own name ("Indie Rock"). Whole words
         * only, the longest name first, so "K-Pop" is not pop and a "House
         * Party" is not house.
         */
        fun genresIn(text: String): Set<String> {
            val words = text.lowercase().replace("&", " and ").split(NOT_A_WORD).filter { it.isNotEmpty() }
            val found = LinkedHashSet<String>()
            var i = 0
            while (i < words.size) {
                var matched = 0
                for (length in minOf(LONGEST_NAME, words.size - i) downTo 1) {
                    val families = NAMES[words.subList(i, i + length).joinToString(" ")] ?: continue
                    found += families
                    matched = length
                    break
                }
                i += maxOf(matched, 1)
            }
            return found
        }

        private val NOT_A_WORD = Regex("[^\\p{L}\\p{N}]+")

        const val COUNTRY = "country"
        const val HIP_HOP = "hip hop"
        const val ROCK = "rock"
        const val INDIE = "indie"
        const val METAL = "metal"
        const val POP = "pop"
        const val RNB = "r&b"
        const val FUNK = "funk"
        const val ELECTRONIC = "electronic"
        const val JAZZ = "jazz"
        const val BLUES = "blues"
        const val CLASSICAL = "classical"
        const val FOLK = "folk"
        const val LATIN = "latin"
        const val REGGAE = "reggae"
        const val K_POP = "k-pop"
        const val J_POP = "j-pop"
        const val DESI = "desi"
        const val AFRO = "afro"
        const val CHRISTIAN = "christian"

        /**
         * Each family and the names that mean it, in a title or on the genre
         * chart. A name may sit in two families ("indie rock" is both indie
         * and rock; "dance" is pop as much as electronic), and then listening
         * to either is enough. Moods and settings (chill, lo-fi, ambient,
         * workout, soundtrack) are left out: they are not a taste in genre.
         */
        private val FAMILIES: Map<String, List<String>> = mapOf(
            COUNTRY to listOf("country", "americana", "bluegrass", "honky tonk", "outlaw country", "alt country"),
            HIP_HOP to listOf(
                "hip hop", "hiphop", "rap", "trap", "drill", "grime", "boom bap", "phonk", "cloud rap",
                "conscious hip hop", "west coast rap", "east coast rap", "gangsta rap",
            ),
            ROCK to listOf(
                "rock", "classic rock", "hard rock", "soft rock", "pop rock", "alternative rock", "alt rock",
                "indie rock", "punk", "punk rock", "pop punk", "post punk", "grunge", "emo", "new wave", "britpop",
                "progressive rock", "prog rock", "garage rock", "psychedelic", "shoegaze", "post rock", "math rock",
                "surf rock", "rockabilly", "rock and roll", "rock n roll", "jam band", "j rock",
            ),
            INDIE to listOf(
                "indie", "indie pop", "indie rock", "indie folk", "alternative", "alt", "alternative rock",
                "alt rock", "dream pop", "art pop", "shoegaze", "bedroom pop",
            ),
            METAL to listOf(
                "metal", "heavy metal", "nu metal", "metalcore", "hardcore", "doom metal", "black metal",
                "death metal", "thrash metal", "industrial",
            ),
            POP to listOf(
                "pop", "synthpop", "synth pop", "electropop", "hyperpop", "indie pop", "pop rock", "dance pop",
                "dance", "art pop", "dream pop", "bedroom pop", "teen pop", "boy bands", "boy band",
            ),
            RNB to listOf(
                "r and b", "rnb", "randb", "r n b", "rhythm and blues", "soul", "neo soul", "motown", "quiet storm",
                "slow jams",
            ),
            FUNK to listOf("funk", "disco", "boogie", "motown"),
            ELECTRONIC to listOf(
                "electronic", "electronica", "electro", "edm", "house", "deep house", "tech house", "techno",
                "trance", "dubstep", "drum and bass", "drum n bass", "dnb", "jungle", "garage", "uk garage",
                "dance", "synthwave", "downtempo", "trip hop", "hardstyle", "rave",
            ),
            JAZZ to listOf("jazz", "swing", "big band", "bossa nova", "bebop", "smooth jazz"),
            BLUES to listOf("blues"),
            CLASSICAL to listOf("classical", "opera", "orchestral", "baroque", "symphony"),
            FOLK to listOf("folk", "indie folk", "singer songwriter", "singer songwriters", "americana", "bluegrass"),
            LATIN to listOf(
                "latin", "latin pop", "latino", "latina", "reggaeton", "regueton", "salsa", "bachata", "cumbia",
                "regional mexican", "corridos", "banda", "mariachi", "urbano", "bossa nova",
            ),
            REGGAE to listOf("reggae", "dancehall", "ska", "dub"),
            K_POP to listOf("k pop", "kpop", "korean"),
            J_POP to listOf("j pop", "jpop", "j rock", "jrock", "city pop", "anime"),
            DESI to listOf(
                "bollywood", "punjabi", "desi", "bhangra", "hindi", "sufi", "ghazal", "tamil", "telugu", "filmi",
                "indian",
            ),
            AFRO to listOf("afrobeats", "afrobeat", "afro beats", "afropop", "afro", "amapiano"),
            CHRISTIAN to listOf("christian", "gospel", "worship"),
        )

        /** Names that hold a genre's word without meaning the genre. */
        private val NOT_A_GENRE = listOf("house party", "pop culture", "country roads", "soul food")

        /** Each name's families. */
        private val NAMES: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
            for ((family, names) in FAMILIES) for (name in names) getOrPut(name) { LinkedHashSet() } += family
            for (name in NOT_A_GENRE) put(name, LinkedHashSet())
        }

        private val LONGEST_NAME = NAMES.keys.maxOf { it.count { c -> c == ' ' } + 1 }
    }
}
