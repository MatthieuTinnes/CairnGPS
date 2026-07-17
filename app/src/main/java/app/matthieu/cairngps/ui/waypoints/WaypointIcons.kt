package app.matthieu.cairngps.ui.waypoints

import androidx.annotation.StringRes
import app.matthieu.cairngps.R
import app.matthieu.cairngps.ui.theme.Glyph

/**
 * One selectable waypoint icon: a stable [key] persisted on [app.matthieu.cairngps.data.Waypoint],
 * the [glyph] codepoint that renders it, and a [labelRes] for its French name.
 *
 * [key] (not the font codepoint) is what gets stored in Room, so a future font/codepoint change
 * never invalidates saved waypoints.
 */
data class WaypointIcon(val key: String, val glyph: Char, @StringRes val labelRes: Int)

/**
 * The catalogue backing the icon picker (screens 6a/6b), in the order shown in the grid.
 */
object WaypointIcons {

    val all: List<WaypointIcon> = listOf(
        WaypointIcon("flag", Glyph.Flag, R.string.waypoint_icon_flag),
        WaypointIcon("terrain", Glyph.Terrain, R.string.waypoint_icon_terrain),
        WaypointIcon("forest", Glyph.Forest, R.string.waypoint_icon_forest),
        WaypointIcon("water_drop", Glyph.WaterDrop, R.string.waypoint_icon_water_drop),
        WaypointIcon("park", Glyph.Park, R.string.waypoint_icon_park),
        WaypointIcon("hiking", Glyph.Hiking, R.string.waypoint_icon_hiking),
        WaypointIcon("cottage", Glyph.Cottage, R.string.waypoint_icon_cottage),
        WaypointIcon("cabin", Glyph.Cabin, R.string.waypoint_icon_cabin),
        WaypointIcon("restaurant", Glyph.Restaurant, R.string.waypoint_icon_restaurant),
        WaypointIcon("photo_camera", Glyph.PhotoCamera, R.string.waypoint_icon_photo_camera),
        WaypointIcon("local_parking", Glyph.LocalParking, R.string.waypoint_icon_local_parking),
        WaypointIcon("warning", Glyph.Warning, R.string.waypoint_icon_warning),
        WaypointIcon("star", Glyph.Star, R.string.waypoint_icon_star),
        WaypointIcon("pin_drop", Glyph.PinDrop, R.string.waypoint_icon_pin_drop),
        WaypointIcon("sailing", Glyph.Sailing, R.string.waypoint_icon_sailing),
        WaypointIcon("waves", Glyph.Waves, R.string.waypoint_icon_waves),
    )

    private val byKey: Map<String, WaypointIcon> = all.associateBy { it.key }

    private val default: WaypointIcon = all.first()

    /** The [WaypointIcon] for [key], falling back to the flag when [key] is unrecognized. */
    fun iconFor(key: String): WaypointIcon = byKey[key] ?: default

    /** Shorthand for `iconFor(key).glyph`. */
    fun glyphFor(key: String): Char = iconFor(key).glyph
}
