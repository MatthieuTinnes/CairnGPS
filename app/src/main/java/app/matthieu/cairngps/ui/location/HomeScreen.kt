package app.matthieu.cairngps.ui.location

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.LocationData
import app.matthieu.cairngps.data.LocationRepository

/**
 * Screen route: wires up the [LocationViewModel], starts tracking as soon as it is shown
 * (it is only ever composed once the location permission has been granted), and renders
 * the current fix state.
 */
@Composable
fun HomeRoute(
    repository: LocationRepository,
    modifier: Modifier = Modifier,
) {
    val viewModel: LocationViewModel = viewModel(factory = LocationViewModel.factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Permission is guaranteed granted at this point by LocationPermissionGate.
    @SuppressLint("MissingPermission")
    LaunchedEffect(Unit) {
        viewModel.startTracking()
    }

    HomeScreen(uiState = uiState, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    uiState: LocationUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (uiState) {
                LocationUiState.WaitingForFix -> WaitingForFix()
                is LocationUiState.Fixed -> FixObtained(uiState.data)
            }
        }
    }
}

@Composable
private fun WaitingForFix() {
    CircularProgressIndicator()
    Spacer(Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.waiting_for_fix),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun FixObtained(data: LocationData) {
    Text(
        text = stringResource(R.string.fix_obtained),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FixRow("Latitude", "%.6f°".format(data.latitude))
            FixRow("Longitude", "%.6f°".format(data.longitude))
            FixRow("Altitude", "%.1f m".format(data.altitude))
            FixRow("Vitesse", "%.1f m/s".format(data.speed))
            FixRow("Précision horiz.", "±%.1f m".format(data.horizontalAccuracy))
            FixRow(
                "Précision vert.",
                data.verticalAccuracy?.let { "±%.1f m".format(it) } ?: "—",
            )
        }
    }
}

@Composable
private fun FixRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
