package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cas de symptômes/maladie signalé sur le lot. Le [status] distingue explicitement trois
 * niveaux de certitude, pour ne jamais présenter un diagnostic comme acquis :
 * un symptôme observé n'est qu'une observation, une maladie suspectée reste une hypothèse
 * de l'éleveur, et seul un diagnostic vétérinaire confirmé (vetDiagnosis renseigné) doit
 * être traité comme une certitude.
 */
@Entity(tableName = "disease_cases")
data class DiseaseCase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val suspectedDisease: String? = null,
    val symptoms: List<String> = emptyList(),
    val status: String = "SYMPTOME_OBSERVE", // SYMPTOME_OBSERVE, MALADIE_SUSPECTEE, DIAGNOSTIC_CONFIRME
    val severity: String = "MOYENNE", // FAIBLE, MOYENNE, ELEVEE
    val dateReported: Long,
    val affectedCount: Int? = null,
    val deathCount: Int? = null,
    val zone: String? = null,
    val observerNotes: String? = null,
    val vetDiagnosis: String? = null,
    val actionsTaken: String? = null,
    val evolutionNotes: String? = null,
    val firestoreId: String? = null,
    val farmId: String? = null,
    val batchId: String? = null
) {
    companion object {
        val COMMON_SYMPTOMS = listOf(
            "Baisse d'appétit",
            "Baisse de consommation d'eau",
            "Diarrhée",
            "Toux",
            "Éternuements",
            "Difficultés respiratoires",
            "Écoulements",
            "Baisse de ponte",
            "Coquilles anormales",
            "Plumage anormal",
            "Boiterie",
            "Comportement inhabituel",
            "Mortalité inhabituelle"
        )
    }
}
