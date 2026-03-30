package com.example.poulailler_copilot.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccineEntryDao {
    @Insert
    suspend fun insert(entry: VaccineEntry)

    @Update
    suspend fun update(entry: VaccineEntry)

    @Delete
    suspend fun delete(entry: VaccineEntry)

    @Query("SELECT * FROM vaccine_entries ORDER BY date DESC")
    suspend fun getAll(): List<VaccineEntry>

    @Query("SELECT * FROM vaccine_entries ORDER BY date DESC")
    fun getAllFlow(): Flow<List<VaccineEntry>>
}
