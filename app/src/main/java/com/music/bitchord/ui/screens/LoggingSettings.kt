package com.music.bitchord.ui.screens

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.LogRecorder
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.IosSwitch
import kotlinx.coroutines.launch
import java.io.File

/**
 * Advanced's "Enable logging": a switch that records everything the app logs
 * until it is switched off, then offers the log to share or to save to the
 * phone's storage. It stays on over a launch of the app: see [LogRecorder].
 */
@Composable
internal fun EnableLoggingRow(title: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recording by LogRecorder.recording.collectAsStateWithLifecycle()
    var finished by remember { mutableStateOf<File?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { target ->
        val file = finished ?: return@rememberLauncherForActivityResult
        if (target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = LogRecorder.saveTo(context.contentResolver, file, target)
            Toast.makeText(
                context,
                context.getString(if (saved) R.string.log_saved else R.string.log_save_failed),
                Toast.LENGTH_SHORT,
            ).show()
            if (saved) finished = null
        }
    }
    val toggle: (Boolean) -> Unit = { on ->
        if (on) {
            LogRecorder.start(context)
        } else {
            finished = LogRecorder.stop(context)
        }
    }

    SettingsRow(
        icon = Icons.Rounded.BugReport,
        title = title,
        subtitle = stringResource(if (recording) R.string.enable_logging_running else R.string.enable_logging_subtitle),
        trailing = { IosSwitch(checked = recording, onCheckedChange = { toggle(it) }) },
        onClick = { toggle(!recording) },
    )

    finished?.let { file ->
        AlertDialog(
            onDismissRequest = { finished = null },
            title = { Text(stringResource(R.string.log_ready_title)) },
            text = {
                Text(stringResource(R.string.log_ready_message, Formatter.formatShortFileSize(context, file.length())))
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(LogRecorder.shareIntent(context, file))
                    finished = null
                }) { Text(stringResource(R.string.share_log)) }
            },
            dismissButton = {
                TextButton(onClick = { saveLauncher.launch(file.name) }) {
                    Text(stringResource(R.string.save_log))
                }
            },
        )
    }
}

/** Storage's "Download canvases": save each downloaded song's Canvas with it. */
@Composable
internal fun DownloadCanvasesRow(title: String) {
    val on by AppSettings.downloadCanvases.collectAsStateWithLifecycle()
    SettingsRow(
        icon = Icons.Rounded.Movie,
        title = title,
        subtitle = stringResource(R.string.download_canvases_subtitle),
        trailing = { IosSwitch(checked = on, onCheckedChange = AppSettings::setDownloadCanvases) },
        onClick = { AppSettings.setDownloadCanvases(!on) },
    )
}
