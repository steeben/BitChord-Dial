package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Watch-queue rows trimmed from YouTube Music's `next` response. */
class VersionPairTest {

    private fun row(videoId: String, title: String, type: String, byline: String) = """
        {
          "playlistPanelVideoRenderer": {
            "videoId": "$videoId",
            "title": { "runs": [ { "text": "$title" } ] },
            "longBylineText": { "runs": [ { "text": "$byline" } ] },
            "lengthText": { "runs": [ { "text": "3:30" } ] },
            "navigationEndpoint": { "watchEndpoint": { "videoId": "$videoId",
              "watchEndpointMusicSupportedConfigs": { "watchEndpointMusicConfig": { "musicVideoType": "$type" } } } }
          }
        }
    """.trimIndent()

    private fun queue(vararg items: String) = Json.parseToJsonElement(
        """{ "contents": [ ${items.joinToString(",")} ] }""",
    )

    @Test
    fun `a paired video names its song`() {
        val wrapper = """
            { "playlistPanelVideoWrapperRenderer": {
                "primaryRenderer": ${row("vid", "Song (Official Video)", "MUSIC_VIDEO_TYPE_OMV", "Artist • 1M views")},
                "counterpart": [ { "counterpartRenderer": ${row("atv", "Song", "MUSIC_VIDEO_TYPE_ATV", "Artist • Album • 2020")} } ]
            } }
        """.trimIndent()
        val pair = InnertubeParser.parseVersionPair(queue(wrapper), "vid")!!
        assertTrue(pair.isVideo)
        assertEquals("atv", pair.counterpart?.videoId)
        assertFalse(pair.counterpart!!.isVideo)

        val back = InnertubeParser.parseVersionPair(queue(wrapper), "atv")!!
        assertFalse(back.isVideo)
        assertEquals("vid", back.counterpart?.videoId)
        assertTrue(back.counterpart!!.isVideo)
    }

    @Test
    fun `a plain upload is a video even with no views in its byline`() {
        val pair = InnertubeParser.parseVersionPair(queue(row("ugc", "Song", "MUSIC_VIDEO_TYPE_UGC", "Uploader")), "ugc")!!
        assertTrue(pair.isVideo)
        assertNull(pair.counterpart)
    }

    @Test
    fun `a track the queue doesn't list has no answer`() {
        assertNull(InnertubeParser.parseVersionPair(queue(row("a", "Song", "MUSIC_VIDEO_TYPE_ATV", "Artist")), "b"))
    }
}
