package com.example.poulailler_copilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val category: String,
    val amount: Double,
    val quantityKg: Double? = null,
    val description: String? = null,
    val firestoreId: String? = null,
    val farmId: String? = null
)