package app.matthieu.cairngps.ui.satellites

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.Constellation
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.SatelliteInfo
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.ConstellationBeidou
import app.matthieu.cairngps.ui.theme.ConstellationGalileo
import app.matthieu.cairngps.ui.theme.ConstellationGlonass
import app.matthieu.cairngps.ui.theme.ConstellationGps
import app.matthieu.cairngps.ui.theme.ConstellationIrnss
import app.matthieu.cairngps.ui.theme.ConstellationQzss
import app.matthieu.cairngps.ui.theme.ConstellationSbas
import app.matthieu.cairngps.ui.theme.ConstellationUnknown
import app.matthieu.cairngps.ui.theme.DarkOnSurface
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.OutlineSubtle
import app.matthieu.cairngps.ui.theme.QualityGood
import app.matthieu.cairngps.ui.theme.SkyPlotInnerRing
import app.matthieu.cairngps.ui.theme.StatusChipBg
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.theme.ValueMuted
import kotlin.math.roundToInt

/**
 * C/N0 range (dB-Hz) the signal bar is scaled across: 18 (weak, empty) to 48 (excellent, full),
 * matching the design's `(cn0 - 18) / 30` fill formula. Always at least 6% full so even a weak
 * signal shows a sliver of bar.
 */
private const val CN0_RANGE_MIN_DBHZ = 18f
private const val CN0_RANGE_SPAN_DBHZ = 30f
private const val CN0_MIN_FRACTION = 0.06f

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
                    if (uiState.hasData) {
                        StatusChip(inView = uiState.inViewCount, usedInFix = uiState.usedInFixCount)
                        Spacer(Modifier.width(8.dp))
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
                item(key = "skyplot") {
                    SkyPlotCard(satellites = uiState.satellites.orEmpty())
                }
                item(key = "shortcuts") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ShortcutButton(
                            icon = Glyph.Public,
                            label = stringResource(R.string.action_open_satellite_globe),
                            onClick = onOpenGlobe,
                            modifier = Modifier.weight(1f),
                        )
                        ShortcutButton(
                            icon = Glyph.Info,
                            label = stringResource(R.string.action_open_constellation_info),
                            onClick = onOpenInfo,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                uiState.satellitesByConstellation.forEach { (constellation, sats) ->
                    item(key = "group-${constellation.name}") {
                        ConstellationGroupCard(constellation = constellation, satellites = sats)
                    }
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

/** "N vus · M fix" pill in the top bar (screen 1d). */
@Composable
private fun StatusChip(inView: Int, usedInFix: Int) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(StatusChipBg)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(QualityGood, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.sats_status_chip_fmt, inView, usedInFix),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkOnSurface,
        )
    }
}

/** The sky-plot card: polar az/el plot plus a per-constellation legend row (screen 1d). */
@Composable
private fun SkyPlotCard(satellites: List<SatelliteInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SkyPlot(satellites = satellites, modifier = Modifier.fillMaxWidth())
            val present = satellites.map { it.constellation }.distinct().sortedBy { it.ordinal }
            if (present.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    present.forEach { constellation ->
                        LegendChip(constellation)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendChip(constellation: Constellation) {
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(StatusChipBg)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(constellation.color(), CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = constellation.displayName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = CairnStone)
    }
}

/** "Globe 3D" / "Constellations" shortcut button (screen 1d). */
@Composable
private fun ShortcutButton(icon: Char, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(width = 1.dp, color = OutlineSubtle, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(icon = icon, contentDescription = null, tint = CairnGreen, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** One constellation's satellites, grouped in a single card: header, then one row per satellite. */
@Composable
private fun ConstellationGroupCard(constellation: Constellation, satellites: List<SatelliteInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ConstellationGroupHeader(constellation = constellation, count = satellites.size)
            satellites.forEach { satellite ->
                SatelliteRow(satellite)
            }
        }
    }
}

/** Header row for one constellation group: color dot, name, satellite count. */
@Composable
private fun ConstellationGroupHeader(constellation: Constellation, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = constellation.color(), shape = CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = constellation.displayName,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(text = count.toString(), fontSize = 12.sp, color = LabelMuted)
    }
}

@Composable
private fun SatelliteRow(satellite: SatelliteInfo) {
    val color = satellite.constellation.color()
    // Satellites tracked but not used in the fix are dimmed so the fixed ones stand out.
    val contentAlpha = if (satellite.usedInFix) 1f else 0.55f

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${satellite.constellation.shortPrefix()}%02d".format(satellite.svid),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = MonoFontFamily,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            modifier = Modifier.width(38.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(
                R.string.sat_elevation_azimuth,
                satellite.elevationDegrees.roundToInt(),
                satellite.azimuthDegrees.roundToInt(),
            ),
            fontSize = 11.5.sp,
            color = ValueMuted.copy(alpha = contentAlpha),
            modifier = Modifier.width(110.dp),
        )
        SignalBar(
            cn0DbHz = satellite.cn0DbHz,
            color = color.copy(alpha = contentAlpha),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "%.0f".format(satellite.cn0DbHz),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = MonoFontFamily,
            color = CairnStone.copy(alpha = contentAlpha),
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
    }
}

/** Horizontal signal bar filled proportionally to the C/N0 value. */
@Composable
private fun SignalBar(cn0DbHz: Float, color: Color, modifier: Modifier = Modifier) {
    // Animate level changes so successive GNSS snapshots slide instead of flickering.
    val fraction by animateFloatAsState(
        targetValue = ((cn0DbHz - CN0_RANGE_MIN_DBHZ) / CN0_RANGE_SPAN_DBHZ).coerceIn(CN0_MIN_FRACTION, 1f),
        label = "cn0",
    )
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(SkyPlotInnerRing),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
    }
}

/** Single-letter satellite ID prefix used in the compact per-row code (e.g. "G05", "R10"). */
private fun Constellation.shortPrefix(): String = when (this) {
    Constellation.GPS -> "G"
    Constellation.GLONASS -> "R"
    Constellation.GALILEO -> "E"
    Constellation.BEIDOU -> "C"
    Constellation.QZSS -> "J"
    Constellation.SBAS -> "S"
    Constellation.IRNSS -> "I"
    Constellation.UNKNOWN -> "?"
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
