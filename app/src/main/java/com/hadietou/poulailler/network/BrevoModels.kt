package com.hadietou.poulailler.network

import com.google.gson.annotations.SerializedName

data class BrevoEmailRequest(
    val sender: BrevoSender,
    val to: List<BrevoReceiver>,
    val subject: String,
    val htmlContent: String
)

data class BrevoSender(
    val name: String,
    val email: String
)

data class BrevoReceiver(
    val email: String,
    val name: String? = null
)

data class BrevoResponse(
    val messageId: String? = null,
    val code: String? = null,
    val message: String? = null
)
