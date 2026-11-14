package com.music.bitchord

import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.SearchTopPick
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.SourceSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSearchTest {
    private fun song(title: String, artist: String = "x") =
        Song(videoId = title, title = title, artist = artist, thumbnailUrl = null)

    private fun item(title: String, type: BrowseType) = BrowseItem("id:$type:$title", title, "", null, type)

    @Test
    fun onlyKindsWithHitsGetATab() {
        val songs = listOf(song("a"))
        assertEquals(
            listOf(SearchFilter.ALL, SearchFilter.SONGS, SearchFilter.ARTISTS),
            SourceSearch.filters(songs, listOf(item("b", BrowseType.ARTIST))),
        )
        assertEquals(
            listOf(SearchFilter.ALL, SearchFilter.ALBUMS, SearchFilter.PLAYLISTS),
            SourceSearch.filters(emptyList(), listOf(item("b", BrowseType.PLAYLIST), item("c", BrowseType.ALBUM))),
        )
    }

    @Test
    fun hitsOfOneKindGetNoTabs() {
        assertTrue(SourceSearch.filters(listOf(song("a")), emptyList()).isEmpty())
        assertTrue(SourceSearch.filters(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun theBestNameMatchIsTheTopResult() {
        val songs = listOf(song("Opal (Four Tet Remix)"), song("Tet"))
        assertEquals(SearchResult.TopTrack(songs[1]), SourceSearch.topResult("tet", songs, emptyList()))
    }

    @Test
    fun anArtistComesBeforeASongOfTheSameName() {
        val artist = item("Garden", BrowseType.ARTIST)
        val album = item("Garden", BrowseType.ALBUM)
        assertEquals(
            SearchResult.Browse(artist),
            SourceSearch.topResult("garden", listOf(song("Garden")), listOf(album, artist)),
        )
    }

    @Test
    fun withNoNameMatchTheFirstSongIsTheTopResult() {
        val songs = listOf(song("One", "Savage Garden"), song("Two", "Savage Garden"))
        assertEquals(SearchResult.TopTrack(songs[0]), SourceSearch.topResult("zzz", songs, emptyList()))
    }

    @Test
    fun theSourcesOwnPickIsUsedWhenItIsAmongTheHits() {
        val songs = listOf(song("Tet"), song("Other"))
        val playlist = item("Mix", BrowseType.PLAYLIST)
        assertEquals(
            SearchResult.TopTrack(songs[1]),
            SourceSearch.topResult("tet", songs, emptyList(), SearchTopPick(songIndex = 1)),
        )
        assertEquals(
            SearchResult.Browse(playlist),
            SourceSearch.topResult("tet", songs, listOf(playlist), SearchTopPick(item = playlist)),
        )
        assertEquals(
            SearchResult.TopTrack(songs[0]),
            SourceSearch.topResult("tet", songs, emptyList(), SearchTopPick(item = playlist)),
        )
    }

    @Test
    fun noHitsGiveNoTopResult() {
        assertNull(SourceSearch.topResult("tet", emptyList(), emptyList()))
    }

    @Test
    fun aSpotifyAlbumOpensOnlyAnAlbumOfTheSameName() {
        val deluxe = item("Rounds (Deluxe)", BrowseType.ALBUM)
        val exact = item("Rounds", BrowseType.ALBUM)
        val other = item("Pink", BrowseType.ALBUM)
        assertEquals(exact, SourceSearch.matchAlbum("Rounds", listOf(other, deluxe, exact)))
        assertEquals(deluxe, SourceSearch.matchAlbum("Rounds", listOf(other, deluxe)))
        assertNull(SourceSearch.matchAlbum("Rounds", listOf(other, item("Rounds", BrowseType.ARTIST))))
    }
}
