package com.hadietou.poulailler.repository

import com.hadietou.poulailler.data.EggSale
import com.hadietou.poulailler.data.EggSaleDao
import com.hadietou.poulailler.data.Expense
import com.hadietou.poulailler.data.ExpenseDao

class FinanceRepository(
    private val eggSaleDao: EggSaleDao,
    private val expenseDao: ExpenseDao
) {
    suspend fun addSale(userId: String, date: Long, quantity: Int, pricePerUnit: Double, buyer: String?, phoneNumber: String?) {
        val totalPrice = quantity * pricePerUnit
        eggSaleDao.insert(EggSale(
            userId = userId,
            date = date, 
            quantity = quantity, 
            pricePerUnit = pricePerUnit, 
            totalPrice = totalPrice, 
            buyer = buyer,
            phoneNumber = phoneNumber
        ))
    }

    suspend fun addExpense(date: Long, category: String, amount: Double, description: String?) {
        expenseDao.insert(Expense(date = date, category = category, amount = amount, description = description))
    }

    suspend fun getTotalSales(): Double = eggSaleDao.getTotalSalesRevenue() ?: 0.0
    suspend fun getTotalExpenses(): Double = expenseDao.getTotalExpenses() ?: 0.0
}
