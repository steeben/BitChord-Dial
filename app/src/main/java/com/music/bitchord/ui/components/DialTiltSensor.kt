package com.music.bitchord.ui.components

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.CustomizationSettings
import kotlin.math.exp

/** The tilt, in radians, that moves the dials' art all the way. */
private const val FULL_TILT_RAD = 0.3f

/** How quickly a held tilt is taken as the new rest, in seconds: the art drifts home. */
private const val RECENTER_SECONDS = 1.4f

/**
 * The phone's tilt for the dials' parallax (see [dialParallax]), read off the
 * gyroscope while the app is in front and "Tilt parallax" is on. The turn
 * rates are added up into a tilt that eases back to rest, so wherever the
 * phone is held becomes level and only movement shifts the art.
 *
 * Null with the setting off or no gyroscope, which leaves the art still.
 */
@Composable
fun rememberDialTilt(): DialTilt? {
    val enabled by CustomizationSettings.dialParallax.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sensors = remember(context) { context.getSystemService(SensorManager::class.java) }
    val gyroscope = remember(sensors) { sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    if (!enabled || sensors == null || gyroscope == null) return null
    val tilt = remember { DialTilt() }
    LifecycleResumeEffect(tilt, sensors, gyroscope) {
        val listener = object : SensorEventListener {
            private var lastNanos = 0L
            private var aroundX = 0f
            private var aroundY = 0f

            override fun onSensorChanged(event: SensorEvent) {
                val last = lastNanos
                lastNanos = event.timestamp
                if (last == 0L) return
                val dt = ((event.timestamp - last) / 1e9f).coerceIn(0f, 0.1f)
                val settle = exp(-dt / RECENTER_SECONDS)
                aroundX = ((aroundX + event.values[0] * dt) * settle).coerceIn(-FULL_TILT_RAD, FULL_TILT_RAD)
                aroundY = ((aroundY + event.values[1] * dt) * settle).coerceIn(-FULL_TILT_RAD, FULL_TILT_RAD)
                tilt.x = aroundY / FULL_TILT_RAD
                tilt.y = aroundX / FULL_TILT_RAD
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose {
            sensors.unregisterListener(listener)
            tilt.x = 0f
            tilt.y = 0f
        }
    }
    return tilt
}
