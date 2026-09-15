package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treatments")
data class Treatment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationName: String,
    val reason: String? = null,
    val startDate: Long,
    val endDate: Long? = null,
    val dose: String? = null,
    val route: String? = null,
    val targetCount: Int? = null,
    val prescriber: String? = null,
    val status: String = "EN_COURS", // PREVU, EN_COURS, TERMINE
    // Délais d'attente saisis manuellement (en jours après endDate) : aucune base de
    // référence par molécule n'existe dans l'app, l'éleveur/vétérinaire renseigne le délai
    // indiqué sur la notice du médicament.
    val eggWithdrawalDays: Int? = null,
    val slaughterWithdrawalDays: Int? = null,
    val notes: String? = null,
    val firestoreId: String? = null,
    val farmId: String? = null,
    val batchId: String? = null
) {
    /** Date jusqu'à laquelle les œufs ne doivent pas être commercialisés, si applicable. */
    val eggWithdrawalUntil: Long?
        get() {
            val end = endDate ?: return null
            val days = eggWithdrawalDays ?: return null
            return end + days * 86_400_000L
        }

    /** Date jusqu'à laquelle l'abattage doit être évité, si applicable. */
    val slaughterWithdrawalUntil: Long?
        get() {
            val end = endDate ?: return null
            val days = slaughterWithdrawalDays ?: return null
            return end + days * 86_400_000L
        }
}
