package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Check-list sanitaire quotidienne (Phase 3). Chaque bloc a un état parmi une petite liste
 * prédéfinie (pas de saisie libre) pour rester saisissable en moins d'une minute au poulailler.
 * [anomalyDetected] est calculé automatiquement dès qu'un bloc s'écarte de son état "normal".
 */
@Entity(tableName = "health_observations")
data class HealthObservation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val behavior: String = "NORMAL",
    val feed: String = "NORMAL",
    val water: String = "NORMAL",
    val respiration: String = "NORMAL",
    val droppings: String = "NORMAL",
    val appearance: String = "NORMAL",
    val notes: String? = null,
    val recordedBy: String? = null,
    val firestoreId: String? = null,
    val farmId: String? = null,
    val batchId: String? = null
) {
    val anomalyDetected: Boolean
        get() = listOf(behavior, feed, water, respiration, droppings, appearance).any { it != "NORMAL" }

    val anomalyFields: List<Pair<String, String>>
        get() = listOfNotNull(
            "Comportement".takeIf { behavior != "NORMAL" }?.let { it to behaviorLabels.getValue(behavior) },
            "Alimentation".takeIf { feed != "NORMAL" }?.let { it to feedLabels.getValue(feed) },
            "Eau".takeIf { water != "NORMAL" }?.let { it to waterLabels.getValue(water) },
            "Respiration".takeIf { respiration != "NORMAL" }?.let { it to respirationLabels.getValue(respiration) },
            "Fientes".takeIf { droppings != "NORMAL" }?.let { it to droppingsLabels.getValue(droppings) },
            "Aspect général".takeIf { appearance != "NORMAL" }?.let { it to appearanceLabels.getValue(appearance) }
        )

    companion object {
        val behaviorLabels = linkedMapOf(
            "NORMAL" to "Activité normale",
            "REGROUPEMENT" to "Regroupement inhabituel",
            "APATHIE" to "Apathie",
            "AGRESSIVITE" to "Agressivité"
        )
        val feedLabels = linkedMapOf(
            "NORMAL" to "Consommation normale",
            "BAISSE" to "Baisse de consommation",
            "REFUS" to "Refus de nourriture"
        )
        val waterLabels = linkedMapOf(
            "NORMAL" to "Consommation normale",
            "BAISSE" to "Baisse inhabituelle",
            "AUGMENTATION" to "Augmentation inhabituelle",
            "PROBLEME_ABREUVOIR" to "Problème d'abreuvoir"
        )
        val respirationLabels = linkedMapOf(
            "NORMAL" to "Normale",
            "TOUX" to "Toux",
            "ETERNUEMENTS" to "Éternuements",
            "DIFFICILE" to "Respiration difficile"
        )
        val droppingsLabels = linkedMapOf(
            "NORMAL" to "Normales",
            "DIARRHEE" to "Diarrhée",
            "COULEUR_INHABITUELLE" to "Couleur inhabituelle",
            "ANOMALIES" to "Présence d'anomalies"
        )
        val appearanceLabels = linkedMapOf(
            "NORMAL" to "Normal (plumage, yeux, bec, pattes, crête)",
            "ANORMAL" to "Anomalie visible (plumage, yeux, bec, pattes ou crête)"
        )
    }
}
