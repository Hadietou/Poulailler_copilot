package com.hadietou.poulailler.data

/**
 * Trace permanente d'un rappel de santé marqué comme FAIT.
 * Contrairement à [HealthReminder] (qui représente l'état courant d'un rappel récurrent
 * et est réutilisé/réinitialisé à chaque nouveau cycle), une entrée ici n'est jamais
 * modifiée ni supprimée : c'est l'historique consultable des soins effectués.
 */
data class HealthReminderLog(
    val title: String,
    val type: String,
    val description: String? = null,
    val dueDate: Long,
    val doneDate: Long,
    val batchId: String? = null,
    val firestoreId: String? = null
)
