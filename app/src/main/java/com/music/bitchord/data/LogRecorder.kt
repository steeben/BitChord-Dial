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
 * On until switched off again or until the process ends — nothing about it is
 * persisted, so a log never quietly keeps running across launches. The file is
 * written under `cache/logs/`, which the FileProvider shares (see
 * `res/xml/file_paths.xml`), and the previous session's file is cleared when a
 * new one starts.
 */
object LogRecorder {

    private const val FOLDER = "logs"

    val recording: StateFlow<Boolean> get() = TrackLog.recording

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    fun start(context: Context) {
        val folder = File(context.cacheDir, FOLDER)
        folder.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(folder, "bitchord-dial-log-$stamp.txt")
        TrackLog.startSession(
            file,
            header = listOf(
                "Dial log",
                "build: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})",
                "device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            ),
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
        TrackLog.i("Log", "logging enabled")
    }

    /** Ends the session and returns its file, or null if nothing was running. */
    fun stop(): File? {
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
