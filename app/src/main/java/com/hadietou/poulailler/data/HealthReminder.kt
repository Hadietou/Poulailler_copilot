package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_reminders")
data class HealthReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "VACCIN", "VITAMINE", "DEPARASITAGE"
    val title: String,
    val description: String? = null,
    val dueDate: Long,
    val isDone: Boolean = false,
    val batchId: String? = null,
    val recurring: Boolean = false,
    val frequencyMonths: Int? = null,
    val firestoreId: String? = null
)
