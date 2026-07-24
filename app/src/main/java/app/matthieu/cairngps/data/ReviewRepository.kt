package app.matthieu.cairngps.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.matthieu.cairngps.domain.ReviewPrompt
import app.matthieu.cairngps.domain.ReviewTrigger
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore distinct de "settings" : cet état n'est pas une préférence que l'utilisateur règle, il
// ne doit ni apparaître dans l'écran Réglages ni partir dans l'export de sauvegarde.
private val Context.reviewDataStore: DataStore<Preferences> by preferencesDataStore(name = "review")

/**
 * Décide quand proposer l'invitation Play "noter l'application" et retient ce qui a déjà été
 * demandé. Deux déclencheurs, voir [ReviewTrigger] : une trace enregistrée et un succès débloqué.
 *
 * L'invitation ne peut pas toujours être affichée au moment exact du déclencheur : l'arrêt d'une
 * trace peut venir de la notification alors que l'app est en arrière-plan, et l'API Play exige une
 * Activity au premier plan. On mémorise donc une demande *en attente* ([isRequestPending]), que
 * l'UI consomme dès qu'une activité est là. Cette attente est persistée pour survivre à une mort du
 * process entre le déclencheur et la réouverture de l'app.
 */
class ReviewRepository(
    context: Context,
    private val sessionRepository: SessionRepository,
    private val achievementsRepository: AchievementsRepository,
) {

    private val dataStore = context.applicationContext.reviewDataStore

    private object Keys {
        val PENDING = booleanPreferencesKey("request_pending")
        val LAST_REQUESTED_AT = longPreferencesKey("last_requested_at")
    }

    // Un fichier de préférences illisible ne doit pas faire planter l'app pour si peu : on retombe
    // sur "rien en attente, jamais demandé".
    private val preferences: Flow<Preferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    /** Vrai quand une invitation attend d'être affichée au prochain passage au premier plan. */
    val isRequestPending: Flow<Boolean> = preferences.map { it[Keys.PENDING] == true }

    /** À appeler une fois qu'une trace a réellement été enregistrée. */
    suspend fun onSessionCompleted(now: Long = System.currentTimeMillis()) {
        arm(ReviewTrigger.COMPLETED_SESSIONS, sessionRepository.finishedCount(), now)
    }

    /** À appeler à chaque nouveau succès débloqué (pas sur un succès déjà acquis). */
    suspend fun onAchievementUnlocked(now: Long = System.currentTimeMillis()) {
        arm(ReviewTrigger.UNLOCKED_ACHIEVEMENTS, achievementsRepository.unlockedCount(), now)
    }

    /** Arme l'invitation si la politique de [ReviewPrompt] l'autorise, sinon ne fait rien. */
    private suspend fun arm(trigger: ReviewTrigger, count: Int, now: Long) {
        val lastRequestedAt = preferences.first()[Keys.LAST_REQUESTED_AT]
        if (!ReviewPrompt.shouldRequest(trigger, count, lastRequestedAt, now)) return
        dataStore.edit { prefs -> prefs[Keys.PENDING] = true }
    }

    /**
     * L'invitation a été affichée : on désarme et on note la date, qui fait courir le délai avant
     * la prochaine.
     */
    suspend fun markRequested(now: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[Keys.PENDING] = false
            prefs[Keys.LAST_REQUESTED_AT] = now
        }
    }

    /**
     * L'invitation n'a pas pu être affichée (Play Store absent, appel refusé par le quota…). On
     * désarme sans dater : l'invitation pourra être réarmée à la prochaine trace enregistrée.
     */
    suspend fun clearRequest() {
        dataStore.edit { prefs -> prefs[Keys.PENDING] = false }
    }
}
