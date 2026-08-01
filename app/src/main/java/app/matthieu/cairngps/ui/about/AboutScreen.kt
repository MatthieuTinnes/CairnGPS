package app.matthieu.cairngps.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.matthieu.cairngps.BuildConfig
import app.matthieu.cairngps.R
import app.matthieu.cairngps.ui.theme.AboutChipBg
import app.matthieu.cairngps.ui.theme.AboutChipBgLight
import app.matthieu.cairngps.ui.theme.AboutChipTextLight
import app.matthieu.cairngps.ui.theme.AboutDivider
import app.matthieu.cairngps.ui.theme.AboutDividerLight
import app.matthieu.cairngps.ui.theme.AboutMuted
import app.matthieu.cairngps.ui.theme.AboutTrailingIconLight
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.OnGreenButton
import app.matthieu.cairngps.ui.theme.Sym

/**
 * Static identity page: what the app is, which version is installed, where its sources live and how
 * to reach its author. Like the constellation reference page it holds no live data, so it needs no
 * ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenThirdParty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val light = LocalIsLightTheme.current
    val mutedText = if (light) AboutMuted else LabelMuted
    val footnote = AboutMuted

    val sourceUri = stringResource(R.string.about_source_uri)
    val websiteUri = stringResource(R.string.about_website_uri)
    val emailAddress = stringResource(R.string.about_email_address)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IdentityCard(light = light, mutedText = mutedText)

            AboutSection(stringResource(R.string.about_section_project)) {
                AboutRow(
                    glyph = Glyph.Code,
                    title = stringResource(R.string.about_source_title),
                    subtitle = stringResource(R.string.about_source_url),
                    subtitleMono = true,
                    external = true,
                    onClick = { openLink(context, sourceUri) },
                )
                AboutRow(
                    glyph = Glyph.Balance,
                    title = stringResource(R.string.about_license_row_title),
                    subtitle = stringResource(R.string.about_license_row_subtitle),
                    subtitleMono = false,
                    external = false,
                    onClick = onOpenLicense,
                )
                AboutRow(
                    glyph = Glyph.Inventory2,
                    title = stringResource(R.string.about_libraries_title),
                    subtitle = stringResource(
                        R.string.about_libraries_subtitle_fmt,
                        THIRD_PARTY_CREDIT_COUNT,
                    ),
                    subtitleMono = false,
                    external = false,
                    onClick = onOpenThirdParty,
                    last = true,
                )
            }

            AboutSection(stringResource(R.string.about_section_contact)) {
                AboutRow(
                    glyph = Glyph.Language,
                    title = stringResource(R.string.about_website_title),
                    subtitle = stringResource(R.string.about_website_url),
                    subtitleMono = true,
                    external = true,
                    onClick = { openLink(context, websiteUri) },
                )
                AboutRow(
                    glyph = Glyph.Mail,
                    title = stringResource(R.string.about_email_title),
                    subtitle = emailAddress,
                    subtitleMono = true,
                    external = true,
                    onClick = { openEmail(context, emailAddress) },
                    last = true,
                )
            }

            Text(
                text = stringResource(R.string.about_bug_hint),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = footnote,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 2.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym(
                    icon = Glyph.VisibilityOff,
                    contentDescription = null,
                    size = 18.dp,
                    tint = if (light) AboutTrailingIconLight else footnote,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.about_privacy),
                    fontSize = 12.5.sp,
                    color = footnote,
                )
            }
        }
    }
}

@Composable
private fun IdentityCard(light: Boolean, mutedText: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CairnGreenDark),
                contentAlignment = Alignment.Center,
            ) {
                // The launcher foreground is drawn with the adaptive-icon safe margin, so it has to
                // overflow its tile to fill it — same ratio as the Profil hub's avatar.
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(108.dp),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                )
                Text(
                    text = stringResource(R.string.about_tagline),
                    fontSize = 13.5.sp,
                    lineHeight = 19.5.sp,
                    color = mutedText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = MonoFontFamily,
                    color = if (light) AboutChipTextLight else OnGreenButton,
                    modifier = Modifier
                        .background(
                            color = if (light) AboutChipBgLight else AboutChipBg,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
                // Only the build number, never a build date: an embedded timestamp would make the
                // APK differ from build to build and break F-Droid's reproducible builds.
                Text(
                    text = stringResource(R.string.about_build_fmt, BuildConfig.VERSION_CODE),
                    fontSize = 12.5.sp,
                    fontFamily = MonoFontFamily,
                    color = mutedText,
                )
            }
        }
    }
}

/** An uppercase group heading over a single card holding that group's rows. */
@Composable
private fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
 * One tappable row inside an [AboutSection] card. [external] picks the trailing glyph: an
 * "open in new" arrow for destinations outside the app, a chevron for in-app sub-screens.
 * [last] drops the divider under the final row of a card.
 */
@Composable
private fun AboutRow(
    glyph: Char,
    title: String,
    subtitle: String,
    subtitleMono: Boolean,
    external: Boolean,
    onClick: () -> Unit,
    last: Boolean = false,
) {
    val light = LocalIsLightTheme.current
    val trailingTint = if (light) AboutTrailingIconLight else AboutMuted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                fontFamily = if (subtitleMono) MonoFontFamily else null,
                color = if (light) AboutMuted else LabelMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Sym(
            icon = if (external) Glyph.OpenInNew else Glyph.ChevronRight,
            contentDescription = if (external) {
                stringResource(R.string.about_open_external)
            } else {
                null
            },
            size = 19.dp,
            tint = trailingTint,
        )
    }
    if (!last) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 54.dp)
                .height(1.dp)
                .background(if (light) AboutDividerLight else AboutDivider),
        )
    }
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // resolveActivity is unreliable on Android 11+ (package visibility), hence the catch.
        Toast.makeText(context, R.string.about_no_app, Toast.LENGTH_SHORT).show()
    }
}

private fun openEmail(context: Context, address: String) {
    // ACTION_SENDTO with a mailto: URI reaches mail apps only, and needs no <queries> entry.
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$address".toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.about_no_app, Toast.LENGTH_SHORT).show()
    }
}
