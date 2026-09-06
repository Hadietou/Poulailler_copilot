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
            checkSameDayHeatUpdate()
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
                "Période de chaleur prévue ($highTempValue°C le $highTempDay). Hydratation et anti-stress recommandés."
            )

            // On mémorise le jour annoncé pour pouvoir revérifier la prévision
            // (plus précise) le jour J à 8h, via checkSameDayHeatUpdate().
            prefs.edit().putString("pendingHeatDay", highTempDay).apply()
        }

        prefs.edit().putString("lastCheckDate", todayDate).apply()
    }

    /**
     * Revérifie, le jour J à partir de 8h (heure locale de Nouakchott), la prévision
     * d'un jour de chaleur préalablement annoncé 24h à l'avance par [checkHeatAlert].
     * La prévision du jour même étant plus précise que celle établie la veille, on
     * envoie une nouvelle alerte "mise à jour" avec la valeur recalculée si le seuil
     * est toujours dépassé (l'anti-spam de sendHeatAlertEmail autorise un envoi par
     * date calendaire, donc l'alerte J-1 et la mise à jour du jour J passent toutes
     * les deux). Si la température a finalement baissé sous le seuil, on n'envoie rien.
     */
    private suspend fun checkSameDayHeatUpdate() {
        val prefs = applicationContext.getSharedPreferences("HeatAlertPrefs", Context.MODE_PRIVATE)
        val pendingHeatDay = prefs.getString("pendingHeatDay", "")
        if (pendingHeatDay.isNullOrEmpty()) return

        val nouakchottTz = TimeZone.getTimeZone("Africa/Nouakchott")
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = nouakchottTz }.format(Date())

        // La revérification ne concerne que le jour annoncé, pas avant.
        if (pendingHeatDay != todayDate) return

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
            val updatedTemp = maxTemperatures[todayIndex]
            if (updatedTemp >= info.heatAlertTempCelsius) {
                firebaseRepo.sendHeatAlertEmail(email, info.farmName, todayDate, updatedTemp)
            } else {
                Log.d("HeatAlertWorker", "Revérification 8h : température finalement sous le seuil ($updatedTemp°C), pas d'alerte.")
            }
        }

        // Que l'alerte ait été renvoyée ou non, la revérification du jour est faite :
        // on évite de la refaire à chaque exécution du worker et on libère le jour annoncé.
        prefs.edit()
            .putString("sameDayCheckDate", todayDate)
            .putString("pendingHeatDay", "")
            .apply()
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
