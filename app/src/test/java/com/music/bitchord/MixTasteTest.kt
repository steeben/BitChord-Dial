package com.music.bitchord

import com.music.bitchord.data.mixes.MixTaste
import com.music.bitchord.data.mixes.MixesDial
import com.music.bitchord.data.model.ShelfItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MixTasteTest {

    private val hour = 60 * 60 * 1000L

    private fun playlist(id: String, title: String) =
        ShelfItem(title = title, subtitle = "", thumbnailUrl = null, videoId = null, browseId = id)

    @Test
    fun aTitleNamesItsGenres() {
        assertEquals(setOf(MixTaste.COUNTRY), MixTaste.genresIn("'00s Country"))
        assertEquals(setOf(MixTaste.HIP_HOP), MixTaste.genresIn("Hip-Hop/Rap Mix"))
        assertEquals(setOf(MixTaste.RNB), MixTaste.genresIn("'90s R&B"))
        assertEquals(setOf(MixTaste.ROCK), MixTaste.genresIn("Classic Rock Mix"))
        assertEquals(setOf(MixTaste.ROCK, MixTaste.INDIE), MixTaste.genresIn("Indie Rock Road Trip"))
        assertEquals(setOf(MixTaste.COUNTRY, MixTaste.ROCK), MixTaste.genresIn("Country & Rock"))
    }

    @Test
    fun aGenresWordInsideAnotherNameIsNotThatGenre() {
        assertEquals(setOf(MixTaste.K_POP), MixTaste.genresIn("K-Pop Hits"))
        assertEquals(emptySet<String>(), MixTaste.genresIn("House Party"))
        assertEquals(emptySet<String>(), MixTaste.genresIn("Rocking Out"))
        assertEquals(emptySet<String>(), MixTaste.genresIn("Soulful Sunday"))
    }

    @Test
    fun mixesWithoutAGenreNameNone() {
        for (title in listOf("My Mix 1", "'80s Hits", "Chill Mix", "Discover Weekly", "Workout Supermix", "Lo-Fi Beats")) {
            assertEquals(title, emptySet<String>(), MixTaste.genresIn(title))
        }
    }

    @Test
    fun theChartsGenresAreReadInFamilies() {
        val taste = MixTaste.of(mapOf("Trap" to 5 * hour, "Indie Rock" to 3 * hour, "Drum & Bass" to hour, "Soundtrack" to hour))
        assertTrue(taste.known)
        assertTrue(taste.listens(MixTaste.HIP_HOP))
        assertTrue(taste.listens(MixTaste.ROCK))
        assertTrue(taste.listens(MixTaste.INDIE))
        assertTrue(taste.listens(MixTaste.ELECTRONIC))
        assertFalse(taste.listens(MixTaste.COUNTRY))
    }

    @Test
    fun aMixInGenresNotListenedToIsMissed() {
        val taste = MixTaste.of(mapOf("Rock" to 8 * hour, "Hip-Hop" to 2 * hour))
        assertTrue(taste.misses("'00s Country"))
        assertTrue(taste.misses("Jazz Classics"))
        assertFalse(taste.misses("'00s Rock"))
        assertFalse(taste.misses("'90s Hip-Hop"))
        // One of its genres being listened to is enough.
        assertFalse(taste.misses("Country Rock"))
        // No genre named: nothing to miss.
        assertFalse(taste.misses("'80s Hits"))
        assertFalse(taste.misses("My Mix 3"))
    }

    @Test
    fun aSliverOfListeningIsNotATaste() {
        val taste = MixTaste.of(mapOf("Rock" to 99 * hour, "Country" to hour))
        assertTrue(taste.misses("'00s Country"))
        assertFalse(MixTaste.of(mapOf("Rock" to 9 * hour, "Country" to hour)).misses("'00s Country"))
    }

    @Test
    fun tooLittleToGoOnMissesNothing() {
        assertFalse(MixTaste.UNKNOWN.misses("'00s Country"))
        assertFalse(MixTaste.of(emptyMap()).known)
        // Half an hour of listening is not a taste.
        assertFalse(MixTaste.of(mapOf("Rock" to hour / 2)).misses("'00s Country"))
        // Nor is one made-for-you mix.
        assertFalse(MixTaste.of(emptyMap(), listOf("Rock Mix", "My Mix 1")).known)
    }

    @Test
    fun mixesMadeForTheListenerShowTheirGenres() {
        val taste = MixTaste.of(emptyMap(), listOf("Rock Mix", "Hip-Hop Mix", "Chill Mix", "Daily Mix 1"))
        assertTrue(taste.known)
        assertFalse(taste.misses("'90s Rock"))
        assertTrue(taste.misses("'00s Country"))
        // They add to what the chart shows.
        val both = MixTaste.of(mapOf("Rock" to 5 * hour), listOf("Country Mix"))
        assertFalse(both.misses("'00s Country"))
        assertTrue(both.misses("Jazz Classics"))
    }

    @Test
    fun listenedGenresWeighMoreAndMissedOnesLess() {
        val taste = MixTaste.of(mapOf("Rock" to 8 * hour, "Hip-Hop" to 2 * hour))
        assertEquals(1.0, taste.weight("My Mix 1"), 0.0)
        assertTrue(taste.weight("Rock Mix") > taste.weight("Hip-Hop Mix"))
        assertTrue(taste.weight("Hip-Hop Mix") > 1.0)
        assertTrue(taste.weight("Country Mix") < 1.0)
        assertEquals(1.0, MixTaste.UNKNOWN.weight("Country Mix"), 0.0)
    }

    @Test
    fun theShuffleLeansTowardsListenedGenres() {
        val taste = MixTaste.of(mapOf("Rock" to 10 * hour))
        val titles = listOf("Rock Mix", "Country Mix", "Chill Mix", "Focus Mix", "Jazz Mix", "Happy Mix")
        val first = (1..400).map { seed -> taste.shuffled(titles, Random(seed)) { it }.first() }
        val rock = first.count { it == "Rock Mix" }
        val country = first.count { it == "Country Mix" }
        assertTrue("rock first $rock times", rock > 400 / 6 * 2)
        assertTrue("country first $country times", country < 400 / 6 / 2)
        // Every mix still gets to lead sometimes, and nothing is lost.
        assertEquals(titles.toSet(), first.toSet())
        assertEquals(titles.toSet(), taste.shuffled(titles, Random(1)) { it }.toSet())
    }

    @Test
    fun withoutATasteThePickIsAsItWas() {
        val yt = (0 until 12).map { playlist("VLRDTMAK$it", "Mix ${'A' + it}") }
        assertEquals(MixesDial.pick(yt, emptyList(), Random(3)), MixesDial.pick(yt, emptyList(), Random(3), taste = MixTaste.UNKNOWN))
    }

    @Test
    fun thePickLeadsWithListenedGenres() {
        val taste = MixTaste.of(mapOf("Rock" to 10 * hour))
        val yt = listOf(playlist("VLRDTMAK0", "Rock Mix")) + (1 until 12).map { playlist("VLRDTMAK$it", "Mood ${'A' + it} Mix") }
        val places = (1..300).map { seed -> MixesDial.pick(yt, emptyList(), Random(seed), taste = taste).indexOfFirst { it.title == "Rock Mix" } }
        assertTrue(places.none { it < 0 })
        // Unweighted, its place would average 5.5.
        assertTrue("average place ${places.average()}", places.average() < 3.5)
    }

    @Test
    fun madeForYouIsTheAccountsMixesAndSpotifys() {
        val yt = listOf(playlist("VLRDTMAK1", "Rock Mix"), playlist("VLRDCLAK2", "Country Mix"))
        val sp = listOf(playlist("spotify:playlist:1", "Hip-Hop Mix"))
        assertEquals(listOf("Rock Mix", "Hip-Hop Mix"), MixesDial.madeForYou(yt, sp))
    }
}
