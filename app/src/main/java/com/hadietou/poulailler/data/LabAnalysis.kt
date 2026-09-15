package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lab_analyses")
data class LabAnalysis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // LABORATOIRE, PARASITOLOGIE, MICROBIOLOGIE, AUTOPSIE, EAU, ALIMENT, AUTRE
    val date: Long,
    val resultSummary: String? = null,
    val notes: String? = null,
    val firestoreId: String? = null,
    val farmId: String? = null,
    val batchId: String? = null
) {
    companion object {
        val TYPE_LABELS = linkedMapOf(
            "LABORATOIRE" to "🧪 Analyse de laboratoire",
            "PARASITOLOGIE" to "🐛 Analyse parasitologique",
            "MICROBIOLOGIE" to "🦠 Analyse microbiologique",
            "AUTOPSIE" to "🔬 Autopsie",
            "EAU" to "💧 Analyse d'eau",
            "ALIMENT" to "🌾 Analyse d'aliment",
            "AUTRE" to "📋 Autre examen"
        )
    }
}
