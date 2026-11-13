package com.music.bitchord.data

import android.content.Context
import android.os.SystemClock
import android.view.Choreographer
import android.webkit.CookieManager
import android.webkit.WebView

/**
 * Starts the WebView engine while the splash is still up.
 *
 * Every launch makes a WebView within its first seconds: the one that reads
 * Spotify's token, for a listener signed in there, and the one behind
 * YouTube's PO token for everyone. The first WebView of a process starts the
 * whole engine, which can only be done on the app's thread and takes 50 to 90
 * ms there. Left to whichever asked first, that fell just as Home came in from
 * under the splash, and was a stop in plain sight.
 *
 * The first frame is held back for the splash's animation in any case (see
 * `LaunchGate`), and the app's thread has nothing to do for most of that. The
 * engine is started then, where the time is not seen, and the WebViews made
 * later find it running.
 */
object WebViewWarmUp {
    private var asked = false

    /**
     * Call from the activity's `onCreate`. The engine is started once the
     * first layout is done, so the first composition and the requests it
     * sends are not kept waiting for it.
     */
    fun behindSplash(context: Context) {
        if (asked) return
        asked = true
        val app = context.applicationContext
        // The frame after the first one: the first frame's layout is the
        // first composition. A frame callback and not a posted message,
        // because a held first frame keeps posted messages waiting behind it.
        val choreographer = Choreographer.getInstance()
        choreographer.postFrameCallback {
            choreographer.postFrameCallback { start(app) }
        }
    }

    private fun start(app: Context) {
        val began = SystemClock.uptimeMillis()
        runCatching {
            CookieManager.getInstance()
            WebView(app).destroy()
        }.onSuccess {
            TrackLog.toSession('I', TAG, "WebView engine started in ${SystemClock.uptimeMillis() - began}ms, behind the splash")
        }.onFailure {
            // A device with no WebView, or one in the middle of an update:
            // whatever needs one later says so itself.
            TrackLog.toSession('W', TAG, "WebView engine not started: ${it.message}")
        }
    }

    private const val TAG = "Launch"
}
