package app.matthieu.cairngps.data

/** How latitude/longitude are rendered on screen. Persisted as an app-wide preference. */
enum class CoordinateFormat {
    /** Decimal degrees, e.g. `47.123456°`. */
    DECIMAL,

    /** Degrees / minutes / seconds, e.g. `47°07'24.4"N`. */
    DMS,
}

/** Which color scheme the app uses. Persisted as an app-wide preference. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Reference the compass expresses headings against. Persisted as an app-wide preference. */
enum class NorthReference {
    MAGNETIC,
    TRUE,
}

/**
 * User-configurable application settings.
 *
 * The default values here are also the fallback used before anything has been persisted.
 */
data class AppSettings(
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DECIMAL,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val northReference: NorthReference = NorthReference.MAGNETIC,
)
