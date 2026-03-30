package com.example.poulailler_copilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "egg_entries")
data class EggEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val date: Long,
    val eggsCount: Int,
    val brokenEggsCount: Int = 0,
    val remarks: String?,
    val firestoreId: String? = null
)