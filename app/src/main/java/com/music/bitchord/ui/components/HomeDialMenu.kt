package com.music.bitchord.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.ui.screens.GlassMenuPanel
import com.music.bitchord.ui.screens.MenuHeading
import com.music.bitchord.ui.screens.SegmentedControl
import dev.chrisbanes.haze.HazeState

/**
 * What Home's "…" button opens, on the same glass as the Library's menu,
 * grown out of the top bar's right end: the dial's own settings — its grid,
 * the artists' dial, the die, the tile names, the pin badges, how the tiles
 * are edged — applied as they are touched.
 */
@Composable
fun HomeDialMenu(
    visible: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
) {
    val rows by CustomizationSettings.speedDialRows.collectAsStateWithLifecycle()
    val artistDial by CustomizationSettings.artistDial.collectAsStateWithLifecycle()
    val feelingLucky by CustomizationSettings.feelingLucky.collectAsStateWithLifecycle()
    val pinIcon by CustomizationSettings.dialPinIcon.collectAsStateWithLifecycle()
    val labels by CustomizationSettings.dialLabels.collectAsStateWithLifecycle()
    val tileStyle by CustomizationSettings.dialTileStyle.collectAsStateWithLifecycle()
    GlassMenuPanel(visible = visible, hazeState = hazeState, onDismiss = onDismiss) {
        Column(Modifier.widthIn(min = 280.dp, max = 340.dp)) {
            MenuHeading(stringResource(R.string.speed_dial_grid))
            // On glass of its own, so the track reads as frosted over the panel.
            SegmentedControl(
                options = listOf("3 × 3", "3 × 4"),
                selectedIndex = rows - 3,
                onSelect = { CustomizationSettings.setSpeedDialRows(it + 3) },
                glass = true,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            )
            DialSwitch(stringResource(R.string.artist_dial), artistDial, CustomizationSettings::setArtistDial)
            DialSwitch(stringResource(R.string.feeling_lucky_setting), feelingLucky, CustomizationSettings::setFeelingLucky)
            DialSwitch(stringResource(R.string.dial_labels_default), labels, CustomizationSettings::setDialLabels)
            DialSwitch(stringResource(R.string.dial_pin_icon), pinIcon, CustomizationSettings::setDialPinIcon)
            MenuHeading(stringResource(R.string.dial_tile_style))
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.dial_tile_style_none),
                    stringResource(R.string.dial_tile_style_edge),
                    stringResource(R.string.dial_tile_style_glass),
                ),
                selectedIndex = tileStyle.ordinal,
                onSelect = { CustomizationSettings.setDialTileStyle(DialTileStyle.entries[it]) },
                glass = true,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun DialSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(start = 20.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalContentColor.current,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        IosSwitch(
            checked = checked,
            onCheckedChange = onChange,
        )
    }
}
