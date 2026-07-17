package app.matthieu.cairngps.ui.waypoints

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.domain.format.DASH
import app.matthieu.cairngps.domain.format.copyCoordinates
import app.matthieu.cairngps.domain.format.formatAccuracy
import app.matthieu.cairngps.domain.format.formatAltitude
import app.matthieu.cairngps.domain.format.formatCoordinate
import app.matthieu.cairngps.domain.format.formatSpeed
import app.matthieu.cairngps.domain.format.formatWaypointTimestamp
import app.matthieu.cairngps.domain.format.shortDistanceValueAndUnit
import app.matthieu.cairngps.domain.format.shortUnitLabel
import app.matthieu.cairngps.domain.format.speedUnitLabel
import app.matthieu.cairngps.ui.settings.SettingsViewModel
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.CompassDialBorder
import app.matthieu.cairngps.ui.theme.CompassDialBorderLight
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.LightBorderSubtle
import app.matthieu.cairngps.ui.theme.LightStatusText
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.OnGreenButton
import app.matthieu.cairngps.ui.theme.OnGreenButtonDark
import app.matthieu.cairngps.ui.theme.OutlineSubtle
import app.matthieu.cairngps.ui.theme.SoftError
import app.matthieu.cairngps.ui.theme.SoftErrorLight
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.theme.WaypointIconBg
import java.util.Locale

/**
 * Route: loads the waypoint identified by [waypointId] and renders its full detail. Navigates back
 * automatically once the waypoint has been deleted. Only ever composed once location permission
 * has been granted (behind [app.matthieu.cairngps.ui.permission.LocationPermissionGate]).
 */
@SuppressLint("MissingPermission")
@Composable
fun WaypointDetailRoute(
    waypointId: Long,
    waypointRepository: WaypointRepository,
    sessionRepository: SessionRepository,
    locationRepository: LocationRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onNavigate: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WaypointDetailViewModel =
        viewModel(
            factory = WaypointDetailViewModel.factory(
                waypointRepository,
                sessionRepository,
                locationRepository,
                waypointId,
            ),
        )
    val settingsViewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModel.factory(settingsRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    // Once the delete completes, leave the detail screen (side-effect, not done during composition).
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }

    // One-shot "distance actuelle" snapshot, taken once the waypoint has finished loading (keyed on
    // its id so it doesn't re-run on every recomposition, e.g. after a rename).
    LaunchedEffect(uiState.waypoint?.id) {
        if (uiState.waypoint != null) viewModel.refreshCurrentDistance()
    }

    WaypointDetailScreen(
        waypoint = uiState.waypoint,
        session = uiState.session,
        currentDistanceMeters = uiState.currentDistanceMeters,
        unitSystem = settings.unitSystem,
        onBack = onBack,
        onDelete = viewModel::delete,
        onEdit = viewModel::edit,
        onSetIcon = viewModel::setIcon,
        onOpenSession = onOpenSession,
        onNavigate = { onNavigate(waypointId) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaypointDetailScreen(
    waypoint: Waypoint?,
    session: Session?,
    currentDistanceMeters: Double?,
    unitSystem: UnitSystem,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (String, String) -> Unit,
    onSetIcon: (String) -> Unit,
    onOpenSession: (Long) -> Unit,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val light = LocalIsLightTheme.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    // Staged icon for the rename-dialog flow (3a): only persisted once "ENREGISTRER" is tapped,
    // re-seeded from the waypoint's saved icon every time the dialog (re)opens.
    var pendingIcon by remember { mutableStateOf(waypoint?.icon ?: "flag") }
    var showRenameIconPicker by remember { mutableStateOf(false) }
    // Separate picker for the header-avatar flow (1i): selecting an icon there saves immediately.
    var showAvatarIconPicker by remember { mutableStateOf(false) }

    // Re-seed the staged icon from the persisted value every time the rename dialog opens, so a
    // cancelled edit (or a picker choice made then abandoned) never leaks into the next open.
    LaunchedEffect(showRenameDialog) {
        if (showRenameDialog) pendingIcon = waypoint?.icon ?: "flag"
    }

    if (showDeleteDialog && waypoint != null) {
        DeleteConfirmDialog(
            title = stringResource(R.string.waypoint_delete_dialog_title),
            message = stringResource(R.string.waypoint_delete_dialog_message_fmt, waypoint.name),
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
        )
    }

    if (showRenameDialog && waypoint != null) {
        RenameDialog(
            title = stringResource(R.string.waypoint_rename_dialog_title),
            label = stringResource(R.string.waypoint_name_label),
            initialName = waypoint.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                onEdit(newName, pendingIcon)
            },
            iconRow = {
                val changeIconLabel = stringResource(R.string.action_change_icon)
                val accent = if (light) CairnGreenDark else CairnGreen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClickLabel = changeIconLabel) { showRenameIconPicker = true }
                        .padding(vertical = 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (light) CairnGreenDark.copy(alpha = 0.12f) else WaypointIconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Sym(icon = WaypointIcons.glyphFor(pendingIcon), contentDescription = null, tint = accent, size = 22.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.waypoint_icon_edit_link),
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                    )
                }
            },
        )
    }

    if (showRenameIconPicker) {
        WaypointIconPickerSheet(
            selectedKey = pendingIcon,
            onSelect = { key ->
                pendingIcon = key
                showRenameIconPicker = false
            },
            onDismiss = { showRenameIconPicker = false },
        )
    }

    if (showAvatarIconPicker && waypoint != null) {
        WaypointIconPickerSheet(
            selectedKey = waypoint.icon,
            onSelect = { key ->
                onSetIcon(key)
                showAvatarIconPicker = false
            },
            onDismiss = { showAvatarIconPicker = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(waypoint?.name ?: stringResource(R.string.waypoint_detail_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    IconButton(onClick = onBack) {
                        Sym(icon = Glyph.ArrowBack, contentDescription = backLabel)
                    }
                },
                actions = {
                    if (waypoint != null) {
                        val deleteLabel = stringResource(R.string.action_delete)
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Sym(
                                icon = Glyph.Delete,
                                contentDescription = deleteLabel,
                                tint = if (light) SoftErrorLight else SoftError,
                            )
                        }
                        val renameLabel = stringResource(R.string.action_rename)
                        IconButton(onClick = { showRenameDialog = true }) {
                            Sym(icon = Glyph.Edit, contentDescription = renameLabel)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        // waypoint stays null only for the brief load; nothing to render until it resolves.
        if (waypoint == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val changeIconLabel = stringResource(R.string.action_change_icon)
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(CairnGreenDark, CircleShape)
                        .clickable(onClickLabel = changeIconLabel) { showAvatarIconPicker = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Sym(
                        icon = WaypointIcons.glyphFor(waypoint.icon),
                        contentDescription = null,
                        filled = true,
                        tint = OnGreenButton,
                        size = 26.dp,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(text = waypoint.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(R.string.waypoint_created_at_fmt, formatWaypointTimestamp(waypoint.timestamp)),
                        fontSize = 13.sp,
                        color = LabelMuted,
                    )
                }
            }

            Card(
                onClick = { context.copyCoordinates(waypoint.latitude, waypoint.longitude) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        text = stringResource(R.string.label_coordinates).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                        color = LabelMuted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = formatCoordinate(waypoint.latitude, isLatitude = true, format = CoordinateFormat.DECIMAL) +
                            "\n" +
                            formatCoordinate(waypoint.longitude, isLatitude = false, format = CoordinateFormat.DECIMAL),
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontFamily = MonoFontFamily,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 10.dp)
                            .height(1.dp)
                            .background(if (light) LightBorderSubtle else CompassDialBorder),
                    )
                    Text(
                        text = formatCoordinate(waypoint.latitude, isLatitude = true, format = CoordinateFormat.DMS) +
                            " · " +
                            formatCoordinate(waypoint.longitude, isLatitude = false, format = CoordinateFormat.DMS),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontFamily = MonoFontFamily,
                        color = if (light) LightStatusText else CairnStone,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MeasurementTile(
                    label = stringResource(R.string.label_altitude),
                    value = formatAltitude(waypoint.altitude, unitSystem),
                    unit = shortUnitLabel(unitSystem),
                    modifier = Modifier.weight(1f),
                )
                val (distanceValue, distanceUnit) = if (currentDistanceMeters == null) {
                    DASH to shortUnitLabel(unitSystem)
                } else {
                    shortDistanceValueAndUnit(currentDistanceMeters, unitSystem)
                }
                MeasurementTile(
                    label = stringResource(R.string.label_current_distance),
                    value = distanceValue,
                    unit = distanceUnit,
                    modifier = Modifier.weight(1f),
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.label_measurements).uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                        color = LabelMuted,
                    )
                    MeasurementRow(
                        stringResource(R.string.label_speed),
                        "${formatSpeed(waypoint.speed, unitSystem)} ${speedUnitLabel(unitSystem)}",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                    )
                    MeasurementRow(
                        stringResource(R.string.label_accuracy),
                        "±${formatAccuracy(waypoint.horizontalAccuracy, unitSystem)} ${shortUnitLabel(unitSystem)}",
                    )
                    MeasurementRow(
                        stringResource(R.string.label_satellites_used),
                        waypoint.satellitesUsedInFix?.toString() ?: DASH,
                    )
                }
            }

            if (session != null) {
                Card(
                    onClick = { onOpenSession(session.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.waypoint_parent_session).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.2.sp,
                                color = LabelMuted,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                            Text(
                                text = session.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (light) CairnGreenDark else CairnGreen,
                            )
                        }
                        Sym(icon = Glyph.ChevronRight, contentDescription = null, tint = if (light) CairnGreenDark else CairnGreen)
                    }
                }
            }

            Button(
                onClick = onNavigate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (light) CairnGreenDark else CairnGreen,
                    contentColor = if (light) Color.White else OnGreenButtonDark,
                ),
            ) {
                Sym(icon = Glyph.Navigation, contentDescription = null, filled = true)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_navigate_to_waypoint),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = if (light) CompassDialBorderLight else OutlineSubtle,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { openInMaps(context, waypoint) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym(icon = Glyph.Map, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_open_in_maps),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** One tile of the altitude/distance grid below the coordinates card (screen 1i). */
@Composable
private fun MeasurementTile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val light = LocalIsLightTheme.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = label.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                color = LabelMuted,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(text = value, fontSize = 28.sp, fontFamily = MonoFontFamily)
                Spacer(Modifier.width(6.dp))
                Text(text = unit, fontSize = 14.sp, color = if (light) LightStatusText else CairnStone)
            }
        }
    }
}

/** Confirmation dialog shared by every "delete this X?" flow (waypoints, sessions). */
@Composable
fun DeleteConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val light = LocalIsLightTheme.current
    val errorColor = if (light) SoftErrorLight else SoftError
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Sym(icon = Glyph.Warning, contentDescription = null, tint = errorColor)
                Spacer(Modifier.height(8.dp))
                Text(title)
            }
        },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = errorColor,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Dialog to edit a saved item's name, pre-filled with its current value.
 *
 * [iconRow], when set, renders below the name field/counter (screens 3a/5o's icon tile + "Modifier
 * l'icône" link, used by the waypoint rename dialog to also let the user change the icon in the
 * same flow). Other callers (session rename, compass "create waypoint here") leave it `null` and
 * get the plain field.
 */
@Composable
fun RenameDialog(
    title: String,
    label: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    maxLength: Int = 40,
    iconRow: (@Composable () -> Unit)? = null,
) {
    val light = LocalIsLightTheme.current
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Sym(icon = Glyph.Edit, contentDescription = null, tint = if (light) LightStatusText else CairnStone)
                Spacer(Modifier.height(8.dp))
                Text(title)
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= maxLength) name = it },
                    singleLine = true,
                    label = { Text(label) },
                    supportingText = {
                        Text(
                            text = "${name.length} / $maxLength",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (iconRow != null) {
                    Spacer(Modifier.height(2.dp))
                    iconRow()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** One "label ... value" baseline row inside the Mesures card (screen 1i). */
@Composable
private fun MeasurementRow(label: String, value: String, valueColor: Color? = null) {
    val light = LocalIsLightTheme.current
    val mutedColor = if (light) LightStatusText else CairnStone
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, fontSize = 14.sp, color = mutedColor, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = MonoFontFamily,
            color = valueColor ?: mutedColor,
        )
    }
}

/**
 * Opens the waypoint in a map app via a `geo:` URI. Android shows the picker for every installed
 * geo app (Google Maps, Organic Maps, OsmAnd…), so there's no need to code against each one.
 */
private fun openInMaps(context: Context, waypoint: Waypoint) {
    // Locale.US forces a decimal point: a comma would break the geo: URI.
    val coordinates = "%.6f,%.6f".format(Locale.US, waypoint.latitude, waypoint.longitude)
    // q=lat,lon(label) drops a named marker at the point; the name must be encoded.
    val uri = "geo:$coordinates?q=$coordinates(${Uri.encode(waypoint.name)})".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // resolveActivity is unreliable on Android 11+ (package visibility), hence the catch.
        Toast.makeText(context, R.string.no_maps_app, Toast.LENGTH_SHORT).show()
    }
}
