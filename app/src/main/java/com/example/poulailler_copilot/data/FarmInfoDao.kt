package com.example.poulailler_copilot.data

import androidx.room.*

@Dao
interface FarmInfoDao {

    @Query("SELECT * FROM farm_info WHERE id = 1")
    suspend fun getInfo(): FarmInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(info: FarmInfo)
}