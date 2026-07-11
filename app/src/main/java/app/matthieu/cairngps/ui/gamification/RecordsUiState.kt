package app.matthieu.cairngps.ui.gamification

import androidx.annotation.StringRes
import app.matthieu.cairngps.data.RecordEntry
import app.matthieu.cairngps.data.RecordType

/**
 * One row of the Records screen: a fixed [type]/[labelRes] pairing (defined by
 * `RecordsViewModel`'s display order) plus the current [entry], or `null` if that record hasn't
 * been set yet (shown as tirets — never a bare zero, per the app's display conventions).
 */
data class RecordDisplayItem(
    val type: RecordType,
    @StringRes val labelRes: Int,
    val entry: RecordEntry?,
)

/**
 * State of the Records screen.
 *
 * @property items The records in display order, or `null` while the first database load is still
 *                  in flight.
 */
data class RecordsUiState(
    val items: List<RecordDisplayItem>? = null,
)
