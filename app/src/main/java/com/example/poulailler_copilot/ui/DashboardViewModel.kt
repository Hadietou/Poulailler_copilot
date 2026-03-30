package com.example.poulailler_copilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.poulailler_copilot.data.*
import com.example.poulailler_copilot.repository.FirebaseRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseRepo = FirebaseRepository()

    val userName = MutableLiveData<String>()
    val farmInfo = MutableLiveData<FarmInfo?>()
    val todayEggs = MutableLiveData<Int>()
    val totalSales = MutableLiveData<Double>()
    val totalExpenses = MutableLiveData<Double>()
    val netProfit = MutableLiveData<Double>()
    val weeklyProduction = MutableLiveData<List<Pair<Long, Int>>>()
    
    val totalCollected = MutableLiveData<Int>()
    val totalBroken = MutableLiveData<Int>()
    val totalSold = MutableLiveData<Int>()
    val totalRemaining = MutableLiveData<Int>()
    val layingRate = MutableLiveData<Double>()
    val layingTrend = MutableLiveData<Double>()
    
    val totalFeedKg = MutableLiveData<Double>()
    val feedAutonomyDays = MutableLiveData<Int>()
    val nextVaccine = MutableLiveData<String>()
    
    val expensesByCategory = MutableLiveData<List<CategoryExpense>>()
    val allEntries = MutableLiveData<List<EggEntry>>()
    
    val effectiveHensCount = MutableLiveData<Int>()
    val totalMortalityCount = MutableLiveData<Int>()
    val cumulativeMortalityRate = MutableLiveData<Double>()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            val user = firebaseRepo.getCurrentUserProfile()
            userName.value = user?.username ?: "Utilisateur"
        }

        viewModelScope.launch {
            firebaseRepo.getFarmInfoFlow().collectLatest { info ->
                farmInfo.value = info
                calculateStats()
            }
        }

        viewModelScope.launch {
            firebaseRepo.getEggEntriesFlow().collectLatest { entries ->
                allEntries.value = entries
                calculateStats()
            }
        }

        viewModelScope.launch {
            firebaseRepo.getMortalityFlow().collectLatest { list ->
                totalMortalityCount.value = list.sumOf { it.count }
                calculateStats()
            }
        }

        viewModelScope.launch {
            firebaseRepo.getSalesFlow().collectLatest { list ->
                totalSales.value = list.sumOf { it.totalPrice }
                totalSold.value = list.sumOf { it.quantity }
                calculateNetProfit()
                calculateStats()
            }
        }

        viewModelScope.launch {
            firebaseRepo.getExpensesFlow().collectLatest { list ->
                totalExpenses.value = list.sumOf { it.amount }
                totalFeedKg.value = list.filter { it.category == "Aliment" }.sumOf { it.quantityKg ?: 0.0 }
                calculateNetProfit()
                calculateStats()
                
                expensesByCategory.value = list.groupBy { it.category }
                    .map { (cat, items) -> CategoryExpense(cat, items.sumOf { it.amount }) }
            }
        }
    }

    private fun calculateNetProfit() {
        val sales = totalSales.value ?: 0.0
        val expenses = totalExpenses.value ?: 0.0
        netProfit.value = sales - expenses
    }

    private fun calculateStats() {
        val info = farmInfo.value ?: return
        val entries = allEntries.value ?: emptyList()
        val mortality = totalMortalityCount.value ?: 0
        
        val initialHens = info.hensCount
        val currentHens = initialHens - mortality
        effectiveHensCount.value = currentHens

        if (initialHens > 0) {
            cumulativeMortalityRate.value = (mortality.toDouble() / initialHens.toDouble()) * 100
        }

        val now = Calendar.getInstance()
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)
        val todayStart = now.timeInMillis
        val yesterdayStart = todayStart - TimeUnit.DAYS.toMillis(1)

        val eggsToday = entries.filter { it.date >= todayStart }.sumOf { it.eggsCount }
        todayEggs.value = eggsToday

        val eggsYesterday = entries.filter { it.date in yesterdayStart until todayStart }.sumOf { it.eggsCount }
        
        val divisor = if (currentHens > 0) currentHens else 1
        val currentRate = (eggsToday.toDouble() / divisor.toDouble()) * 100
        val yesterdayRate = (eggsYesterday.toDouble() / divisor.toDouble()) * 100
        
        layingRate.value = currentRate
        layingTrend.value = currentRate - yesterdayRate

        val totalColl = entries.sumOf { it.eggsCount }
        totalCollected.value = totalColl
        totalBroken.value = entries.sumOf { it.brokenEggsCount }
        totalRemaining.value = totalColl - (totalSold.value ?: 0) - (totalBroken.value ?: 0)

        // Weekly production
        val last7Days = mutableListOf<Pair<Long, Int>>()
        for (i in 6 downTo 0) {
            val start = todayStart - TimeUnit.DAYS.toMillis(i.toLong())
            val end = start + TimeUnit.DAYS.toMillis(1) - 1
            val prod = entries.filter { it.date in start..end }.sumOf { it.eggsCount }
            last7Days.add(start to prod)
        }
        weeklyProduction.value = last7Days

        // Autonomy
        val stock = totalFeedKg.value ?: 0.0
        if (currentHens > 0) {
            feedAutonomyDays.value = (stock / (currentHens * 0.120)).toInt()
        }
    }

    fun calculateWeeksAge(setupDate: Long): Int {
        if (setupDate == 0L) return 0
        val diff = System.currentTimeMillis() - setupDate
        return (TimeUnit.MILLISECONDS.toDays(diff) / 7).toInt()
    }
}
