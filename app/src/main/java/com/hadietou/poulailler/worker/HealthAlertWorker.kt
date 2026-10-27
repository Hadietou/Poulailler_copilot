package com.hadietou.poulailler.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hadietou.poulailler.data.FarmInfo
import com.hadietou.poulailler.repository.FirebaseRepository
import com.hadietou.poulailler.util.AlertLevel
import com.hadietou.poulailler.util.AlertNotifier
import com.hadietou.poulailler.util.HealthAlertEngine
import com.hadietou.poulailler.util.IgnoredHealthAlerts
import kotlinx.coroutines.flow.first

/**
 * Prévient par notification des alertes sanitaires CRITIQUES (mortalité anormale, rappel en retard,
 * délai d'attente, diagnostic confirmé…), même quand l'application n'est pas ouverte. Les alertes de
 * niveau « attention » restent visibles dans le Suivi sanitaire sans notification, pour ne pas
 * noyer l'éleveur. Une alerte déjà notifiée n'est pas renvoyée avant [RENOTIFY_AFTER_MS], et celles
 * que l'éleveur a choisi d'ignorer ne sont jamais notifiées.
 */
class HealthAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val firebaseRepo = FirebaseRepository()

    override suspend fun doWork(): Result {
        return try {
            checkHealthAlerts()
            Result.success()
        } catch (e: Exception) {
            Log.e("HealthAlertWorker", "Error during health alert check", e)
            Result.retry()
        }
    }

    private suspend fun checkHealthAlerts() {
        val info = firebaseRepo.getFarmInfo() ?: run {
            Log.w("HealthAlertWorker", "Informations de la ferme introuvables : pas de vérification sanitaire")
            return
        }
        val now = System.currentTimeMillis()

        val alerts = HealthAlertEngine.computeAll(
            mortalities = firebaseRepo.getMortalityFlow().first(),
            reminders = firebaseRepo.getHealthRemindersFlow().first(),
            treatments = firebaseRepo.getTreatmentsFlow().first(),
            diseaseCases = firebaseRepo.getDiseaseCasesFlow().first(),
            observations = firebaseRepo.getHealthObservationsFlow().first(),
            biosecurityTasks = firebaseRepo.getBiosecurityTasksFlow().first(),
            mortalitySpikeMultiplier = info.mortalitySpikeMultiplier ?: FarmInfo.DEFAULT_MORTALITY_SPIKE_MULTIPLIER,
            now = now
        )

        val ignored = IgnoredHealthAlerts.all(applicationContext)
        val prefs = applicationContext.getSharedPreferences("HealthAlertNotifPrefs", Context.MODE_PRIVATE)
        // Entrées « clé|date de notification », purgées après RENOTIFY_AFTER_MS.
        val notified = (prefs.getStringSet("notified", emptySet()) ?: emptySet())
            .mapNotNull { entry -> entry.substringAfterLast('|').toLongOrNull()?.let { entry.substringBeforeLast('|') to it } }
            .filter { now - it.second < RENOTIFY_AFTER_MS }
            .toMap()
            .toMutableMap()

        val fresh = alerts.filter { it.level == AlertLevel.CRITIQUE && it.key !in ignored && it.key !in notified }
        if (fresh.isNotEmpty()) {
            val sent = if (fresh.size == 1) {
                AlertNotifier.notifyHealth(applicationContext, "🔴 ${fresh[0].title} · ${info.farmName}", fresh[0].message, NOTIFICATION_ID)
            } else {
                AlertNotifier.notifyHealth(
                    applicationContext,
                    "🔴 ${fresh.size} alertes sanitaires · ${info.farmName}",
                    fresh.joinToString("\n") { "• ${it.title}" },
                    NOTIFICATION_ID
                )
            }
            // Si les notifications sont refusées sur ce téléphone, on ne marque rien : elles partiront dès l'autorisation.
            if (sent) fresh.forEach { notified[it.key] = now }
        }

        prefs.edit().putStringSet("notified", notified.map { "${it.key}|${it.value}" }.toSet()).apply()
    }

    private companion object {
        const val NOTIFICATION_ID = 2001
        const val RENOTIFY_AFTER_MS = 7 * 86_400_000L
    }
}
