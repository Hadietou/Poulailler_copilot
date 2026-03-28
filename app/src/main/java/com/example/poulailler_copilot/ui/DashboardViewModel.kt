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
    val layingRate5d = MutableLiveData<Double>()
    val brokenRate = MutableLiveData<Double>()
    
    val totalFeedKg = MutableLiveData<Double>()
    val feedAutonomyDays = MutableLiveData<Int>()
    val nextVaccine = MutableLiveData<String>()
    
    val expensesByCategory = MutableLiveData<List<CategoryExpense>>()

    val last5DaysEntries = MutableLiveData<List<EggEntry>>()
    
    val effectiveHensCount = MutableLiveData<Int>()
    val totalMortalityCount = MutableLiveData<Int>()
    val cumulativeMortalityRate = MutableLiveData<Double>()

    private val prophylaxisCalendar = listOf(
        0 to "J1: Bronchite Infectieuse (H120)",
        7 to "J7: Newcastle (HB1)",
        14 to "J14: Gumboro (Intermédiaire)",
        21 to "J21: Newcastle (La Sota) + Rappel Gumboro",
        56 to "S8: Typhose (Variole)",
        84 to "S12: Newcastle + BI (Inactivé)"
    )

    init {
        loadData()
    }

    fun loadData() {
        // En temps réel avec Firestore
        viewModelScope.launch {
            firebaseRepo.getFarmInfoFlow().collectLatest { info ->
                farmInfo.value = info
                calculateStats()
            }
        }

        viewModelScope.launch {
            firebaseRepo.getEggEntriesFlow().collectLatest { entries ->
                last5DaysEntries.value = entries.take(5)
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
            firebaseRepo.getSalesFlow().collectLatest { list: List<EggSale> ->
                val sales = list.sumOf { it.totalPrice }
                totalSales.value = sales
                calculateNetProfit()
                totalSold.value = list.sumOf { it.quantity }
                calculateStats()
            }
        }

        viewModelScope.launch {
            firebaseRepo.getExpensesFlow().collectLatest { list ->
                val expenses = list.sumOf { it.amount }
                totalExpenses.value = expenses
                calculateNetProfit()
                
                val feedTotal = list.filter { it.category == "Aliment" }.sumOf { it.quantityKg ?: 0.0 }
                totalFeedKg.value = feedTotal
                calculateStats()
                
                val catMap = list.groupBy { it.category }
                    .map { (cat, items) -> CategoryExpense(cat, items.sumOf { it.amount }) }
                expensesByCategory.value = catMap
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
        val entries = last5DaysEntries.value ?: emptyList()
        val mortality = totalMortalityCount.value ?: 0
        
        val initialHens = info.hensCount
        val currentHens = initialHens - mortality
        effectiveHensCount.value = currentHens

        // Taux de mortalité cumulé
        if (initialHens > 0) {
            cumulativeMortalityRate.value = (mortality.toDouble() / initialHens.toDouble()) * 100
        } else {
            cumulativeMortalityRate.value = 0.0
        }

        // Calcul de l'âge et Prochain Vaccin
        val ageDays = calculateDaysAge(info.chickBirthDate)
        nextVaccine.value = prophylaxisCalendar.firstOrNull { it.first > ageDays }?.second ?: "Cycle terminé"

        // Autonomie Aliment (Hypothèse: 120g/jour/poule)
        val stock = totalFeedKg.value ?: 0.0
        if (currentHens > 0) {
            val dailyCons = currentHens * 0.120 // en kg
            feedAutonomyDays.value = (stock / dailyCons).toInt()
        } else {
            feedAutonomyDays.value = 0
        }

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        val startTime = calendar.timeInMillis

        val eggsToday = entries.filter { it.date >= startTime }.sumOf { it.eggsCount }
        todayEggs.value = eggsToday

        val totalColl = entries.sumOf { it.eggsCount }
        totalCollected.value = totalColl
        
        val totalBrk = entries.sumOf { it.brokenEggsCount }
        totalBroken.value = totalBrk

        // Taux de casse
        if (totalColl > 0) {
            brokenRate.value = (totalBrk.toDouble() / totalColl.toDouble()) * 100
        } else {
            brokenRate.value = 0.0
        }

        val sld = totalSold.value ?: 0
        totalRemaining.value = totalColl - sld - totalBrk

        val divisor = if (currentHens > 0) currentHens else 1
        layingRate.value = (eggsToday.toDouble() / divisor.toDouble()) * 100

        // Weekly production for chart
        val last7Days = mutableListOf<Pair<Long, Int>>()
        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance()
            dayCal.add(Calendar.DAY_OF_YEAR, -i)
            dayCal.set(Calendar.HOUR_OF_DAY, 0)
            val start = dayCal.timeInMillis
            val end = start + (24 * 60 * 60 * 1000) - 1
            val prod = entries.filter { it.date in start..end }.sumOf { it.eggsCount }
            last7Days.add(start to prod)
        }
        weeklyProduction.value = last7Days
    }

    private fun calculateDaysAge(birthDate: Long): Int {
        if (birthDate == 0L) return 0
        val diff = System.currentTimeMillis() - birthDate
        return TimeUnit.MILLISECONDS.toDays(diff).toInt()
    }

    fun calculateWeeksAge(setupDate: Long): Int {
        if (setupDate == 0L) return 0
        val diff = System.currentTimeMillis() - setupDate
        return (TimeUnit.MILLISECONDS.toDays(diff) / 7).toInt()
    }
}
