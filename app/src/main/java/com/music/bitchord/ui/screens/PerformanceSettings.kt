package com.music.bitchord.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.CustomizationSettings
import com.music.bitchord.ui.components.IosSwitch
import com.music.bitchord.ui.icons.BitChordIcons

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
 * Performance's "Static background colours": the ambient background keeps its
 * colours and stops drifting. It does nothing while the ambient background is
 * off, or while Reduce animations already holds it still.
 */
@Composable
internal fun StaticBackgroundRow(title: String) {
    val on by CustomizationSettings.ambientStatic.collectAsStateWithLifecycle()
    SettingsRow(
        icon = Icons.Rounded.BubbleChart,
        title = title,
        subtitle = stringResource(R.string.static_background_subtitle),
        trailing = { IosSwitch(checked = on, onCheckedChange = CustomizationSettings::setAmbientStatic) },
        onClick = { CustomizationSettings.setAmbientStatic(!on) },
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

/**
 * Performance's "High performance mode": asks the display for a higher
 * refresh rate, [requestedHz] while it is on. The switch does that and no
 * more, whatever the app style is.
 *
 * Android can hold the display's rate down for an app whose battery use it
 * limits. A window used to say so before the switch would turn on, to every
 * person, each time. Now the app looks for itself: with the switch on and
 * battery use not set to Unrestricted, the description says what to set, and
 * a tap on it opens the app's page in Android's settings. The look is taken
 * again each time the app comes back to the front, which is when that page
 * has just been left.
 */
@Composable
internal fun HighPerformanceRow(title: String, requestedHz: Int) {
    val on by AppSettings.highPerformanceMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(batteryUnrestricted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { unrestricted = batteryUnrestricted(context) }
    val warn = on && !unrestricted
    SettingsRow(
        icon = BitChordIcons.Performance,
        title = title,
        subtitleContent = {
            Text(
                text = if (on) {
                    stringResource(R.string.high_performance_active, requestedHz)
                } else {
                    stringResource(R.string.high_performance_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
            )
            if (warn) {
                Text(
                    text = stringResource(R.string.high_performance_battery_needed),
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 5,
                )
            }
        },
        trailing = { IosSwitch(checked = on, onCheckedChange = AppSettings::setHighPerformanceMode) },
        // The description is the row: with the warning in it, the row is the
        // way to the setting it names, and the switch is still the switch.
        onClick = { if (warn) openBatterySettings(context) else AppSettings.setHighPerformanceMode(!on) },
    )
}

/**
 * Whether Android leaves the app's battery use alone: "Unrestricted" on the
 * app's battery page. Where Android will not say, no warning is the answer.
 */
private fun batteryUnrestricted(context: Context): Boolean = runCatching {
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName)
}.getOrNull() ?: true

/**
 * Opens the app's own page in Android's settings, which has its battery use
 * on it. Android's settings as a whole where a phone has no such page.
 */
private fun openBatterySettings(context: Context) {
    val page = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    runCatching { context.startActivity(page) }
        .recoverCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
}
