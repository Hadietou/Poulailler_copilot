package com.example.poulailler_copilot.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EggEntryDao {
    @Insert
    suspend fun insert(entry: EggEntry)

    @Query("SELECT * FROM egg_entries ORDER BY date DESC")
    suspend fun getAll(): List<EggEntry>

    @Query("SELECT * FROM egg_entries ORDER BY date DESC")
    fun getAllFlow(): Flow<List<EggEntry>>

    @Query("SELECT * FROM egg_entries WHERE userId = :userId ORDER BY date DESC")
    suspend fun getByUser(userId: Long): List<EggEntry>

    @Query("SELECT SUM(eggsCount) FROM egg_entries")
    suspend fun getTotalEggs(): Int?

    @Query("SELECT SUM(eggsCount) FROM egg_entries WHERE date BETWEEN :startTime AND :endTime")
    suspend fun getProductionBetween(startTime: Long, endTime: Long): Int?
}
