package com.example.poulailler_copilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mortality")
data class Mortality(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val count: Int,
    val date: Long,
    val firestoreId: String? = null
)
