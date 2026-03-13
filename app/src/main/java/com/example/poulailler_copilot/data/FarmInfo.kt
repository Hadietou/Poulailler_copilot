package com.example.poulailler_copilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "farm_info")
data class FarmInfo(
    @PrimaryKey val id: Int = 1,
    val hensCount: Int,
    val setupDate: Long, // Date d'installation pour calculer l'âge
    val feedInfo: String,
    val mortality: Int = 0,
    val expenses: Double = 0.0
)
