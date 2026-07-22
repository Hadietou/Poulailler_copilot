package com.hadietou.poulailler.network

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    val daily: DailyData
)

data class DailyData(
    val time: List<String>,
    @SerializedName("temperature_2m_max")
    val maxTemperatures: List<Double>
)
