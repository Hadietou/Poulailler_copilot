package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mortality")
data class Mortality(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val count: Int,
    val date: Long,
    val firestoreId: String? = null,
    val farmId: String? = null,
    val batchId: String? = null
)