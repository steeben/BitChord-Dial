package com.music.bitchord.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AmbientStyle

/**
 * A row of the ambient background's styles, each a small still of itself in
 * the background's current colour, the chosen one ringed.
 */
@Composable
fun AmbientStylePicker(
    selected: AmbientStyle,
    onSelect: (AmbientStyle) -> Unit,
    modifier: Modifier = Modifier,
    inset: Dp = 0.dp,
) {
    val colour = LocalAmbientAccent.current ?: AmbientAppColor
    val companion = ambientCompanion(colour)
    val page = MaterialTheme.colorScheme.background
    val tileShape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = inset, end = inset),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AmbientStyle.entries.forEach { style ->
            val chosen = style == selected
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { if (!chosen) onSelect(style) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(width = 68.dp, height = 108.dp)
                        .then(
                            if (chosen) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, tileShape)
                            } else {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.outline, tileShape)
                            },
                        )
                        .padding(3.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(page)
                        .drawBehind {
                            drawAmbient(style, colour, companion, PREVIEW_CLOCK_MS, playing = false)
                        },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(style.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

private fun AmbientStyle.labelRes(): Int = when (this) {
    AmbientStyle.BLOBS -> R.string.ambient_style_blobs
    AmbientStyle.RIPPLE_DIAL -> R.string.ambient_style_ripple_dial
    AmbientStyle.BIT_GRID -> R.string.ambient_style_bit_grid
    AmbientStyle.WAVEFORM_SILK -> R.string.ambient_style_waveform_silk
    AmbientStyle.VINYL_ORBIT -> R.string.ambient_style_vinyl_orbit
}

/** The moment the previews are frozen at: each style partway into a loop. */
private const val PREVIEW_CLOCK_MS = 3_000L
