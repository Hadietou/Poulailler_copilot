package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "login_history")
data class LoginEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val username: String = "",
    val timestamp: Long
)
