package com.hadietou.poulailler.network

import com.hadietou.poulailler.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Journal des requêtes uniquement en debug, et sans la clé d'API Brevo : en release, le logcat
    // du téléphone ne doit pas exposer de clé ni de contenu d'email.
    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        redactHeader("api-key")
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val brevoApi: BrevoApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BrevoApiService.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
            .create(BrevoApiService::class.java)
    }

    val weatherApi: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(WeatherApiService.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
            .create(WeatherApiService::class.java)
    }

    val geocodingApi: GeocodingApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GeocodingApiService.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
            .create(GeocodingApiService::class.java)
    }
}
