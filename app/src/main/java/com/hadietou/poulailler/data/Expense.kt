package com.hadietou.poulailler.data

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
    val farmId: String? = null,
    val batchId: String? = null,
    val subCategory: String? = null,
    /** Nom du produit acheté (médicament, vaccin…) pour la catégorie Cheptel & Santé. */
    val productName: String? = null,
    /** Type du médicament acheté (clé de [com.hadietou.poulailler.util.MedicationType]). */
    val medicationType: String? = null
)