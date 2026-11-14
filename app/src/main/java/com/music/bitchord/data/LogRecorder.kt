package com.music.bitchord.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.music.bitchord.BuildConfig
import com.music.bitchord.data.innertube.InnerTubeXResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings' "Enable logging": the phone's side of [TrackLog]'s session.
 *
 * On until switched off again. The switch is kept, so a session carries on
 * over a launch of the app: the first seconds of a launch are what a report
 * of a slow or stuttering start is about, and they are over long before
 * anyone can reach Settings to turn a log on. [resumeAtLaunch] picks the
 * session up again before anything else in the app runs. The row in Settings
 * shows that it is on, and a carried-on log starts over once it has grown
 * past [MAX_CARRIED_BYTES], so one that was forgotten does not fill the phone.
 *
 * The file is written under `cache/logs/`, which the FileProvider shares (see
 * `res/xml/file_paths.xml`), and the previous session's file is cleared when a
 * new one starts.
 */
object LogRecorder {

    private const val FOLDER = "logs"
    private const val PREFS = "dial_logging"
    private const val KEY_ENABLED = "enabled"

    /** Past this, a session carried over a launch starts a new file. */
    private const val MAX_CARRIED_BYTES = 16L * 1024 * 1024

    val recording: StateFlow<Boolean> get() = TrackLog.recording

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun start(context: Context) {
        val folder = File(context.cacheDir, FOLDER)
        folder.listFiles()?.forEach { it.delete() }
        begin(newFile(folder), append = false)
        remember(context, true)
        TrackLog.i("Log", "logging enabled")
    }

    /**
     * Carries the session on if logging was left on when the app last ran.
     * Called first thing at app start, so the log has the launch itself.
     */
    fun resumeAtLaunch(context: Context) {
        val enabled = runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
        }.getOrDefault(false)
        if (!enabled) return
        runCatching {
            val folder = File(context.cacheDir, FOLDER)
            val last = folder.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
            val carryOn = last != null && last.length() < MAX_CARRIED_BYTES
            if (!carryOn) folder.listFiles()?.forEach { it.delete() }
            begin(if (carryOn) last!! else newFile(folder), append = carryOn)
            TrackLog.i("Log", "app started, logging still enabled")
        }
    }

    private fun newFile(folder: File): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return File(folder, "bitchord-dial-log-$stamp.txt")
    }

    private fun begin(file: File, append: Boolean) {
        TrackLog.startSession(
            file,
            header = listOf(
                "Dial log",
                "build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})",
                "device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            ),
            append = append,
        )
        // The resolver's own step-by-step detail is off outside debug builds;
        // a session someone switched on to reproduce a problem wants it.
        InnerTubeXResolver.debugLogging = true
        // A crash is the line most worth having, and it never reaches a log
        // call of its own.
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (previousHandler == null) {
            previousHandler = current
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                TrackLog.toSession('E', "Crash", "uncaught on ${thread.name}", error)
                previousHandler?.uncaughtException(thread, error)
            }
        }
    }

    /** Written at once: the app may be closed straight after the switch is turned. */
    private fun remember(context: Context, enabled: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).commit()
        }
    }

    /** Ends the session and returns its file, or null if nothing was running. */
    fun stop(context: Context): File? {
        remember(context, false)
        TrackLog.i("Log", "logging disabled")
        InnerTubeXResolver.debugLogging = BuildConfig.DEBUG
        return TrackLog.stopSession()?.takeIf { it.exists() }
    }

    /** A share sheet for [file], through the app's FileProvider. */
    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Copies [file] to [target], a document the listener picked. */
    suspend fun saveTo(resolver: ContentResolver, file: File, target: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
        }.getOrDefault(false)
    }
}
