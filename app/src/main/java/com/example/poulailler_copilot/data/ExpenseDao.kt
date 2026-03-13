package com.example.poulailler_copilot.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAll(): List<Expense>

    @Query("SELECT SUM(amount) FROM expenses")
    suspend fun getTotalExpenses(): Double?
}
