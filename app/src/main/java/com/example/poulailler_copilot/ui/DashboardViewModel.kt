package com.example.poulailler_copilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.FarmInfo
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

    val farmInfo = MutableLiveData<FarmInfo?>()
    val todayEggs = MutableLiveData<Int>()
    val totalSales = MutableLiveData<Double>()
    val totalExpenses = MutableLiveData<Double>()
    val weeklyProduction = MutableLiveData<List<Pair<Long, Int>>>()

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            val info = farmDao.getInfo()
            
            // Production du jour
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            
            val eggsToday = eggDao.getProductionBetween(startTime, System.currentTimeMillis()) ?: 0
            val sales = saleDao.getTotalSalesRevenue() ?: 0.0
            val expenses = expenseDao.getTotalExpenses() ?: 0.0

            // Production hebdo (simulation simplifiée pour le moment)
            val last7Days = mutableListOf<Pair<Long, Int>>()
            for (i in 6 downTo 0) {
                val dayCal = Calendar.getInstance()
                dayCal.add(Calendar.DAY_OF_YEAR, -i)
                dayCal.set(Calendar.HOUR_OF_DAY, 0)
                val start = dayCal.timeInMillis
                dayCal.set(Calendar.HOUR_OF_DAY, 23)
                val end = dayCal.timeInMillis
                val prod = eggDao.getProductionBetween(start, end) ?: 0
                last7Days.add(start to prod)
            }

            withContext(Dispatchers.Main) {
                farmInfo.value = info
                todayEggs.value = eggsToday
                totalSales.value = sales
                totalExpenses.value = expenses
                weeklyProduction.value = last7Days
            }
        }
    }

    fun calculateWeeksAge(setupDate: Long): Int {
        val diff = System.currentTimeMillis() - setupDate
        return (TimeUnit.MILLISECONDS.toDays(diff) / 7).toInt()
    }
}
