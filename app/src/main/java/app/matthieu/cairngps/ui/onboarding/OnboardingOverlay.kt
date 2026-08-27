package app.matthieu.cairngps.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.ui.theme.CairnAmber

/** Rounded-corner radius of the cut-out around a spotlighted target. */
private val CUTOUT_CORNER_RADIUS = 16.dp

/** Extra margin added around a target's reported bounds before cutting it out. */
private val CUTOUT_MARGIN = 8.dp

/** How far past the cut-out's own corners the pulsing halo ring extends at its largest. */
private val HALO_MAX_OUTSET = 10.dp

/**
 * Targets whose real widget stays genuinely usable during its step — the globe can still be
 * panned/zoomed and its satellites tapped, the achievement grid can still be scrolled — rather than
 * only tappable-to-advance like every other spotlighted card or button. Deliberately excludes any
 * target whose real tap has a side effect that would fight the tour's own scripted navigation (a
 * tab, a Profil hub row, the record button…) — letting those through would navigate for real while
 * the step index stays put, leaving the bubble's text out of sync with the screen underneath.
 */
private val PASSTHROUGH_TARGETS = setOf(OnboardingTarget.GLOBE_CANVAS, OnboardingTarget.ACHIEVEMENT_GRID)

/**
 * Full-screen modal overlay driving the onboarding tour: a scrim with a cut-out around the current
 * step's target (if any), a pulsing halo ring marking it as "tap here", and an explanation bubble.
 *
 * Tapping the scrim — anywhere outside the cut-out — advances to the next step via [onNext]. Inside
 * the cut-out, most targets are still tap-to-advance (nothing underneath is actually clickable), but
 * a [PASSTHROUGH_TARGETS] target instead lets every touch reach the real widget, so the globe can
 * still be rotated/zoomed and its satellites tapped, matching the rest of the app.
 */
@Composable
fun OnboardingOverlay(
    step: OnboardingStep,
    stepIndex: Int,
    totalSteps: Int,
    targetRect: Rect?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val animatedRect by animateRectAsState(targetValue = targetRect ?: Rect.Zero, label = "onboarding-target")
    val hasTarget = targetRect != null

    val infiniteTransition = rememberInfiniteTransition(label = "onboarding-halo")
    val haloProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "onboarding-halo-progress",
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val marginPx = with(density) { CUTOUT_MARGIN.toPx() }
        val cornerPx = with(density) { CUTOUT_CORNER_RADIUS.toPx() }
        val haloOutsetPx = with(density) { HALO_MAX_OUTSET.toPx() }
        val scrimColor = Color.Black.copy(alpha = 0.72f)
        val passthrough = hasTarget && step.target in PASSTHROUGH_TARGETS

        if (passthrough) {
            // Tile the tap-to-advance region around the cut-out instead of covering the whole
            // screen, leaving a genuine hole where the real widget receives every touch directly.
            val cutoutLeft = with(density) { (animatedRect.left - marginPx).toDp() }.coerceIn(0.dp, maxWidth)
            val cutoutTop = with(density) { (animatedRect.top - marginPx).toDp() }.coerceIn(0.dp, maxHeight)
            val cutoutRight = with(density) { (animatedRect.right + marginPx).toDp() }.coerceIn(0.dp, maxWidth)
            val cutoutBottom = with(density) { (animatedRect.bottom + marginPx).toDp() }.coerceIn(0.dp, maxHeight)

            if (cutoutTop > 0.dp) {
                TapToAdvanceRegion(onNext, Modifier.fillMaxWidth().height(cutoutTop))
            }
            if (cutoutBottom < maxHeight) {
                TapToAdvanceRegion(
                    onNext,
                    Modifier
                        .fillMaxWidth()
                        .height(maxHeight - cutoutBottom)
                        .offset(y = cutoutBottom),
                )
            }
            if (cutoutLeft > 0.dp) {
                TapToAdvanceRegion(
                    onNext,
                    Modifier
                        .width(cutoutLeft)
                        .height(cutoutBottom - cutoutTop)
                        .offset(y = cutoutTop),
                )
            }
            if (cutoutRight < maxWidth) {
                TapToAdvanceRegion(
                    onNext,
                    Modifier
                        .width(maxWidth - cutoutRight)
                        .height(cutoutBottom - cutoutTop)
                        .offset(x = cutoutRight, y = cutoutTop),
                )
            }
        } else {
            TapToAdvanceRegion(onNext, Modifier.fillMaxSize())
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(color = scrimColor)

            if (hasTarget) {
                val cutout = Rect(
                    left = animatedRect.left - marginPx,
                    top = animatedRect.top - marginPx,
                    right = animatedRect.right + marginPx,
                    bottom = animatedRect.bottom + marginPx,
                )
                drawRoundRect(
                    color = Color.Black,
                    topLeft = cutout.topLeft,
                    size = cutout.size,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    blendMode = BlendMode.Clear,
                )

                // The pulsing halo: an amber ring growing outward from the cut-out and fading out,
                // repeating — the "tap here" affordance called for in the design.
                val haloRect = Rect(
                    left = cutout.left - haloOutsetPx * haloProgress,
                    top = cutout.top - haloOutsetPx * haloProgress,
                    right = cutout.right + haloOutsetPx * haloProgress,
                    bottom = cutout.bottom + haloOutsetPx * haloProgress,
                )
                drawRoundRect(
                    color = CairnAmber.copy(alpha = (1f - haloProgress).coerceIn(0f, 1f)),
                    topLeft = haloRect.topLeft,
                    size = haloRect.size,
                    cornerRadius = CornerRadius(cornerPx + haloOutsetPx * haloProgress, cornerPx + haloOutsetPx * haloProgress),
                    style = Stroke(width = with(density) { 3.dp.toPx() }),
                )
                drawRoundRect(
                    color = CairnAmber,
                    topLeft = cutout.topLeft,
                    size = cutout.size,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = with(density) { 2.dp.toPx() }),
                )
            }
        }

        // Below the cut-out if it sits in the top half of the screen, above it otherwise — keeps
        // the bubble from ever overlapping its own spotlight. Centered when there's no target.
        val bubbleBelowTarget = !hasTarget || animatedRect.center.y < with(density) { maxHeight.toPx() } / 2f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            contentAlignment = when {
                !hasTarget -> Alignment.Center
                bubbleBelowTarget -> Alignment.BottomCenter
                else -> Alignment.TopCenter
            },
        ) {
            OnboardingBubble(
                step = step,
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                onNext = onNext,
                onSkip = onSkip,
                onBack = onBack,
            )
        }
    }
}

/** An invisible tap-to-advance area; see the strips tiled around a passthrough target's cut-out. */
@Composable
private fun TapToAdvanceRegion(onNext: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.pointerInput(Unit) { detectTapGestures(onTap = { onNext() }) })
}

@Composable
private fun OnboardingBubble(
    step: OnboardingStep,
    stepIndex: Int,
    totalSteps: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    val isLastStep = stepIndex == totalSteps - 1
    val isFirstStep = stepIndex == 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, CairnAmber.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(top = 8.dp, start = 20.dp, end = 12.dp, bottom = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_progress, stepIndex + 1, totalSteps),
                    style = MaterialTheme.typography.labelMedium,
                    color = CairnAmber,
                )
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
            Text(
                text = stringResource(step.titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(step.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // No step to return to on the very first one — the hardware/gesture back press
                // still exits the tour there (see OnboardingOverlay's BackHandler).
                if (!isFirstStep) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_back))
                    }
                } else {
                    Spacer(Modifier)
                }
                TextButton(onClick = onNext) {
                    Text(stringResource(if (isLastStep) R.string.onboarding_start else R.string.onboarding_next))
                }
            }
        }
    }
}
