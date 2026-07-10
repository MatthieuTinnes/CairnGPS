package app.matthieu.cairngps.ui.compass

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.CompassRepository
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.CairnGpsTheme
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.QualityPoor
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Screen route. Wires up the [CompassViewModel] and binds the sensor subscription to the screen
 * lifecycle: listening starts in `ON_START` and stops in `ON_STOP`, so the magnetometer is only
 * powered while the screen is visible. Only ever composed once location permission is granted.
 */
@SuppressLint("MissingPermission")
@Composable
fun CompassRoute(
    compassRepository: CompassRepository,
    locationRepository: LocationRepository,
    modifier: Modifier = Modifier,
) {
    val viewModel: CompassViewModel =
        viewModel(factory = CompassViewModel.factory(compassRepository, locationRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        viewModel.startTracking()
        onStopOrDispose { viewModel.stopTracking() }
    }

    CompassScreen(
        uiState = uiState,
        onUseTrueNorthChange = viewModel::setUseTrueNorth,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompassScreen(
    uiState: CompassUiState,
    onUseTrueNorthChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.compass_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            when {
                !uiState.sensorAvailable -> SensorUnavailable()
                else -> {
                    if (uiState.needsCalibration) {
                        CalibrationBanner()
                    }
                    CompassDial(uiState)
                    NorthReferenceToggle(
                        useTrueNorth = uiState.useTrueNorth,
                        trueNorthAvailable = uiState.trueNorthAvailable,
                        onUseTrueNorthChange = onUseTrueNorthChange,
                    )
                    DeclinationInfo(uiState)
                }
            }
        }
    }
}

@Composable
private fun SensorUnavailable() {
    Text(
        text = stringResource(R.string.compass_sensor_unavailable),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CalibrationBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "⚠", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.compass_calibration_needed),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.compass_calibration_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** The rotating compass rose with the current heading and cardinal point read out at its center. */
@Composable
private fun CompassDial(uiState: CompassUiState) {
    val roseLabels = stringArrayResource(R.array.compass_rose_labels)
    val cardinals = stringArrayResource(R.array.compass_cardinals)

    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardinalColor = MaterialTheme.colorScheme.onSurface
    val northColor = QualityPoor
    val southColor = CairnStone
    // Amber, high-contrast heading index — kept distinct from the red north needle.
    val indexColor = CairnAmber
    val hubColor = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCompassRose(
                heading = uiState.headingDegrees,
                roseLabels = roseLabels,
                tickColor = tickColor,
                cardinalColor = cardinalColor,
                northColor = northColor,
                southColor = southColor,
                indexColor = indexColor,
                hubColor = hubColor,
                textMeasurer = textMeasurer,
            )
        }

        if (uiState.hasData) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    // Monospace keeps the width stable as the value changes. The degree sign is a
                    // small superscript, balanced by an invisible copy on the left so the DIGITS
                    // stay centered (and the cardinal below lines up under the middle digit).
                    text = buildAnnotatedString {
                        val degree = SpanStyle(
                            fontSize = 20.sp,
                            baselineShift = BaselineShift.Superscript,
                        )
                        withStyle(degree.copy(color = Color.Transparent)) { append("°") }
                        append("${uiState.headingDegrees.roundToInt() % 360}")
                        withStyle(degree) { append("°") }
                    },
                    fontSize = 38.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = cardinals.getOrElse(uiState.cardinalIndex) { "" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NorthReferenceToggle(
    useTrueNorth: Boolean,
    trueNorthAvailable: Boolean,
    onUseTrueNorthChange: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !useTrueNorth,
            onClick = { onUseTrueNorthChange(false) },
            label = { Text(stringResource(R.string.compass_north_magnetic)) },
        )
        FilterChip(
            selected = useTrueNorth,
            onClick = { onUseTrueNorthChange(true) },
            enabled = trueNorthAvailable,
            label = { Text(stringResource(R.string.compass_north_true)) },
        )
    }
}

@Composable
private fun DeclinationInfo(uiState: CompassUiState) {
    val roseLabels = stringArrayResource(R.array.compass_rose_labels)
    val declination = uiState.declinationDegrees

    val text = if (declination == null) {
        stringResource(R.string.compass_true_north_unavailable)
    } else {
        // roseLabels = [N, E, S, O]; declination is positive toward the east.
        val direction = if (declination >= 0f) roseLabels[1] else roseLabels[3]
        stringResource(
            R.string.compass_declination,
            "%.1f".format(kotlin.math.abs(declination)),
            direction,
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * Draws the compass rose rotated by `-heading` so its north mark points to real (magnetic or true)
 * north, with the needle's red half pointing north. A fixed index at the top marks the heading.
 */
private fun DrawScope.drawCompassRose(
    heading: Float,
    roseLabels: Array<String>,
    tickColor: Color,
    cardinalColor: Color,
    northColor: Color,
    southColor: Color,
    indexColor: Color,
    hubColor: Color,
    textMeasurer: TextMeasurer,
) {
    // Reserve a margin at the edge for the fixed heading index, so it sits clear of the ticks.
    val indexMargin = 16.dp.toPx()
    val radius = size.minDimension / 2f - indexMargin
    val center = Offset(size.width / 2f, size.height / 2f)

    // Outer ring.
    drawCircle(
        color = tickColor.copy(alpha = 0.25f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )

    rotate(degrees = -heading, pivot = center) {
        // Tick marks every 15°, longer and thicker on the 45° marks.
        for (deg in 0 until 360 step 15) {
            val isMajor = deg % 45 == 0
            val rad = Math.toRadians(deg.toDouble())
            val sinV = sin(rad).toFloat()
            val cosV = cos(rad).toFloat()
            val outer = radius - 4.dp.toPx()
            val inner = outer - (if (isMajor) 18.dp.toPx() else 9.dp.toPx())
            drawLine(
                color = tickColor,
                start = Offset(center.x + inner * sinV, center.y - inner * cosV),
                end = Offset(center.x + outer * sinV, center.y - outer * cosV),
                strokeWidth = (if (isMajor) 3f else 1.5f).dp.toPx(),
            )
        }

        // Cardinal letters at N/E/S/O (roseLabels order), N highlighted like the needle.
        roseLabels.forEachIndexed { i, label ->
            val rad = Math.toRadians(i * 90.0)
            val labelRadius = radius - 42.dp.toPx()
            val x = center.x + labelRadius * sin(rad).toFloat()
            val y = center.y - labelRadius * cos(rad).toFloat()
            val layout = textMeasurer.measure(
                text = label,
                style = TextStyle(
                    color = if (i == 0) northColor else cardinalColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f),
            )
        }

        // The two-tone needle: red half toward north, stone half toward south.
        val needleLength = radius - 60.dp.toPx()
        val halfWidth = 9.dp.toPx()
        drawPath(
            Path().apply {
                moveTo(center.x, center.y - needleLength)
                lineTo(center.x - halfWidth, center.y)
                lineTo(center.x + halfWidth, center.y)
                close()
            },
            color = northColor,
        )
        drawPath(
            Path().apply {
                moveTo(center.x, center.y + needleLength)
                lineTo(center.x - halfWidth, center.y)
                lineTo(center.x + halfWidth, center.y)
                close()
            },
            color = southColor,
        )
    }

    // Solid hub behind the center readout so the heading and cardinal stay legible over the needle.
    val hubRadius = 58.dp.toPx()
    drawCircle(color = hubColor, radius = hubRadius, center = center)
    drawCircle(
        color = tickColor.copy(alpha = 0.3f),
        radius = hubRadius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )

    // Fixed heading index, drawn last so it stays on top of the ticks. Points down at the ring from
    // the reserved margin; amber to read clearly and stand apart from the red north needle.
    drawPath(
        Path().apply {
            moveTo(center.x, center.y - radius)
            lineTo(center.x - 11.dp.toPx(), center.y - radius - 14.dp.toPx())
            lineTo(center.x + 11.dp.toPx(), center.y - radius - 14.dp.toPx())
            close()
        },
        color = indexColor,
    )
}

@Preview(showBackground = true)
@Composable
private fun CompassScreenPreview() {
    CairnGpsTheme {
        CompassScreen(
            uiState = CompassUiState(
                sensorAvailable = true,
                hasData = true,
                headingDegrees = 42f,
                cardinalIndex = 1,
                useTrueNorth = false,
                declinationDegrees = 1.5f,
                needsCalibration = true,
            ),
            onUseTrueNorthChange = {},
        )
    }
}
