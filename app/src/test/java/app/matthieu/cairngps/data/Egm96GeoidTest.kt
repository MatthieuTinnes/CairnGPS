package app.matthieu.cairngps.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class Egm96GeoidTest {

    private val rows = Egm96Geoid.ROWS
    private val cols = Egm96Geoid.COLS

    /** Builds a full-size grid (matching the real asset's dimensions) with a single value set. */
    private fun gridOf(vararg nodes: Pair<Pair<Int, Int>, Double>): ShortArray {
        val values = ShortArray(rows * cols)
        for ((rc, meters) in nodes) {
            val (row, col) = rc
            values[row * cols + col] = (meters * 100).toInt().toShort()
        }
        return values
    }

    @Test
    fun `interpolates bilinearly between the four surrounding nodes`() {
        // Row 0 = +90 deg, so row 10/col 10 sits at lat=80, lon=10.
        val geoid = Egm96Geoid.forTesting(
            gridOf(
                (10 to 10) to 10.0,
                (10 to 11) to 20.0,
                (11 to 10) to 30.0,
                (11 to 11) to 40.0,
            ),
        )
        // Quarter-way across the cell in both directions.
        val result = geoid.separationMeters(latitude = 90.0 - 10.25, longitude = 10.25)
        // Bilinear interpolation at (rowFrac=0.25, colFrac=0.25):
        // top = 10 + (20-10)*0.25 = 12.5 ; bottom = 30 + (40-30)*0.25 = 32.5
        // result = 12.5 + (32.5-12.5)*0.25 = 17.5
        assertEquals(17.5, result, 1e-9)
    }

    @Test
    fun `returns the exact node value when queried exactly on a grid point`() {
        val geoid = Egm96Geoid.forTesting(gridOf((45 to 200) to 12.34))
        assertEquals(12.34, geoid.separationMeters(90.0 - 45.0, 200.0), 1e-9)
    }

    @Test
    fun `wraps longitude across the antimeridian seam`() {
        // Column 359 (lon=359) and column 360 (lon=360, i.e. 0) hold different values; a query
        // in between must interpolate across that seam rather than treating 360 as out of range.
        val geoid = Egm96Geoid.forTesting(
            gridOf(
                (0 to 359) to 10.0,
                (0 to 360) to 20.0,
            ),
        )
        assertEquals(15.0, geoid.separationMeters(90.0, 359.5), 1e-9)
        // Longitude given as a negative/normalized value should resolve the same way.
        assertEquals(15.0, geoid.separationMeters(90.0, -0.5), 1e-9)
    }

    @Test
    fun `clamps latitude beyond the poles instead of going out of bounds`() {
        val geoid = Egm96Geoid.forTesting(gridOf((0 to 0) to 42.0))
        assertEquals(42.0, geoid.separationMeters(91.0, 0.0), 1e-9)
        assertEquals(42.0, geoid.separationMeters(90.0, 0.0), 1e-9)
    }

    @Test
    fun `missing grid degrades to zero correction`() {
        val geoid = Egm96Geoid.forTesting(null)
        assertEquals(0.0, geoid.separationMeters(45.92, 6.87), 0.0)
    }

    // --- Sanity check against the real bundled asset ---------------------------------------

    @Test
    fun `bundled asset matches known EGM96 undulations`() {
        val assetFile = File("src/main/assets/egm96_geoid_1deg.dat")
        val geoid = Egm96Geoid.forTesting(Egm96Geoid.readGrid(assetFile.inputStream()))

        // Reference values sampled from the official NGA WW15MGH.GRD (EGM96) 15' grid.
        assertEquals(17.16, geoid.separationMeters(0.0, 0.0), 0.5) // Null Island
        assertEquals(44.6, geoid.separationMeters(48.85, 2.35), 1.0) // Paris
        assertEquals(50.5, geoid.separationMeters(45.92, 6.87), 1.0) // Chamonix
    }
}
