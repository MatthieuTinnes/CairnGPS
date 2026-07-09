package app.matthieu.cairngps.data

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.callbackFlow

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

    /** Whether the GPS provider is currently enabled by the user. */
    val isGpsEnabled: Boolean
        get() = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    /**
     * Cold [Flow] of GPS fixes. A new registration is created for each collector and torn down
     * automatically when collection stops.
     *
     * @param minTimeMs      Minimum interval between updates, in milliseconds.
     * @param minDistanceM   Minimum movement between updates, in meters.
     *
     * The caller MUST hold [Manifest.permission.ACCESS_FINE_LOCATION] before collecting.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun locationUpdates(
        minTimeMs: Long = 1_000L,
        minDistanceM: Float = 0f,
    ): Flow<LocationData> = callbackFlow {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toLocationData())
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
}
