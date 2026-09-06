package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "farm_info")
data class FarmInfo(
    @PrimaryKey val id: Int = 1,
    val farmName: String = "",
    val currency: String = "MRU", // Default currency
    val setupDate: Long = System.currentTimeMillis(),

    // --- Localisation (saisie manuelle ou choisie sur la carte) ---
    // Sert notamment à interroger l'API météo avec les coordonnées réelles de la ferme
    // au lieu des coordonnées par défaut (Nouakchott) utilisées tant qu'aucune n'est définie.
    val locality: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,

    // --- Seuils d'alerte (réglables depuis Paramètres) ---
    // Stock d'alvéoles à œufs en dessous duquel l'alerte critique se déclenche.
    val eggTraysCriticalThreshold: Int = DEFAULT_EGG_TRAYS_CRITICAL,
    // Autonomie du stock d'aliment (en jours) : seuil critique (rouge) et seuil d'alerte (orange).
    val feedStockCriticalDays: Int = DEFAULT_FEED_STOCK_CRITICAL_DAYS,
    val feedStockWarningDays: Int = DEFAULT_FEED_STOCK_WARNING_DAYS,
    // Température (°C) à partir de laquelle une alerte canicule est envoyée.
    val heatAlertTempCelsius: Int = DEFAULT_HEAT_ALERT_TEMP,
    // Correction manuelle (°C) appliquée aux températures prévues par l'API, pour compenser
    // l'écart constaté entre la prévision (modèle large échelle, température sous abri) et le
    // relevé de terrain, en l'absence de capteur sur place. Peut être positive ou négative.
    val weatherTempOffsetCelsius: Double = DEFAULT_WEATHER_TEMP_OFFSET,
    // Photopériode : nombre d'heures d'éclairage artificiel après le lever du soleil.
    val lightingHoursAfterSunrise: Int = DEFAULT_LIGHTING_HOURS,
    // Intervalles (en mois) des rappels sanitaires récurrents.
    val vaccineIntervalMonths: Int = DEFAULT_VACCINE_INTERVAL_MONTHS,
    val dewormingInternalIntervalMonths: Int = DEFAULT_DEWORMING_INTERNAL_MONTHS,
    val dewormingExternalIntervalMonths: Int = DEFAULT_DEWORMING_EXTERNAL_MONTHS
) {
    companion object {
        const val DEFAULT_EGG_TRAYS_CRITICAL = 50
        const val DEFAULT_FEED_STOCK_CRITICAL_DAYS = 5
        const val DEFAULT_FEED_STOCK_WARNING_DAYS = 10
        const val DEFAULT_HEAT_ALERT_TEMP = 35
        const val DEFAULT_WEATHER_TEMP_OFFSET = 0.0
        const val DEFAULT_LIGHTING_HOURS = 15
        const val DEFAULT_VACCINE_INTERVAL_MONTHS = 2
        const val DEFAULT_DEWORMING_INTERNAL_MONTHS = 3
        const val DEFAULT_DEWORMING_EXTERNAL_MONTHS = 2
    }
}
