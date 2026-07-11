package app.matthieu.cairngps.ui.satellites

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.Constellation
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.SatelliteInfo
import app.matthieu.cairngps.ui.theme.ConstellationBeidou
import app.matthieu.cairngps.ui.theme.ConstellationGalileo
import app.matthieu.cairngps.ui.theme.ConstellationGlonass
import app.matthieu.cairngps.ui.theme.ConstellationGps
import app.matthieu.cairngps.ui.theme.ConstellationIrnss
import app.matthieu.cairngps.ui.theme.ConstellationQzss
import app.matthieu.cairngps.ui.theme.ConstellationSbas
import app.matthieu.cairngps.ui.theme.ConstellationUnknown
import kotlin.math.roundToInt

/**
 * C/N0 value (dB-Hz) treated as a full signal bar. Typical open-sky values sit between
 * ~20 (weak) and ~45 (excellent), so 45 gives a useful dynamic range.
 */
private const val CN0_FULL_SCALE_DBHZ = 45f

/**
 * Screen route. Wires up the [SatellitesViewModel] and binds the GNSS status subscription to the
 * screen lifecycle: registered in `ON_START`, unregistered in `ON_STOP`. Only ever composed once
 * the location permission has been granted.
 */
@SuppressLint("MissingPermission")
@Composable
fun SatellitesRoute(
    locationRepository: LocationRepository,
    onOpenInfo: () -> Unit,
    onOpenGlobe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SatellitesViewModel =
        viewModel(factory = SatellitesViewModel.factory(locationRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        viewModel.startTracking()
        onStopOrDispose { viewModel.stopTracking() }
    }

    SatellitesScreen(
        uiState = uiState,
        onOpenInfo = onOpenInfo,
        onOpenGlobe = onOpenGlobe,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatellitesScreen(
    uiState: SatellitesUiState,
    onOpenInfo: () -> Unit,
    onOpenGlobe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.satellites_title)) },
                actions = {
                    val globeLabel = stringResource(R.string.action_open_satellite_globe)
                    IconButton(
                        onClick = onOpenGlobe,
                        modifier = Modifier.semantics { contentDescription = globeLabel },
                    ) {
                        // Text glyph avoids depending on the large material-icons-extended artifact.
                        Text(
                            text = "🌐",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    val infoLabel = stringResource(R.string.action_open_constellation_info)
                    IconButton(
                        onClick = onOpenInfo,
                        modifier = Modifier.semantics { contentDescription = infoLabel },
                    ) {
                        // Text glyph avoids depending on the large material-icons-extended artifact.
                        Text(
                            text = "ℹ",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (!uiState.hasData) {
            WaitingForGnss(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "counts") {
                    SatelliteCounts(
                        inView = uiState.inViewCount,
                        usedInFix = uiState.usedInFixCount,
                    )
                }
                item(key = "constellations") {
                    ConstellationSummaryCard(summaries = uiState.constellationSummaries)
                }
                item(key = "list-title") {
                    Text(
                        text = stringResource(R.string.label_satellite_list).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(
                    items = uiState.satellites.orEmpty(),
                    // Svid is only unique within a constellation; combine both for a stable key.
                    key = { "${it.constellation.name}-${it.svid}" },
                ) { satellite ->
                    SatelliteRow(satellite)
                }
            }
        }
    }
}

@Composable
private fun WaitingForGnss(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.sats_waiting),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Headline counters: satellites in view vs. used in the current fix. */
@Composable
private fun SatelliteCounts(inView: Int, usedInFix: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp)) {
            CountColumn(
                value = inView,
                label = stringResource(R.string.sats_in_view),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            CountColumn(
                value = usedInFix,
                label = stringResource(R.string.sats_used_in_fix),
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CountColumn(
    value: Int,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConstellationSummaryCard(summaries: List<ConstellationSummary>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.label_constellations).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summaries.forEach { summary ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(color = summary.constellation.color(), shape = CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = summary.constellation.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(
                            R.string.sats_constellation_summary,
                            summary.inView,
                            summary.usedInFix,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SatelliteRow(satellite: SatelliteInfo) {
    val color = satellite.constellation.color()
    // Satellites tracked but not used in the fix are dimmed so the fixed ones stand out.
    val contentAlpha = if (satellite.usedInFix) 1f else 0.55f

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color = color.copy(alpha = contentAlpha), shape = CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.width(96.dp)) {
                Text(
                    text = "${satellite.constellation.displayName} %02d".format(satellite.svid),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                )
                Text(
                    text = stringResource(
                        R.string.sat_elevation_azimuth,
                        satellite.elevationDegrees.roundToInt(),
                        satellite.azimuthDegrees.roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
            Spacer(Modifier.width(12.dp))
            SignalBar(
                cn0DbHz = satellite.cn0DbHz,
                color = color.copy(alpha = contentAlpha),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "%.0f".format(satellite.cn0DbHz),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            Spacer(Modifier.width(8.dp))
            UsedInFixMarker(used = satellite.usedInFix)
        }
    }
}

/** Horizontal signal bar filled proportionally to the C/N0 value. */
@Composable
private fun SignalBar(cn0DbHz: Float, color: Color, modifier: Modifier = Modifier) {
    // Animate level changes so successive GNSS snapshots slide instead of flickering.
    val fraction by animateFloatAsState(
        targetValue = (cn0DbHz / CN0_FULL_SCALE_DBHZ).coerceIn(0f, 1f),
        label = "cn0",
    )
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}

/** Check glyph for satellites used in the fix; invisible placeholder otherwise to keep alignment. */
@Composable
private fun UsedInFixMarker(used: Boolean) {
    val label = stringResource(R.string.sat_used_in_fix_marker)
    Text(
        text = "✓",
        style = MaterialTheme.typography.titleMedium,
        color = if (used) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = if (used) Modifier.semantics { contentDescription = label } else Modifier,
    )
}

internal fun Constellation.color(): Color = when (this) {
    Constellation.GPS -> ConstellationGps
    Constellation.GLONASS -> ConstellationGlonass
    Constellation.GALILEO -> ConstellationGalileo
    Constellation.BEIDOU -> ConstellationBeidou
    Constellation.QZSS -> ConstellationQzss
    Constellation.SBAS -> ConstellationSbas
    Constellation.IRNSS -> ConstellationIrnss
    Constellation.UNKNOWN -> ConstellationUnknown
}
