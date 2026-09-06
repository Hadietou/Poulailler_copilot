package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batches")
data class Batch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hensCount: Int,
    val henBreed: String,
    val arrivalDate: Long,
    val chickBirthDate: Long,
    val status: String = "ACTIVE", // ACTIVE, COMPLETED
    val typeLot: String = "PONDEUSE", // PONDEUSE, CHAIR
    val firestoreId: String? = null,
    val farmId: String? = null,
    val feedRation: Double = 0.120, // Ration actuelle en kg/sujet/jour
    val providerName: String? = null,
    val providerPhone: String? = null,
    // Historique des changements de ration manuelle, sérialisé sous la forme
    // "dateEffective1:ration1;dateEffective2:ration2;...", trié par date croissante.
    // Permet de recalculer le stock consommé en appliquant à chaque jour la ration
    // qui était réellement en vigueur ce jour-là, plutôt que d'appliquer la ration
    // actuelle rétroactivement à tout l'historique du lot.
    val feedRationHistory: String = ""
)

/** Un changement de ration effectif à partir d'une date donnée (minuit ce jour-là). */
data class FeedRationChange(val effectiveDate: Long, val ration: Double)

fun Batch.parseFeedRationHistory(): List<FeedRationChange> {
    if (feedRationHistory.isBlank()) return emptyList()
    return feedRationHistory.split(";").mapNotNull { entry ->
        val parts = entry.split(":")
        val date = parts.getOrNull(0)?.toLongOrNull()
        val ration = parts.getOrNull(1)?.toDoubleOrNull()
        if (date != null && ration != null) FeedRationChange(date, ration) else null
    }.sortedBy { it.effectiveDate }
}

fun List<FeedRationChange>.toHistoryString(): String =
    joinToString(";") { "${it.effectiveDate}:${it.ration}" }
