package app.matthieu.cairngps.ui.gamification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.RecordType
import app.matthieu.cairngps.data.RecordsRepository
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.domain.format.DASH
import app.matthieu.cairngps.domain.format.formatAltitude
import app.matthieu.cairngps.domain.format.formatCoordinate
import app.matthieu.cairngps.domain.format.formatDistanceKm
import app.matthieu.cairngps.domain.format.formatElevation
import app.matthieu.cairngps.domain.format.formatSpeedKmh
import app.matthieu.cairngps.domain.format.formatWaypointTimestamp
import app.matthieu.cairngps.ui.settings.SettingsViewModel
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.Sym

/** Route: the Records screen, reached from a button on the Succès screen. */
@Composable
fun RecordsRoute(
    recordsRepository: RecordsRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RecordsViewModel = viewModel(factory = RecordsViewModel.factory(recordsRepository))
    // Coordinates follow the user's chosen format (decimal / DMS), same as the Position screen.
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(settingsRepository))

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    RecordsScreen(
        uiState = uiState,
        coordinateFormat = settings.coordinateFormat,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordsScreen(
    uiState: RecordsUiState,
    coordinateFormat: CoordinateFormat,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.records_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    IconButton(onClick = onBack) {
                        Sym(icon = Glyph.ArrowBack, contentDescription = backLabel)
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.items.orEmpty(), key = { it.type }) { item ->
                RecordCard(item, coordinateFormat)
            }
        }
    }
}

/** Leading glyph per record type, matching the design's records list. */
private val RecordType.glyph: Char
    get() = when (this) {
        RecordType.MAX_SPEED -> Glyph.Speed
        RecordType.MAX_ALTITUDE, RecordType.MIN_ALTITUDE -> Glyph.Landscape
        RecordType.MAX_ELEVATION_GAIN -> Glyph.Elevation
        RecordType.MAX_DISTANCE -> Glyph.Route
        RecordType.NORTHERNMOST, RecordType.SOUTHERNMOST,
        RecordType.EASTERNMOST, RecordType.WESTERNMOST,
        -> Glyph.Public
        RecordType.MAX_SATELLITES -> Glyph.SatelliteAlt
    }

@Composable
private fun RecordCard(item: RecordDisplayItem, coordinateFormat: CoordinateFormat) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sym(icon = item.type.glyph, contentDescription = null, tint = CairnGreen)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(text = stringResource(item.labelRes), fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    val achievedAt = item.entry?.achievedAt
                    if (achievedAt != null) {
                        Text(
                            text = formatWaypointTimestamp(achievedAt),
                            fontSize = 12.sp,
                            color = LabelMuted,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatRecordValue(item, coordinateFormat),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = MonoFontFamily,
                )
                secondaryCoordinate(item, coordinateFormat)?.let { secondary ->
                    Text(
                        text = secondary,
                        fontSize = 12.sp,
                        color = CairnStone,
                        fontFamily = MonoFontFamily,
                    )
                }
            }
        }
    }
}

/** The record's headline value, formatted in its natural unit — [DASH] when not set yet. */
private fun formatRecordValue(item: RecordDisplayItem, coordinateFormat: CoordinateFormat): String {
    val entry = item.entry ?: return DASH
    return when (item.type) {
        RecordType.MAX_SPEED -> "${formatSpeedKmh(entry.value.toFloat())} km/h"
        RecordType.MAX_ALTITUDE, RecordType.MIN_ALTITUDE -> "${formatAltitude(entry.value)} m"
        RecordType.MAX_ELEVATION_GAIN -> "${formatElevation(entry.value)} m"
        RecordType.MAX_DISTANCE -> "${formatDistanceKm(entry.value)} km"
        RecordType.NORTHERNMOST, RecordType.SOUTHERNMOST ->
            formatCoordinate(entry.value, isLatitude = true, format = coordinateFormat)
        RecordType.EASTERNMOST, RecordType.WESTERNMOST ->
            formatCoordinate(entry.value, isLatitude = false, format = coordinateFormat)
        // Tracked for the satellites achievement, not shown on this screen — see RecordsViewModel.
        RecordType.MAX_SATELLITES -> entry.value.toInt().toString()
    }
}

/**
 * The coordinate paired with a geographic record's value, when known. Only live fixes pair both
 * coordinates together (a session only stores independent bounding-box extremes — see
 * [app.matthieu.cairngps.data.GamificationManager]), so this is `null` for a session-sourced
 * record.
 */
private fun secondaryCoordinate(item: RecordDisplayItem, coordinateFormat: CoordinateFormat): String? {
    val entry = item.entry ?: return null
    return when (item.type) {
        RecordType.NORTHERNMOST, RecordType.SOUTHERNMOST ->
            entry.longitude?.let { formatCoordinate(it, isLatitude = false, format = coordinateFormat) }
        RecordType.EASTERNMOST, RecordType.WESTERNMOST ->
            entry.latitude?.let { formatCoordinate(it, isLatitude = true, format = coordinateFormat) }
        else -> null
    }
}
