package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "egg_entries")
data class EggEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val date: Long,
    val eggsCount: Int,
    val brokenEggsCount: Int = 0,
    val remarks: String?,
    val firestoreId: String? = null,
    val farmId: String? = null,
    val batchId: String? = null
)