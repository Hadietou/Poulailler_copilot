package com.hadietou.poulailler.network

/**
 * Point d'entrée unique pour interroger l'API météo, afin que tous les écrans (Dashboard,
 * Suivi Sanitaire, alerte canicule en tâche de fond) utilisent la même logique :
 *  1. Les coordonnées réelles de la ferme si elles sont renseignées, sinon un repli sur
 *     Nouakchott-centre (coordonnées par défaut de [WeatherApiService]).
 *  2. Une correction manuelle (°C) appliquée aux températures reçues, pour compenser l'écart
 *     entre la prévision (modèle large échelle, température sous abri standard) et le relevé
 *     réel sur le terrain, en l'absence de capteur sur place. Voir [com.hadietou.poulailler.data.FarmInfo.weatherTempOffsetCelsius].
 */
object WeatherUtils {

    suspend fun fetchForecast(latitude: Double?, longitude: Double?): WeatherResponse =
        if (latitude != null && longitude != null) {
            RetrofitClient.weatherApi.getForecast(lat = latitude, lon = longitude)
        } else {
            RetrofitClient.weatherApi.getForecast()
        }

    /** Applique la correction de calibration à une liste de températures. */
    fun applyOffset(temperatures: List<Double>, offsetCelsius: Double): List<Double> =
        if (offsetCelsius == 0.0) temperatures else temperatures.map { it + offsetCelsius }
}
