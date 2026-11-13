package com.music.bitchord.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.IosSwitch

/**
 * Performance's "Performance mode": one switch over the settings that cost
 * the most to draw. See [AppSettings.setPerformanceMode] for what it sets and
 * what it puts back.
 */
@Composable
internal fun PerformanceModeRow(title: String) {
    val on by AppSettings.performanceMode.collectAsStateWithLifecycle()
    SettingsRow(
        icon = Icons.Rounded.Speed,
        title = title,
        subtitle = stringResource(if (on) R.string.performance_mode_on else R.string.performance_mode_subtitle),
        trailing = { IosSwitch(checked = on, onCheckedChange = AppSettings::setPerformanceMode) },
        onClick = { AppSettings.setPerformanceMode(!on) },
    )
}
