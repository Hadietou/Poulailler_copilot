package com.hadietou.poulailler.util

import android.content.Context

/**
 * Mémorise, sur l'appareil, les alertes sanitaires que l'éleveur a choisi d'ignorer (voir
 * [HealthAlert.key]). Les alertes liées à un jour précis (pic ou hausse de mortalité) reviennent
 * d'elles-mêmes le lendemain si la situation persiste, car leur clé change.
 */
object IgnoredHealthAlerts {

    private const val PREFS = "HealthAlertPrefs"
    private const val KEY_IGNORED = "ignored_alert_keys"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): Set<String> = prefs(context).getStringSet(KEY_IGNORED, emptySet()) ?: emptySet()

    fun ignore(context: Context, key: String) = save(context, all(context) + key)

    fun restore(context: Context, key: String) = save(context, all(context) - key)

    fun restoreAll(context: Context) = save(context, emptySet())

    // Copie obligatoire : le Set renvoyé par getStringSet ne doit pas être modifié en place.
    private fun save(context: Context, keys: Set<String>) {
        prefs(context).edit().putStringSet(KEY_IGNORED, HashSet(keys)).apply()
    }
}
