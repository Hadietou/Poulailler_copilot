package com.example.poulailler_copilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "farm_info")
data class FarmInfo(
    @PrimaryKey val id: Int = 1,
    val farmName: String = "",
    val currency: String = "MRU", // Default currency
    val setupDate: Long = System.currentTimeMillis()
)
