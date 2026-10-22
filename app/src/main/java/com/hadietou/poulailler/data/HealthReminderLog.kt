package com.hadietou.poulailler.data

/**
 * Trace permanente d'une action sur un rappel de santé : marqué FAIT ou IGNORÉ.
 * Contrairement à [HealthReminder] (qui représente l'état courant d'un rappel récurrent
 * et est réutilisé/réinitialisé à chaque nouveau cycle), une entrée ici n'est jamais
 * modifiée ni supprimée : c'est l'historique consultable des soins effectués ou ignorés.
 *
 * [doneDate] = date de l'action (soin fait ou rappel ignoré).
 * [nextDueDate] = prochaine échéance prévue pour un rappel cyclique (null sinon).
 */
data class HealthReminderLog(
    val title: String,
    val type: String,
    val description: String? = null,
    val dueDate: Long,
    val doneDate: Long,
    val batchId: String? = null,
    val firestoreId: String? = null,
    val status: String = STATUS_DONE,
    val nextDueDate: Long? = null
) {
    val isIgnored: Boolean get() = status == STATUS_IGNORED

    companion object {
        const val STATUS_DONE = "FAIT"
        const val STATUS_IGNORED = "IGNORE"
    }
}
