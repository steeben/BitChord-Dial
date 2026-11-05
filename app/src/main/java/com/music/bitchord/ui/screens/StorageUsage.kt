package com.music.bitchord.ui.screens

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.canvas.CanvasDownloads
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** What the app keeps on the device, by kind, in bytes. */
data class StorageUsage(
    val audio: Long,
    val artwork: Long,
    val canvases: Long,
    val other: Long,
    /** Free space left on the volume the app's data lives on. */
    val free: Long,
) {
    val total: Long get() = audio + artwork + canvases + other
}

/**
 * The length the bar stands for: the cache limit, or — when the app's use has
 * already gone past it, or there is no limit — what is used plus what is free,
 * so the bar still reads as a share of something real.
 */
internal fun storageBarScale(usage: StorageUsage, limitBytes: Long): Long {
    val unlimited = limitBytes == AppSettings.UNLIMITED_CACHE_LIMIT_BYTES || limitBytes <= 0
    return when {
        unlimited -> (usage.total + usage.free).coerceAtLeast(1L)
        else -> maxOf(limitBytes, usage.total).coerceAtLeast(1L)
    }
}

/** Each kind's share of [scale], in the order the bar draws them. */
internal fun storageBarFractions(usage: StorageUsage, scale: Long): List<Float> =
    listOf(usage.audio, usage.artwork, usage.canvases, usage.other).map {
        (it.toDouble() / scale).toFloat().coerceIn(0f, 1f)
    }

/** Walks the app's private storage and files every byte under one kind. */
suspend fun measureStorageUsage(context: Context): StorageUsage = withContext(Dispatchers.IO) {
    val files = context.filesDir
    val cache = context.cacheDir
    val noBackup = context.noBackupFilesDir
    val audioDirs = listOf(
        File(noBackup, "audio_cache"),
        File(cache, "audio"),
        File(files, "downloads"),
        File(files, "offline-hls"),
    )
    val artworkDirs = listOf(
        File(cache, "image_cache"),
        File(cache, "remote_art"),
        File(files, "download-artwork"),
        File(files, "playlist_covers"),
        File(files, "album_art"),
    )
    val canvasDirs = listOf(
        File(cache, "canvas"),
        File(noBackup, CanvasDownloads.DIR),
    )
    val audio = audioDirs.sumOf(::sizeOf)
    val artwork = artworkDirs.sumOf(::sizeOf)
    val canvases = canvasDirs.sumOf(::sizeOf)
    val everything = sizeOf(context.dataDir) + (context.externalCacheDir?.let(::sizeOf) ?: 0L)
    StorageUsage(
        audio = audio,
        artwork = artwork,
        canvases = canvases,
        other = (everything - audio - artwork - canvases).coerceAtLeast(0L),
        free = files.usableSpace,
    )
}

private fun sizeOf(file: File): Long = runCatching {
    if (!file.exists()) 0L else file.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

private val AudioColor = Color(0xFF0A84FF)
private val ArtworkColor = Color(0xFF30D158)
private val CanvasColor = Color(0xFFFF9F0A)
private val OtherColor = Color(0xFFBF5AF2)

/**
 * The bar at the foot of Storage: what the app is using, coloured by kind and
 * drawn against the cache limit, with each kind's size underneath.
 */
@Composable
internal fun StorageUsageBar(limitBytes: Long, refreshKey: Any? = null) {
    val context = LocalContext.current
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    LaunchedEffect(limitBytes, refreshKey) {
        // Measured again now and then while it's on screen: a song finishing
        // caching or a download landing moves it.
        while (true) {
            usage = measureStorageUsage(context)
            delay(REFRESH_MS)
        }
    }
    val unlimited = limitBytes == AppSettings.UNLIMITED_CACHE_LIMIT_BYTES
    val format = { bytes: Long -> Formatter.formatShortFileSize(context, bytes) }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_INSET, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.storage_usage),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = usage?.let { format(it.total) } ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        val current = usage
        val fractions = current?.let { storageBarFractions(it, storageBarScale(it, limitBytes)) }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(track),
        ) {
            if (fractions == null) return@Canvas
            var x = 0f
            listOf(AudioColor, ArtworkColor, CanvasColor, OtherColor).zip(fractions).forEach { (color, fraction) ->
                val width = (size.width * fraction).coerceAtMost(size.width - x)
                if (width <= 0f) return@forEach
                drawRect(color, topLeft = Offset(x, 0f), size = Size(width, size.height))
                x += width
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                current == null -> stringResource(R.string.storage_usage_measuring)
                unlimited -> stringResource(R.string.storage_usage_unlimited, format(current.total))
                else -> stringResource(R.string.storage_usage_of_limit, format(current.total), format(limitBytes))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (current != null) {
            Spacer(Modifier.height(10.dp))
            val entries = listOf(
                Triple(AudioColor, stringResource(R.string.storage_audio), current.audio),
                Triple(ArtworkColor, stringResource(R.string.storage_artwork), current.artwork),
                Triple(CanvasColor, stringResource(R.string.storage_canvases), current.canvases),
                Triple(OtherColor, stringResource(R.string.storage_other), current.other),
            )
            entries.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pair.forEach { (color, label, bytes) ->
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = format(bytes),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val REFRESH_MS = 15_000L
