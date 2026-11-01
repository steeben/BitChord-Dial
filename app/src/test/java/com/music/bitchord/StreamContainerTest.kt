package com.music.bitchord

import com.music.bitchord.playback.StreamContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamContainerTest {

    @Test
    fun `dash reopen changes virtual uri while preserving playback identity`() {
        val original = "bitchord://watch?v=iQnRCdtECl8&n=Wait&a=M83&d=344"

        val reopened = StreamContainer.markedForManifestReopen(
            original,
            "application/dash+xml",
        )

        assertNotEquals(original, reopened)
        assertEquals("$original&manifest_reopen=dash", reopened)
    }

    @Test
    fun `hls source-track reopen preserves source parameters and fragment`() {
        val original = "bitchord://source?s=addon-1&t=track-2&n=Wait#part"

        assertEquals(
            "bitchord://source?s=addon-1&t=track-2&n=Wait&manifest_reopen=hls#part",
            StreamContainer.markedForManifestReopen(
                original,
                "application/x-mpegURL",
            ),
        )
    }

    @Test
    fun `non-manifest mime does not force a source rebuild`() {
        val original = "bitchord://watch?v=iQnRCdtECl8"

        assertEquals(
            original,
            StreamContainer.markedForManifestReopen(original, "audio/flac"),
        )
    }

    @Test
    fun `live broadcast playlists and segments are recognised`() {
        assertTrue(
            StreamContainer.isLiveBroadcastUrl(
                "https://manifest.googlevideo.com/api/manifest/hls_variant/expire/1761958000/ei/abc/" +
                    "source/yt_live_broadcast/id/prpkGMHVGFo.1/file/index.m3u8",
            ),
        )
        assertTrue(
            StreamContainer.isLiveBroadcastUrl(
                "https://manifest.googlevideo.com/api/manifest/hls_playlist/expire/1761958000/itag/93/" +
                    "playlist/index.m3u8",
            ),
        )
        assertTrue(
            StreamContainer.isLiveBroadcastUrl(
                "https://rr3---sn-abc.googlevideo.com/videoplayback/id/prpkGMHVGFo.1/itag/93/" +
                    "source/yt_live_broadcast/live/1/sq/4021/file/seg.ts",
            ),
        )
        assertTrue(
            StreamContainer.isLiveBroadcastUrl(
                "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1&itag=140&live=1&sq=12",
            ),
        )
    }

    @Test
    fun `ordinary streams are not taken for live`() {
        assertFalse(
            StreamContainer.isLiveBroadcastUrl(
                "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1&itag=251&source=youtube&mime=audio%2Fwebm",
            ),
        )
        assertFalse(StreamContainer.isLiveBroadcastUrl("https://cdn.example.com/track/123/playlist.m3u8"))
    }

    @Test
    fun `a track served a live playlist is reported live`() {
        StreamContainer.served("liveTestId1", "https://manifest.googlevideo.com/api/manifest/hls_variant/x/file/index.m3u8")
        StreamContainer.served("vodTestId01", "https://rr1---sn.googlevideo.com/videoplayback?itag=251&source=youtube")
        assertTrue(StreamContainer.isServingLive("liveTestId1"))
        assertFalse(StreamContainer.isServingLive("vodTestId01"))
        assertFalse(StreamContainer.isServingLive("neverServed"))
    }
}
