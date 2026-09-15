package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tâche de biosécurité récurrente (Phase 3), rattachée à la ferme (pas à un lot précis).
 * [nextDueDate] et [isOverdue] sont calculés à partir de la fréquence et de la dernière
 * exécution — aucune date d'échéance n'est stockée telle quelle.
 */
@Entity(tableName = "biosecurity_tasks")
data class BiosecurityTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val task: String,
    val frequency: String, // QUOTIDIEN, HEBDOMADAIRE, MENSUEL, EVENEMENT
    val lastDoneDate: Long? = null,
    val doneBy: String? = null,
    val firestoreId: String? = null,
    val farmId: String? = null
) {
    val intervalDays: Int?
        get() = when (frequency) {
            "QUOTIDIEN" -> 1
            "HEBDOMADAIRE" -> 7
            "MENSUEL" -> 30
            else -> null // EVENEMENT : pas de périodicité automatique
        }

    val nextDueDate: Long?
        get() {
            val interval = intervalDays ?: return null
            val base = lastDoneDate ?: return null // jamais fait : pas d'échéance calculable, voir isOverdue
            return base + interval * 86_400_000L
        }

    val isOverdue: Boolean
        get() {
            if (lastDoneDate == null) return true // jamais fait -> toujours à faire
            val due = nextDueDate ?: return false // fréquence EVENEMENT : pas de périodicité automatique
            return due < System.currentTimeMillis()
        }

    companion object {
        val FREQUENCY_LABELS = linkedMapOf(
            "QUOTIDIEN" to "Quotidienne",
            "HEBDOMADAIRE" to "Hebdomadaire",
            "MENSUEL" to "Mensuelle",
            "EVENEMENT" to "Selon événement"
        )
        val COMMON_TASKS = listOf(
            "Nettoyage du poulailler",
            "Désinfection",
            "Pédiluve à l'entrée",
            "Contrôle des visiteurs",
            "Contrôle des équipements",
            "Lutte contre les rongeurs",
            "Lutte contre les insectes",
            "Nettoyage des abreuvoirs",
            "Nettoyage des mangeoires",
            "Gestion des cadavres",
            "Contrôle de l'accès au poulailler",
            "Quarantaine des nouveaux animaux"
        )
    }
}
