package app.matthieu.cairngps.domain

import kotlin.math.abs

/**
 * Turns a stream of GPS altitudes into D+/D- increments.
 *
 * GPS altitude jitters by several meters from one fix to the next. Counting every change past a
 * small threshold folds that jitter into both D+ and D-, overestimating them by 20-30 % on a hike.
 * Two stages keep only real relief:
 * 1. a trailing moving average over [windowMs] removes the fix-to-fix jitter;
 * 2. a change of the smoothed altitude is counted only once it reaches [thresholdMeters] from the
 *    last counted altitude ([referenceAltitude]), which then becomes the new reference.
 *
 * The average is only trusted once the window spans at least [warmUpMs]: after a pause or a gap in
 * fixes the window restarts from a single sample, whose noise would otherwise be counted.
 *
 * Samples taken while stationary are ignored altogether: at rest the altitude only drifts. This
 * loses no real climb, since the reference is held and any altitude change made while
 * "stationary" is counted as soon as the user moves again.
 *
 * Window, warm-up and threshold were calibrated against reference hikes (see
 * ElevationAccumulatorTest).
 *
 * @param referenceAltitude Last counted smoothed altitude, to resume an interrupted recording;
 *                          `null` to start fresh.
 */
class ElevationAccumulator(
    referenceAltitude: Double? = null,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val warmUpMs: Long = DEFAULT_WARM_UP_MS,
    private val thresholdMeters: Double = DEFAULT_THRESHOLD_METERS,
) {
    private class Sample(val timestampMs: Long, val altitude: Double)

    private val window = ArrayDeque<Sample>()
    private var windowSum = 0.0

    /** Last smoothed altitude counted into D+/D-, or `null` until the window first warms up. */
    var referenceAltitude: Double? = referenceAltitude
        private set

    /**
     * Feeds one altitude sample and returns the elevation change it validates: positive for D+,
     * negative for D-, `0.0` when nothing is counted yet.
     */
    fun add(timestampMs: Long, altitude: Double, isMoving: Boolean): Double {
        if (!isMoving) return 0.0

        window.addLast(Sample(timestampMs, altitude))
        windowSum += altitude
        while (timestampMs - window.first().timestampMs > windowMs) {
            windowSum -= window.removeFirst().altitude
        }
        if (timestampMs - window.first().timestampMs < warmUpMs) return 0.0
        val smoothed = windowSum / window.size

        val reference = referenceAltitude
        if (reference == null) {
            referenceAltitude = smoothed
            return 0.0
        }
        val delta = smoothed - reference
        if (abs(delta) >= thresholdMeters) {
            referenceAltitude = smoothed
            return delta
        }
        return 0.0
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 75_000L
        const val DEFAULT_WARM_UP_MS = 30_000L
        const val DEFAULT_THRESHOLD_METERS = 6.0
    }
}
