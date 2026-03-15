package com.example.poulailler_copilot.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LoginDao {
    @Insert
    suspend fun insert(entry: LoginEntry)

    @Query("SELECT * FROM login_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<LoginEntry>
}
