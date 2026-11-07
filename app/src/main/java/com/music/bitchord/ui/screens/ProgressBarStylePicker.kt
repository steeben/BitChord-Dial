package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ProgressBarStyle
import com.music.bitchord.ui.player.ThinSlider
import com.music.bitchord.ui.player.WaveAccents

/**
 * The player's seek bar styles side by side, each a live preview of the real bar on a small
 * dark player-like card, laid out like the app icon picker. Tapping a card picks that style.
 */
@Composable
internal fun ProgressBarStylePicker(
    selected: ProgressBarStyle,
    onSelect: (ProgressBarStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary
    // The previews have no song to take a colour from, so they wear the app's own accent.
    val accents = remember(primary) {
        WaveAccents(
            light = lerp(primary, Color.White, 0.45f),
            accent = primary,
            dark = lerp(primary, Color.Black, 0.4f),
        )
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProgressBarStyle.entries.forEach { style ->
            val isSelected = style == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { if (!isSelected) onSelect(style) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PREVIEW_BACKDROP)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ThinSlider(
                        value = PREVIEW_PROGRESS,
                        onValueChange = {},
                        waves = accents.takeIf { style == ProgressBarStyle.WAVE },
                        glow = accents.takeIf { style == ProgressBarStyle.GLOW },
                        wavesPlaying = true,
                        animateWaves = !reduceAnimation,
                    )
                    // Over the bar, so a tap picks the style instead of scrubbing the preview.
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { if (!isSelected) onSelect(style) },
                    )
                }
                Text(
                    text = stringResource(
                        when (style) {
                            ProgressBarStyle.GLOW -> R.string.progress_bar_glow
                            ProgressBarStyle.WAVE -> R.string.progress_bar_wave
                            ProgressBarStyle.PLAIN -> R.string.progress_bar_plain
                        },
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** A dark card, like the player behind the bar, whatever the app's theme. */
private val PREVIEW_BACKDROP = Color(0xFF1E1A22)
private const val PREVIEW_PROGRESS = 0.62f
