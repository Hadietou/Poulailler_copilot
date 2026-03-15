package com.example.poulailler_copilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.poulailler_copilot.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val farmDao = db.farmInfoDao()
    private val eggDao = db.eggEntryDao()
    private val saleDao = db.eggSaleDao()
    private val expenseDao = db.expenseDao()
    private val vaccineDao = db.vaccineEntryDao()

    val farmInfo = MutableLiveData<FarmInfo?>()
    val todayEggs = MutableLiveData<Int>()
    val totalSales = MutableLiveData<Double>()
    val totalExpenses = MutableLiveData<Double>()
    val weeklyProduction = MutableLiveData<List<Pair<Long, Int>>>()
    
    val totalCollected = MutableLiveData<Int>()
    val totalBroken = MutableLiveData<Int>()
    val totalSold = MutableLiveData<Int>()
    val totalRemaining = MutableLiveData<Int>()
    val layingRate = MutableLiveData<Double>()
    val layingRate5d = MutableLiveData<Double>()
    
    val totalFeedKg = MutableLiveData<Double>()
    val expensesByCategory = MutableLiveData<List<CategoryExpense>>()

    val last5DaysEntries = MutableLiveData<List<EggEntry>>()
    val lastVaccines = MutableLiveData<List<VaccineEntry>>()

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val info = farmDao.getInfo()
            val hensCount = info?.hensCount ?: 1
            
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            
            val eggsToday = eggDao.getProductionBetween(startTime, System.currentTimeMillis()) ?: 0
            val salesRevenue = saleDao.getTotalSalesRevenue() ?: 0.0
            val expenses = expenseDao.getTotalExpenses() ?: 0.0
            val feedQty = expenseDao.getTotalFeedQuantity() ?: 0.0
            val catExpenses = expenseDao.getExpensesByCategory()
            val vaccines = vaccineDao.getAll().take(5) // On prend les 5 derniers

            val totalColl = eggDao.getTotalEggs() ?: 0
            val totalSld = saleDao.getTotalEggsSold() ?: 0
            
            val allEntries = eggDao.getAll()
            val totalBrk = allEntries.sumOf { it.brokenEggsCount }
            val remaining = totalColl - totalSld - totalBrk

            val rate = (eggsToday.toDouble() / hensCount.toDouble()) * 100

            val calendar5d = Calendar.getInstance()
            calendar5d.add(Calendar.DAY_OF_YEAR, -4)
            calendar5d.set(Calendar.HOUR_OF_DAY, 0)
            val start5d = calendar5d.timeInMillis
            
            val prod5d = eggDao.getProductionBetween(start5d, System.currentTimeMillis()) ?: 0
            val rate5d = (prod5d.toDouble() / (hensCount * 5).toDouble()) * 100

            val last5 = allEntries.take(5)

            val last7Days = mutableListOf<Pair<Long, Int>>()
            for (i in 6 downTo 0) {
                val dayCal = Calendar.getInstance()
                dayCal.add(Calendar.DAY_OF_YEAR, -i)
                dayCal.set(Calendar.HOUR_OF_DAY, 0)
                val start = dayCal.timeInMillis
                val end = start + (24 * 60 * 60 * 1000) - 1
                val prod = eggDao.getProductionBetween(start, end) ?: 0
                last7Days.add(start to prod)
            }

            withContext(Dispatchers.Main) {
                farmInfo.value = info
                todayEggs.value = eggsToday
                totalSales.value = salesRevenue
                totalExpenses.value = expenses
                weeklyProduction.value = last7Days
                
                totalCollected.value = totalColl
                totalBroken.value = totalBrk
                totalSold.value = totalSld
                totalRemaining.value = remaining
                layingRate.value = rate
                layingRate5d.value = rate5d
                last5DaysEntries.value = last5
                
                totalFeedKg.value = feedQty
                expensesByCategory.value = catExpenses
                lastVaccines.value = vaccines
            }
        }
    }

    fun calculateWeeksAge(setupDate: Long): Int {
        val diff = System.currentTimeMillis() - setupDate
        return (TimeUnit.MILLISECONDS.toDays(diff) / 7).toInt()
    }
}
