package com.hadietou.poulailler.network

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") lat: Double = 18.0858,
        @Query("longitude") lon: Double = -15.9785,
        @Query("daily") daily: String = "temperature_2m_max",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") days: Int = 5
    ): WeatherResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
    }
}
