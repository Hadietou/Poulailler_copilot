package com.hadietou.poulailler.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hadietou.poulailler.data.*
import com.hadietou.poulailler.repository.FirebaseRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseRepo = FirebaseRepository()

    val userName = MutableLiveData<String>("")
    val farmInfo = MutableLiveData<FarmInfo?>()
    val todayEggs = MutableLiveData<Int>(0)
    val lastCollectedCount = MutableLiveData<Int>(0)
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

    // New Monthly KPIs
    val monthlyProduction = MutableLiveData<Double>(0.0)
    val monthlySalesTablettes = MutableLiveData<Int>(0)
    val monthlyLayingRate = MutableLiveData<Double>(0.0)
    
    val prodTrend = MutableLiveData<Int>(0)
    val salesTrend = MutableLiveData<Int>(0)
    val rateTrend = MutableLiveData<Int>(0)
    
    // Tech KPIs
    val cumulativeMortalityRate = MutableLiveData<Double>(0.0)
    val feedConversionRatio = MutableLiveData<Double>(0.0)
    val layingGapVsStandard = MutableLiveData<Double>(0.0)

    // Health KPIs
    val survivalRate = MutableLiveData<Double>(100.0)
    val healthExpenses = MutableLiveData<Double>(0.0)
    val nextVaccine = MutableLiveData<VaccineEntry?>()
    val monthlyMortalityCount = MutableLiveData<Int>(0)
    
    // Feed KPIs
    val totalFeedPurchasedKg = MutableLiveData<Double>(0.0)
    val feedAutonomyDays = MutableLiveData<Int>(0)
    val currentStockKg = MutableLiveData<Double>(0.0)
    val dailyConsumptionTotalKg = MutableLiveData<Double>(0.0)
    val dailyConsumptionPerHenG = MutableLiveData<Double>(0.0)
    val totalFeedConsumedKg = MutableLiveData<Double>(0.0)
    val totalFeedCost = MutableLiveData<Double>(0.0)
    
    val expensesByCategory = MutableLiveData<List<CategoryExpense>>(emptyList())
    val allEntries = MutableLiveData<List<EggEntry>>(emptyList())
    val allMortalities = MutableLiveData<List<Mortality>>(emptyList())
    val allExpenses = MutableLiveData<List<Expense>>(emptyList())
    val allSales = MutableLiveData<List<EggSale>>(emptyList())
    val allVaccines = MutableLiveData<List<VaccineEntry>>(emptyList())

    val allBatches = MutableLiveData<List<Batch>>(emptyList())
    val selectedBatch = MutableLiveData<Batch?>()

    val effectiveHensCount = MutableLiveData<Int>(0)
    val totalMortalityCount = MutableLiveData<Int>(0)

    val isAccessBlocked = MutableLiveData<Boolean>(false)

    fun checkAccessStatus() {
        viewModelScope.launch {
            try {
                val blocked = firebaseRepo.isFarmAccessBlocked()
                isAccessBlocked.postValue(blocked)
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error checking access status", e)
            }
        }
    }

    fun loadData() {
        checkAccessStatus()
        viewModelScope.launch {
            try {
                val user = firebaseRepo.getCurrentUserProfile()
                if (user != null) {
                    val nameToShow = if (user.username.isNullOrEmpty() || user.username == "Utilisateur") user.role else user.username
                    userName.postValue(nameToShow)
                }
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
                firebaseRepo.getBatchesFlow().collectLatest { batches ->
                    allBatches.value = batches
                    if (selectedBatch.value == null && batches.isNotEmpty()) {
                        selectedBatch.value = batches.firstOrNull { it.status == "ACTIVE" } ?: batches.first()
                    }
                    refreshAllStats()
                }
            } catch (e: Exception) { Log.e("DashboardVM", "Error in batchesFlow", e) }
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
                    refreshAllStats()
                }
            } catch (e: Exception) { Log.e("DashboardVM", "Error in mortalityFlow", e) }
        }
        viewModelScope.launch {
            try {
                firebaseRepo.getSalesFlow().collectLatest { list ->
                    allSales.value = list
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
        viewModelScope.launch {
            try {
                firebaseRepo.getVaccinesFlow().collectLatest { list ->
                    allVaccines.value = list
                    refreshAllStats()
                }
            } catch (e: Exception) { Log.e("DashboardVM", "Error in vaccinesFlow", e) }
        }
    }

    fun selectBatch(batch: Batch) {
        selectedBatch.value = batch
        refreshAllStats()
    }

    fun refreshAllStats() {
        val batch = selectedBatch.value ?: return
        val batchId = batch.firestoreId ?: return
        val isChair = batch.typeLot == "CHAIR"

        val entries = allEntries.value?.filter { it.batchId == batchId } ?: emptyList()
        val mortalities = allMortalities.value?.filter { it.batchId == batchId } ?: emptyList()
        val sales = allSales.value?.filter { it.batchId == batchId } ?: emptyList()
        val expenses = allExpenses.value?.filter { it.batchId == batchId } ?: emptyList()
        val vaccines = allVaccines.value?.filter { it.batchId == batchId } ?: emptyList()

        // Find Next Vaccine
        val now = System.currentTimeMillis()
        val next = vaccines.filter { it.date >= now }.minByOrNull { it.date }
        nextVaccine.postValue(next)

        // Financials
        val expTotal = expenses.sumOf { it.amount }
        val salesTotal = sales.sumOf { it.totalPrice }
        totalSales.postValue(salesTotal)
        totalExpenses.postValue(expTotal)
        netProfit.postValue(salesTotal - expTotal)

        val feedPurchased = expenses.filter { it.category == "Aliment" }.sumOf { it.quantityKg ?: 0.0 }
        totalFeedPurchasedKg.postValue(feedPurchased)
        val fCost = expenses.filter { it.category == "Aliment" }.sumOf { it.amount }
        totalFeedCost.postValue(fCost)

        expensesByCategory.postValue(expenses.groupBy { it.category }
            .map { (cat, items) -> CategoryExpense(cat, items.sumOf { it.amount }) })
            
        val hExp = expenses.filter { it.category == "Santé" }.sumOf { it.amount }
        healthExpenses.postValue(hExp)

        // Hens and Mortality
        val totalMortality = mortalities.sumOf { it.count }
        totalMortalityCount.postValue(totalMortality)
        val currentHens = (batch.hensCount - totalMortality).coerceAtLeast(0)
        effectiveHensCount.postValue(currentHens)

        // Time calculations
        val cal = Calendar.getInstance()
        val todayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val currentMonthStart = cal.timeInMillis
        val daysInCurrentMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        cal.add(Calendar.MONTH, -1)
        val lastMonthStart = cal.timeInMillis
        val lastMonthEnd = currentMonthStart - 1
        val daysInLastMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Monthly Mortality
        val monthlyMortality = mortalities.filter { it.date >= currentMonthStart }.sumOf { it.count }
        monthlyMortalityCount.postValue(monthlyMortality)

        // Tech KPI: Cumulative Mortality Rate
        val mortalityRate = if (batch.hensCount > 0) (totalMortality.toDouble() / batch.hensCount) * 100 else 0.0
        cumulativeMortalityRate.postValue(mortalityRate)
        survivalRate.postValue(100.0 - mortalityRate)

        if (isChair) {
            todayEggs.postValue(0); lastCollectedCount.postValue(0); layingRate.postValue(0.0)
            layingTrend.postValue(0.0); totalCollected.postValue(0); totalSold.postValue(0)
            totalBroken.postValue(0); totalRemaining.postValue(0); weeklyProduction.postValue(emptyList())
            monthlyProduction.postValue(0.0); monthlyLayingRate.postValue(0.0)
            prodTrend.postValue(0); rateTrend.postValue(0)
            
            val curMonthSalesQty = sales.filter { it.date >= currentMonthStart }.sumOf { it.quantity }
            val lastMonthSalesQty = sales.filter { it.date in lastMonthStart..lastMonthEnd }.sumOf { it.quantity }
            monthlySalesTablettes.postValue(curMonthSalesQty / 30)
            salesTrend.postValue(if (curMonthSalesQty > lastMonthSalesQty) 1 else if (curMonthSalesQty < lastMonthSalesQty) -1 else 0)
            
            feedConversionRatio.postValue(0.0)
            layingGapVsStandard.postValue(0.0)
        } else {
            val yesterdayStart = todayStart - TimeUnit.DAYS.toMillis(1)
            val eggsToday = entries.filter { it.date >= todayStart }.sumOf { it.eggsCount }
            todayEggs.postValue(eggsToday)
            val eggsYesterday = entries.filter { it.date in yesterdayStart until todayStart }.sumOf { it.eggsCount }
            
            val divisor = if (currentHens > 0) currentHens else 1
            val lastEntry = entries.maxByOrNull { it.date }
            val lastEggsCount = lastEntry?.eggsCount ?: 0
            lastCollectedCount.postValue(lastEggsCount)

            val lastRate = (lastEggsCount.toDouble() / divisor.toDouble()) * 100
            val currentRate = (eggsToday.toDouble() / divisor.toDouble()) * 100
            val yesterdayRate = (eggsYesterday.toDouble() / divisor.toDouble()) * 100
            layingRate.postValue(lastRate)
            layingTrend.postValue(currentRate - yesterdayRate)

            val totalColl = entries.sumOf { it.eggsCount }
            val totalSoldQty = sales.sumOf { it.quantity }
            val totalBrk = entries.sumOf { it.brokenEggsCount }
            totalCollected.postValue(totalColl); totalSold.postValue(totalSoldQty); totalBroken.postValue(totalBrk)
            totalRemaining.postValue(totalColl - totalSoldQty - totalBrk)

            // Monthly Stats
            val curMonthEntries = entries.filter { it.date >= currentMonthStart }
            val curMonthProd = curMonthEntries.sumOf { it.eggsCount }
            val curMonthBroken = curMonthEntries.sumOf { it.brokenEggsCount }
            val curMonthBrokenRate = if (curMonthProd > 0) (curMonthBroken.toDouble() / curMonthProd) * 100 else 0.0
            monthlyProduction.postValue(curMonthBrokenRate)
            
            val lastMonthEntries = entries.filter { it.date in lastMonthStart..lastMonthEnd }
            val lastMonthProd = lastMonthEntries.sumOf { it.eggsCount }
            val lastMonthBroken = lastMonthEntries.sumOf { it.brokenEggsCount }
            val lastMonthBrokenRate = if (lastMonthProd > 0) (lastMonthBroken.toDouble() / lastMonthProd) * 100 else 0.0
            prodTrend.postValue(if (curMonthBrokenRate < lastMonthBrokenRate) 1 else if (curMonthBrokenRate > lastMonthBrokenRate) -1 else 0)

            val curMonthSalesQty = sales.filter { it.date >= currentMonthStart }.sumOf { it.quantity }
            val lastMonthSalesQty = sales.filter { it.date in lastMonthStart..lastMonthEnd }.sumOf { it.quantity }
            monthlySalesTablettes.postValue(curMonthSalesQty / 30)
            salesTrend.postValue(if (curMonthSalesQty > lastMonthSalesQty) 1 else if (curMonthSalesQty < lastMonthSalesQty) -1 else 0)

            val curMonthRate = if (currentHens > 0) (curMonthProd.toDouble() / (currentHens * daysInCurrentMonth)) * 100 else 0.0
            val lastMonthRate = if (currentHens > 0) (lastMonthProd.toDouble() / (currentHens * daysInLastMonth)) * 100 else 0.0
            monthlyLayingRate.postValue(curMonthRate)
            rateTrend.postValue(if (curMonthRate > lastMonthRate) 1 else if (curMonthRate < lastMonthRate) -1 else 0)

            // Tech KPI: IC and Gap vs Standard
            val ageInWeeks = if (batch.chickBirthDate > 0) ((System.currentTimeMillis() - batch.chickBirthDate) / (TimeUnit.DAYS.toMillis(1) * 7)).toInt() else 0
            val stdRate = getStandardLayingRate(ageInWeeks)
            layingGapVsStandard.postValue(lastRate - stdRate)

            val history = mutableListOf<Pair<Long, Int>>()
            for (i in 14 downTo 0) {
                val start = todayStart - TimeUnit.DAYS.toMillis(i.toLong())
                val end = start + TimeUnit.DAYS.toMillis(1) - 1
                history.add(start to entries.filter { it.date in start..end }.sumOf { it.eggsCount })
            }
            weeklyProduction.postValue(history)
        }

        calculateFeedStats(batch, mortalities, feedPurchased)
    }

    private fun getStandardLayingRate(ageWeeks: Int): Double {
        return when {
            ageWeeks < 18 -> 0.0
            ageWeeks < 20 -> 10.0
            ageWeeks < 22 -> 50.0
            ageWeeks < 25 -> 85.0
            ageWeeks < 40 -> 92.0
            ageWeeks < 60 -> 85.0
            ageWeeks < 80 -> 75.0
            else -> 60.0
        }
    }

    private fun calculateFeedStats(batch: Batch, mortalities: List<Mortality>, totalPurchased: Double) {
        val initialHens = batch.hensCount
        val startDate = batch.arrivalDate
        val isChair = batch.typeLot == "CHAIR"
        if (startDate <= 0 || initialHens <= 0) {
            currentStockKg.postValue(totalPurchased); feedAutonomyDays.postValue(0); dailyConsumptionTotalKg.postValue(0.0); return
        }

        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        var totalConsumed = 0.0
        var currentDay = startDate
        val dayMillis = TimeUnit.DAYS.toMillis(1)

        while (currentDay < today) {
            val mortalityUntilThen = mortalities.filter { it.date <= currentDay }.sumOf { it.count }
            val hensThatDay = (initialHens - mortalityUntilThen).coerceAtLeast(0)
            val ageInWeeks = ((currentDay - batch.chickBirthDate) / (dayMillis * 7)).toInt()
            val dailyFeed = if (isChair) {
                when { ageInWeeks < 2 -> 0.040; ageInWeeks < 4 -> 0.100; ageInWeeks < 6 -> 0.160; else -> 0.200 }
            } else {
                when { ageInWeeks < 4 -> 0.040; ageInWeeks < 8 -> 0.070; ageInWeeks < 17 -> 0.090; else -> 0.120 }
            }
            totalConsumed += hensThatDay * dailyFeed
            currentDay += dayMillis
        }

        val stockRestant = (totalPurchased - totalConsumed).coerceAtLeast(0.0)
        currentStockKg.postValue(stockRestant)
        totalFeedConsumedKg.postValue(totalConsumed)

        if (!isChair) {
            val totalEggs = allEntries.value?.filter { it.batchId == batch.firestoreId }?.sumOf { it.eggsCount } ?: 0
            if (totalEggs > 0) {
                val tablettes = totalEggs.toDouble() / 30.0
                feedConversionRatio.postValue(totalConsumed / tablettes)
            } else {
                feedConversionRatio.postValue(0.0)
            }
        }

        val currentHens = (initialHens - mortalities.sumOf { it.count }).coerceAtLeast(0)
        val currentAgeInWeeks = if (batch.chickBirthDate > 0) ((today - batch.chickBirthDate) / (dayMillis * 7)).toInt() else 20
        val dailyFeedPerHen = if (isChair) {
            when { currentAgeInWeeks < 2 -> 0.040; currentAgeInWeeks < 4 -> 0.100; currentAgeInWeeks < 6 -> 0.160; else -> 0.200 }
        } else {
            when { currentAgeInWeeks < 4 -> 0.040; currentAgeInWeeks < 8 -> 0.070; currentAgeInWeeks < 17 -> 0.090; else -> 0.120 }
        }

        dailyConsumptionTotalKg.postValue(currentHens * dailyFeedPerHen)
        dailyConsumptionPerHenG.postValue(dailyFeedPerHen * 1000.0)

        if (currentHens > 0 && dailyFeedPerHen > 0) {
            feedAutonomyDays.postValue((stockRestant / (currentHens * dailyFeedPerHen)).toInt())
        } else {
            feedAutonomyDays.postValue(0)
        }
    }

    fun getFormattedAge(birthDate: Long): String {
        if (birthDate == 0L) return "-- semaines"
        val totalDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - birthDate)
        val weeks = totalDays / 7
        val days = totalDays % 7
        val weeksStr = if (weeks > 1) "$weeks semaines" else "$weeks semaine"
        val daysStr = if (days > 0) "$days jours" else "$days jour"
        return when {
            weeks > 0 && days > 0 -> "$weeksStr et $daysStr"
            weeks > 0 -> weeksStr
            days > 0 -> daysStr
            else -> "0 jour"
        }
    }
}
