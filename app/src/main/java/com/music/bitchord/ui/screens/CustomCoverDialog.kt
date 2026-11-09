package com.music.bitchord.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.music.bitchord.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Custom cover" for a playlist: a card in the middle of the screen offering
 * a cover generated from the playlist's songs, made like its page's animated
 * backdrop, with a choice of ground colour (the backdrop's own first) and a
 * preview that follows it, or an image picked off the device.
 */
@Composable
internal fun CustomCoverDialog(
    browseId: String,
    onChooseFromDevice: () -> Unit,
    /** The generated cover was saved (true) or couldn't be (false). */
    onSaved: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scope = rememberCoroutineScope()
    // Null while fetching; the outcome once it lands (a draft, or nothing to make one from).
    val draft by produceState<Result<CollageCoverDraft?>?>(null, browseId) {
        value = Result.success(prepareCollageCover(context, browseId, dark))
    }
    val ready = draft?.getOrNull()
    // Null is "Automatic": whatever the backdrop would be.
    var chosen by remember(browseId) { mutableStateOf<Color?>(null) }
    var saving by remember { mutableStateOf(false) }
    val background = chosen ?: ready?.automatic
    val preview by produceState<ImageBitmap?>(null, ready, background) {
        val d = ready ?: return@produceState
        val ground = background ?: return@produceState
        value = withContext(Dispatchers.Default) { renderCollageCover(d, ground, PREVIEW_PX).asImageBitmap() }
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.custom_cover),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(background ?: MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val shown = preview
                    when {
                        shown != null -> Image(
                            bitmap = shown,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                        draft != null && ready == null -> Text(
                            text = stringResource(R.string.custom_cover_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                        else -> CircularProgressIndicator()
                    }
                }
                if (ready != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.custom_cover_background),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val auto = stringResource(R.string.custom_cover_automatic)
                        Swatch(ready.automatic, selected = chosen == null, label = auto, automatic = true) { chosen = null }
                        ready.choices.forEach { color ->
                            Swatch(color, selected = chosen == color, label = null) { chosen = color }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        val d = ready ?: return@Button
                        val ground = background ?: return@Button
                        saving = true
                        scope.launch { onSaved(saveCollageCover(context, d, ground)) }
                    },
                    enabled = ready != null && !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.custom_cover_generate))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onChooseFromDevice,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.custom_cover_from_device))
                }
            }
        }
    }
}

/** One ground colour to pick; "Automatic" carries a sparkle. */
@Composable
private fun Swatch(color: Color, selected: Boolean, label: String?, automatic: Boolean = false, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(40.dp)
            .then(if (selected) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick)
            .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (automatic) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private const val PREVIEW_PX = 540
