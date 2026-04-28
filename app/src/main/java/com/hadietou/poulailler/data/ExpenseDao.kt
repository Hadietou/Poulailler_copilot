package com.hadietou.poulailler.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAll(): List<Expense>

    @Query("SELECT * FROM expenses ORDER BY date DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedExpenses(limit: Int, offset: Int): List<Expense>

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllFlow(): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses")
    suspend fun getTotalExpenses(): Double?

    @Query("SELECT category, SUM(amount) as totalAmount FROM expenses GROUP BY category")
    suspend fun getExpensesByCategory(): List<CategoryExpense>

    @Query("SELECT SUM(quantityKg) FROM expenses WHERE category = 'Aliment'")
    suspend fun getTotalFeedQuantity(): Double?
}

data class CategoryExpense(
    val category: String,
    val totalAmount: Double
)
