package com.music.bitchord.data.innertube

import com.music.bitchord.data.model.isEpisode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shapes trimmed from YouTube Music's own responses for a podcast show (`MPSPP…`). */
class PodcastTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private fun episode(videoId: String, title: String, runtime: String, extra: String = "") = """
        {
          "musicMultiRowListItemRenderer": {
            "thumbnail": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [
              { "url": "https://i.ytimg.com/vi/$videoId/hq720.jpg", "width": 853, "height": 479 }
            ] } } },
            "onTap": { "watchEndpoint": {
              "videoId": "$videoId",
              "watchEndpointMusicSupportedConfigs": {
                "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" }
              }
            } },
            "subtitle": { "runs": [ { "text": "128K views" }, { "text": " • " }, { "text": "2d ago" } ] },
            "playbackProgress": { "musicPlaybackProgressRenderer": {
              "durationText": { "runs": [ { "text": " • " }, { "text": "$runtime" } ] }
            } },
            "title": { "runs": [ { "text": "$title" } ] }$extra
          }
        }"""

    private val showPage = json(
        """
        {
          "contents": { "twoColumnBrowseResultsRenderer": {
            "tabs": [ { "tabRenderer": { "content": { "sectionListRenderer": { "contents": [
              { "musicResponsiveHeaderRenderer": {
                "title": { "runs": [ { "text": "Huberman Lab" } ] },
                "subtitle": {},
                "straplineTextOne": { "runs": [ { "text": "Andrew Huberman" } ] }
              } }
            ] } } } } ],
            "secondaryContents": { "sectionListRenderer": { "contents": [
              { "musicShelfRenderer": {
                "contents": [
                  ${episode("NlVu0P1RdHs", "Ross Edgley", "2 hr 43 min")},
                  ${episode("eYSOabR3YP0", "Essentials: Supplementation", "34 min")}
                ],
                "continuations": [ { "nextContinuationData": { "continuation": "PAGE2" } } ]
              } }
            ] } }
          } }
        }
        """,
    )

    @Test
    fun showPage_listsItsEpisodesAsPlayableTracks() {
        val show = InnertubeParser.podcastShow(showPage)
        assertEquals("Huberman Lab", show)

        val songs = InnertubeParser.collectSongsDeep(showPage, show)
        assertEquals(listOf("NlVu0P1RdHs", "eYSOabR3YP0"), songs.map { it.videoId })
        assertEquals(listOf("Huberman Lab", "Huberman Lab"), songs.map { it.artist })
        assertEquals(listOf("2:43:00", "34:00"), songs.map { it.durationText })
        // Widescreen art, but an episode is listened to.
        assertFalse(songs.any { it.isVideo })
        assertEquals("PAGE2", InnertubeParser.continuationToken(showPage))
    }

    @Test
    fun showPage_readsWhatAnEpisodeRowSaysBeyondATracks() {
        val row = json(
            """{ "contents": [ ${episode(
                "NlVu0P1RdHs", "Ross Edgley", "2 hr 43 min",
                """, "description": { "runs": [ { "text": "How to build endurance.\n\nSponsors below. " } ] }""",
            ).replace(
                """"durationText"""",
                """"playbackProgressPercentage": 37, "durationText"""",
            )} ] }""",
        )
        val episode = InnertubeParser.collectSongsDeep(row, "Huberman Lab").single().episode!!
        assertEquals("How to build endurance.\n\nSponsors below.", episode.description)
        assertEquals("2d ago", episode.released)
        assertEquals("128K", episode.views)
        assertEquals(37, episode.playedPercent)

        // Not started, and nothing written about it.
        val plain = InnertubeParser.collectSongsDeep(showPage, "Huberman Lab").first().episode!!
        assertNull(plain.description)
        assertEquals(0, plain.playedPercent)
    }

    @Test
    fun playlistOfEpisodes_listsThem_eachWithItsShow() {
        val page = json(
            """
            { "contents": { "twoColumnBrowseResultsRenderer": { "secondaryContents": { "sectionListRenderer": {
              "contents": [ { "musicPlaylistShelfRenderer": { "contents": [
                ${episode(
                "TPXdcOJRmRc", "World News Tonight", "20 min",
                """, "secondTitle": { "runs": [ { "text": "ABC World News Tonight", "navigationEndpoint": {
                    "browseEndpoint": { "browseId": "MPSPPLQOa26lW-uI8hXwg0nCxcX2ukSk_iCP3p" } } } ] }""",
            )},
                ${episode("eYSOabR3YP0", "Essentials: Supplementation", "34 min")}
              ] } } ]
            } } } } }
            """,
        )
        val songs = InnertubeParser.parsePlaylistShelf(page)!!.songs
        assertEquals(listOf("TPXdcOJRmRc", "eYSOabR3YP0"), songs.map { it.videoId })
        assertEquals("MPSPPLQOa26lW-uI8hXwg0nCxcX2ukSk_iCP3p", songs[0].episode?.showId)
        assertEquals("ABC World News Tonight", songs[0].artist)
        assertNull(songs[1].episode?.showId)
    }

    @Test
    fun continuation_hasNoHeaderSoTheShowIsCarriedIn() {
        val page = json(
            """
            { "continuationContents": { "musicShelfContinuation": { "contents": [
              ${episode("P-h5WSQG1Sw", "Emotions", "2 hr 9 min")}
            ] } } }
            """,
        )
        assertNull(InnertubeParser.podcastShow(page))
        assertNull(InnertubeParser.parsePlaylistShelf(page))
        val song = InnertubeParser.collectSongsDeep(page, "Huberman Lab").single()
        assertEquals("Huberman Lab", song.artist)
        assertEquals("2:09:00", song.durationText)
    }

    @Test
    fun episodeAwayFromItsShow_namesTheShowItself() {
        val shelf = json(
            """{ "contents": [ ${episode(
                "TPXdcOJRmRc", "World News Tonight", "20 min",
                """, "secondTitle": { "runs": [ { "text": "ABC World News Tonight" } ] }""",
            )} ] }""",
        )
        assertEquals("ABC World News Tonight", InnertubeParser.collectSongsDeep(shelf, "Ignored").single().artist)
    }

    @Test
    fun searchEpisode_isCreditedToItsShowAndKeptOnTheAllTab() {
        val response = json(
            """
            { "contents": [ { "musicResponsiveListItemRenderer": {
              "thumbnail": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [
                { "url": "https://i.ytimg.com/vi/Y566_T-YlNQ/hqdefault.jpg", "width": 400, "height": 225 }
              ] } } },
              "overlay": { "musicItemThumbnailOverlayRenderer": { "content": { "musicPlayButtonRenderer": {
                "playNavigationEndpoint": { "watchEndpoint": {
                  "videoId": "Y566_T-YlNQ",
                  "watchEndpointMusicSupportedConfigs": {
                    "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_PODCAST_EPISODE" }
                  }
                } }
              } } } },
              "flexColumns": [
                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Become Mentally Dangerous" } ] } } },
                { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [
                  { "text": "Episode" }, { "text": " • " }, { "text": "Sep 1" }, { "text": " • " },
                  { "text": "Figuring Out With Raj Shamani", "navigationEndpoint": { "browseEndpoint": {
                    "browseId": "MPSPPLE0Jo6NF_JYO5-phess8GKafKMtPv3tfZ",
                    "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig": {
                      "pageType": "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"
                    } }
                  } } }
                ] } } }
              ]
            } } ] }
            """,
        )
        val song = InnertubeParser.parseSearchSongs(response).single()
        assertEquals("Y566_T-YlNQ", song.videoId)
        assertEquals("Figuring Out With Raj Shamani", song.artist)
        assertFalse(song.isVideo)
        // What the player knows it by: it shows its picture and looks for no lyrics.
        assertTrue(song.isEpisode)
    }

    @Test
    fun views_areTheCountAlone_whereTheTextIsEnglish() {
        fun runs(vararg parts: String) = json(
            """{ "runs": [ ${parts.joinToString(""", { "text": " • " }, """) { """{ "text": "$it" }""" }} ] }""",
        )["runs"] as kotlinx.serialization.json.JsonArray
        val read = InnertubeParser::episodeViews
        assertEquals("489K", read(runs("489K views", "1d ago")))
        assertEquals("1.4M", read(runs("1.4M views", "Oct 24")))
        assertEquals("1", read(runs("1 view", "Nov 14, 2025")))
        assertEquals("12,345", read(runs("12,345 views")))
        // Another language: YouTube Music's own words, where a date follows them.
        assertEquals("48.9万 回視聴", read(runs("48.9万 回視聴", "1 日前")))
        // The date alone is not a count.
        assertNull(read(runs("1d ago")))
        assertNull(read(runs("Oct 24")))
        assertNull(read(null))
    }

    @Test
    fun watchQueue_knowsAnEpisodeFromAMusicVideo() {
        fun row(videoId: String, type: String) = """
            { "playlistPanelVideoRenderer": {
              "videoId": "$videoId",
              "title": { "runs": [ { "text": "Title $videoId" } ] },
              "longBylineText": { "runs": [ { "text": "Someone" }, { "text": " • " }, { "text": "128K views" } ] },
              "lengthText": { "runs": [ { "text": "34:00" } ] },
              "navigationEndpoint": { "watchEndpoint": { "watchEndpointMusicSupportedConfigs": {
                "watchEndpointMusicConfig": { "musicVideoType": "$type" }
              } } }
            } }
        """
        val queue = InnertubeParser.parseWatchQueue(
            json(
                """{ "contents": [ ${row("NlVu0P1RdHs", "MUSIC_VIDEO_TYPE_PODCAST_EPISODE")},
                    ${row("eYSOabR3YP0", "MUSIC_VIDEO_TYPE_OMV")} ] }""",
            ),
        )
        assertEquals(listOf(true, false), queue.map { it.isEpisode })
        // An episode's byline counts its views too, and it is no music video.
        assertEquals(listOf(false, true), queue.map { it.isVideo })
    }

    private fun showCard(playlistId: String, title: String, publisher: String) = """
        { "musicTwoRowItemRenderer": {
          "thumbnailRenderer": { "musicThumbnailRenderer": { "thumbnail": { "thumbnails": [
            { "url": "https://i.ytimg.com/pl_c/$playlistId/small.jpg", "width": 226, "height": 226 },
            { "url": "https://i.ytimg.com/pl_c/$playlistId/large.jpg", "width": 544, "height": 544 }
          ] } } },
          "title": { "runs": [ { "text": "$title" } ] },
          "subtitle": { "runs": [ { "text": "$publisher", "navigationEndpoint": { "browseEndpoint": {
            "browseId": "UC0123456789"
          } } } ] },
          "navigationEndpoint": { "browseEndpoint": {
            "browseId": "MPSP$playlistId",
            "browseEndpointContextSupportedConfigs": { "browseEndpointContextMusicConfig": {
              "pageType": "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"
            } }
          } }
        } }"""

    private fun carousel(title: String, items: String, more: String = "") = """
        { "musicCarouselShelfRenderer": {
          "header": { "musicCarouselShelfBasicHeaderRenderer": {
            "title": { "runs": [ { "text": "$title" } ] }$more
          } },
          "contents": [ $items ]
        } }"""

    /** Home under its Podcasts chip: shows picked for the listener, then episodes by category. */
    private val podcastsHome = json(
        """
        { "contents": { "singleColumnBrowseResultsRenderer": { "tabs": [ { "tabRenderer": { "content": {
          "sectionListRenderer": {
            "contents": [
              ${carousel(
                "Shows for you",
                showCard("PLaaa", "The Daily", "The New York Times") + "," +
                    showCard("PLbbb", "The Video Game Show", "Some Studio"),
            )},
              ${carousel(
                "Comedy",
                episode(
                    "TPXdcOJRmRc", "A funny one", "1 hr 5 min",
                    """, "secondTitle": { "runs": [ { "text": "Laugh Lab" } ] }""",
                ) + "," + episode("eYSOabR3YP0", "No show named", "34 min"),
            )}
            ],
            "continuations": [ { "nextContinuationData": { "continuation": "NEXT" } } ]
          }
        } } } ] } } }
        """,
    )

    @Test
    fun podcastsHome_hasAShelfOfShowsAndAShelfOfEpisodes() {
        val shelves = InnertubeParser.parsePodcastShelves(podcastsHome)
        assertEquals(listOf("Shows for you", "Comedy"), shelves.map { it.title })

        val shows = shelves[0]
        assertEquals(com.music.bitchord.data.model.ShelfLayout.CARDS, shows.layout)
        // A show with "video" in its name is still a show.
        assertEquals(listOf("MPSPPLaaa", "MPSPPLbbb"), shows.items.map { it.browseId })
        assertEquals(listOf("The New York Times", "Some Studio"), shows.items.map { it.subtitle })
        assertEquals("https://i.ytimg.com/pl_c/PLaaa/large.jpg", shows.items.first().thumbnailUrl)
        assertFalse(shows.items.any { it.videoId != null })

        val episodes = shelves[1]
        assertEquals(com.music.bitchord.data.model.ShelfLayout.VIDEO, episodes.layout)
        assertEquals(listOf("TPXdcOJRmRc", "eYSOabR3YP0"), episodes.items.map { it.videoId })
        assertEquals(listOf("Laugh Lab", ""), episodes.items.map { it.subtitle })
        assertEquals(listOf("1:05:00", "34:00"), episodes.items.map { it.durationText })
        assertEquals("NEXT", InnertubeParser.continuationToken(podcastsHome))
    }

    @Test
    fun podcastsContinuation_isReadWhereverItsShelvesLand() {
        val page = json(
            """
            { "continuationContents": { "sectionListContinuation": { "contents": [
              ${carousel(
                "News",
                episode("NlVu0P1RdHs", "Today", "20 min"),
                """, "moreContentButton": { "buttonRenderer": { "navigationEndpoint": { "browseEndpoint": {
                  "browseId": "FEmusic_top_non_music_audio_episodes"
                } } } }""",
            )},
              ${carousel("Popular shows", showCard("PLccc", "Up First", "NPR"))},
              ${carousel(
                "Albums for you",
                """{ "musicTwoRowItemRenderer": {
                  "title": { "runs": [ { "text": "An album" } ] },
                  "navigationEndpoint": { "browseEndpoint": { "browseId": "MPREb_123" } }
                } }""",
            )}
            ] } } }
            """,
        )
        val shelves = InnertubeParser.parsePodcastShelves(page)
        // A shelf with neither shows nor episodes is not a podcasts shelf.
        assertEquals(listOf("News", "Popular shows"), shelves.map { it.title })
        assertEquals("FEmusic_top_non_music_audio_episodes", shelves[0].moreBrowseId)
        assertEquals("MPSPPLccc", shelves[1].items.single().browseId)
        assertNull(InnertubeParser.continuationToken(page))
    }

    @Test
    fun runtime_isReadInTheAppsLanguage() {
        val read = InnertubeParser::episodeRuntime
        // English, as before.
        assertEquals("2:43:00", read(" • 2 hr 43 min"))
        assertEquals("34:00", read("34 min"))
        assertEquals("0:45", read("45 sec"))
        assertEquals("2:00:00", read("2 hours"))
        // Other languages: hours and minutes by their numbers.
        assertEquals("1:05:00", read(" • 1 h 5 min"))
        assertEquals("1:05:00", read("1 Std. 5 Min."))
        assertEquals("1:05:00", read("1時間5分"))
        assertEquals("1:05:00", read("1 ч 5 мин"))
        // One number is minutes, unless the word beside it is one for hours.
        assertEquals("20:00", read("20 Min."))
        assertEquals("20:00", read("20分"))
        assertEquals("20:00", read("20 мин"))
        assertEquals("2:00:00", read("2 Std."))
        assertEquals("2:00:00", read("2時間"))
        assertEquals("2:00:00", read("2 ч"))
        assertEquals("2:00:00", read("2 horas"))
        assertNull(read(""))
        assertNull(read("Live"))
    }
}
