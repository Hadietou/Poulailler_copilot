package com.example.poulailler_copilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "egg_sales")
data class EggSale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val date: Long,
    val quantity: Int,
    val pricePerUnit: Double,
    val totalPrice: Double,
    val buyer: String? = null,
    val phoneNumber: String? = null,
    val firestoreId: String? = null
)
