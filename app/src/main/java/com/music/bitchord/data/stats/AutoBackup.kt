package com.music.bitchord.data.stats

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * Writes a [Backup] into a folder the user picked, daily, weekly or monthly.
 *
 * Its own preference file rather than AppSettings: the folder grant belongs to
 * this device, so it must not travel in a backup or be replaced by a restore.
 *
 * WorkManager runs a check every few hours, and the check writes a backup only
 * once the chosen interval has passed since the last one. A monthly periodic
 * job would slip by its flex window and lose its place across reboots; a
 * frequent check against a stored time does neither.
 */
object AutoBackup {

    enum class Frequency(val millis: Long) {
        OFF(0L),
        DAILY(TimeUnit.DAYS.toMillis(1)),
        WEEKLY(TimeUnit.DAYS.toMillis(7)),
        MONTHLY(TimeUnit.DAYS.toMillis(30)),
    }

    private lateinit var prefs: SharedPreferences

    private val _folder = MutableStateFlow("")
    /** The picked folder's tree URI, or empty. */
    val folder: StateFlow<String> = _folder.asStateFlow()

    private val _frequency = MutableStateFlow(Frequency.OFF)
    val frequency: StateFlow<Frequency> = _frequency.asStateFlow()

    private val _lastBackupAt = MutableStateFlow(0L)
    /** When the last automatic backup was written, or 0. */
    val lastBackupAt: StateFlow<Long> = _lastBackupAt.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    /** Why the last attempt failed, cleared by the next one that works. */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _folder.value = prefs.getString(KEY_FOLDER, "").orEmpty()
        _frequency.value = prefs.getString(KEY_FREQUENCY, null)
            ?.let { name -> Frequency.entries.firstOrNull { it.name == name } }
            ?: Frequency.OFF
        _lastBackupAt.value = prefs.getLong(KEY_LAST, 0L)
        _lastError.value = prefs.getString(KEY_ERROR, null)
        schedule(context, replace = false)
    }

    fun setFolder(context: Context, uri: Uri) {
        val old = _folder.value
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        if (old.isNotEmpty() && old != uri.toString()) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(old),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        _folder.value = uri.toString()
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
        // A new folder with a schedule already on: start it off with a backup now.
        if (_frequency.value != Frequency.OFF) {
            schedule(context, replace = true)
            backUpNow(context)
        }
    }

    fun setFrequency(context: Context, value: Frequency) {
        val wasOff = _frequency.value == Frequency.OFF
        _frequency.value = value
        prefs.edit().putString(KEY_FREQUENCY, value.name).apply()
        schedule(context, replace = true)
        if (wasOff && value != Frequency.OFF && _folder.value.isNotEmpty()) backUpNow(context)
    }

    /** Queues a backup right away, whatever the schedule says. */
    fun backUpNow(context: Context) {
        if (_folder.value.isEmpty()) return
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInputData(androidx.work.workDataOf(KEY_FORCE to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    private fun schedule(context: Context, replace: Boolean) {
        val work = WorkManager.getInstance(context)
        if (_frequency.value == Frequency.OFF || _folder.value.isEmpty()) {
            work.cancelUniqueWork(PERIODIC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(CHECK_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Writes a backup if one is due (or [force]d), and prunes old automatic ones. */
    internal suspend fun run(context: Context, force: Boolean) {
        if (!this::prefs.isInitialized) init(context)
        val folder = _folder.value.takeIf { it.isNotEmpty() } ?: return
        val frequency = _frequency.value
        if (!force && !isDue(frequency, _lastBackupAt.value, System.currentTimeMillis())) return
        val tree = Uri.parse(folder)
        Backup.exportToFolder(context, tree).fold(
            onSuccess = {
                val now = System.currentTimeMillis()
                _lastBackupAt.value = now
                _lastError.value = null
                prefs.edit().putLong(KEY_LAST, now).remove(KEY_ERROR).apply()
                runCatching { prune(context, tree) }
            },
            onFailure = {
                val message = it.message ?: it.javaClass.simpleName
                _lastError.value = message
                prefs.edit().putString(KEY_ERROR, message).apply()
            },
        )
    }

    /**
     * Whether a backup is due. An hour of slack so a check that lands just short
     * of the interval doesn't push the backup back a whole check period.
     */
    internal fun isDue(frequency: Frequency, last: Long, now: Long): Boolean =
        frequency != Frequency.OFF && now - last >= frequency.millis - SLACK_MILLIS

    /** Keeps the newest [KEEP] automatic backups in the folder; never touches other files. */
    private fun prune(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val ours = mutableListOf<Pair<String, String>>() // name to document id
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (name.startsWith(Backup.AUTO_PREFIX) && name.endsWith(".json")) ours += name to id
            }
        }
        // The names are dated yyyy-MM-dd-HHmm, so they sort oldest first.
        ours.sortedByDescending { it.first }.drop(KEEP).forEach { (_, id) ->
            runCatching {
                DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))
            }
        }
    }

    /** How many automatic backups are kept in the folder. */
    const val KEEP = 10

    internal const val KEY_FORCE = "force"
    private const val PREFS = "bitchord_auto_backup"
    private const val KEY_FOLDER = "folder"
    private const val KEY_FREQUENCY = "frequency"
    private const val KEY_LAST = "last_backup_at"
    private const val KEY_ERROR = "last_error"
    private const val PERIODIC_WORK = "auto_backup"
    private const val ONE_OFF_WORK = "auto_backup_now"
    private const val CHECK_HOURS = 6L
    private val SLACK_MILLIS = TimeUnit.HOURS.toMillis(1)
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AutoBackup.run(applicationContext, inputData.getBoolean(AutoBackup.KEY_FORCE, false))
        // Never a retry: a failure is shown in settings, and the next check tries again.
        return Result.success()
    }
}
