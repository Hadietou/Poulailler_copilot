package com.hadietou.poulailler.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hadietou.poulailler.data.HealthReminder
import com.hadietou.poulailler.network.WeatherUtils
import com.hadietou.poulailler.repository.FirebaseRepository
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Vérifie périodiquement (toutes les 6h, voir la planification dans PoulaillerApplication)
 * les prévisions météo pour déclencher l'alerte canicule, même quand l'application n'est
 * pas ouverte. Reprend la logique auparavant portée par DashboardViewModel, qui ne
 * s'exécutait qu'à l'ouverture du dashboard et pouvait donc manquer la fenêtre d'alerte
 * (J-1) ou la revérification du jour J à 8h si l'utilisateur n'ouvrait pas l'appli à temps.
 */
class HeatAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val firebaseRepo = FirebaseRepository()

    override suspend fun doWork(): Result {
        return try {
            checkHeatAlert()
            checkTodayHeatAlert()
            Result.success()
        } catch (e: Exception) {
            Log.e("HeatAlertWorker", "Error during heat alert check", e)
            Result.retry()
        }
    }

    /**
     * Alerte à J-1 : si une des prochaines prévisions dépasse le seuil, on prévient
     * 24 heures à l'avance afin de laisser le temps de prendre les précautions, au lieu
     * d'alerter le jour même de la chaleur.
     */
    private suspend fun checkHeatAlert() {
        val prefs = applicationContext.getSharedPreferences("HeatAlertPrefs", Context.MODE_PRIVATE)
        val lastCheckDate = prefs.getString("lastCheckDate", "")
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (lastCheckDate == todayDate) return

        val email = firebaseRepo.getResponsibleEmail() ?: return
        val info = firebaseRepo.getFarmInfo() ?: return

        val response = WeatherUtils.fetchForecast(info.latitude, info.longitude)
        val daily = response.daily
        val maxTemperatures = WeatherUtils.applyOffset(daily.maxTemperatures, info.weatherTempOffsetCelsius)

        var alertNeeded = false
        var highTempDay = ""
        var highTempValue = 0.0

        // On démarre à i = 1 (demain) et non i = 0 (aujourd'hui), pour la même raison
        // que ci-dessus : prévenir 24h à l'avance.
        val heatThreshold = info.heatAlertTempCelsius
        for (i in 1 until daily.time.size) {
            val temp = maxTemperatures[i]
            if (temp >= heatThreshold) {
                alertNeeded = true
                highTempDay = daily.time[i]
                highTempValue = temp
                break
            }
        }

        if (alertNeeded) {
            firebaseRepo.sendHeatAlertEmail(email, info.farmName, highTempDay, highTempValue)
            addVitaminReminderForActiveBatch(
                "Vitamine C / Électrolytes",
                "Période de chaleur prévue (${Math.round(highTempValue)}°C le $highTempDay). Hydratation et anti-stress recommandés."
            )
        }

        prefs.edit().putString("lastCheckDate", todayDate).apply()
    }

    /**
     * Vérifie, chaque jour à partir de 8h (heure locale de Nouakchott), si la température
     * du jour même dépasse le seuil, indépendamment de ce que [checkHeatAlert] a trouvé ou
     * non pour les jours suivants.
     *
     * Auparavant, cette revérification se basait sur un "jour annoncé" (pendingHeatDay)
     * mémorisé par checkHeatAlert() lors de l'alerte J-1 de la veille. Problème : si
     * checkHeatAlert() retrouvait UN AUTRE jour chaud (ex: demain) lors de son passage du
     * jour même, il écrasait ce jour annoncé avant que cette fonction ait pu le lire —
     * la reconfirmation du jour même se retrouvait alors silencieusement sautée (elle ne
     * trouvait plus "aujourd'hui" comme jour en attente). En vérifiant directement la
     * prévision d'aujourd'hui (sans dépendre d'un état laissé par une autre fonction),
     * ce problème disparaît : les deux alertes (J-1 pour demain, confirmation du jour
     * même) peuvent désormais partir le même jour calendaire sans se marcher dessus.
     */
    private suspend fun checkTodayHeatAlert() {
        val prefs = applicationContext.getSharedPreferences("HeatAlertPrefs", Context.MODE_PRIVATE)
        val nouakchottTz = TimeZone.getTimeZone("Africa/Nouakchott")
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = nouakchottTz }.format(Date())

        val localHour = Calendar.getInstance(nouakchottTz).get(Calendar.HOUR_OF_DAY)
        if (localHour < 8) return

        val sameDayCheckDate = prefs.getString("sameDayCheckDate", "")
        if (sameDayCheckDate == todayDate) return

        val email = firebaseRepo.getResponsibleEmail() ?: return
        val info = firebaseRepo.getFarmInfo() ?: return

        val response = WeatherUtils.fetchForecast(info.latitude, info.longitude)
        val daily = response.daily
        val maxTemperatures = WeatherUtils.applyOffset(daily.maxTemperatures, info.weatherTempOffsetCelsius)
        val todayIndex = daily.time.indexOf(todayDate)

        if (todayIndex != -1) {
            val todayTemp = maxTemperatures[todayIndex]
            if (todayTemp >= info.heatAlertTempCelsius) {
                firebaseRepo.sendHeatAlertEmail(email, info.farmName, todayDate, todayTemp)
            } else {
                Log.d("HeatAlertWorker", "Vérification 8h : température du jour finalement sous le seuil (${Math.round(todayTemp)}°C), pas d'alerte.")
            }
        }

        // Qu'une alerte ait été envoyée ou non, la vérification du jour est faite : on
        // évite de la refaire à chaque exécution du worker (toutes les 6h) le même jour.
        prefs.edit().putString("sameDayCheckDate", todayDate).apply()
    }

    private suspend fun addVitaminReminderForActiveBatch(title: String, desc: String) {
        val batches = firebaseRepo.getBatchesFlow().first()
        val batch = batches.firstOrNull { it.status == "ACTIVE" } ?: batches.firstOrNull() ?: return
        val batchId = batch.firestoreId ?: return

        val reminders = firebaseRepo.getHealthRemindersFlow().first()
        val existing = reminders.find { it.title == title && it.batchId == batchId && !it.isDone }
        if (existing == null) {
            firebaseRepo.addHealthReminder(
                HealthReminder(
                    type = "VITAMINE",
                    title = title,
                    description = desc,
                    dueDate = System.currentTimeMillis(),
                    batchId = batchId
                )
            )
        }
    }
}
