package com.example.poulailler_copilot.ui

import android.app.Application
import android.util.Log
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

    val userName = MutableLiveData<String>("Utilisateur")
    val farmInfo = MutableLiveData<FarmInfo?>()
    val todayEggs = MutableLiveData<Int>(0)
    val totalSales = MutableLiveData<Double>(0.0)
    val totalExpenses = MutableLiveData<Double>(0.0)
    val netProfit = MutableLiveData<Double>(0.0)
    val weeklyProduction = MutableLiveData<List<Pair<Long, Int>>>(emptyList())
    
    val totalCollected = MutableLiveData<Int>(0)
    val totalBroken = MutableLiveData<Int>(0)
    val totalSold = MutableLiveData<Int>(0)
    val totalRemaining = MutableLiveData<Int>(0)
    val layingRate = MutableLiveData<Double>(0.0)
    val layingTrend = MutableLiveData<Double>(0.0)
    
    val totalFeedPurchasedKg = MutableLiveData<Double>(0.0)
    val feedAutonomyDays = MutableLiveData<Int>(0)
    val currentStockKg = MutableLiveData<Double>(0.0)
    
    val expensesByCategory = MutableLiveData<List<CategoryExpense>>(emptyList())
    val allEntries = MutableLiveData<List<EggEntry>>(emptyList())
    val allMortalities = MutableLiveData<List<Mortality>>(emptyList())
    val allExpenses = MutableLiveData<List<Expense>>(emptyList())

    val effectiveHensCount = MutableLiveData<Int>(0)
    val totalMortalityCount = MutableLiveData<Int>(0)

    fun loadData() {
        Log.d("DashboardVM", "Loading dashboard data...")
        
        viewModelScope.launch {
            try {
                val user = firebaseRepo.getCurrentUserProfile()
                if (user != null) userName.postValue(user.username)
            } catch (e: Exception) { Log.e("DashboardVM", "Error loading user profile", e) }
        }

        viewModelScope.launch {
            try {
                firebaseRepo.getFarmInfoFlow().collectLatest { info ->
                    farmInfo.value = info
                    refreshAllStats()
                }
            } catch (e: Exception) { Log.e("DashboardVM", "Error in farmInfoFlow", e) }
        }

        viewModelScope.launch {
            try {
                firebaseRepo.getEggEntriesFlow().collectLatest { entries ->
                    allEntries.value = entries
                    refreshAllStats()
                }
            } catch (e: Exception) { Log.e("DashboardVM", "Error in eggEntriesFlow", e) }
        }

        viewModelScope.launch {
            try {
                firebaseRepo.getMortalityFlow().collectLatest { list ->
                    allMortalities.value = list
                    totalMortalityCount.value = list.sumOf { it.count }
                    refreshAllStats()
                }
            } catch (e: Exception) { Log.e("DashboardVM", "Error in mortalityFlow", e) }
        }

        viewModelScope.launch {
            try {
                firebaseRepo.getSalesFlow().collectLatest { list ->
                    totalSales.value = list.sumOf { it.totalPrice }
                    totalSold.value = list.sumOf { it.quantity }
                    refreshAllStats()
                }
            } catch (e: Exception) { Log.e("DashboardVM", "Error in salesFlow", e) }
        }

        viewModelScope.launch {
            try {
                firebaseRepo.getExpensesFlow().collectLatest { list ->
                    allExpenses.value = list
                    refreshAllStats()
                }
            } catch (e: Exception) { Log.e("DashboardVM", "Error in expensesFlow", e) }
        }
    }

    private fun refreshAllStats() {
        // Run financial calculations even if farmInfo is null
        val expenses = allExpenses.value ?: emptyList()
        val sales = totalSales.value ?: 0.0
        val entries = allEntries.value ?: emptyList()
        val mortalities = allMortalities.value ?: emptyList()

        val expTotal = expenses.sumOf { it.amount }
        totalExpenses.postValue(expTotal)
        netProfit.postValue(sales - expTotal)

        val feedPurchased = expenses.filter { it.category == "Aliment" }.sumOf { it.quantityKg ?: 0.0 }
        totalFeedPurchasedKg.postValue(feedPurchased)

        expensesByCategory.postValue(expenses.groupBy { it.category }
            .map { (cat, items) -> CategoryExpense(cat, items.sumOf { it.amount }) })

        val info = farmInfo.value ?: return

        // Hens and Mortality
        val totalMortality = mortalities.sumOf { it.count }
        val currentHens = (info.hensCount - totalMortality).coerceAtLeast(0)
        effectiveHensCount.postValue(currentHens)

        // Egg production
        val now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val todayStart = now.timeInMillis
        val yesterdayStart = todayStart - TimeUnit.DAYS.toMillis(1)

        val eggsToday = entries.filter { it.date >= todayStart }.sumOf { it.eggsCount }
        todayEggs.postValue(eggsToday)

        val eggsYesterday = entries.filter { it.date in yesterdayStart until todayStart }.sumOf { it.eggsCount }
        
        val divisor = if (currentHens > 0) currentHens else 1
        val currentRate = (eggsToday.toDouble() / divisor.toDouble()) * 100
        val yesterdayRate = (eggsYesterday.toDouble() / divisor.toDouble()) * 100
        
        layingRate.postValue(currentRate)
        layingTrend.postValue(currentRate - yesterdayRate)

        val totalColl = entries.sumOf { it.eggsCount }
        totalCollected.postValue(totalColl)
        totalBroken.postValue(entries.sumOf { it.brokenEggsCount })
        totalRemaining.postValue(totalColl - (totalSold.value ?: 0) - (totalBroken.value ?: 0))

        // Weekly Production
        val last7Days = mutableListOf<Pair<Long, Int>>()
        for (i in 6 downTo 0) {
            val start = todayStart - TimeUnit.DAYS.toMillis(i.toLong())
            val end = start + TimeUnit.DAYS.toMillis(1) - 1
            val prod = entries.filter { it.date in start..end }.sumOf { it.eggsCount }
            last7Days.add(start to prod)
        }
        weeklyProduction.postValue(last7Days)

        // Feed Stats
        calculateFeedStats(info, mortalities, feedPurchased)
    }

    private fun calculateFeedStats(info: FarmInfo, mortalities: List<Mortality>, totalPurchased: Double) {
        val initialHens = info.hensCount
        // Use arrivalDate if available, fallback to setupDate or birthDate
        val startDate = when {
            info.arrivalDate > 0 -> info.arrivalDate
            info.setupDate > 0 -> info.setupDate
            else -> info.chickBirthDate
        }
        
        if (startDate <= 0 || initialHens <= 0) {
            currentStockKg.postValue(totalPurchased)
            feedAutonomyDays.postValue(0)
            return
        }

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var totalConsumed = 0.0
        var currentDay = startDate
        val dayMillis = TimeUnit.DAYS.toMillis(1)

        while (currentDay < today) {
            val mortalityUntilThen = mortalities.filter { it.date <= currentDay }.sumOf { it.count }
            val hensThatDay = (initialHens - mortalityUntilThen).coerceAtLeast(0)
            val ageInWeeks = ((currentDay - info.chickBirthDate) / (dayMillis * 7)).toInt()

            val dailyFeedPerHen = when {
                ageInWeeks < 4 -> 0.040
                ageInWeeks < 8 -> 0.070
                ageInWeeks < 17 -> 0.090
                else -> 0.120
            }
            totalConsumed += hensThatDay * dailyFeedPerHen
            currentDay += dayMillis
        }

        val stockRestant = (totalPurchased - totalConsumed).coerceAtLeast(0.0)
        currentStockKg.postValue(stockRestant)

        val currentHens = (initialHens - mortalities.sumOf { it.count }).coerceAtLeast(0)
        val currentAgeInWeeks = if (info.chickBirthDate > 0) ((today - info.chickBirthDate) / (dayMillis * 7)).toInt() else 20
        val currentDailyFeedPerHen = when {
            currentAgeInWeeks < 4 -> 0.040
            currentAgeInWeeks < 8 -> 0.070
            currentAgeInWeeks < 17 -> 0.090
            else -> 0.120
        }

        if (currentHens > 0 && currentDailyFeedPerHen > 0) {
            feedAutonomyDays.postValue((stockRestant / (currentHens * currentDailyFeedPerHen)).toInt())
        } else {
            feedAutonomyDays.postValue(0)
        }
    }

    fun getFormattedAge(birthDate: Long): String {
        if (birthDate == 0L) return "-- semaines"
        val diff = System.currentTimeMillis() - birthDate
        val totalDays = TimeUnit.MILLISECONDS.toDays(diff)
        val weeks = totalDays / 7
        val days = totalDays % 7
        
        val weeksStr = if (weeks > 1) "$weeks semaines" else "$weeks semaine"
        val daysStr = if (days > 1) "$days jours" else "$days jour"
        
        return when {
            weeks > 0 && days > 0 -> "$weeksStr et $daysStr"
            weeks > 0 -> weeksStr
            days > 0 -> daysStr
            else -> "0 jour"
        }
    }
}
