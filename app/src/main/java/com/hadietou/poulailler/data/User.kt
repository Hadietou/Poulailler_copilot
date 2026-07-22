package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = "", // Firebase UID
    val username: String,
    val email: String = "",
    val password: String,
    val role: String,
    val active: Boolean = true,
    val farmId: String? = null,
    val isPending: Boolean = false,
    val createdAt: Long = 0L
)
