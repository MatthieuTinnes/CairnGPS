package app.matthieu.cairngps.domain

import app.matthieu.cairngps.data.GnssBand
import app.matthieu.cairngps.data.GnssBandSample
import app.matthieu.cairngps.data.GnssSignalEvent
import kotlin.math.abs

/** Outcome of the C/N0 + AGC check. */
enum class SignalCheck {
    /** No measurement received yet. */
    WAITING,

    /** The device provides no raw measurements, or no AGC: the check can't run. */
    UNSUPPORTED,

    /** Measurements are arriving but the sliding windows aren't full yet. */
    WARMING_UP,
    OK,
    JAMMING,
    SPOOFING,
}

/** Outcome of the GNSS time vs network time check. */
enum class TimeCheck {
    /** No fix received yet. */
    WAITING,

    /** Network time unavailable (offline, or Android < 13). */
    UNAVAILABLE,
    OK,
    SPOOFING,
}

/**
 * Current verdict of the interference detection.
 *
 * @property affectedBands Bands that triggered the [signalCheck] alert, empty unless it is
 *                         [SignalCheck.JAMMING] or [SignalCheck.SPOOFING].
 * @property timeOffsetMs  Latest network minus GNSS time, in milliseconds, or `null` if unknown.
 */
data class InterferenceStatus(
    val signalCheck: SignalCheck = SignalCheck.WAITING,
    val affectedBands: List<GnssBand> = emptyList(),
    val timeCheck: TimeCheck = TimeCheck.WAITING,
    val timeOffsetMs: Long? = null,
) {
    val isJammingSuspected: Boolean get() = signalCheck == SignalCheck.JAMMING

    val isSpoofingSuspected: Boolean
        get() = signalCheck == SignalCheck.SPOOFING || timeCheck == TimeCheck.SPOOFING

    val isAlert: Boolean get() = isJammingSuspected || isSpoofingSuspected
}

/**
 * Flags suspected GNSS jamming and spoofing from raw signal data, following the heuristics of
 * Google's GnssLogger app (developer.android.com/develop/sensors-and-location/sensors/gnss-spoof-jam):
 *
 * - For each [GnssBand], the mean of the last [recentEpochs] epochs is compared with the mean of
 *   the [baselineEpochs] before them.
 * - AGC dropping while C/N0 drops (or the band's signals are lost) → jamming: a strong in-band
 *   emitter makes the receiver cut its gain and drowns the satellites.
 * - AGC dropping while C/N0 rises → spoofing: a fake transmitter is louder than the real sky.
 * - Network time differing from GNSS time by more than [timeOffsetThresholdMs] → spoofing.
 *
 * Requiring the AGC drop is what tells interference apart from mere sky obstruction (forest,
 * canyon, tunnel), which lowers C/N0 but leaves the receiver's gain untouched. The thresholds are
 * empirical: GnssLogger doesn't publish its own.
 *
 * Only *changes* are detected: interference already present when the windows fill up, or lasting
 * longer than the baseline window, ends up in the baseline and goes unnoticed. Stateful and not
 * thread-safe: feed it from a single collector.
 */
class GnssInterferenceDetector(
    private val baselineEpochs: Int = 50,
    private val recentEpochs: Int = 10,
    private val cn0ChangeDbHz: Double = 4.0,
    private val agcDropDb: Double = 3.0,
    private val alertHoldEpochs: Int = 30,
    private val agcProbeEpochs: Int = 10,
    private val timeOffsetThresholdMs: Long = 1_000L,
    private val timeOffsetConfirmations: Int = 3,
) {
    private val windowEpochs = baselineEpochs + recentEpochs
    private val history = mutableMapOf<GnssBand, ArrayDeque<GnssBandSample>>()
    private var measurementEpochs = 0
    private var sawAgc = false

    private var heldAlert: SignalCheck? = null
    private var heldBands: List<GnssBand> = emptyList()
    private var holdRemaining = 0
    private var timeOffsetStrikes = 0

    var status: InterferenceStatus = InterferenceStatus()
        private set

    fun onEvent(event: GnssSignalEvent): InterferenceStatus {
        status = when (event) {
            is GnssSignalEvent.Measurements -> onMeasurements(event.bands)
            is GnssSignalEvent.TimeOffset -> onTimeOffset(event.networkMinusGnssMs)
            GnssSignalEvent.MeasurementsUnsupported ->
                status.copy(signalCheck = SignalCheck.UNSUPPORTED, affectedBands = emptyList())
        }
        return status
    }

    private fun onMeasurements(bands: Map<GnssBand, GnssBandSample>): InterferenceStatus {
        measurementEpochs++
        if (bands.values.any { it.agcDb != null }) sawAgc = true

        // A band missing from this epoch still gets an empty sample, so every window stays aligned
        // on the same epochs and a band whose signals all vanished shows up as lost.
        (history.keys + bands.keys).forEach { band ->
            val samples = history.getOrPut(band) { ArrayDeque() }
            samples.addLast(bands[band] ?: EMPTY_SAMPLE)
            if (samples.size > windowEpochs) samples.removeFirst()
        }
        history.values.removeAll { samples -> samples.all { it == EMPTY_SAMPLE } }

        val detections = history.mapNotNull { (band, samples) -> classify(samples)?.let { band to it } }
        val detected = when {
            detections.any { it.second == SignalCheck.SPOOFING } -> SignalCheck.SPOOFING
            detections.isNotEmpty() -> SignalCheck.JAMMING
            else -> null
        }
        // Held for a while after the last detection so the alert doesn't flicker on and off as
        // the windows slide over the edges of the event.
        if (detected != null) {
            heldAlert = detected
            heldBands = detections.filter { it.second == detected }.map { it.first }.sortedBy { it.label }
            holdRemaining = alertHoldEpochs
        } else if (heldAlert != null && --holdRemaining <= 0) {
            heldAlert = null
            heldBands = emptyList()
        }

        heldAlert?.let { return status.copy(signalCheck = it, affectedBands = heldBands) }
        val check = when {
            !sawAgc && measurementEpochs >= agcProbeEpochs -> SignalCheck.UNSUPPORTED
            history.values.none { it.size >= windowEpochs && it.any { s -> s.agcDb != null } } ->
                SignalCheck.WARMING_UP
            else -> SignalCheck.OK
        }
        return status.copy(signalCheck = check, affectedBands = emptyList())
    }

    private fun classify(samples: ArrayDeque<GnssBandSample>): SignalCheck? {
        if (samples.size < windowEpochs) return null
        val baseline = samples.take(baselineEpochs)
        val recent = samples.takeLast(recentEpochs)

        val agcBefore = baseline.meanOf { it.agcDb } ?: return null
        val agcNow = recent.meanOf { it.agcDb } ?: return null
        if (agcNow - agcBefore > -agcDropDb) return null

        val cn0Before = baseline.meanOf { it.cn0DbHz } ?: return null
        val cn0Now = recent.meanOf { it.cn0DbHz }
        return when {
            cn0Now == null || cn0Now - cn0Before <= -cn0ChangeDbHz -> SignalCheck.JAMMING
            cn0Now - cn0Before >= cn0ChangeDbHz -> SignalCheck.SPOOFING
            else -> null
        }
    }

    private fun onTimeOffset(offsetMs: Long?): InterferenceStatus {
        if (offsetMs == null) {
            timeOffsetStrikes = 0
            return status.copy(timeCheck = TimeCheck.UNAVAILABLE, timeOffsetMs = null)
        }
        // A few consecutive confirmations, so a single glitched fix right after acquisition
        // doesn't raise the alert.
        timeOffsetStrikes = if (abs(offsetMs) > timeOffsetThresholdMs) timeOffsetStrikes + 1 else 0
        val check = if (timeOffsetStrikes >= timeOffsetConfirmations) TimeCheck.SPOOFING else TimeCheck.OK
        return status.copy(timeCheck = check, timeOffsetMs = offsetMs)
    }

    private companion object {
        val EMPTY_SAMPLE = GnssBandSample(cn0DbHz = null, agcDb = null)

        /** Mean of the non-null values, or `null` when fewer than half the samples have one. */
        fun List<GnssBandSample>.meanOf(value: (GnssBandSample) -> Double?): Double? {
            val values = mapNotNull(value)
            return if (values.size * 2 < size) null else values.average()
        }
    }
}
