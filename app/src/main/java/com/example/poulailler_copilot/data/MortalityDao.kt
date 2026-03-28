package com.example.poulailler_copilot.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MortalityDao {
    @Query("SELECT * FROM mortality ORDER BY date DESC")
    fun getAllMortality(): Flow<List<Mortality>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mortality: Mortality)

    @Query("SELECT SUM(count) FROM mortality")
    suspend fun getTotalMortality(): Int?
}
