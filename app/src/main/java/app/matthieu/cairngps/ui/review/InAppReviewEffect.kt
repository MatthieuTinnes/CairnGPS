package app.matthieu.cairngps.ui.review

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.matthieu.cairngps.data.ReviewRepository
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Affiche l'invitation Play "noter l'application" quand [ReviewRepository] en a armé une, c'est-à-
 * dire après une trace enregistrée (voir ce repository pour la politique de déclenchement).
 *
 * Ne dessine rien : l'écran est entièrement rendu par Play par-dessus l'activité en cours. Comme ce
 * composable n'est composé que lorsque l'activité est là, la contrainte "une Activity au premier
 * plan" de l'API est satisfaite d'office — d'où le passage par un état *en attente* plutôt qu'un
 * appel direct à l'arrêt de la trace, qui peut venir de la notification app en arrière-plan.
 *
 * Play décide seul d'afficher ou non le formulaire (quota par utilisateur, build installée hors
 * Play Store…) et ne le dit pas à l'appelant : un flux qui se termine sans rien montrer est le cas
 * nominal, pas une erreur.
 */
@Composable
fun InAppReviewEffect(reviewRepository: ReviewRepository) {
    val activity = LocalActivity.current ?: return
    val isPending by reviewRepository.isRequestPending.collectAsStateWithLifecycle(initialValue = false)

    LaunchedEffect(isPending) {
        if (!isPending) return@LaunchedEffect

        val manager = ReviewManagerFactory.create(activity)
        val shown = runCatching {
            val info = manager.requestReview()
            manager.launchReview(activity, info)
        }
        if (shown.isSuccess) {
            reviewRepository.markRequested()
        } else {
            // ReviewException (Play indisponible, appareil sans Play Store…) : on désarme sans
            // consommer le délai, l'invitation sera réarmée à la prochaine trace enregistrée.
            reviewRepository.clearRequest()
        }
    }
}
