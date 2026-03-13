package com.example.poulailler_copilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vaccine_entries")
data class VaccineEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: Long,
    val remarks: String? = null
)