package app.matthieu.cairngps.ui.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.RecordEntry
import app.matthieu.cairngps.data.RecordType
import app.matthieu.cairngps.data.RecordsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Exposes the cross-session extremes tracked in [RecordsRepository] for the Records screen. */
class RecordsViewModel(recordsRepository: RecordsRepository) : ViewModel() {

    val uiState: StateFlow<RecordsUiState> = recordsRepository.records()
        .map { records -> RecordsUiState(buildItems(records)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordsUiState(),
        )

    private fun buildItems(records: List<RecordEntry>): List<RecordDisplayItem> {
        fun entryFor(type: RecordType) = records.firstOrNull { it.type == type.name }
        return DISPLAY_ORDER.map { (type, labelRes) -> RecordDisplayItem(type, labelRes, entryFor(type)) }
    }

    companion object {
        /**
         * The records shown on the page and their order, per the brief: max speed, min/max
         * altitude, the four geographic extremes, longest elevation gain, and longest distance.
         * [RecordType.MAX_SATELLITES] is tracked (it feeds the satellites achievement) but is
         * deliberately not part of this list.
         */
        private val DISPLAY_ORDER: List<Pair<RecordType, Int>> = listOf(
            RecordType.MAX_SPEED to R.string.record_max_speed,
            RecordType.MAX_ALTITUDE to R.string.record_max_altitude,
            RecordType.MIN_ALTITUDE to R.string.record_min_altitude,
            RecordType.NORTHERNMOST to R.string.record_northernmost,
            RecordType.SOUTHERNMOST to R.string.record_southernmost,
            RecordType.EASTERNMOST to R.string.record_easternmost,
            RecordType.WESTERNMOST to R.string.record_westernmost,
            RecordType.MAX_ELEVATION_GAIN to R.string.record_max_elevation_gain,
            RecordType.MAX_DISTANCE to R.string.record_max_distance,
        )

        /** Factory that injects the [RecordsRepository] without needing a DI framework. */
        fun factory(recordsRepository: RecordsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return RecordsViewModel(recordsRepository) as T
                }
            }
    }
}
