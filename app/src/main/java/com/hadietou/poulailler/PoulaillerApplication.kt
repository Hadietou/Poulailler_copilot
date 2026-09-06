package com.hadietou.poulailler

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hadietou.poulailler.worker.HeatAlertWorker
import org.osmdroid.config.Configuration
import java.io.File
import java.util.concurrent.TimeUnit

class PoulaillerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        configureOsmdroid()
        scheduleHeatAlertWorker()
    }

    /**
     * Doit être fait une fois avant toute utilisation d'un MapView (écran de sélection
     * de la localité de la ferme) : userAgent requis par les serveurs de tuiles OSM,
     * cache dans le dossier interne de l'appli (pas de permission de stockage nécessaire).
     */
    private fun configureOsmdroid() {
        val config = Configuration.getInstance()
        config.userAgentValue = packageName
        config.osmdroidBasePath = File(cacheDir, "osmdroid")
        config.osmdroidTileCache = File(config.osmdroidBasePath, "tiles")
    }

    /**
     * Planifie la vérification météo (alerte canicule) toutes les 6h, y compris quand
     * l'application n'est pas ouverte. Voir [HeatAlertWorker] pour la logique.
     * ExistingPeriodicWorkPolicy.KEEP : si une planification existe déjà (ex: après un
     * redémarrage du téléphone ou une mise à jour), on ne la remplace pas pour ne pas
     * décaler son cycle.
     */
    private fun scheduleHeatAlertWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<HeatAlertWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "heat_alert_check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
