package com.music.bitchord.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.IosSwitch

/**
 * Settings, Performance: the switch that stops every haptic in the app. Off by
 * default, so the haptics stay on until it is switched on.
 */
@Composable
internal fun DisableHapticsRow(title: String) {
    val disableHaptics by AppSettings.disableHaptics.collectAsStateWithLifecycle()
    SettingsRow(
        icon = Icons.Rounded.Vibration,
        title = title,
        subtitle = stringResource(R.string.disable_haptics_subtitle),
        trailing = {
            IosSwitch(
                checked = disableHaptics,
                onCheckedChange = AppSettings::setDisableHaptics,
            )
        },
        onClick = { AppSettings.setDisableHaptics(!disableHaptics) },
    )
}
