package app.matthieu.cairngps.data

import kotlinx.serialization.Serializable

/** How latitude/longitude are rendered on screen. Persisted as an app-wide preference. */
@Serializable
enum class CoordinateFormat {
    /** Decimal degrees, e.g. `47.123456°`. */
    DECIMAL,

    /** Degrees / minutes / seconds, e.g. `47°07'24.4"N`. */
    DMS,
}

/** Which color scheme the app uses. Persisted as an app-wide preference. */
@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** Reference the compass expresses headings against. Persisted as an app-wide preference. */
@Serializable
enum class NorthReference {
    MAGNETIC,
    TRUE,
}

/** Unit system used to display measurements. Persisted as an app-wide preference. */
@Serializable
enum class UnitSystem {
    METRIC,
    IMPERIAL,
}

/**
 * User-configurable application settings.
 *
 * The default values here are also the fallback used before anything has been persisted.
 */
@Serializable
data class AppSettings(
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DMS,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val northReference: NorthReference = NorthReference.MAGNETIC,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
)
