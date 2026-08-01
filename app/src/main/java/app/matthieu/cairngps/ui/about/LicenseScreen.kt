package app.matthieu.cairngps.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LightStatusText
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.Sym
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's full AGPL-3.0 text, bundled as a raw resource so it stays readable without a network
 * connection — the same promise the rest of the app makes. The text is legal boilerplate published
 * in English only, so it lives in `res/raw` rather than in `strings.xml`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val light = LocalIsLightTheme.current
    // 35 kB read off the main thread, hence the null state while it loads.
    val licenseText by produceState<String?>(initialValue = null, resources) {
        value = withContext(Dispatchers.IO) {
            resources.openRawResource(R.raw.license).bufferedReader().use { it.readText() }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_license_screen_title)) },
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
        val text = licenseText
        if (text == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Text(
                        text = text,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        fontFamily = MonoFontFamily,
                        color = if (light) LightStatusText else CairnStone,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
        }
    }
}
