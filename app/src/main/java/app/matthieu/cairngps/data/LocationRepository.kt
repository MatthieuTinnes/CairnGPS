package app.matthieu.cairngps.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import app.matthieu.cairngps.demo.DemoGpsSource
import app.matthieu.cairngps.demo.DemoMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * Single source of truth for raw device location.
 *
 * Deliberately uses [LocationManager] with [LocationManager.GPS_PROVIDER] rather than
 * FusedLocationProviderClient: we need direct access to the GPS chip so that we can later
 * consume raw GNSS satellite measurements from the same provider.
 */
class LocationRepository(context: Context) {

    // Application context is stored via getSystemService below; we don't retain the Context itself.
    private val locationManager: LocationManager =
        requireNotNull(context.applicationContext.getSystemService()) {
            "LocationManager service is unavailable on this device"
        }
    private val appContext = context.applicationContext

    // App-scoped: this repository is a CairnApplication singleton, never torn down, so a
    // permanent scope is correct here. Backs both the shared GPS flows below and the background
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Converts raw ellipsoidal GPS altitude to mean sea level; see Egm96Geoid for why. Starts as
    // a no-op (separationMeters() == 0.0) and is replaced once the ~130 KB asset has been read off
    @Volatile
    private var geoid: Egm96Geoid = Egm96Geoid.forTesting(null)

    // Screenshot/screencast demo mode (debug builds only): every public flow below serves synthetic
    // data and the GPS chip is never touched. Null — and, in release, folded away entirely — in
    // every normal run. See DemoMode.
    private val demo: DemoGpsSource? = if (DemoMode.isEnabled) DemoGpsSource() else null

    init {
        if (demo == null) {
            scope.launch(Dispatchers.IO) { geoid = Egm96Geoid.fromAssets(appContext) }
        }
    }

    /**
     * The most recent cached GPS fix the OS holds, or `null` if it has none. Cheap and does *not*
     * power up the GPS chip — used e.g. to seed magnetic declination for the compass without
     * forcing a full location request.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun lastKnownLocation(): LocationData? =
        demo?.lastKnownLocation()
            ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                it.toLocationData(geoid.separationMeters(it.latitude, it.longitude))
            }

    /**
     * [Flow] of GPS fixes, shared across every collector : several call sites used to
     * each open their own [LocationManager.requestLocationUpdates] registration. A single
     * upstream registration now backs all of them via [shareIn], reference-counted by
     * [SharingStarted.WhileSubscribed] so the GPS chip powers down once nobody is collecting.
     * `replay = 0` keeps the cold-flow semantics collectors already depend on: a fresh collector
     * never receives a stale fix from before it subscribed (important for
     * [RecordingRepository], which must not fold an old point into a new recording).
     *
     * The caller MUST hold [Manifest.permission.ACCESS_FINE_LOCATION] before collecting.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun locationUpdates(): Flow<LocationData> = sharedLocationUpdates

    private val sharedLocationUpdates: Flow<LocationData> =
        (demo?.locationUpdates() ?: locationUpdatesCold(minTimeMs = 1_000L, minDistanceM = 0f))
            .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), replay = 0)

    /**
     * Cold [Flow] of GPS fixes backing [locationUpdates]. A new registration is created for each
     * collector and torn down automatically when collection stops.
     *
     * @param minTimeMs      Minimum interval between updates, in milliseconds.
     * @param minDistanceM   Minimum movement between updates, in meters.
     */
    // The @RequiresPermission below documents the requirement for callers, but lint doesn't honor
    // an annotation on a lambda receiver at the requestLocationUpdates() call site inside it.
    @SuppressLint("MissingPermission")
    private fun locationUpdatesCold(
        minTimeMs: Long,
        minDistanceM: Float,
    ): Flow<LocationData> = callbackFlow @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]) {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val separation = geoid.separationMeters(location.latitude, location.longitude)
                trySend(location.toLocationData(separation))
            }

            // Overridden for source/binary compatibility across API levels; nothing to do here.
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Deprecated in API 29, still abstract on older levels")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            minTimeMs,
            minDistanceM,
            listener,
            Looper.getMainLooper(),
        )

        awaitClose { locationManager.removeUpdates(listener) }
    }
        // Location callbacks fire on the main looper; hop off it and keep only the latest fix
        // if a slow collector falls behind.
        .conflate()
        .flowOn(Dispatchers.Default)

    /**
     * [Flow] of GNSS satellite snapshots, one [List] per [GnssStatus] update (~1 Hz while the GPS
     * engine is running), shared across every collector the same way as
     * [locationUpdates]: one upstream registration (GNSS callback + its own keep-alive location
     * request) backs every collector via [shareIn]/[SharingStarted.WhileSubscribed], instead of
     * each collector opening its own. `replay = 0`: a fresh collector waits for the next snapshot
     * rather than receiving a stale one.
     *
     * The GNSS engine only produces [GnssStatus] updates while at least one location request is
     * active — a status callback alone never powers the chip. This flow therefore also holds its
     * own [GPS_PROVIDER][LocationManager.GPS_PROVIDER] request (fixes discarded) so that satellite
     * data keeps flowing even when no other screen is collecting [locationUpdates].
     *
     * Nothing is emitted until the GPS engine delivers its first status: collectors should treat
     * the absence of a value as "waiting for satellite data".
     *
     * The caller MUST hold [Manifest.permission.ACCESS_FINE_LOCATION] before collecting.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun satelliteUpdates(): Flow<List<SatelliteInfo>> = sharedSatelliteUpdates

    private val sharedSatelliteUpdates: Flow<List<SatelliteInfo>> =
        (demo?.satelliteUpdates() ?: satelliteUpdatesCold())
            .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), replay = 0)

    /** Cold [Flow] of GNSS satellite snapshots backing [satelliteUpdates]; see its doc for details. */
    // satelliteUpdates() carries @RequiresPermission, but lint can't trace that through the
    // property initializer down into this private helper's own requestLocationUpdates() call.
    @SuppressLint("MissingPermission")
    private fun satelliteUpdatesCold(): Flow<List<SatelliteInfo>> = callbackFlow {
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                trySend(status.toSatelliteInfoList())
            }
        }

        // Keep-alive location request: without it the GNSS engine stays off and the status
        // callback never fires. The fixes themselves are ignored here.
        val keepAliveListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Deprecated in API 29, still abstract on older levels")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1_000L,
            0f,
            keepAliveListener,
            Looper.getMainLooper(),
        )

        val mainHandler = Handler(Looper.getMainLooper())
        val registered = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            locationManager.registerGnssStatusCallback(
                { command -> mainHandler.post(command) },
                callback,
            )
        } else {
            // Handler overload is the only pre-API 30 option; deprecated but fully functional.
            @Suppress("DEPRECATION")
            locationManager.registerGnssStatusCallback(callback, mainHandler)
        }
        // Registration can fail if the GNSS engine is unavailable; the flow then completes
        // without emitting and the UI stays in its waiting state.
        if (!registered) close()

        awaitClose {
            locationManager.unregisterGnssStatusCallback(callback)
            locationManager.removeUpdates(keepAliveListener)
        }
    }
        // Status callbacks fire on the main looper; hop off it and keep only the latest snapshot
        // if a slow collector falls behind.
        .conflate()
        .flowOn(Dispatchers.Default)

    /**
     * [Flow] of the [GPS_PROVIDER][LocationManager.GPS_PROVIDER] enabled state: emits the current
     * state on collection, then again on every change (user toggling location on/off). Backed by a
     * [BroadcastReceiver] on [LocationManager.PROVIDERS_CHANGED_ACTION], so it costs no battery
     * and works without any active location request. Lets the UI react immediately when the GPS
     * is disabled instead of waiting for a staleness timeout.
     */
    fun gpsProviderEnabled(): Flow<Boolean> = sharedGpsProviderEnabled

    // Demo mode never reads the real provider, so it reports permanently enabled: a capture must
    // not be interrupted by the GPS-disabled banner just because location is off on the device.
    private val sharedGpsProviderEnabled: Flow<Boolean> =
        if (demo != null) flowOf(true) else providerEnabledCold()

    /** Cold [Flow] backing [gpsProviderEnabled]; see its doc for details. */
    private fun providerEnabledCold(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            }
        }
        appContext.registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        trySend(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
        awaitClose { appContext.unregisterReceiver(receiver) }
    }
        // PROVIDERS_CHANGED fires for every provider, not just GPS; drop the non-changes.
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), replay = 0)
}
