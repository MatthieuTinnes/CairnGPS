package app.matthieu.cairngps.ui.satellites

import android.content.Context
import app.matthieu.cairngps.R
import app.matthieu.cairngps.domain.EcefPosition
import app.matthieu.cairngps.domain.SatelliteGeometry

/**
 * Loads the embedded world landmass outlines used as the globe's base map.
 *
 * The data is Natural Earth "110m land" (public domain), pre-simplified into a compact text
 * resource: one polygon per line, `lat,lon` pairs separated by `;`, coordinates rounded to
 * 0.01° and islets below 8 points dropped. Each ring is closed (last point equals the first).
 */
object WorldLandmasses {

    /** Parses the raw resource into ECEF polygons on the Earth's surface. Call off the main thread. */
    fun load(context: Context): List<List<EcefPosition>> =
        context.resources.openRawResource(R.raw.world_land).bufferedReader().useLines { lines ->
            lines
                .filter { it.isNotBlank() }
                .map { line ->
                    line.split(';').map { pair ->
                        val comma = pair.indexOf(',')
                        SatelliteGeometry.geodeticToEcef(
                            latitudeDeg = pair.substring(0, comma).toDouble(),
                            longitudeDeg = pair.substring(comma + 1).toDouble(),
                            altitudeMeters = 0.0,
                        )
                    }
                }
                .toList()
        }
}
