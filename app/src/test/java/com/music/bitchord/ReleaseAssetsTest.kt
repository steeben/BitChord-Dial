package com.music.bitchord

import com.music.bitchord.data.migration.ReleaseAssets
import com.music.bitchord.data.migration.ReleaseAssets.Asset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseAssetsTest {

    private val bridge = Asset("Dial-v1.5.0-bridge.apk", "b")
    private val app = Asset("Dial-v1.5.0.apk", "a")

    @Test
    fun `the app and the bridge are told apart whatever the order`() {
        assertEquals(app, ReleaseAssets.pick(listOf(bridge, app), bridge = false))
        assertEquals(app, ReleaseAssets.pick(listOf(app, bridge), bridge = false))
        assertEquals(bridge, ReleaseAssets.pick(listOf(app, bridge), bridge = true))
    }

    @Test
    fun `an older release has no bridge`() {
        assertNull(ReleaseAssets.pick(listOf(Asset("Dial-v1.4.1.apk", "a")), bridge = true))
    }

    @Test
    fun `unfinished uploads and other files are skipped`() {
        val assets = listOf(Asset("Dial-v1.5.0.apk", "x", uploaded = false), Asset("notes.txt", "n"), app)
        assertEquals(app, ReleaseAssets.pick(assets, bridge = false))
    }

    @Test
    fun `old builds taking the first apk by name get the bridge`() {
        // 1.4.1 and older take the first .apk; the bridge must sort first.
        assertEquals(bridge.name, listOf(app.name, bridge.name).sorted().first())
    }
}
