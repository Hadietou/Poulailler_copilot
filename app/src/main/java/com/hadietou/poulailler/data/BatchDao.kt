package com.hadietou.poulailler.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchDao {
    @Query("SELECT * FROM batches WHERE farmId = :farmId ORDER BY arrivalDate DESC")
    fun getBatchesForFarm(farmId: String): Flow<List<Batch>>

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun getBatchById(id: Long): Batch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(batch: Batch): Long

    @Update
    suspend fun update(batch: Batch)

    @Delete
    suspend fun delete(batch: Batch)

    @Query("DELETE FROM batches WHERE farmId = :farmId")
    suspend fun deleteBatchesForFarm(farmId: String)
}