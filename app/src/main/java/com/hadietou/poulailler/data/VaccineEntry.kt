package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vaccine_entries")
data class VaccineEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: Long, // date prévue si status == PLANIFIE, date réalisée sinon
    val remarks: String? = null,
    val firestoreId: String? = null,
    val farmId: String? = null,
    val batchId: String? = null,
    val status: String = "REALISE", // PLANIFIE, REALISE, REPORTE, ANNULE
    val manufacturer: String? = null,
    val lotNumber: String? = null,
    val expiryDate: Long? = null,
    val dose: String? = null,
    val route: String? = null,
    val targetCount: Int? = null,
    val administeredBy: String? = null
)