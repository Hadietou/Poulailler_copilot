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
 * Affiche un bandeau persistant "Pas de connexion internet" tant que l'appareil n'a pas
 * de connectivité active, et le masque dès qu'elle revient. L'app dépendant entièrement
 * de Firebase, l'utilisateur doit savoir immédiatement pourquoi rien ne se charge/synchronise.
 */
object NetworkStatusMonitor {

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
                        offlineSnackbar = Snackbar.make(rootView, "Pas de connexion internet", Snackbar.LENGTH_INDEFINITE)
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
