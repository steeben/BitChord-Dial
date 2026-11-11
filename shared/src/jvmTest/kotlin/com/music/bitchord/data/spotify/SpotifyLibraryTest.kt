package com.music.bitchord.data.spotify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotifyLibraryTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun card(id: String, name: String, type: String = "Playlist") =
        """{"uri":"spotify:playlist:$id","content":{"__typename":"PlaylistResponseWrapper","data":{
          "__typename":"$type","uri":"spotify:playlist:$id","name":"$name",
          "images":{"items":[{"sources":[{"url":"https://img/$id"}]}]},
          "ownerV2":{"data":{"name":"Spotify"}}}}}"""

    @Test
    fun topMixesAreTheHomeShelfByUriOrTitle() {
        fun home(uri: String, title: String) = json.parseToJsonElement(
            """
            {"data":{"home":{"sectionContainer":{"sections":{"items":[
              {"uri":"spotify:section:other","data":{"title":{"transformedLabel":"Recommended radios"}},
               "sectionItems":{"items":[${card("r1", "Radio X")}]}},
              {"uri":"$uri","data":{"title":{"transformedLabel":"$title"}},
               "sectionItems":{"items":[${card("m1", "Chill Mix")},${card("37i9dQZF1EYkqdzj48dyYq", "DJ")},
                 ${card("a1", "An album", "Album")},${card("m2", "Pop Mix")}]}}
            ]}}}}}
            """.trimIndent(),
        ).jsonObject
        val byUri = parseHomeTopMixes(home(SpotifyLibrary.TOP_MIXES_SECTION, "Tes mixes"))
        assertEquals(listOf("m1", "m2"), byUri.map { it.id })
        assertEquals("Chill Mix", byUri.first().name)
        assertEquals("Spotify", byUri.first().owner)
        assertEquals("https://img/m1", byUri.first().imageUrl)
        assertEquals(listOf("m1", "m2"), parseHomeTopMixes(home("spotify:section:moved", "Your top mixes")).map { it.id })
        assertEquals(emptyList(), parseHomeTopMixes(home("spotify:section:moved", "Something else")))
    }

    @Test
    fun aShelfAskedForOnItsOwnGivesItsPlaylists() {
        val root = json.parseToJsonElement(
            """
            {"data":{"homeSections":{"sections":[
              {"sectionItems":{"totalCount":2,"items":[${card("m1", "Chill Mix")},${card("m1", "Chill Mix")},${card("m2", "Pop Mix")}]}}
            ]}}}
            """.trimIndent(),
        ).jsonObject
        assertEquals(listOf("m1", "m2"), parseHomeSectionPlaylists(root).map { it.id })
    }

    @Test
    fun playlistsIncludeFolderLeaves() {
        val root = json.parseToJsonElement(
            """
            {"data":{"me":{"libraryV3":{"totalCount":1,"items":[
              {"item":{"__typename":"PlaylistResponseWrapper","_uri":"spotify:playlist:abc","data":{
                "__typename":"Playlist","name":"Road","ownerV2":{"data":{"name":"Amit"}},
                "images":{"items":[{"sources":[{"url":"https://i.scdn.co/x"}]}]}
              }}}
            ]}}}}
            """.trimIndent(),
        ).jsonObject
        val (items, total) = parsePlaylistPage(root)
        assertEquals(1, total)
        assertEquals("abc", items.single().id)
        assertEquals("Road", items.single().name)
        assertEquals("Amit", items.single().owner)
        assertEquals("https://i.scdn.co/x", items.single().imageUrl)
    }

    @Test
    fun tracksReadTitleArtistAndDuration() {
        val root = json.parseToJsonElement(
            """
            {"data":{"playlistV2":{"content":{"totalCount":1,"items":[
              {"itemV2":{"data":{
                "name":"Song","uri":"spotify:track:t1",
                "artists":{"items":[{"uri":"spotify:artist:a","profile":{"name":"Artist"}}]},
                "albumOfTrack":{"name":"Album","coverArt":{"sources":[{"url":"https://img"}]}},
                "duration":{"totalMilliseconds":180000}
              }}}
            ]}}}}
            """.trimIndent(),
        ).jsonObject
        val track = parseTrackPage(root).first.single()
        assertEquals("t1", track.id)
        assertEquals("Song", track.title)
        assertEquals("Artist", track.artist)
        assertEquals("Album", track.album)
        assertEquals(180000, track.durationMs)
        assertEquals("https://img", track.imageUrl)
    }

    @Test
    fun playlistIdsComeFromLinksAndUris() {
        val id = "37i9dQZF1DXcBWIGoYBM5M"
        assertEquals(id, SpotifyImporter.extractPlaylistId("https://open.spotify.com/playlist/$id"))
        assertEquals(id, SpotifyImporter.extractPlaylistId("  https://open.spotify.com/playlist/$id?si=abc123  "))
        assertEquals(id, SpotifyImporter.extractPlaylistId("https://open.spotify.com/intl-id/playlist/$id"))
        assertEquals(id, SpotifyImporter.extractPlaylistId("spotify:playlist:$id"))
        assertNull(SpotifyImporter.extractPlaylistId("https://open.spotify.com/album/$id"))
        assertNull(SpotifyImporter.extractPlaylistId("https://notspotify.com/playlist/$id"))
        assertNull(SpotifyImporter.extractPlaylistId("ftp://open.spotify.com/playlist/$id"))
        assertNull(SpotifyImporter.extractPlaylistId("https://open.spotify.com/playlist/short"))
        assertNull(SpotifyImporter.extractPlaylistId(""))
    }

    @Test
    fun searchReadsTrackHitsAndSkipsOthers() {
        val root = json.parseToJsonElement(
            """
            {"data":{"searchV2":{"tracksV2":{"totalCount":2,"items":[
              {"item":{"__typename":"TrackResponseWrapper","_uri":"spotify:track:s1","data":{
                "__typename":"Track","name":"Hit",
                "artists":{"items":[{"profile":{"name":"One"}},{"profile":{"name":"Two"}}]},
                "albumOfTrack":{"name":"LP","coverArt":{"sources":[{"url":"https://small"},{"url":"https://big"}]}},
                "duration":{"totalMilliseconds":200000}
              }}},
              {"item":{"__typename":"RestrictedContent","_uri":"spotify:track:s2","data":{"__typename":"RestrictedContent"}}}
            ]}}}}
            """.trimIndent(),
        ).jsonObject
        val track = parseSearchTracks(root).single()
        assertEquals("s1", track.id)
        assertEquals("Hit", track.title)
        assertEquals("One, Two", track.artist)
        assertEquals("LP", track.album)
        assertEquals(200000, track.durationMs)
    }

    @Test
    fun readsAPlaylistsOldestEntriesByTheirPlace() {
        val root = Json.parseToJsonElement(
            """{"data":{"playlistV2":{"content":{"totalCount":120,"items":[
                {"uid":"u1","itemV2":{"data":{"uri":"spotify:track:a","name":"A"}}},
                {"uid":"u2","itemV2":{"data":{"_uri":"spotify:track:b"}}},
                {"itemV2":{"data":{"uri":"spotify:track:c"}}}
            ]}}}}""",
        ).jsonObject
        assertEquals(
            120 to listOf("u1" to "spotify:track:a", "u2" to "spotify:track:b"),
            parsePlaylistHead(root),
        )
    }
}
