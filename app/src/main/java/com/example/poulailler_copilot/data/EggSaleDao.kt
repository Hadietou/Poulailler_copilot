package com.example.poulailler_copilot.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EggSaleDao {
    @Insert
    suspend fun insert(sale: EggSale)

    @Query("SELECT * FROM egg_sales ORDER BY date DESC")
    fun getAll(): Flow<List<EggSale>>

    @Query("SELECT * FROM egg_sales WHERE userId = :userId ORDER BY date DESC")
    fun getByUser(userId: Long): Flow<List<EggSale>>

    @Query("SELECT SUM(totalPrice) FROM egg_sales")
    suspend fun getTotalSalesRevenue(): Double?

    @Query("SELECT SUM(quantity) FROM egg_sales")
    suspend fun getTotalEggsSold(): Int?
}
