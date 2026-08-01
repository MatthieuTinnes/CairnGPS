package app.matthieu.cairngps.ui.about

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.ui.theme.AboutMuted
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.LightStatusText
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.Sym

private data class Credit(
    @StringRes val nameRes: Int,
    @StringRes val licenseRes: Int,
    @StringRes val descriptionRes: Int,
)

private val CREDITS = listOf(
    Credit(R.string.credit_androidx_name, R.string.credit_androidx_license, R.string.credit_androidx_desc),
    Credit(R.string.credit_kotlin_name, R.string.credit_kotlin_license, R.string.credit_kotlin_desc),
    Credit(
        R.string.credit_natural_earth_name,
        R.string.credit_natural_earth_license,
        R.string.credit_natural_earth_desc,
    ),
    Credit(R.string.credit_egm96_name, R.string.credit_egm96_license, R.string.credit_egm96_desc),
    Credit(R.string.credit_fonts_name, R.string.credit_fonts_license, R.string.credit_fonts_desc),
)

/** Derived from [CREDITS] so the About screen's row subtitle can't drift from the list itself. */
internal val THIRD_PARTY_CREDIT_COUNT = CREDITS.size

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThirdPartyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_libraries_screen_title)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.about_libraries_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(items = CREDITS, key = { it.nameRes }) { credit ->
                CreditCard(credit)
            }
        }
    }
}

@Composable
private fun CreditCard(credit: Credit) {
    val light = LocalIsLightTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(credit.nameRes),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(credit.licenseRes),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = MonoFontFamily,
                    color = if (light) AboutMuted else LabelMuted,
                )
            }
            Text(
                text = stringResource(credit.descriptionRes),
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                color = if (light) LightStatusText else CairnStone,
            )
        }
    }
}
