package com.hadietou.poulailler.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.snackbar.Snackbar

/**
 * Prévient brièvement l'utilisateur quand la connexion se coupe, puis se masque tout seul.
 *
 * Les saisies (collecte, vente, dépense…) fonctionnent normalement hors connexion : Firestore
 * les garde en attente sur l'appareil et les synchronise dès que le réseau revient, sans action
 * de l'utilisateur (voir FirebaseRepository.fireAndForget). Ce bandeau n'est donc qu'une
 * information de confort, plus un avertissement bloquant — d'où une durée limitée (quelques
 * secondes) plutôt que permanente, pour ne pas gêner l'écran tant que la coupure dure.
 */
object NetworkStatusMonitor {

    /** Durée d'affichage du bandeau (ms) : visible assez longtemps pour être lu, sans s'incruster. */
    private const val BANNER_DURATION_MS = 4000

    /** À appeler une fois dans onCreate() avec la vue racine de l'écran (ex: binding.root). */
    fun observe(activity: AppCompatActivity, rootView: View) {
        val connectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        var offlineSnackbar: Snackbar? = null

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity.runOnUiThread {
                    offlineSnackbar?.dismiss()
                    offlineSnackbar = null
                }
            }

            override fun onLost(network: Network) {
                activity.runOnUiThread {
                    if (offlineSnackbar == null) {
                        offlineSnackbar = Snackbar.make(
                            rootView,
                            "Hors connexion : vos saisies seront synchronisées au retour du réseau.",
                            BANNER_DURATION_MS
                        )
                        offlineSnackbar?.addCallback(object : Snackbar.Callback() {
                            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                                offlineSnackbar = null
                            }
                        })
                        offlineSnackbar?.show()
                    }
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            return
        }

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    // Déjà désenregistré ou callback jamais actif : sans conséquence.
                }
            }
        })
    }
}
