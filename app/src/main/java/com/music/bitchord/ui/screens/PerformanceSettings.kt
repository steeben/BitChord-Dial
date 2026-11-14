package com.music.bitchord.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
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

/**
 * Performance's "Keep Home loaded": Home stays built while another page is
 * up, for the memory its pictures take. See [KeptPage].
 */
@Composable
internal fun KeepHomeRow(title: String) {
    val on by AppSettings.keepHome.collectAsStateWithLifecycle()
    SettingsRow(
        icon = Icons.Rounded.Home,
        title = title,
        subtitle = stringResource(R.string.keep_home_subtitle),
        trailing = { IosSwitch(checked = on, onCheckedChange = AppSettings::setKeepHome) },
        onClick = { AppSettings.setKeepHome(!on) },
    )
}
