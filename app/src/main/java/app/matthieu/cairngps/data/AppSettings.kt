package app.matthieu.cairngps.data

/** How latitude/longitude are rendered on screen. Persisted as an app-wide preference. */
enum class CoordinateFormat {
    /** Decimal degrees, e.g. `47.123456°`. */
    DECIMAL,

    /** Degrees / minutes / seconds, e.g. `47°07'24.4"N`. */
    DMS,
}

/**
 * User-configurable application settings.
 *
 * The default values here are also the fallback used before anything has been persisted.
 */
data class AppSettings(
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
)
