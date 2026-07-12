package app.matthieu.cairngps.ui.satellites

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.Constellation
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.DarkSurface
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.Sym

/**
 * Static reference page explaining the GNSS constellations. Reached from the satellites screen; it
 * holds no live data, so it needs no ViewModel or lifecycle-bound GPS subscription.
 */
private data class ConstellationDoc(
    val constellation: Constellation,
    @StringRes val regionRes: Int,
    @StringRes val statsRes: Int,
    @StringRes val descriptionRes: Int,
)

// The five constellations covered by the design (screen 1f); IRNSS/SBAS/UNKNOWN aren't documented
// here even though they can appear live in the sky plot/globe.
private val CONSTELLATION_DOCS = listOf(
    ConstellationDoc(
        Constellation.GPS,
        R.string.constellation_gps_region,
        R.string.constellation_gps_stats,
        R.string.constellation_gps_desc,
    ),
    ConstellationDoc(
        Constellation.GLONASS,
        R.string.constellation_glonass_region,
        R.string.constellation_glonass_stats,
        R.string.constellation_glonass_desc,
    ),
    ConstellationDoc(
        Constellation.GALILEO,
        R.string.constellation_galileo_region,
        R.string.constellation_galileo_stats,
        R.string.constellation_galileo_desc,
    ),
    ConstellationDoc(
        Constellation.BEIDOU,
        R.string.constellation_beidou_region,
        R.string.constellation_beidou_stats,
        R.string.constellation_beidou_desc,
    ),
    ConstellationDoc(
        Constellation.QZSS,
        R.string.constellation_qzss_region,
        R.string.constellation_qzss_stats,
        R.string.constellation_qzss_desc,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConstellationInfoScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.constellation_info_title)) },
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    IconButton(onClick = onBack) {
                        Sym(icon = Glyph.ArrowBack, contentDescription = backLabel)
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.constellation_info_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(
                items = CONSTELLATION_DOCS,
                key = { it.constellation.name },
            ) { doc ->
                ConstellationDocCard(doc)
            }
        }
    }
}

@Composable
private fun ConstellationDocCard(doc: ConstellationDoc) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(color = doc.constellation.color(), shape = CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = doc.constellation.displayName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(doc.regionRes),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LabelMuted,
                )
            }
            Text(
                text = stringResource(doc.statsRes),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = MonoFontFamily,
                color = CairnStone,
            )
            Text(
                text = stringResource(doc.descriptionRes),
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                color = CairnStone,
            )
        }
    }
}
