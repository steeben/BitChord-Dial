package com.music.bitchord.data.migration

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.music.bitchord.data.AppUpdateChecker
import com.music.bitchord.data.Http
import com.music.bitchord.data.stats.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File
import kotlin.system.exitProcess

/**
 * Moving from the old app (com.steeben.bitchord) to Dial (com.steeben.dial).
 *
 * A new package is a new app to Android: it installs beside the old one, starts
 * empty, and can't read the old one's files. So the old app has to hand its
 * data over, and an install of 1.4.1 or earlier doesn't know how. Releases
 * therefore carry a *bridge* APK too, this same app built under the old
 * package ([ReleaseAssets]): it installs over the old app like any update,
 * keeping its data, and serves that data to Dial through [HandoverProvider].
 *
 * The routes, all ending in Dial holding everything:
 *  - Old app updates (its update checker takes the bridge), then offers Dial
 *    ([Stage.GetDial]), Dial opens, finds the bridge and takes the data
 *    ([Stage.Import]), then offers to uninstall the old one ([Stage.UninstallOld]).
 *  - Dial is installed some other way while the old app is still 1.4.1: Dial
 *    fetches the bridge and installs it over the old app first ([Stage.UpdateOld]).
 *
 * Android allows neither a silent install nor a silent uninstall, so each of
 * those is one confirmation from the system. The hand-over itself is silent.
 */
object AppMove {

    const val OLD_PACKAGE = "com.steeben.bitchord"
    const val NEW_PACKAGE = "com.steeben.dial"

    private val HANDOVER_URI: Uri = Uri.parse("content://$OLD_PACKAGE.handover/state")

    private const val RELEASES_URL = "https://api.github.com/repos/steeben/Dial/releases?per_page=10"

    private const val PREFS = "dial_app_move"
    private const val KEY_MOVED = "moved"
    private const val KEY_KEEP_OLD = "keep_old"

    sealed interface Stage {
        data object None : Stage

        /** Dial: the old app can hand over. */
        data object Import : Stage

        /** Dial: the old app is installed but too old to hand over. */
        data object UpdateOld : Stage

        /** Dial: moved, and the old app is still installed. */
        data object UninstallOld : Stage

        /** Old app: Dial isn't installed yet. */
        data object GetDial : Stage

        /** Old app: Dial is installed; the move happens when it opens. */
        data object OpenDial : Stage
    }

    sealed interface Work {
        data object Idle : Work
        data class Downloading(val fraction: Float) : Work
        data object Moving : Work
        data class Failed(val message: String) : Work
    }

    private val _work = MutableStateFlow<Work>(Work.Idle)
    val work = _work.asStateFlow()

    /** "Not now" lasts until the next launch. */
    @Volatile
    var dismissedThisLaunch = false

    private val json = Json { ignoreUnknownKeys = true }

    fun isOldApp(context: Context): Boolean = context.packageName == OLD_PACKAGE

    fun stage(context: Context): Stage {
        if (dismissedThisLaunch) return Stage.None
        return when (context.packageName) {
            NEW_PACKAGE -> {
                if (!isInstalled(context, OLD_PACKAGE)) return Stage.None
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                when {
                    prefs.getBoolean(KEY_MOVED, false) ->
                        if (prefs.getBoolean(KEY_KEEP_OLD, false)) Stage.None else Stage.UninstallOld
                    canHandOver(context) -> Stage.Import
                    else -> Stage.UpdateOld
                }
            }
            OLD_PACKAGE -> if (isInstalled(context, NEW_PACKAGE)) Stage.OpenDial else Stage.GetDial
            else -> Stage.None
        }
    }

    private fun isInstalled(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    }.getOrDefault(false)

    private fun canHandOver(context: Context): Boolean =
        context.packageManager.resolveContentProvider("$OLD_PACKAGE.handover", 0) != null

    /**
     * Reads the old app's data and makes it this app's, then restarts: sign-ins,
     * downloads and half the stores are read once at start.
     */
    suspend fun importFromOldApp(context: Context) {
        _work.value = Work.Moving
        val result = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(HANDOVER_URI)
                    ?.use { it.readBytes().decodeToString() }
                    ?: error("The old app didn't answer")
            }.mapCatching { text -> Backup.importHandover(context, text).getOrThrow() }
        }
        result.fold(
            onSuccess = {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_MOVED, true).commit()
                restart(context)
            },
            onFailure = { _work.value = Work.Failed(it.message ?: "Couldn't move your data") },
        )
    }

    fun keepOldApp(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_KEEP_OLD, true).apply()
    }

    /** The system's uninstall confirmation for the old app. */
    fun uninstallOldApp(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$OLD_PACKAGE"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openDial(context: Context) {
        context.packageManager.getLaunchIntentForPackage(NEW_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let(context::startActivity)
    }

    /**
     * Downloads and installs Dial (from the old app) or the bridge (from Dial,
     * to bring an old install up to one that can hand over). The newest release
     * that has the APK wins, so the bridge can be dropped from later releases.
     */
    suspend fun downloadAndInstall(context: Context, bridge: Boolean) {
        _work.value = Work.Downloading(0f)
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val url = findApk(bridge) ?: error("No download found on the releases page")
                download(context, url)
            }
        }
        file.fold(
            onSuccess = {
                _work.value = Work.Idle
                AppUpdateChecker.installApk(context, it)
            },
            onFailure = { _work.value = Work.Failed(it.message ?: "Download failed") },
        )
    }

    fun resetWork() {
        _work.value = Work.Idle
    }

    private fun findApk(bridge: Boolean): String? {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = Http.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Couldn't reach GitHub: HTTP ${response.code}" }
            response.body?.string()
        } ?: return null
        val releases = json.parseToJsonElement(body) as? JsonArray ?: return null
        return releases.asSequence()
            .mapNotNull { it as? JsonObject }
            .filter { it["draft"]?.jsonPrimitive?.contentOrNull != "true" && it["prerelease"]?.jsonPrimitive?.contentOrNull != "true" }
            .mapNotNull { release ->
                val assets = release["assets"]?.jsonArray.orEmpty().mapNotNull { asset ->
                    val obj = asset as? JsonObject ?: return@mapNotNull null
                    ReleaseAssets.Asset(
                        name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        url = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        uploaded = obj["state"]?.jsonPrimitive?.contentOrNull == "uploaded",
                    )
                }
                ReleaseAssets.pick(assets, bridge)?.url
            }
            .firstOrNull()
    }

    private fun download(context: Context, url: String): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "app-move.apk")
        Http.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
            val body = response.body ?: error("Empty download")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        read += n
                        total?.let { _work.value = Work.Downloading((read.toFloat() / it).coerceIn(0f, 1f)) }
                    }
                }
            }
        }
        return target
    }

    private fun restart(context: Context) {
        val component = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return
        context.startActivity(Intent.makeRestartActivityTask(component))
        exitProcess(0)
    }
}
