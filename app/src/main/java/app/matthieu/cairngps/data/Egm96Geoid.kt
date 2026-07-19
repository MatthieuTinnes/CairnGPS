package app.matthieu.cairngps.data

import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.InputStream

/**
 * Looks up the EGM96 geoid undulation (separation between the WGS84 ellipsoid and mean sea
 * level) so that raw GPS altitude — which [Location.getAltitude][android.location.Location]
 * reports above the *ellipsoid* — can be converted to the mean-sea-level altitude that maps
 * like Komoot or IGN display. Over France the geoid sits ~44-51 m below the ellipsoid, so
 * without this correction GPS altitude reads roughly that much too high.
 *
 * Backed by [egm96_geoid_1deg.dat][ASSET_NAME], a 1°x1° grid (181 latitude rows from +90° to
 * -90°, 361 longitude columns from 0° to 360° inclusive) sampled directly from the official
 * NGA `WW15MGH.GRD` 15'x15' EGM96 grid, stored as big-endian signed 16-bit centimeters. At 1°
 * resolution the interpolation error is at most ~1 m, well under GPS vertical accuracy.
 */
class Egm96Geoid private constructor(private val grid: ShortArray?) {

    /**
     * Geoid undulation N at [latitude]/[longitude], in meters, via bilinear interpolation
     * between the four surrounding grid nodes. Returns `0.0` (no correction) if the grid asset
     * could not be loaded, so callers degrade gracefully to the uncorrected ellipsoidal altitude.
     */
    fun separationMeters(latitude: Double, longitude: Double): Double {
        val values = grid ?: return 0.0

        val lat = latitude.coerceIn(-90.0, 90.0)
        val lon = ((longitude % 360.0) + 360.0) % 360.0

        // Row 0 is +90°, increasing southward; clamp so row+1 always stays in bounds.
        val rowF = 90.0 - lat
        val row0 = rowF.toInt().coerceIn(0, ROWS - 2)
        val rowFrac = rowF - row0

        // Col 0 is 0°E, increasing eastward; col 360 duplicates col 0 so the antimeridian/prime
        // meridian seam needs no special-casing.
        val colF = lon
        val col0 = colF.toInt().coerceIn(0, COLS - 2)
        val colFrac = colF - col0

        fun nodeAt(row: Int, col: Int) = values[row * COLS + col] / 100.0

        val top = lerp(nodeAt(row0, col0), nodeAt(row0, col0 + 1), colFrac)
        val bottom = lerp(nodeAt(row0 + 1, col0), nodeAt(row0 + 1, col0 + 1), colFrac)
        return lerp(top, bottom, rowFrac)
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    companion object {
        private const val TAG = "Egm96Geoid"
        private const val ASSET_NAME = "egm96_geoid_1deg.dat"
        internal const val ROWS = 181
        internal const val COLS = 361

        /**
         * Loads the bundled grid from the app's assets. Never throws: if the asset is missing or
         * corrupted, logs a warning and returns a lookup that always yields `0.0`.
         */
        fun fromAssets(context: Context): Egm96Geoid {
            val grid = try {
                context.assets.open(ASSET_NAME).use { readGrid(it) }
            } catch (e: IOException) {
                // Asset missing/unreadable.
                Log.w(TAG, "Failed to load $ASSET_NAME, altitude will not be geoid-corrected", e)
                null
            } catch (e: IllegalArgumentException) {
                // readGrid()'s require() rejects an asset of unexpected size (corrupted/regenerated).
                Log.w(TAG, "Failed to load $ASSET_NAME, altitude will not be geoid-corrected", e)
                null
            }
            return Egm96Geoid(grid)
        }

        /** Exposed for tests: builds a lookup directly from a pre-parsed grid. */
        internal fun forTesting(grid: ShortArray?) = Egm96Geoid(grid)

        internal fun readGrid(input: InputStream): ShortArray {
            val bytes = input.readBytes()
            require(bytes.size == ROWS * COLS * 2) {
                "Unexpected geoid asset size: ${bytes.size} bytes"
            }
            return ShortArray(bytes.size / 2) { i ->
                (((bytes[i * 2].toInt() and 0xFF) shl 8) or (bytes[i * 2 + 1].toInt() and 0xFF)).toShort()
            }
        }
    }
}
