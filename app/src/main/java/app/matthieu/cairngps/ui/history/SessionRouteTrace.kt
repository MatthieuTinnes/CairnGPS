package app.matthieu.cairngps.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.domain.format.formatTimeOfDay
import app.matthieu.cairngps.ui.theme.AchievementLabelGold
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.LabelMuted

/**
 * The session's route shape (screen 1j), with a filled start marker and a hollow end marker, plus
 * their departure/arrival times below. `track` must be non-empty; callers should skip this card
 * otherwise (sessions recorded before the track-points feature existed have none).
 */
@Composable
fun SessionRouteTrace(
    track: List<TrackPoint>,
    startTimestamp: Long,
    endTimestamp: Long,
    modifier: Modifier = Modifier,
) {
    if (track.size < 2) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
        ) {
            val project = trackProjector(track, marginXFraction = 0.04f, marginYFraction = 0.1f)

            val path = Path()
            track.forEachIndexed { index, point ->
                val offset = project(point)
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            drawPath(
                path = path,
                color = CairnGreen,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            drawCircle(color = CairnGreen, radius = 6.dp.toPx(), center = project(track.first()))
            drawCircle(
                color = CairnAmber,
                radius = 6.dp.toPx(),
                center = project(track.last()),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.session_departure_fmt, formatTimeOfDay(startTimestamp)),
                fontSize = 11.5.sp,
                color = LabelMuted,
            )
            Text(
                text = stringResource(R.string.session_arrival_fmt, formatTimeOfDay(endTimestamp)),
                fontSize = 11.5.sp,
                color = AchievementLabelGold,
            )
        }
    }
}
