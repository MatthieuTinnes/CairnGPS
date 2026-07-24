package app.matthieu.cairngps.domain

import java.util.concurrent.TimeUnit

/**
 * Les deux "bons moments" où l'app propose de la noter, chacun avec le palier à franchir : juste
 * après une trace enregistrée, et juste après un succès débloqué. Les deux sont des instants où
 * l'utilisateur vient d'obtenir quelque chose, ce que recommande l'In-App Review API.
 */
enum class ReviewTrigger(val threshold: Int) {
    /** Nombre de traces terminées. */
    COMPLETED_SESSIONS(3),

    /** Nombre de succès débloqués. */
    UNLOCKED_ACHIEVEMENTS(3),
}

/**
 * Politique de déclenchement de l'invitation Play "noter l'application", volontairement isolée du
 * stockage et de l'API Play pour rester testable sans Android.
 *
 * Play applique déjà son propre quota (une invitation affichée seulement de temps en temps par
 * utilisateur, et jamais en debug/side-load) : ces règles servent à ne pas gaspiller ce quota sur
 * un utilisateur qui découvre encore l'app, et à ne jamais réclamer deux fois de suite.
 */
object ReviewPrompt {

    /** Délai minimal entre deux invitations, tous déclencheurs confondus. */
    val MIN_INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(90)

    /**
     * Vrai si le palier de [trigger] vient d'être atteint et qu'aucune invitation n'a été affichée
     * trop récemment.
     *
     * @param count Compteur suivi par [trigger], l'élément qui vient d'être obtenu inclus.
     * @param lastRequestedAt Date de la dernière invitation affichée, ou `null` si jamais.
     */
    fun shouldRequest(
        trigger: ReviewTrigger,
        count: Int,
        lastRequestedAt: Long?,
        now: Long,
    ): Boolean {
        if (count < trigger.threshold) return false
        // Une horloge reculée (changement de fuseau/date par l'utilisateur) rendrait l'écart
        // négatif : on considère alors qu'il est trop tôt plutôt que de réinviter aussitôt.
        if (lastRequestedAt != null && now - lastRequestedAt < MIN_INTERVAL_MS) return false
        return true
    }
}
