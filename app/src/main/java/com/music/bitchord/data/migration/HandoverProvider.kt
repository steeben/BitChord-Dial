package com.music.bitchord.data.migration

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import com.music.bitchord.data.stats.Backup
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The old app's half of moving to Dial: hands its whole state, sign-ins
 * included, to the new app as one JSON file.
 *
 * Exported, but answers only an app signed with this app's own key — checked
 * per call against the caller's uid rather than through a signature permission,
 * because a permission only reaches an app that was installed after it was
 * defined, and here either app can be installed first. Only the old package
 * (com.steeben.bitchord) serves anything; see [AppMove].
 *
 * The file goes through a descriptor, not a Bundle: a few years of listening
 * plus playlist covers is far past what a binder transaction can carry.
 */
class HandoverProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException()
        if (context.packageName != AppMove.OLD_PACKAGE) throw FileNotFoundException("Nothing to hand over")
        if (context.packageManager.checkSignatures(Process.myUid(), Binder.getCallingUid()) !=
            PackageManager.SIGNATURE_MATCH
        ) {
            throw SecurityException("Only Dial can read this")
        }
        // Providers start before Application.onCreate; the stores it reads
        // aren't open until that has run.
        if (!ready.await(20, TimeUnit.SECONDS)) throw FileNotFoundException("Still starting up")

        val text = runBlocking { Backup.encodeHandover(context) }
        val file = File(context.cacheDir, "handover.json")
        file.writeText(text)
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        // The open descriptor keeps the bytes; nothing with sign-ins in it stays on disk.
        file.delete()
        return descriptor
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String = "application/json"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private val ready = CountDownLatch(1)

        /** Called at the end of Application.onCreate. */
        fun markReady() = ready.countDown()
    }
}
