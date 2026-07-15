package app.matthieu.cairngps.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.NorthReference
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.ThemeMode
import app.matthieu.cairngps.ui.common.SegmentedToggle
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.Sym

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(stringResource(R.string.settings_display_section)) {
                SettingCard(stringResource(R.string.settings_coordinate_format)) {
                    SegmentedToggle(
                        options = listOf(
                            stringResource(R.string.coordinate_format_decimal),
                            stringResource(R.string.coordinate_format_dms),
                        ),
                        selectedIndex = if (coordinateFormat == CoordinateFormat.DECIMAL) 0 else 1,
                        onSelect = { index ->
                            onCoordinateFormatChange(if (index == 0) CoordinateFormat.DECIMAL else CoordinateFormat.DMS)
                        },
                    )
                }
                SettingCard(stringResource(R.string.settings_theme)) {
                    SegmentedToggle(
                        options = listOf(
                            stringResource(R.string.theme_system),
                            stringResource(R.string.theme_light),
                            stringResource(R.string.theme_dark),
                        ),
                        selectedIndex = when (themeMode) {
                            ThemeMode.SYSTEM -> 0
                            ThemeMode.LIGHT -> 1
                            ThemeMode.DARK -> 2
                        },
                        onSelect = { index ->
                            onThemeModeChange(
                                when (index) {
                                    0 -> ThemeMode.SYSTEM
                                    1 -> ThemeMode.LIGHT
                                    else -> ThemeMode.DARK
                                },
                            )
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Sym(
                            icon = Glyph.BatterySaver,
                            contentDescription = null,
                            size = 16.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.settings_theme_battery_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_navigation_section)) {
                SettingCard(stringResource(R.string.settings_north_reference)) {
                    SegmentedToggle(
                        options = listOf(
                            stringResource(R.string.compass_north_true),
                            stringResource(R.string.compass_north_magnetic),
                        ),
                        selectedIndex = if (northReference == NorthReference.TRUE) 0 else 1,
                        onSelect = { index ->
                            onNorthReferenceChange(if (index == 0) NorthReference.TRUE else NorthReference.MAGNETIC)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        content()
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            content()
        }
    }
}
