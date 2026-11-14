package com.music.bitchord

import com.music.bitchord.ui.performance.CompiledCode
import com.music.bitchord.ui.performance.StackDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppThreadWatchTest {
    private fun frame(type: String, method: String, line: Int = -1) =
        StackTraceElement(type, method, "File.kt", line)

    @Test
    fun `a thread that waits for work is not busy`() {
        val waiting = arrayOf(
            frame("android.os.MessageQueue", "nativePollOnce"),
            frame("android.os.MessageQueue", "next"),
            frame("android.os.Looper", "loop"),
        )
        assertNull(StackDigest.of(waiting))
        assertNull(StackDigest.of(emptyArray()))
    }

    @Test
    fun `the app's own frames are kept, innermost first, with what the thread was in`() {
        val stack = arrayOf(
            frame("android.webkit.WebView", "<init>", 400),
            frame("com.music.bitchord.data.spotify.SpotifyTokenKt", "harvestViaWebView", 120),
            frame("kotlin.coroutines.jvm.internal.BaseContinuationImpl", "resumeWith", 33),
            frame("com.music.bitchord.ui.MainViewModel\$loadHome\$1", "invokeSuspend", 2310),
            frame("android.os.Handler", "dispatchMessage"),
        )
        assertEquals(
            "in android.webkit.WebView.<init>:400, from " +
                "data.spotify.SpotifyTokenKt.harvestViaWebView:120 < ui.MainViewModel\$loadHome\$1.invokeSuspend:2310",
            StackDigest.of(stack),
        )
    }

    @Test
    fun `a stack with the app's own frame on top starts with it`() {
        val stack = arrayOf(
            frame("com.music.bitchord.ui.screens.HomeScreenKt", "HomeScreen", 90),
            frame("androidx.compose.runtime.ComposerImpl", "doCompose"),
            frame("com.music.bitchord.MainActivityKt", "BitChordApp"),
        )
        assertEquals("ui.screens.HomeScreenKt.HomeScreen:90 < MainActivityKt.BitChordApp", StackDigest.of(stack))
    }

    @Test
    fun `the same frame twice in a row is written once, and no more than the limit`() {
        val stack = arrayOf(
            frame("com.music.bitchord.A", "a"),
            frame("com.music.bitchord.A", "a"),
            frame("com.music.bitchord.B", "b"),
            frame("com.music.bitchord.C", "c"),
        )
        assertEquals("A.a < B.b", StackDigest.of(stack, limit = 2))
    }

    @Test
    fun `a stack with none of the app's own gives its top frames`() {
        val stack = arrayOf(
            frame("android.graphics.RenderNode", "nEndRecording"),
            frame("android.view.View", "updateDisplayListIfDirty"),
        )
        assertEquals(
            "in android.graphics.RenderNode.nEndRecording < android.view.View.updateDisplayListIfDirty",
            StackDigest.of(stack),
        )
    }

    @Test
    fun `compiled code is the app's own executable mappings`() {
        val dir = "/data/app/~~a==/com.steeben.dial-b=="
        val maps = sequenceOf(
            "7a00000000-7a00100000 r--p 00000000 fd:05 11 $dir/oat/arm64/base.odex",
            "7a00100000-7a00500000 r-xp 00100000 fd:05 11 $dir/oat/arm64/base.odex",
            "7a00500000-7a00600000 r--p 00000000 fd:05 12 $dir/oat/arm64/base.vdex",
            // Another app's code, loaded into this one.
            "7b00000000-7b00800000 r-xp 00000000 fd:05 13 /data/app/~~c==/com.google.android.webview-d==/oat/arm64/base.odex",
            "7c00000000-7c00100000 r-xp 00000000 fd:05 14 /apex/com.android.art/lib64/libart.so",
            "7d00000000-7d00001000 rw-p 00000000 00:00 0 ",
            "not a line of the file",
        )
        assertEquals(0x400000L, CompiledCode.bytes(maps, dir))
    }

    @Test
    fun `an install with no compiled code has none`() {
        val dir = "/data/app/~~a==/com.steeben.dial-b=="
        val maps = sequenceOf(
            "7a00000000-7a00100000 r--p 00000000 fd:05 11 $dir/oat/arm64/base.odex",
            "7a00500000-7a00600000 r--p 00000000 fd:05 12 $dir/oat/arm64/base.vdex",
            "7a00600000-7a00900000 r-xp 00000000 fd:05 12 $dir/lib/arm64/libapp.so",
        )
        assertEquals(0L, CompiledCode.bytes(maps, dir))
    }
}
