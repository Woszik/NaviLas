package pl.navilas.finder.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

internal fun desiredAmbientNightMode(currentNight: Boolean, lux: Float): Boolean? =
    when {
        currentNight && lux >= AmbientLightThemeController.DAY_THRESHOLD_LUX -> false
        !currentNight && lux <= AmbientLightThemeController.NIGHT_THRESHOLD_LUX -> true
        else -> null
    }

internal class AmbientLightThemeController(
    context: Context,
    initialNightMode: Boolean,
    private val onNightModeChanged: (Boolean) -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val handler = Handler(Looper.getMainLooper())
    private var currentNightMode = initialNightMode
    private var candidateNightMode: Boolean? = null
    private var latestLux = Float.NaN
    private var registered = false
    private val applyCandidate = Runnable {
        val candidate = candidateNightMode ?: return@Runnable
        if (desiredAmbientNightMode(currentNightMode, latestLux) != candidate) {
            candidateNightMode = null
            return@Runnable
        }
        currentNightMode = candidate
        candidateNightMode = null
        onNightModeChanged(candidate)
    }

    val isAvailable: Boolean
        get() = lightSensor != null

    fun start() {
        val sensor = lightSensor ?: return
        if (registered) return
        registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (registered) sensorManager.unregisterListener(this)
        registered = false
        handler.removeCallbacks(applyCandidate)
        candidateNightMode = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val lux = event.values.firstOrNull() ?: return
        latestLux = lux
        val desired = desiredAmbientNightMode(currentNightMode, lux)
        if (desired == null) {
            handler.removeCallbacks(applyCandidate)
            candidateNightMode = null
            return
        }

        if (candidateNightMode == desired) return
        handler.removeCallbacks(applyCandidate)
        candidateNightMode = desired
        handler.postDelayed(applyCandidate, STABLE_READING_MS)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        /** Separate thresholds prevent rapid switching around twilight. */
        internal const val NIGHT_THRESHOLD_LUX = 30f
        internal const val DAY_THRESHOLD_LUX = 150f
        private const val STABLE_READING_MS = 2_000L
    }
}
