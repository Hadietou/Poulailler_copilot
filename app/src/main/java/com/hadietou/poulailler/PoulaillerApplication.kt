package com.hadietou.poulailler

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.appbar.AppBarLayout
import com.hadietou.poulailler.worker.HeatAlertWorker
import org.osmdroid.config.Configuration
import java.io.File
import java.util.concurrent.TimeUnit

class PoulaillerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // L'app n'a qu'une palette pensée pour le mode clair (le mode sombre partiel via
        // values-night/colors.xml ne redéfinissait que certaines couleurs, ex: pas @color/primary,
        // ce qui produisait un mélange incohérent vert/noir sous le thème sombre du système).
        // On force donc le mode clair partout tant qu'un vrai thème sombre complet n'existe pas.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        configureOsmdroid()
        scheduleHeatAlertWorker()
        fixEdgeToEdgeInsets()
    }

    /**
     * Depuis targetSdk 35+ (Android 15/16), le système impose l'affichage "edge-to-edge" :
     * le contenu des écrans est dessiné derrière les barres système (statut en haut,
     * navigation/gestes en bas), ce qui peut masquer le bas des pages ET rendre les icônes
     * du bandeau supérieur (☰, retour…) impossibles à toucher : la bande de la barre de
     * statut intercepte les taps même quand elle est transparente et affiche du contenu de
     * l'appli derrière elle. On réserve donc automatiquement l'espace du bas (padding sur
     * le contenu) ET celui du haut (padding sur le bandeau coloré/AppBarLayout, sans casser
     * l'effet "couleur derrière la barre de statut" puisque le padding ne rogne pas le fond),
     * pour toutes les activités de l'app, sans avoir à modifier chaque layout individuellement.
     */
    private fun fixEdgeToEdgeInsets() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                val content = activity.findViewById<View>(android.R.id.content) ?: return
                ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    view.updatePadding(bottom = bars.bottom)
                    insets
                }

                content.post {
                    val appBar = findFirstAppBarLayout(content)
                    if (appBar != null) {
                        val initialTopPadding = appBar.paddingTop
                        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
                            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                            view.updatePadding(top = initialTopPadding + bars.top)
                            insets
                        }
                        ViewCompat.requestApplyInsets(appBar)
                    }
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun findFirstAppBarLayout(view: View): AppBarLayout? {
        if (view is AppBarLayout) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findFirstAppBarLayout(view.getChildAt(i))?.let { return it }
            }
        }
        return null
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
