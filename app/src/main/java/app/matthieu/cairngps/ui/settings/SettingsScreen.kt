package app.matthieu.cairngps.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.NorthReference
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.ThemeMode

/** Route: wires up the shared [SettingsViewModel] and renders the settings UI. */
@Composable
fun SettingsRoute(
    repository: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(repository))
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    SettingsScreen(
        coordinateFormat = settings.coordinateFormat,
        onCoordinateFormatChange = viewModel::setCoordinateFormat,
        themeMode = settings.themeMode,
        onThemeModeChange = viewModel::setThemeMode,
        northReference = settings.northReference,
        onNorthReferenceChange = viewModel::setNorthReference,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    coordinateFormat: CoordinateFormat,
    onCoordinateFormatChange: (CoordinateFormat) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    northReference: NorthReference,
    onNorthReferenceChange: (NorthReference) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = backLabel },
                    ) {
                        // Text glyph avoids depending on the large material-icons-extended artifact.
                        Text(
                            text = "←",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(vertical = 8.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_theme))
            Column(Modifier.selectableGroup()) {
                ThemeModeOption(
                    title = stringResource(R.string.theme_system),
                    selected = themeMode == ThemeMode.SYSTEM,
                    onSelect = { onThemeModeChange(ThemeMode.SYSTEM) },
                )
                ThemeModeOption(
                    title = stringResource(R.string.theme_light),
                    selected = themeMode == ThemeMode.LIGHT,
                    onSelect = { onThemeModeChange(ThemeMode.LIGHT) },
                )
                ThemeModeOption(
                    title = stringResource(R.string.theme_dark),
                    selected = themeMode == ThemeMode.DARK,
                    onSelect = { onThemeModeChange(ThemeMode.DARK) },
                )
            }

            SectionHeader(stringResource(R.string.settings_coordinate_format))
            Column(Modifier.selectableGroup()) {
                CoordinateFormatOption(
                    title = stringResource(R.string.coordinate_format_decimal),
                    example = stringResource(R.string.coordinate_format_decimal_example),
                    selected = coordinateFormat == CoordinateFormat.DECIMAL,
                    onSelect = { onCoordinateFormatChange(CoordinateFormat.DECIMAL) },
                )
                CoordinateFormatOption(
                    title = stringResource(R.string.coordinate_format_dms),
                    example = stringResource(R.string.coordinate_format_dms_example),
                    selected = coordinateFormat == CoordinateFormat.DMS,
                    onSelect = { onCoordinateFormatChange(CoordinateFormat.DMS) },
                )
            }

            SectionHeader(stringResource(R.string.settings_north_reference))
            Column(Modifier.selectableGroup()) {
                ThemeModeOption(
                    title = stringResource(R.string.compass_north_magnetic),
                    selected = northReference == NorthReference.MAGNETIC,
                    onSelect = { onNorthReferenceChange(NorthReference.MAGNETIC) },
                )
                ThemeModeOption(
                    title = stringResource(R.string.compass_north_true),
                    selected = northReference == NorthReference.TRUE,
                    onSelect = { onNorthReferenceChange(NorthReference.TRUE) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun CoordinateFormatOption(
    title: String,
    example: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(
            modifier = Modifier.padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = example,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeModeOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
