package app.matthieu.cairngps.ui.compass

import app.matthieu.cairngps.data.NorthReference
import app.matthieu.cairngps.data.Waypoint

/**
 * State of the compass screen.
 *
 * @property sensorAvailable   False when the device has no rotation vector sensor at all.
 * @property hasData           True once at least one (smoothed) heading has been produced.
 * @property headingDegrees    Displayed heading in degrees (0..360), already resolved against the
 *                             selected north reference (magnetic or true).
 * @property cardinalIndex     Index into the 8-point cardinal names (N, NE, E, SE, S, SO, O, NO).
 * @property useTrueNorth      Whether the heading is *currently* expressed relative to true north —
 *                             falls back to magnetic when the user preference is
 *                             [NorthReference.TRUE] but no declination could be computed yet.
 * @property declinationDegrees Magnetic declination for the last known position (positive = east),
 *                             or `null` when no GPS position is known — true north is then N/A.
 * @property needsCalibration  True when the magnetometer accuracy is low and the user should do
 *                             the figure-of-eight calibration gesture.
 * @property targetName            Name of the selected target waypoint, or `null` when no target
 *                                  is set (or it was deleted).
 * @property targetDistanceMeters   Great-circle distance to the target, or `null` without a target
 *                                  or a current fix.
 * @property bearingToTargetDegrees True bearing (0..360) from the current position to the target,
 *                                  or `null` without a target or a current fix.
 * @property waypoints              Every saved waypoint, for the "Changer de repère cible" picker.
 */
data class CompassUiState(
    val sensorAvailable: Boolean = true,
    val hasData: Boolean = false,
    val headingDegrees: Float = 0f,
    val cardinalIndex: Int = 0,
    val useTrueNorth: Boolean = false,
    val declinationDegrees: Float? = null,
    val needsCalibration: Boolean = false,
    val targetName: String? = null,
    val targetDistanceMeters: Double? = null,
    val bearingToTargetDegrees: Float? = null,
    val waypoints: List<Waypoint> = emptyList(),
) {
    /** True once a target waypoint is selected, regardless of whether a bearing could be computed. */
    val hasTarget: Boolean get() = targetName != null

    /**
     * Bearing to the target relative to the heading currently on screen — this is the angle to
     * rotate the arrow glyph by so it points at the target regardless of which way the device
     * faces. `null` unless both the target bearing and the current heading are known.
     */
    val relativeBearingDegrees: Float?
        get() = bearingToTargetDegrees?.let { target ->
            ((target - headingDegrees) % 360f + 360f) % 360f
        }
}
