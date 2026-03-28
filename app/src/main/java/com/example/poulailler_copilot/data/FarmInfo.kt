package com.example.poulailler_copilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "farm_info")
data class FarmInfo(
    @PrimaryKey val id: Int = 1,
    val farmName: String = "",
    val hensCount: Int = 0,
    val henBreed: String = "",
    val arrivalDate: Long = 0,
    val chickBirthDate: Long = 0,
    val setupDate: Long = System.currentTimeMillis(),
    val feedInfo: String = "",
    val mortality: Int = 0,
    val expenses: Double = 0.0,
    val currency: String = "MRU" // Default currency
)
