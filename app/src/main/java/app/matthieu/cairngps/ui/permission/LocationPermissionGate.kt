package app.matthieu.cairngps.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.matthieu.cairngps.R
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.Sym

private const val LOCATION_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION

/**
 * Gates [content] behind the runtime [Manifest.permission.ACCESS_FINE_LOCATION] permission.
 *
 * - If the permission is granted, renders [content].
 * - Otherwise shows an explanation screen with a button to request it.
 * - If the user has permanently denied it, the screen instead offers to open the app settings.
 */
@Composable
fun LocationPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    // True once we've asked at least once, so we can distinguish "not asked yet" from "denied".
    var hasRequestedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        hasRequestedOnce = true
    }

    if (hasPermission) {
        content()
        return
    }

    // Permanently denied when: we've asked, it's still not granted, and the system says a
    // rationale should no longer be shown (i.e. "Don't ask again" / denied twice).
    val permanentlyDenied = hasRequestedOnce && !context.shouldShowLocationRationale()

    LocationPermissionRequest(
        modifier = modifier,
        permanentlyDenied = permanentlyDenied,
        onRequest = { launcher.launch(LOCATION_PERMISSION) },
        onOpenSettings = { context.openAppSettings() },
    )
}

@Composable
private fun LocationPermissionRequest(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Sym(icon = Glyph.LocationOff, contentDescription = null, size = 48.dp, tint = CairnAmber)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (permanentlyDenied) R.string.permission_denied_rationale
                else R.string.permission_rationale,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        // 56 dp: touch targets stay glove-friendly for outdoor use (see CLAUDE.md).
        if (permanentlyDenied) {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.permission_open_settings))
            }
        } else {
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Sym(icon = Glyph.MyLocation, contentDescription = null, filled = true)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.permission_grant))
            }
        }
    }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

private fun Context.shouldShowLocationRationale(): Boolean {
    val activity = findActivity() ?: return false
    return activity.shouldShowRequestPermissionRationale(LOCATION_PERMISSION)
}

private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
