package app.matthieu.cairngps.ui.interference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.domain.InterferenceStatus
import app.matthieu.cairngps.domain.SignalCheck
import app.matthieu.cairngps.domain.TimeCheck
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.QualityGood
import app.matthieu.cairngps.ui.theme.QualityPoor
import app.matthieu.cairngps.ui.theme.QualityUnknown
import app.matthieu.cairngps.ui.theme.Sym
import java.util.Locale

/** Explains what the jamming/spoofing detection does and, above all, what it can't do. */
@Composable
fun InterferenceInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Sym(icon = Glyph.SatelliteAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.gnss_interference_info_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                InfoParagraph(
                    title = stringResource(R.string.gnss_interference_info_what_title),
                    body = stringResource(R.string.gnss_interference_info_what),
                )
                InfoParagraph(
                    title = stringResource(R.string.gnss_interference_info_how_title),
                    body = stringResource(R.string.gnss_interference_info_how),
                )
                InfoParagraph(
                    title = stringResource(R.string.gnss_interference_info_limits_title),
                    body = stringResource(R.string.gnss_interference_info_limits),
                )
                Text(
                    text = stringResource(R.string.gnss_interference_info_advice),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun InfoParagraph(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Red warning shown only while jamming or spoofing is suspected. */
@Composable
fun InterferenceAlertBanner(status: InterferenceStatus, modifier: Modifier = Modifier) {
    if (!status.isAlert) return
    val bands = status.affectedBands.joinToString(", ") { it.label }
    val lines = buildList {
        when (status.signalCheck) {
            SignalCheck.JAMMING -> add(stringResource(R.string.gnss_alert_jamming_body_fmt, bands))
            SignalCheck.SPOOFING -> add(stringResource(R.string.gnss_alert_spoofing_signal_body_fmt, bands))
            else -> Unit
        }
        if (status.timeCheck == TimeCheck.SPOOFING) {
            add(stringResource(R.string.gnss_alert_spoofing_time_body_fmt, formatSeconds(status.timeOffsetMs)))
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(QualityPoor.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Sym(icon = Glyph.Warning, contentDescription = null, filled = true, tint = QualityPoor)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(
                    if (status.isSpoofingSuspected) R.string.gnss_alert_spoofing_title
                    else R.string.gnss_alert_jamming_title,
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            lines.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** State of both checks, with a button opening [InterferenceInfoDialog]. */
@Composable
fun InterferenceMonitorCard(
    status: InterferenceStatus,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.gnss_monitor_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onInfo) {
                    Sym(
                        icon = Glyph.Info,
                        contentDescription = stringResource(R.string.gnss_interference_info_cd),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            CheckRow(
                label = stringResource(R.string.gnss_monitor_signal_label),
                value = signalCheckText(status.signalCheck),
                color = signalCheckColor(status.signalCheck),
            )
            CheckRow(
                label = stringResource(R.string.gnss_monitor_time_label),
                value = timeCheckText(status),
                color = timeCheckColor(status.timeCheck),
            )
        }
    }
}

@Composable
private fun CheckRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun signalCheckText(check: SignalCheck): String = stringResource(
    when (check) {
        SignalCheck.WAITING -> R.string.gnss_state_waiting
        SignalCheck.UNSUPPORTED -> R.string.gnss_state_unsupported
        SignalCheck.WARMING_UP -> R.string.gnss_state_warming_up
        SignalCheck.OK -> R.string.gnss_state_ok
        SignalCheck.JAMMING -> R.string.gnss_state_jamming
        SignalCheck.SPOOFING -> R.string.gnss_state_spoofing
    },
)

private fun signalCheckColor(check: SignalCheck): Color = when (check) {
    SignalCheck.OK -> QualityGood
    SignalCheck.JAMMING, SignalCheck.SPOOFING -> QualityPoor
    SignalCheck.WAITING, SignalCheck.UNSUPPORTED, SignalCheck.WARMING_UP -> QualityUnknown
}

@Composable
private fun timeCheckText(status: InterferenceStatus): String = when (status.timeCheck) {
    TimeCheck.WAITING -> stringResource(R.string.gnss_state_waiting)
    TimeCheck.UNAVAILABLE -> stringResource(R.string.gnss_state_time_unavailable)
    TimeCheck.OK -> stringResource(R.string.gnss_state_time_ok_fmt, formatSeconds(status.timeOffsetMs))
    TimeCheck.SPOOFING -> stringResource(R.string.gnss_state_time_spoofing_fmt, formatSeconds(status.timeOffsetMs))
}

private fun timeCheckColor(check: TimeCheck): Color = when (check) {
    TimeCheck.OK -> QualityGood
    TimeCheck.SPOOFING -> QualityPoor
    TimeCheck.WAITING, TimeCheck.UNAVAILABLE -> QualityUnknown
}

private fun formatSeconds(ms: Long?): String =
    ms?.let { String.format(Locale.getDefault(), "%+.1f", it / 1_000.0) } ?: "--"
