package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vet_visits")
data class VetVisit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val vetName: String,
    val reason: String? = null,
    val observations: String? = null,
    val diagnosis: String? = null,
    val recommendations: String? = null,
    val vaccinationsDone: String? = null,
    val analysesRequested: String? = null,
    val nextVisitDate: Long? = null,
    val firestoreId: String? = null,
    val farmId: String? = null,
    val batchId: String? = null
)
