package app.matthieu.cairngps.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.BackupImportError
import app.matthieu.cairngps.data.BackupRepository
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.GamificationFlagsRepository
import app.matthieu.cairngps.data.NorthReference
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.ThemeMode
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.demo.DemoMode
import app.matthieu.cairngps.ui.common.SegmentedToggle
import app.matthieu.cairngps.ui.theme.AboutDivider
import app.matthieu.cairngps.ui.theme.AboutDividerLight
import app.matthieu.cairngps.ui.theme.AboutMuted
import app.matthieu.cairngps.ui.theme.AboutTrailingIconLight
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.Sym
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.SharedFlow

// Default export file name, e.g. "cairn-backup-2026-07-17.json" — sortable, filesystem-safe.
private val BackupFileNameDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

// Maps the current per-app locale override to the language SegmentedToggle's index. An empty
// list means "follow the system language" (the default, nothing set yet).
private fun languageIndexOf(locales: LocaleListCompat): Int = when {
    locales.isEmpty -> 0
    locales[0]?.language == "fr" -> 1
    else -> 2
}

/** Route: wires up the shared [SettingsViewModel] and renders the settings UI. */
@Composable
fun SettingsRoute(
    repository: SettingsRepository,
    backupRepository: BackupRepository,
    gamificationFlagsRepository: GamificationFlagsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModel.factory(repository, gamificationFlagsRepository))
    val backupViewModel: BackupViewModel =
        viewModel(factory = BackupViewModel.factory(backupRepository, gamificationFlagsRepository))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isBackupWorking by backupViewModel.isBackupWorking.collectAsStateWithLifecycle()

    SettingsScreen(
        coordinateFormat = settings.coordinateFormat,
        onCoordinateFormatChange = viewModel::setCoordinateFormat,
        themeMode = settings.themeMode,
        onThemeModeChange = viewModel::setThemeMode,
        // Language is deliberately not part of AppSettings/DataStore: AppCompat's per-app
        // language API owns its own persistence (autoStoreLocales in the manifest) and
        // integrates with the Android 13+ system language picker.
        languageIndex = languageIndexOf(AppCompatDelegate.getApplicationLocales()),
        onLanguageChange = { index ->
            AppCompatDelegate.setApplicationLocales(
                when (index) {
                    1 -> LocaleListCompat.forLanguageTags("fr")
                    2 -> LocaleListCompat.forLanguageTags("en")
                    else -> LocaleListCompat.getEmptyLocaleList()
                },
            )
        },
        northReference = settings.northReference,
        onNorthReferenceChange = viewModel::setNorthReference,
        unitSystem = settings.unitSystem,
        onUnitSystemChange = viewModel::setUnitSystem,
        isBackupWorking = isBackupWorking,
        backupEvents = backupViewModel.backupEvents,
        onExport = backupViewModel::exportBackup,
        onImport = backupViewModel::importBackup,
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
    languageIndex: Int,
    onLanguageChange: (Int) -> Unit,
    northReference: NorthReference,
    onNorthReferenceChange: (NorthReference) -> Unit,
    unitSystem: UnitSystem,
    onUnitSystemChange: (UnitSystem) -> Unit,
    isBackupWorking: Boolean,
    backupEvents: SharedFlow<BackupEvent>,
    onExport: (OutputStream) -> Unit,
    onImport: (InputStream) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showImportConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.let(onExport) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.let(onImport) }
    }

    // Resolved once per composition so the effect below doesn't need stringResource() (illegal in
    // a non-@Composable lambda) each time an event is collected.
    val exportSuccessMessage = stringResource(R.string.settings_export_success)
    val exportErrorMessage = stringResource(R.string.settings_export_error)
    val importSuccessMessage = stringResource(R.string.settings_import_success)
    val importUnreadableMessage = stringResource(R.string.settings_import_error_unreadable)
    val importFutureVersionMessage = stringResource(R.string.settings_import_error_future_version)

    LaunchedEffect(backupEvents) {
        backupEvents.collect { event ->
            val message = when (event) {
                BackupEvent.ExportSuccess -> exportSuccessMessage
                BackupEvent.ExportError -> exportErrorMessage
                BackupEvent.ImportSuccess -> importSuccessMessage
                is BackupEvent.ImportError -> when (event.reason) {
                    BackupImportError.UNREADABLE -> importUnreadableMessage
                    BackupImportError.FUTURE_VERSION -> importFutureVersionMessage
                }
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_import_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importLauncher.launch(arrayOf("application/json"))
                }) {
                    Text(stringResource(R.string.settings_import_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(stringResource(R.string.settings_display_section)) {
                SettingRow(glyph = Glyph.Public, title = stringResource(R.string.settings_coordinate_format)) {
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
                SettingRow(glyph = Glyph.WbTwilight, title = stringResource(R.string.settings_theme)) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Sym(
                            icon = Glyph.BatterySaver,
                            contentDescription = null,
                            size = 16.dp,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.settings_theme_battery_hint),
                            fontSize = 12.5.sp,
                            color = AboutMuted,
                        )
                    }
                }
                SettingRow(glyph = Glyph.Language, title = stringResource(R.string.settings_language)) {
                    SegmentedToggle(
                        options = listOf(
                            stringResource(R.string.language_system),
                            stringResource(R.string.language_french),
                            stringResource(R.string.language_english),
                        ),
                        selectedIndex = languageIndex,
                        onSelect = onLanguageChange,
                    )
                }
                SettingRow(
                    glyph = Glyph.Speed,
                    title = stringResource(R.string.settings_unit_system),
                    last = true,
                ) {
                    SegmentedToggle(
                        options = listOf(
                            stringResource(R.string.unit_system_metric),
                            stringResource(R.string.unit_system_imperial),
                        ),
                        selectedIndex = if (unitSystem == UnitSystem.METRIC) 0 else 1,
                        onSelect = { index ->
                            onUnitSystemChange(if (index == 0) UnitSystem.METRIC else UnitSystem.IMPERIAL)
                        },
                    )
                }
            }

            SettingsSection(stringResource(R.string.settings_navigation_section)) {
                SettingRow(
                    glyph = Glyph.Explore,
                    title = stringResource(R.string.settings_north_reference),
                    last = true,
                ) {
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

            SettingsSection(stringResource(R.string.settings_data_section)) {
                DataActionRow(
                    glyph = Glyph.FileDownload,
                    title = stringResource(R.string.settings_export_title),
                    subtitle = stringResource(R.string.settings_export_subtitle),
                    enabled = !isBackupWorking,
                    onClick = {
                        val fileName = "cairn-backup-${LocalDate.now().format(BackupFileNameDateFormatter)}.json"
                        exportLauncher.launch(fileName)
                    },
                )
                DataActionRow(
                    glyph = Glyph.FileUpload,
                    title = stringResource(R.string.settings_import_title),
                    subtitle = stringResource(R.string.settings_import_subtitle),
                    enabled = !isBackupWorking,
                    onClick = { showImportConfirm = true },
                    last = true,
                )
            }

            // Debug builds only: absent from the release APK, where DemoMode.isAvailable is the
            // compile-time constant false.
            if (DemoMode.isAvailable) {
                DemoModeSection()
            }
        }
    }
}

/**
 * The screenshot/screencast demo-mode switch. Toggling it restarts the app, since which database
 * file is open and whether the sensors are simulated are both decided at process start — see
 * [DemoMode].
 */
@Composable
private fun DemoModeSection() {
    val context = LocalContext.current
    val enabled = remember { DemoMode.isPersistedEnabled(context) }
    val light = LocalIsLightTheme.current

    SettingsSection(stringResource(R.string.settings_demo_section)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Sym(
                icon = Glyph.PhotoCamera,
                contentDescription = null,
                size = 22.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_demo_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_demo_subtitle),
                    fontSize = 12.5.sp,
                    color = if (light) AboutMuted else LabelMuted,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { DemoMode.setEnabled(context, it) },
            )
        }
    }
}

/** An uppercase group heading over a single card holding that group's rows. */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            content()
        }
    }
}

/**
 * One row inside a [SettingsSection] card: a leading glyph next to the setting's title, with its
 * control (toggle, hint…) stacked underneath. [last] drops the divider under the final row of a
 * card.
 */
@Composable
private fun SettingRow(
    glyph: Char,
    title: String,
    last: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The control (below) spans the row's full width rather than sitting inset under the
        // glyph — a triple SegmentedToggle needs that width or its French labels wrap to 2 lines.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Sym(
                icon = glyph,
                contentDescription = null,
                size = 22.dp,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        content()
    }
    if (!last) RowDivider()
}

/** A tappable settings row for a single action (export/import), matching the About screen's rows. */
@Composable
private fun DataActionRow(
    glyph: Char,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    last: Boolean = false,
) {
    val light = LocalIsLightTheme.current
    val trailingTint = if (light) AboutTrailingIconLight else AboutMuted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Sym(
            icon = glyph,
            contentDescription = null,
            size = 22.dp,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                color = if (light) AboutMuted else LabelMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Sym(
            icon = Glyph.ChevronRight,
            contentDescription = null,
            size = 19.dp,
            tint = trailingTint,
        )
    }
    if (!last) RowDivider()
}

/** The hairline separating two rows inside a section card, indented past the leading glyph. */
@Composable
private fun RowDivider() {
    val light = LocalIsLightTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 54.dp)
            .height(1.dp)
            .background(if (light) AboutDividerLight else AboutDivider),
    )
}
