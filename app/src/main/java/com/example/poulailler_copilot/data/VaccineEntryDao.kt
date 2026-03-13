package com.example.poulailler_copilot.data

import androidx.room.*

@Dao
interface VaccineEntryDao {
    @Insert
    suspend fun insert(entry: VaccineEntry)

    @Query("SELECT * FROM vaccine_entries ORDER BY date DESC")
    suspend fun getAll(): List<VaccineEntry>
}
