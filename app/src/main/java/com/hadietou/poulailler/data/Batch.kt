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
    val feedRation: Double = 0.120 // Ration en kg/sujet/jour
)
