package com.example.poulailler_copilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "login_history")
data class LoginEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val timestamp: Long
)
