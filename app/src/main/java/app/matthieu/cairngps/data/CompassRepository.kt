package app.matthieu.cairngps.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import app.matthieu.cairngps.demo.DemoGpsSource
import app.matthieu.cairngps.demo.DemoMode
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * A single compass reading derived from the device's rotation vector sensor.
 *
 * @property azimuthMagneticDegrees Heading of the top of the device relative to *magnetic* north,
 *                                  clockwise, normalized to 0..360. The declination toward true
 *                                  north is applied downstream since it depends on GPS position.
 * @property accuracy One of the `SensorManager.SENSOR_STATUS_*` constants. Low values
 *                    (`SENSOR_STATUS_UNRELIABLE`, `SENSOR_STATUS_ACCURACY_LOW`) mean the
 *                    magnetometer needs the figure-of-eight calibration gesture.
 */
data class CompassReading(
    val azimuthMagneticDegrees: Float,
    val accuracy: Int,
)

/**
 * Single source of truth for compass heading, wrapping [SensorManager].
 *
 * Deliberately uses [Sensor.TYPE_ROTATION_VECTOR] rather than manually fusing the accelerometer
 * and magnetometer: the rotation vector is already gravity-compensated and tilt-corrected by the
 * platform sensor-fusion, so it is far more stable and needs no hand-rolled complementary filter.
 */
class CompassRepository(context: Context) {

    private val sensorManager: SensorManager =
        requireNotNull(context.applicationContext.getSystemService()) {
            "SensorManager service is unavailable on this device"
        }

    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Screenshot/screencast demo mode (debug builds only): the heading follows the simulated walk
    // instead of the magnetometer, so the dial and the position on screen agree. See DemoMode.
    private val demo: DemoGpsSource? = if (DemoMode.isEnabled) DemoGpsSource() else null

    /** Whether this device exposes a rotation vector sensor at all (some cheap devices don't). */
    val isSensorAvailable: Boolean get() = demo != null || rotationVectorSensor != null

    /**
     * Cold [Flow] of compass readings. A listener is registered per collector and unregistered
     * automatically when collection stops, so listening follows the collector's lifecycle
     * (typically ON_START → ON_STOP). Emits nothing if the device has no rotation vector sensor.
     */
    fun headingUpdates(): Flow<CompassReading> = demo?.headingUpdates() ?: sensorHeadingUpdates()

    private fun sensorHeadingUpdates(): Flow<CompassReading> = callbackFlow {
        val sensor = rotationVectorSensor
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        // Reused across callbacks to avoid allocating on every sensor event.
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Rotation vector → rotation matrix → orientation angles. orientation[0] is the
                // azimuth: rotation about the vertical axis, i.e. where the top of the device points
                // relative to magnetic north.
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                val normalized = (azimuthDeg % 360f + 360f) % 360f
                trySend(CompassReading(normalized, event.accuracy))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // Accuracy is also carried on every SensorEvent, so nothing to do here.
            }
        }

        // SENSOR_DELAY_UI (~60 ms) is smooth enough for a compass while staying easy on the
        // battery; the low-pass filter in the ViewModel removes the remaining jitter.
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

        awaitClose { sensorManager.unregisterListener(listener) }
    }
        // Keep only the latest reading if a slow collector falls behind.
        .conflate()
}
