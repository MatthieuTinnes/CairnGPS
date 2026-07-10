package app.matthieu.cairngps.ui.compass

/**
 * State of the compass screen.
 *
 * @property sensorAvailable   False when the device has no rotation vector sensor at all.
 * @property hasData           True once at least one (smoothed) heading has been produced.
 * @property headingDegrees    Displayed heading in degrees (0..360), already resolved against the
 *                             selected north reference (magnetic or true).
 * @property cardinalIndex     Index into the 8-point cardinal names (N, NE, E, SE, S, SO, O, NO).
 * @property useTrueNorth      Whether the heading is expressed relative to true (geographic) north.
 * @property declinationDegrees Magnetic declination for the last known position (positive = east),
 *                             or `null` when no GPS position is known — true north is then N/A.
 * @property needsCalibration  True when the magnetometer accuracy is low and the user should do
 *                             the figure-of-eight calibration gesture.
 */
data class CompassUiState(
    val sensorAvailable: Boolean = true,
    val hasData: Boolean = false,
    val headingDegrees: Float = 0f,
    val cardinalIndex: Int = 0,
    val useTrueNorth: Boolean = false,
    val declinationDegrees: Float? = null,
    val needsCalibration: Boolean = false,
) {
    /** True north can only be shown when a declination could be computed from a GPS position. */
    val trueNorthAvailable: Boolean get() = declinationDegrees != null
}
