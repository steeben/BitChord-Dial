package com.music.bitchord.ui.performance

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import com.music.bitchord.data.TrackLog

/**
 * A thread's stack, cut down to what names the work it is doing.
 *
 * A stack taken in the middle of a slow frame is hundreds of lines of the
 * framework and of Compose with a handful of the app's own among them. Those
 * few are the ones that say which page, row or request the time went to.
 */
object StackDigest {
    private const val OWN = "com.music.bitchord."

    /** Where the app's thread stands while it waits for its next message: it is not busy. */
    private const val IDLE = "nativePollOnce"

    /**
     * One line for [stack], innermost first: what the thread was in, then the
     * app's own frames that led there. Null for a thread that is waiting.
     */
    fun of(stack: Array<StackTraceElement>, limit: Int = 6): String? {
        val top = stack.firstOrNull() ?: return null
        if (top.methodName == IDLE) return null
        val own = ArrayList<String>(limit)
        for (frame in stack) {
            if (!frame.className.startsWith(OWN)) continue
            val name = name(frame)
            if (own.lastOrNull() == name) continue
            own += name
            if (own.size == limit) break
        }
        val inside = if (top.className.startsWith(OWN)) "" else "in ${name(top)}"
        return when {
            own.isEmpty() ->
                // None of the app's own: the few on top have to do.
                stack.take(4).joinToString(" < ", prefix = "in ") { name(it) }
            inside.isEmpty() -> own.joinToString(" < ")
            else -> "$inside, from ${own.joinToString(" < ")}"
        }
    }

    private fun name(frame: StackTraceElement): String {
        val line = if (frame.lineNumber > 0) ":${frame.lineNumber}" else ""
        return "${frame.className.removePrefix(OWN)}.${frame.methodName}$line"
    }
}

/**
 * Says what the app's thread was doing each time it was busy for longer than
 * a few frames, while a log session is recording.
 *
 * The frame log says that a frame was slow and how its time was split. It
 * cannot say which code ran: by the time a frame's times come in, the work is
 * over. This asks the app's thread to answer every few milliseconds, and when
 * an answer is late, looks at what the thread is in the middle of.
 */
internal object AppThreadWatch {
    private const val TAG = "Frames"

    /** How often the app's thread is asked to answer. */
    private const val ASK_MS = 8L

    /** No answer for this long: the thread's stack is looked at, and again each time this much more has gone by. */
    private const val LOOK_AFTER_MS = 16L
    private const val MOST_LOOKS = 3

    /** A wait of at least this long is written. */
    private const val WRITE_FROM_MS = 28L

    /** With no frame drawn for this long there is nothing to stutter, and the thread is left alone. */
    private const val STILL_MS = 2_000L
    private const val STILL_SLEEP_MS = 250L

    /** When the window last drew a frame. Set by [FrameLog]. */
    @Volatile
    var lastFrameAt = 0L

    @Volatile
    private var watcher: Thread? = null

    fun start() {
        if (watcher != null) return
        lastFrameAt = SystemClock.uptimeMillis()
        watcher = Thread(::watch, "AppThreadWatch").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        watcher?.interrupt()
        watcher = null
    }

    @Volatile
    private var answeredAt = 0L
    private val answer = Runnable { answeredAt = SystemClock.uptimeMillis() }

    private fun watch() {
        val looper = Looper.getMainLooper()
        val appThread = looper.thread
        val handler = Handler(looper)
        val looks = ArrayList<String>(MOST_LOOKS)
        try {
            while (watcher === Thread.currentThread()) {
                if (SystemClock.uptimeMillis() - lastFrameAt > STILL_MS) {
                    Thread.sleep(STILL_SLEEP_MS)
                    continue
                }
                answeredAt = 0L
                looks.clear()
                val asked = SystemClock.uptimeMillis()
                // Asynchronous, so that it is not held back behind a frame
                // that is only waiting for the display: a wait then means the
                // thread was running something.
                handler.sendMessage(Message.obtain(handler, answer).apply { isAsynchronous = true })
                var nextLook = LOOK_AFTER_MS
                while (answeredAt == 0L) {
                    Thread.sleep(ASK_MS)
                    val waited = SystemClock.uptimeMillis() - asked
                    if (answeredAt == 0L && waited >= nextLook && looks.size < MOST_LOOKS) {
                        nextLook = waited + LOOK_AFTER_MS
                        StackDigest.of(appThread.stackTrace)?.let { looks += "${waited}ms in, $it" }
                    }
                }
                val busy = answeredAt - asked
                if (busy >= WRITE_FROM_MS && looks.isNotEmpty()) {
                    TrackLog.toSession(
                        'I',
                        TAG,
                        "app thread busy ${busy}ms on ${FrameLog.page.ifEmpty { "launch" }}: ${looks.joinToString("; ")}",
                    )
                }
            }
        } catch (_: InterruptedException) {
            // Stopped.
        }
    }
}

/**
 * How much of the app's own code Android has compiled ahead of time.
 *
 * A build installed from its file starts with none: the code is interpreted,
 * and compiled piece by piece as it is used, until Android compiles it in the
 * background (usually overnight, on charge). A log taken in between shows
 * slow frames that a day-old install does not have, so the log says which of
 * the two it was taken on.
 */
internal object CompiledCode {
    /**
     * The bytes of compiled code mapped in from under [codeDir], given the
     * lines of `/proc/self/maps`.
     */
    fun bytes(maps: Sequence<String>, codeDir: String): Long {
        var total = 0L
        for (line in maps) {
            val path = line.substringAfterLast(' ', "")
            if (!path.startsWith(codeDir) || !(path.endsWith(".odex") || path.endsWith(".oat"))) continue
            val parts = line.split(' ', limit = 3)
            if (parts.size < 2 || 'x' !in parts[1]) continue
            val range = parts[0].split('-')
            val from = range.getOrNull(0)?.toLongOrNull(16) ?: continue
            val to = range.getOrNull(1)?.toLongOrNull(16) ?: continue
            if (to > from) total += to - from
        }
        return total
    }

    /** One line for the log, or null when it cannot be told. */
    fun line(apkPath: String?): String? {
        val codeDir = apkPath?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() } ?: return null
        val bytes = runCatching {
            java.io.File("/proc/self/maps").useLines { bytes(it, codeDir) }
        }.getOrNull() ?: return null
        return if (bytes > 0L) {
            "app code compiled ahead of time: ${"%.1f".format(java.util.Locale.ROOT, bytes / 1_048_576.0)} MB"
        } else {
            "app code not compiled ahead of time: this install runs it interpreted, and compiles it as it is used"
        }
    }
}
