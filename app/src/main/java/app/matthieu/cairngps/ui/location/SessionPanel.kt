package app.matthieu.cairngps.ui.location

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.LocationData
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.domain.format.AccuracyQuality
import app.matthieu.cairngps.domain.format.accuracyQuality
import app.matthieu.cairngps.domain.format.distanceUnitLabel
import app.matthieu.cairngps.domain.format.formatAccuracy
import app.matthieu.cairngps.domain.format.formatAltitude
import app.matthieu.cairngps.domain.format.formatDistance
import app.matthieu.cairngps.domain.format.formatDuration
import app.matthieu.cairngps.domain.format.formatElevation
import app.matthieu.cairngps.domain.format.formatSpeed
import app.matthieu.cairngps.domain.format.shortUnitLabel
import app.matthieu.cairngps.domain.format.speedUnitLabel
import app.matthieu.cairngps.ui.common.CardTitle
import app.matthieu.cairngps.ui.common.DataCard
import app.matthieu.cairngps.ui.common.PulsingDot
import app.matthieu.cairngps.ui.recording.RecordingUiState
import app.matthieu.cairngps.ui.theme.DashText
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.LightBorderSubtle
import app.matthieu.cairngps.ui.theme.LightRecChipBg
import app.matthieu.cairngps.ui.theme.LightRecChipText
import app.matthieu.cairngps.ui.theme.LightStatusText
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.OutlineSubtle
import app.matthieu.cairngps.ui.theme.QualityGood
import app.matthieu.cairngps.ui.theme.QualityMedium
import app.matthieu.cairngps.ui.theme.QualityPoor
import app.matthieu.cairngps.ui.theme.QualityUnknown
import app.matthieu.cairngps.ui.theme.RecChipBg
import app.matthieu.cairngps.ui.theme.RecChipText
import app.matthieu.cairngps.ui.theme.StatusChipBg
import app.matthieu.cairngps.ui.theme.Sym
import kotlinx.coroutines.launch

// LabelMuted and CairnStone (tertiary) are too light on the light theme's white cards, so labels
// and units switch to the darker status-line tone there.
@Composable
private fun mutedLabelColor(): Color = if (LocalIsLightTheme.current) LightStatusText else LabelMuted

@Composable
private fun unitColor(): Color =
    if (LocalIsLightTheme.current) LightStatusText else MaterialTheme.colorScheme.tertiary

@Composable
private fun borderColor(): Color = if (LocalIsLightTheme.current) LightBorderSubtle else OutlineSubtle

/**
 * Compact recording summary docked above the bottom actions (design B): always visible while
 * recording, without scrolling. Tapping it opens [SessionSheet].
 */
@Composable
internal fun SessionBar(
    uiState: RecordingUiState,
    unitSystem: UnitSystem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val light = LocalIsLightTheme.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .clip(shape)
            .background(if (light) MaterialTheme.colorScheme.surface else StatusChipBg)
            .border(1.dp, borderColor(), shape)
            .clickable(
                onClickLabel = stringResource(R.string.recording_expand),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(borderColor(), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (uiState.rejectedAccuracyMeters != null) {
                Sym(icon = Glyph.Warning, contentDescription = null, size = 18.dp, tint = QualityPoor)
            } else {
                PulsingDot(color = QualityPoor, size = 10.dp)
            }
            Spacer(Modifier.width(12.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BarStat(
                    label = stringResource(R.string.recording_distance),
                    value = formatDistance(uiState.distanceMeters, unitSystem),
                    unit = distanceUnitLabel(unitSystem),
                    modifier = Modifier.weight(1f),
                )
                BarStat(
                    label = stringResource(R.string.recording_elevation_gain),
                    value = "+${formatElevation(uiState.elevationGain, unitSystem)}",
                    unit = shortUnitLabel(unitSystem),
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                BarStat(
                    label = stringResource(R.string.recording_duration),
                    value = formatDuration(uiState.elapsedMs),
                    unit = null,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.width(4.dp))
            Sym(
                icon = Glyph.ChevronRight,
                contentDescription = null,
                tint = unitColor(),
                modifier = Modifier.rotate(-90f),
            )
        }
    }
}

@Composable
private fun BarStat(
    label: String,
    value: String,
    unit: String?,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier) {
        AutoSizeValue(value = value, unit = unit, maxFontSize = 20.sp, minFontSize = 13.sp, color = valueColor)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = mutedLabelColor())
    }
}

/**
 * Full-screen view of the running session (design C′): duration first, then distance, the four
 * secondary stats as large tiles, and the live speed/altitude since the Position screen is hidden
 * underneath. [actions] is the same waypoint/stop row as the Position screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionSheet(
    recording: RecordingUiState,
    location: LocationUiState,
    unitSystem: UnitSystem,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            SessionSheetHeader(
                horizontalAccuracy = if (location.hasFix) location.fix?.horizontalAccuracy else null,
                unitSystem = unitSystem,
                onCollapse = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                recording.rejectedAccuracyMeters?.let { accuracy ->
                    NoisySignalBanner(accuracy = accuracy, unitSystem = unitSystem)
                }
                DurationDistanceCard(recording = recording, unitSystem = unitSystem)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SessionTile(
                        icon = Glyph.TrendingUp,
                        label = stringResource(R.string.recording_elevation_gain),
                        value = "+${formatElevation(recording.elevationGain, unitSystem)}",
                        unit = shortUnitLabel(unitSystem),
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    SessionTile(
                        icon = Glyph.TrendingUp,
                        mirrored = true,
                        label = stringResource(R.string.recording_elevation_loss),
                        value = "−${formatElevation(recording.elevationLoss, unitSystem)}",
                        unit = shortUnitLabel(unitSystem),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SessionTile(
                        icon = Glyph.Speed,
                        label = stringResource(R.string.recording_avg_speed),
                        value = formatSpeed(recording.averageSpeed, unitSystem),
                        unit = speedUnitLabel(unitSystem),
                        modifier = Modifier.weight(1f),
                    )
                    SessionTile(
                        icon = Glyph.Speed,
                        label = stringResource(R.string.recording_max_speed),
                        value = formatSpeed(recording.maxSpeed, unitSystem),
                        unit = speedUnitLabel(unitSystem),
                        modifier = Modifier.weight(1f),
                    )
                }
                LiveReadingsCard(location = location, unitSystem = unitSystem)
            }
            actions()
        }
    }
}

@Composable
private fun SessionSheetHeader(
    horizontalAccuracy: Float?,
    unitSystem: UnitSystem,
    onCollapse: () -> Unit,
) {
    val light = LocalIsLightTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Sym(
                icon = Glyph.ChevronRight,
                contentDescription = stringResource(R.string.recording_collapse),
                size = 28.dp,
                modifier = Modifier.rotate(90f),
            )
        }
        PulsingDot(color = QualityPoor, size = 10.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.recording_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        val qualityColor = when (accuracyQuality(horizontalAccuracy)) {
            AccuracyQuality.GOOD -> QualityGood
            AccuracyQuality.MEDIUM -> QualityMedium
            AccuracyQuality.POOR -> QualityPoor
            AccuracyQuality.UNKNOWN -> QualityUnknown
        }
        Row(
            modifier = Modifier
                .background(if (light) MaterialTheme.colorScheme.surface else StatusChipBg, CircleShape)
                .border(1.dp, if (light) LightBorderSubtle else Color.Transparent, CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(qualityColor, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (horizontalAccuracy != null) {
                    "±${formatAccuracy(horizontalAccuracy, unitSystem)} ${shortUnitLabel(unitSystem)}"
                } else {
                    dashAccuracy(unitSystem)
                },
                style = MaterialTheme.typography.labelLarge,
                fontFamily = MonoFontFamily,
                color = if (horizontalAccuracy != null) unitColor() else DashText,
            )
        }
    }
}

@Composable
private fun NoisySignalBanner(accuracy: Float, unitSystem: UnitSystem) {
    val light = LocalIsLightTheme.current
    val textColor = if (light) LightRecChipText else RecChipText
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (light) LightRecChipBg else RecChipBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Sym(icon = Glyph.Warning, contentDescription = null, size = 20.dp, tint = textColor)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(
                R.string.recording_signal_too_noisy_fmt,
                formatAccuracy(accuracy, unitSystem),
                shortUnitLabel(unitSystem),
                formatAccuracy(LocationData.MAX_ACCURACY_METERS, unitSystem),
                shortUnitLabel(unitSystem),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
    }
}

@Composable
private fun DurationDistanceCard(recording: RecordingUiState, unitSystem: UnitSystem) {
    DataCard {
        CardTitle(stringResource(R.string.recording_duration))
        AutoSizeValue(
            value = formatDuration(recording.elapsedMs),
            unit = null,
            maxFontSize = 64.sp,
            minFontSize = 32.sp,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = borderColor())
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.recording_distance))
            Spacer(Modifier.width(16.dp))
            Spacer(Modifier.weight(1f))
            AutoSizeValue(
                value = formatDistance(recording.distanceMeters, unitSystem),
                unit = distanceUnitLabel(unitSystem),
                maxFontSize = 40.sp,
                minFontSize = 20.sp,
                modifier = Modifier.weight(3f, fill = false),
            )
        }
    }
}

@Composable
private fun SessionTile(
    icon: Char,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    mirrored: Boolean = false,
) {
    DataCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sym(
                icon = icon,
                contentDescription = null,
                size = 20.dp,
                tint = accent ?: unitColor(),
                // The font subset has trending_up but not trending_down: mirroring it gives the
                // same descending arrow.
                modifier = if (mirrored) Modifier.graphicsLayer { scaleY = -1f } else Modifier,
            )
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = mutedLabelColor())
        }
        Spacer(Modifier.height(6.dp))
        AutoSizeValue(
            value = value,
            unit = unit,
            maxFontSize = 34.sp,
            minFontSize = 16.sp,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Current speed and altitude, so the sheet stays usable without going back to Position. */
@Composable
private fun LiveReadingsCard(location: LocationUiState, unitSystem: UnitSystem) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor(), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LiveReading(
            label = stringResource(R.string.recording_current_speed),
            value = if (location.hasFix) formatSpeed(location.fix?.speed, unitSystem) else dashSpeed(),
            unit = speedUnitLabel(unitSystem),
            hasValue = location.hasFix,
            modifier = Modifier.weight(1f),
        )
        LiveReading(
            label = stringResource(R.string.label_altitude),
            value = if (location.hasFix) formatAltitude(location.fix?.altitude, unitSystem) else DASH_ALTITUDE,
            unit = shortUnitLabel(unitSystem),
            hasValue = location.hasFix,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LiveReading(label: String, value: String, unit: String, hasValue: Boolean, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = mutedLabelColor())
        AutoSizeValue(
            value = value,
            unit = unit,
            maxFontSize = 24.sp,
            minFontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = if (hasValue) MaterialTheme.colorScheme.onSurface else DashText,
        )
    }
}

/**
 * Monospace value with a smaller trailing unit, shrinking to fit its width on one line — large
 * font scales or long values (e.g. 10:00:00, 123,45 km) would otherwise wrap mid-number.
 */
@Composable
private fun AutoSizeValue(
    value: String,
    unit: String?,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    val unitColor = unitColor()
    val text = buildAnnotatedString {
        append(value)
        if (unit != null) {
            withStyle(
                SpanStyle(
                    fontSize = 0.55.em,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    color = unitColor,
                ),
            ) { append(" $unit") }
        }
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(fontFamily = MonoFontFamily, fontWeight = fontWeight, color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = maxFontSize),
    )
}
