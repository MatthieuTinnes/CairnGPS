package app.matthieu.cairngps.ui.satellites

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.LightStatusText
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme

/**
 * One term explained in the [SatelliteHelpSheet]: its name, the abbreviation used for it in the
 * satellite list's column header ([SatelliteColumnHeader]), and a description.
 */
private data class HelpTerm(
    @StringRes val nameRes: Int,
    @StringRes val colRes: Int?,
    @StringRes val descRes: Int,
)

private val HELP_TERMS = listOf(
    HelpTerm(R.string.sats_help_sat_id_name, R.string.sats_col_sat, R.string.sats_help_sat_id_desc),
    HelpTerm(R.string.sats_help_signal_name, R.string.sats_col_signal, R.string.sats_help_signal_desc),
    HelpTerm(R.string.sats_help_elevation_name, R.string.sats_col_el, R.string.sats_help_elevation_desc),
    HelpTerm(R.string.sats_help_azimuth_name, R.string.sats_col_az, R.string.sats_help_azimuth_desc),
    HelpTerm(R.string.sats_help_fixed_name, colRes = null, R.string.sats_help_fixed_desc),
)

/**
 * Bottom sheet explaining the terms shown on the satellites screen (satellite ID, signal, elevation,
 * azimuth, fixed vs. seen), reached from its top bar's info button. Content is static, so this sheet
 * needs no ViewModel — same idea as [ConstellationInfoScreen], but as a sheet rather than a page since
 * it's a quick reference meant to be dismissed back onto the live data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteHelpSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.sats_help_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                HELP_TERMS.forEach { term ->
                    HelpTermRow(term)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HelpTermRow(term: HelpTerm) {
    val light = LocalIsLightTheme.current
    val textColor = if (light) LightStatusText else CairnStone
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = stringResource(term.nameRes), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (term.colRes != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(term.colRes),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = LabelMuted,
                )
            }
        }
        Text(
            text = stringResource(term.descRes),
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = textColor,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
