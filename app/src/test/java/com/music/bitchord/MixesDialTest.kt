package com.music.bitchord

import com.music.bitchord.data.mixes.MixesDial
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.spotify.SpotifyLibrary
import com.music.bitchord.data.spotify.SpotifyPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MixesDialTest {

    private fun playlist(id: String, title: String) =
        ShelfItem(title = title, subtitle = "", thumbnailUrl = null, videoId = null, browseId = id)

    @Test
    fun youTubeMixesAreTheAccountsMixesAndPlaylistsCalledOne() {
        assertTrue(MixesDial.isYouTubeMix(playlist("VLRDTMAK5uy_kset8DisdE7LSD4TNjEVvrKRTmG7a56sY", "My Mix 1")))
        assertTrue(MixesDial.isYouTubeMix(playlist("RDTMAK5uy_n_5IN6hzAOwdCnM8D8rzrs3vDl12UcZpA", "Discover Mix")))
        assertTrue(MixesDial.isYouTubeMix(playlist("VLRDCLAK5uy_abc", "Chill Mix")))
        assertFalse(MixesDial.isYouTubeMix(playlist("VLRDCLAK5uy_abc", "Chill Hits")))
        assertFalse(MixesDial.isYouTubeMix(playlist("MPREb_abc", "Mix Tape (album)")))
        assertFalse(MixesDial.isYouTubeMix(playlist("UCabc", "Mixmaster")))
        assertFalse(
            MixesDial.isYouTubeMix(
                ShelfItem(title = "Mix", subtitle = "", thumbnailUrl = null, videoId = "abc", browseId = null),
            ),
        )
    }

    @Test
    fun spotifyMixesAreSpotifysPersonalPlaylists() {
        fun sp(name: String, owner: String?, id: String = name) = SpotifyPlaylist(id, name, owner, null)
        assertTrue(MixesDial.isSpotifyMix(sp("Daily Mix 1", "Spotify")))
        assertTrue(MixesDial.isSpotifyMix(sp("Discover Weekly", "Spotify")))
        assertTrue(MixesDial.isSpotifyMix(sp("daylist • chill tuesday evening", "Spotify")))
        assertFalse(MixesDial.isSpotifyMix(sp("Today's Top Hits", "Spotify")))
        assertFalse(MixesDial.isSpotifyMix(sp("My gym mix", "steeben")))
        assertFalse(MixesDial.isSpotifyMix(sp("Liked Songs", null, SpotifyLibrary.LIKED_ID)))
    }

    /** Mixes of [count] different kinds: "Mix A", "Mix B", and so on. */
    private fun kinds(prefix: String, count: Int, name: String = "Mix") =
        (0 until count).map { playlist("$prefix$it", "$name ${'A' + it}") }

    @Test
    fun pickTakesFromBothSourcesInTurn() {
        val yt = kinds("VLRDTMAK", 10)
        val sp = kinds("spotify:playlist:", 3, name = "Daily")
        val dealt = MixesDial.pick(yt, sp, Random(7))
        assertEquals(MixesDial.SIZE, dealt.size)
        // All three of Spotify's within the first six, alternating with YouTube's.
        assertEquals(3, dealt.take(6).count { it.browseId!!.startsWith("spotify:") })
        assertEquals(dealt.size, dealt.map { it.browseId }.toSet().size)
    }

    @Test
    fun pickDropsDuplicatesAndCopesWithOneSource() {
        val mix = playlist("VLRDTMAK1", "My Mix 1")
        assertEquals(listOf(mix), MixesDial.pick(listOf(mix, mix), emptyList(), Random(1)))
        assertEquals(emptyList<ShelfItem>(), MixesDial.pick(emptyList(), emptyList(), Random(1)))
    }

    @Test
    fun differentDealsShuffleDifferently() {
        val yt = kinds("VLRDTMAK", 12)
        val deals = (1..5).map { seed -> MixesDial.pick(yt, emptyList(), Random(seed)) }.toSet()
        assertTrue(deals.size > 1)
    }

    @Test
    fun aMixTypeIsItsNameWithoutTheNumber() {
        assertEquals(MixesDial.mixType("Chill Mix 01"), MixesDial.mixType("Chill Mix 02"))
        assertEquals(MixesDial.mixType("Chill Mix"), MixesDial.mixType("chill mix 2"))
        assertEquals(MixesDial.mixType("Sleep Supermix"), MixesDial.mixType("Sleep super mix"))
        assertEquals(MixesDial.mixType("Feel Good Mix"), MixesDial.mixType("Feel-good mix"))
        assertEquals(MixesDial.mixType("Your Top Songs 2023"), MixesDial.mixType("Your Top Songs 2022"))
        assertFalse(MixesDial.mixType("Chill Mix") == MixesDial.mixType("Focus Mix"))
        assertFalse(MixesDial.mixType("2010s Mix") == MixesDial.mixType("2000s Mix"))
        // A title that is only a number is still a kind of its own.
        assertFalse(MixesDial.mixType("1989") == MixesDial.mixType("1999"))
    }

    @Test
    fun onlyOneMixOfAKindIsDealt() {
        val yt = listOf(
            playlist("VLRDTMAK1", "Chill Mix 01"),
            playlist("VLRDTMAK2", "Chill Mix 02"),
            playlist("VLRDTMAK3", "Feel Good Mix"),
            playlist("VLRDTMAK4", "Sleep Supermix"),
            playlist("VLPL5", "Sleep super mix"),
        )
        val sp = listOf(
            playlist("spotify:playlist:1", "Feel Good Mix"),
            playlist("spotify:playlist:2", "Daily Mix 1"),
            playlist("spotify:playlist:3", "Daily Mix 2"),
        )
        repeat(20) { seed ->
            val dealt = MixesDial.pick(yt, sp, Random(seed))
            val types = dealt.map { MixesDial.mixType(it.title) }
            assertEquals(types.toSet().size, types.size)
            assertEquals(setOf("chillmix", "feelgoodmix", "sleepsupermix", "dailymix"), types.toSet())
        }
    }

    @Test
    fun fillerOnlyTakesWhatRoomIsLeft() {
        val trending = kinds("VLPLtrend", 20, name = "Trending")
        // A full page of mixes leaves the filler out.
        val full = MixesDial.pick(kinds("VLRDTMAK", 12), emptyList(), Random(3), filler = trending)
        assertTrue(full.none { it.browseId!!.startsWith("VLPLtrend") })
        // Fewer mixes than the page holds: all of them first, then the filler.
        val short = MixesDial.pick(kinds("VLRDTMAK", 4), kinds("spotify:playlist:", 2, "Daily"), Random(3), filler = trending)
        assertEquals(MixesDial.SIZE, short.size)
        assertTrue(short.take(6).none { it.browseId!!.startsWith("VLPLtrend") })
        assertTrue(short.drop(6).all { it.browseId!!.startsWith("VLPLtrend") })
        // The filler doesn't repeat a kind of mix either.
        val sameKinds = MixesDial.pick(kinds("VLRDTMAK", 2), emptyList(), Random(3), filler = kinds("VLPLtrend", 2))
        assertEquals(2, sameKinds.size)
    }

    @Test
    fun upToThreeDecadesPlaylistsAreDealtWithTheMixes() {
        val decades = kinds("VLRDCLAK5uy_dec", 8, name = "Decade")
        fun decade(item: ShelfItem) = item.browseId!!.startsWith("VLRDCLAK5uy_dec")
        repeat(20) { seed ->
            // Plenty of mixes: still three of the decades', among the first of the page.
            val full = MixesDial.pick(kinds("VLRDTMAK", 20), kinds("spotify:playlist:", 6, "Daily"), Random(seed), decades = decades)
            assertEquals(MixesDial.SIZE, full.size)
            assertEquals(3, full.count(::decade))
            assertEquals(3, full.take(9).count(::decade))
            // No mixes at all: three and no more.
            assertEquals(3, MixesDial.pick(emptyList(), emptyList(), Random(seed), decades = decades).size)
            // Fewer than three to give.
            assertEquals(2, MixesDial.pick(kinds("VLRDTMAK", 20), emptyList(), Random(seed), decades = decades.take(2)).count(::decade))
            // The cap holds with the filler after it.
            val filled = MixesDial.pick(kinds("VLRDTMAK", 2), emptyList(), Random(seed), filler = kinds("VLPLtrend", 20, "Trending"), decades = decades)
            assertEquals(MixesDial.SIZE, filled.size)
            assertEquals(3, filled.count(::decade))
        }
    }

    @Test
    fun decadesPlaylistsOfAKindAlreadyDealtArePassedOver() {
        val decades = listOf(
            playlist("VLRDCLAK5uy_a", "'80s Hits 1"),
            playlist("VLRDCLAK5uy_b", "'80s Hits 2"),
            playlist("VLRDCLAK5uy_c", "'90s Hits"),
            playlist("VLRDCLAK5uy_d", "Mix A"),
            playlist("VLRDCLAK5uy_e", "'70s Hits"),
        )
        repeat(20) { seed ->
            val dealt = MixesDial.pick(kinds("VLRDTMAK", 1), emptyList(), Random(seed), decades = decades)
            val types = dealt.map { MixesDial.mixType(it.title) }
            assertEquals(types.toSet().size, types.size)
            assertEquals(3, dealt.count { it.browseId!!.startsWith("VLRDCLAK5uy_") })
        }
    }

    @Test
    fun theDecadesCategoryIsFoundByItsTitle() {
        val sections = listOf(
            MoodGenreSection("Moods & moments", listOf(MoodGenre("Chill", "FEmusic_moods_and_genres_category", "chill"))),
            MoodGenreSection(
                "Genres",
                listOf(
                    MoodGenre("Rock", "FEmusic_moods_and_genres_category", "rock"),
                    MoodGenre(" decades ", "FEmusic_moods_and_genres_category", "dec"),
                ),
            ),
        )
        assertEquals("dec", MixesDial.decadesCategory(sections)?.params)
        assertEquals(null, MixesDial.decadesCategory(sections.take(1)))
    }

    @Test
    fun communityShelvesAreFoundByTitleAndGivePlaylistsOnly() {
        val shelves = listOf(
            HomeShelf("Quick picks", listOf(playlist("VLPLa", "Not this"))),
            HomeShelf(
                "from the community",
                listOf(
                    playlist("VLPLb", "Late night drive"),
                    playlist("MPREb_album", "An album"),
                    ShelfItem(title = "A song", subtitle = "", thumbnailUrl = null, videoId = "v", browseId = null),
                ),
            ),
            HomeShelf("Trending community playlists", listOf(playlist("VLPLc", "Viral"))),
        )
        assertEquals(listOf("VLPLb"), MixesDial.shelfPlaylists(shelves, MixesDial.FROM_THE_COMMUNITY).map { it.browseId })
        assertEquals(listOf("VLPLc"), MixesDial.shelfPlaylists(shelves, MixesDial.TRENDING_COMMUNITY).map { it.browseId })
        // Both shelves' playlists show their names on the page; nothing else does.
        assertEquals(setOf("VLPLb", "VLPLc"), MixesDial.communityIds(shelves))
    }

    @Test
    fun sleepMixesAreForTheNightHours() {
        fun at(hour: Int, minute: Int) = MixesDial.isSleepTime(java.time.LocalTime.of(hour, minute))
        assertFalse(at(21, 29))
        assertTrue(at(21, 30))
        assertTrue(at(23, 59))
        assertTrue(at(0, 0))
        assertTrue(at(4, 0))
        assertFalse(at(4, 1))
        assertFalse(at(12, 0))
    }

    @Test
    fun aSleepMixGoesByItsName() {
        assertTrue(MixesDial.isSleepMix(playlist("VLRDTMAK1", "Sleep Mix")))
        assertTrue(MixesDial.isSleepMix(playlist("VLRDTMAK2", "Deep sleep supermix")))
        assertFalse(MixesDial.isSleepMix(playlist("VLRDTMAK3", "Chill Mix")))
    }
}
