package com.hadietou.poulailler.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface BrevoApiService {
    @POST("v3/smtp/email")
    suspend fun sendEmail(
        @Header("api-key") apiKey: String,
        @Body request: BrevoEmailRequest
    ): Response<BrevoResponse>

    companion object {
        const val BASE_URL = "https://api.brevo.com/"
    }
}
