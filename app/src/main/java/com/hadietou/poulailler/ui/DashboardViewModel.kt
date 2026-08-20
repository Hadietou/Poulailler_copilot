package com.hadietou.poulailler.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hadietou.poulailler.data.*
import com.hadietou.poulailler.network.RetrofitClient
import com.hadietou.poulailler.repository.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
    val weeklyProduction = MutableLiveData<List<Triple<Long, Int, Double>>>(emptyList())
    
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
    
    val monthlyCollectedCount = MutableLiveData<Int>(0)
    val monthlySoldCount = MutableLiveData<Int>(0)
    val monthlySalesAmount = MutableLiveData<Double>(0.0)
    val monthlyExpensesAmount = MutableLiveData<Double>(0.0)
    val monthlyBrokenCount = MutableLiveData<Int>(0)
    
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
    val activeHealthReminders = MutableLiveData<List<HealthReminder>>(emptyList())
    
    // Feed KPIs
    val totalFeedPurchasedKg = MutableLiveData<Double>(0.0)
    val feedAutonomyDays = MutableLiveData<Int>(0)
    val currentStockKg = MutableLiveData<Double>(0.0)
    val dailyConsumptionTotalKg = MutableLiveData<Double>(0.0)
    val dailyConsumptionPerHenG = MutableLiveData<Double>(0.0)
    val totalFeedConsumedKg = MutableLiveData<Double>(0.0)
    val totalFeedCost = MutableLiveData<Double>(0.0)

    // Stock Alvéoles
    val eggTraysStock = MutableLiveData<Int?>(null)
    
    val expensesByCategory = MutableLiveData<List<CategoryExpense>>(emptyList())
    
    val allEntries = MutableLiveData<List<EggEntry>?>(null)
    val allMortalities = MutableLiveData<List<Mortality>?>(null)
    val allExpenses = MutableLiveData<List<Expense>?>(null)
    val allSales = MutableLiveData<List<EggSale>?>(null)
    val allVaccines = MutableLiveData<List<VaccineEntry>?>(null)
    val allReminders = MutableLiveData<List<HealthReminder>?>(null)

    val allBatches = MutableLiveData<List<Batch>>(emptyList())
    val selectedBatch = MutableLiveData<Batch?>()

    val effectiveHensCount = MutableLiveData<Int>(0)
    val totalMortalityCount = MutableLiveData<Int>(0)

    val isAccessBlocked = MutableLiveData<Boolean>(false)

    // Anti-doublons en mémoire pour la session actuelle
    private var isCheckingHeatAlert = false
    private var isCheckingStockAlert = false
    private var isGeneratingReminders = false

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
        if (!isCheckingHeatAlert) {
            checkHeatAlert()
        }
        
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
                    checkAndGenerateReminders()
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
        viewModelScope.launch {
            try {
                firebaseRepo.getHealthRemindersFlow().collectLatest { list ->
                    allReminders.value = list
                    updateActiveReminders()
                }
            } catch (e: Exception) { Log.e("DashboardVM", "Error in remindersFlow", e) }
        }
    }

    private fun updateActiveReminders() {
        val batchId = selectedBatch.value?.firestoreId ?: return
        val active = allReminders.value?.filter { it.batchId == batchId && !it.isDone }
            ?.distinctBy { it.title } // Anti-doublons visuel simple
            ?: emptyList()
        activeHealthReminders.postValue(active)
    }

    private fun checkHeatAlert() {
        val prefs = getApplication<Application>().getSharedPreferences("HeatAlertPrefs", Context.MODE_PRIVATE)
        val lastCheckDate = prefs.getString("lastCheckDate", "")
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (lastCheckDate == todayDate) return
        if (isCheckingHeatAlert) return

        isCheckingHeatAlert = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val email = firebaseRepo.getResponsibleEmail()
                val info = firebaseRepo.getFarmInfo()
                
                if (email == null || info == null) {
                    isCheckingHeatAlert = false
                    return@launch
                }

                val response = RetrofitClient.weatherApi.getForecast()
                val daily = response.daily
                
                var alertNeeded = false
                var highTempDay = ""
                var highTempValue = 0.0

                // On démarre à i = 1 (demain) et non i = 0 (aujourd'hui) afin d'envoyer
                // l'alerte 24 heures à l'avance et laisser le temps de prendre les précautions,
                // au lieu d'alerter le jour même de la chaleur.
                val heatThreshold = info.heatAlertTempCelsius
                for (i in 1 until daily.time.size) {
                    val temp = daily.maxTemperatures[i]
                    if (temp >= heatThreshold) {
                        alertNeeded = true
                        highTempDay = daily.time[i]
                        highTempValue = temp
                        break
                    }
                }

                if (alertNeeded) {
                    firebaseRepo.sendHeatAlertEmail(email, info.farmName, highTempDay, highTempValue)
                    
                    val batch = selectedBatch.value
                    if (batch != null && batch.firestoreId != null) {
                        addVitaminReminder(
                            batch.firestoreId,
                            "Vitamine C / Électrolytes",
                            "Période de chaleur prévue ($highTempValue°C le $highTempDay). Hydratation et anti-stress recommandés."
                        )
                    }
                }
                
                prefs.edit().putString("lastCheckDate", todayDate).apply()

            } catch (e: Exception) {
                Log.e("DashboardVM", "Error checking heat alert", e)
            } finally {
                isCheckingHeatAlert = false
            }
        }
    }

    private fun addVitaminReminder(batchId: String, title: String, desc: String) {
        viewModelScope.launch {
            val existing = allReminders.value?.find { it.title == title && it.batchId == batchId && !it.isDone }
            if (existing == null) {
                firebaseRepo.addHealthReminder(HealthReminder(
                    type = "VITAMINE",
                    title = title,
                    description = desc,
                    dueDate = System.currentTimeMillis(),
                    batchId = batchId
                ))
            }
        }
    }

    fun markReminderDone(reminder: HealthReminder) {
        viewModelScope.launch {
            try {
                firebaseRepo.updateHealthReminder(reminder.copy(isDone = true))
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error marking reminder done", e)
            }
        }
    }

    private fun checkAndGenerateReminders() {
        if (isGeneratingReminders) return
        val batch = selectedBatch.value ?: return
        val batchId = batch.firestoreId ?: return
        val arrivalDate = batch.arrivalDate
        if (arrivalDate == 0L) return

        isGeneratingReminders = true
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val info = farmInfo.value
                val vaccineInterval = info?.vaccineIntervalMonths ?: FarmInfo.DEFAULT_VACCINE_INTERVAL_MONTHS
                val dewormingInternalInterval = info?.dewormingInternalIntervalMonths ?: FarmInfo.DEFAULT_DEWORMING_INTERNAL_MONTHS
                val dewormingExternalInterval = info?.dewormingExternalIntervalMonths ?: FarmInfo.DEFAULT_DEWORMING_EXTERNAL_MONTHS
                val tasks = listOf(
                    Triple("Newcastle (ND)", "Vaccination de rappel recommandée (tous les $vaccineInterval mois).", vaccineInterval),
                    Triple("Bronchite infectieuse (IB)", "Vaccination de rappel recommandée (tous les $vaccineInterval mois).", vaccineInterval),
                    Triple("Déparasitage interne", "Traitement contre les vers (tous les $dewormingInternalInterval mois). Produits : lévamisole, albendazole, fenbendazole. Donner 2 jours de vitamines après.", dewormingInternalInterval),
                    Triple("Déparasitage externe", "Traitement contre les poux et acariens (tous les $dewormingExternalInterval mois). Nettoyage + insecticide adapté.", dewormingExternalInterval)
                )

                for (task in tasks) {
                    val title = task.first
                    val desc = task.second
                    val interval = task.third
                    
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = arrivalDate
                    while (cal.timeInMillis < now - TimeUnit.DAYS.toMillis(7)) {
                        cal.add(Calendar.MONTH, interval)
                    }
                    
                    var dueDate = cal.timeInMillis
                    val existing = allReminders.value?.find { it.title == title && it.batchId == batchId }
                    
                    if (existing == null) {
                        firebaseRepo.addHealthReminder(HealthReminder(
                            type = if (title.contains("Newcastle") || title.contains("Bronchite")) "VACCIN" else "DEPARASITAGE",
                            title = title,
                            description = desc,
                            dueDate = dueDate,
                            batchId = batchId,
                            recurring = true,
                            frequencyMonths = interval
                        ))
                    } else if (existing.isDone && existing.dueDate < now - TimeUnit.DAYS.toMillis(1)) {
                        // S'il est marqué comme fait mais que la date due est dépassée de plus d'un jour,
                        // on le réinitialise pour la PROCHAINE occurrence.
                        while (dueDate <= existing.dueDate) {
                            cal.add(Calendar.MONTH, interval)
                            dueDate = cal.timeInMillis
                        }
                        firebaseRepo.updateHealthReminder(existing.copy(dueDate = dueDate, isDone = false))
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error generating reminders", e)
            } finally {
                isGeneratingReminders = false
            }
        }
    }

    private fun checkFeedStockAlert(stock: Double, days: Int) {
        val warningDays = farmInfo.value?.feedStockWarningDays ?: FarmInfo.DEFAULT_FEED_STOCK_WARNING_DAYS
        if (days >= warningDays) return

        val prefs = getApplication<Application>().getSharedPreferences("StockAlertPrefs", Context.MODE_PRIVATE)
        val lastCheckDate = prefs.getString("lastCheckDate", "")
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (lastCheckDate == todayDate) return
        if (isCheckingStockAlert) return

        isCheckingStockAlert = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val email = firebaseRepo.getResponsibleEmail()
                val info = firebaseRepo.getFarmInfo()
                
                if (email == null || info == null) {
                    isCheckingStockAlert = false
                    return@launch
                }

                firebaseRepo.sendFeedStockAlertEmail(email, info.farmName, stock, days)
                prefs.edit().putString("lastCheckDate", todayDate).apply()
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error sending stock alert", e)
            } finally {
                isCheckingStockAlert = false
            }
        }
    }

    fun selectBatch(batch: Batch) {
        selectedBatch.value = batch
        refreshAllStats()
        updateActiveReminders()
        checkAndGenerateReminders()
    }

    fun refreshAllStats() {
        val batch = selectedBatch.value ?: return
        val batchId = batch.firestoreId ?: return
        val isChair = batch.typeLot == "CHAIR"

        // Protection : ne rien calculer tant que les données ne sont pas arrivées de Firebase
        val allE = allEntries.value ?: return
        val allExp = allExpenses.value ?: return
        val allMort = allMortalities.value ?: return
        val allS = allSales.value ?: return
        val allV = allVaccines.value ?: return

        val entries = allE.filter { it.batchId == batchId }
        val mortalities = allMort.filter { it.batchId == batchId }
        val sales = allS.filter { it.batchId == batchId }
        val expenses = allExp.filter { it.batchId == batchId }
        val vaccines = allV.filter { it.batchId == batchId }

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
            
        val hExp = expenses.filter { it.category == "Santé" || it.category == "Cheptel & Santé" }.sumOf { it.amount }
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

        // Monthly Expenses
        val curMonthExpenses = expenses.filter { it.date >= currentMonthStart }
        monthlyExpensesAmount.postValue(curMonthExpenses.sumOf { it.amount })

        if (isChair) {
            todayEggs.postValue(0); lastCollectedCount.postValue(0); layingRate.postValue(0.0)
            layingTrend.postValue(0.0); totalCollected.postValue(0); totalSold.postValue(0)
            totalBroken.postValue(0); totalRemaining.postValue(0); weeklyProduction.postValue(emptyList())
            monthlyProduction.postValue(0.0); monthlyLayingRate.postValue(0.0)
            monthlyCollectedCount.postValue(0); monthlySoldCount.postValue(0); monthlySalesAmount.postValue(0.0)
            monthlyBrokenCount.postValue(0)
            prodTrend.postValue(0); rateTrend.postValue(0)
            eggTraysStock.postValue(0)
            
            val curMonthSales = sales.filter { it.date >= currentMonthStart }
            val curMonthSalesQty = curMonthSales.sumOf { it.quantity }
            val lastMonthSalesQty = sales.filter { it.date in lastMonthStart..lastMonthEnd }.sumOf { it.quantity }
            monthlySalesTablettes.postValue(curMonthSalesQty / 30)
            monthlySalesAmount.postValue(curMonthSales.sumOf { it.totalPrice })
            salesTrend.postValue(if (curMonthSalesQty > lastMonthSalesQty) 1 else if (curMonthSalesQty < lastMonthSalesQty) -1 else 0)
            
            feedConversionRatio.postValue(0.0)
            layingGapVsStandard.postValue(0.0)
        } else {
            val yesterdayStart = todayStart - TimeUnit.DAYS.toMillis(1)
            val entriesToday = entries.filter { it.date >= todayStart }
            val eggsTodayCount = entriesToday.sumOf { it.eggsCount }
            val brokenTodayCount = entriesToday.sumOf { it.brokenEggsCount }
            todayEggs.postValue(eggsTodayCount)
            
            val entriesYesterday = entries.filter { it.date in yesterdayStart until todayStart }
            // Note: On considère eggsCount comme la production TOTALE (sains + cassés)
            val eggsYesterdayTotal = entriesYesterday.sumOf { it.eggsCount }
            
            val divisor = if (currentHens > 0) currentHens else 1
            val lastEntry = entries.maxByOrNull { it.date }
            val lastTotalProduced = lastEntry?.eggsCount ?: 0
            lastCollectedCount.postValue((lastEntry?.eggsCount ?: 0) - (lastEntry?.brokenEggsCount ?: 0))

            val lastRate = (lastTotalProduced.toDouble() / divisor.toDouble()) * 100
            val currentTotalProduced = eggsTodayCount
            val currentRate = (currentTotalProduced.toDouble() / divisor.toDouble()) * 100
            val yesterdayRate = (eggsYesterdayTotal.toDouble() / divisor.toDouble()) * 100
            
            layingRate.postValue(lastRate)
            layingTrend.postValue(currentRate - yesterdayRate)
            
            // Trigger Vitamin reminder if drop in production > 5%
            if (currentRate < yesterdayRate - 5.0 && currentRate > 0) {
                addVitaminReminder(batchId, "Calcium + Vitamine D3 (Alerte Ponte)", "Une baisse importante de ponte a été détectée. Apport en Calcium + D3 recommandé.")
            }

            val totalColl = entries.sumOf { it.eggsCount }
            val totalSoldQty = sales.sumOf { it.quantity }
            val totalBrk = entries.sumOf { it.brokenEggsCount }
            totalCollected.postValue(totalColl); totalSold.postValue(totalSoldQty); totalBroken.postValue(totalBrk)
            totalRemaining.postValue(totalColl - totalSoldQty - totalBrk)

            // Monthly Stats
            val curMonthEntries = entries.filter { it.date >= currentMonthStart }
            val curMonthTotalProd = curMonthEntries.sumOf { it.eggsCount }
            val curMonthBroken = curMonthEntries.sumOf { it.brokenEggsCount }
            monthlyBrokenCount.postValue(curMonthBroken)

            val curMonthBrokenRate = if (curMonthTotalProd > 0) (curMonthBroken.toDouble() / curMonthTotalProd) * 100 else 0.0
            monthlyProduction.postValue(curMonthBrokenRate)
            
            monthlyCollectedCount.postValue(curMonthTotalProd - curMonthBroken)
            
            val lastMonthEntries = entries.filter { it.date in lastMonthStart..lastMonthEnd }
            val lastMonthTotalProd = lastMonthEntries.sumOf { it.eggsCount }
            val lastMonthBroken = lastMonthEntries.sumOf { it.brokenEggsCount }
            val lastMonthBrokenRate = if (lastMonthTotalProd > 0) (lastMonthBroken.toDouble() / lastMonthTotalProd) * 100 else 0.0
            prodTrend.postValue(if (curMonthBrokenRate < lastMonthBrokenRate) 1 else if (curMonthBrokenRate > lastMonthBrokenRate) -1 else 0)

            val curMonthSales = sales.filter { it.date >= currentMonthStart }
            val curMonthSalesQty = curMonthSales.sumOf { it.quantity }
            val lastMonthSalesQty = sales.filter { it.date in lastMonthStart..lastMonthEnd }.sumOf { it.quantity }
            monthlySalesTablettes.postValue(curMonthSalesQty / 30)
            monthlySoldCount.postValue(curMonthSalesQty)
            monthlySalesAmount.postValue(curMonthSales.sumOf { it.totalPrice })
            salesTrend.postValue(if (curMonthSalesQty > lastMonthSalesQty) 1 else if (curMonthSalesQty < lastMonthSalesQty) -1 else 0)

            val curMonthRate = if (currentHens > 0) (curMonthTotalProd.toDouble() / (currentHens * daysInCurrentMonth)) * 100 else 0.0
            val lastMonthRate = if (currentHens > 0) (lastMonthTotalProd.toDouble() / (currentHens * daysInLastMonth)) * 100 else 0.0
            monthlyLayingRate.postValue(curMonthRate)
            rateTrend.postValue(if (curMonthRate > lastMonthRate) 1 else if (curMonthRate < lastMonthRate) -1 else 0)

            // Stock Alvéoles GLOBAL (Ferme) : Achats Farm - Collectes Farm
            val totalAlveolesPurchased = allExp.filter { it.category == "Alvéoles à œufs" || (it.category == "Consommables" && it.subCategory == "Alvéoles") }
                .sumOf { it.quantityKg ?: 0.0 }.toInt()
            val totalEggsCollectedFarm = allE.sumOf { it.eggsCount - it.brokenEggsCount }
            val farmAlveolesStock = totalAlveolesPurchased - (totalEggsCollectedFarm / 30)
            eggTraysStock.postValue(farmAlveolesStock)

            // Tech KPI: IC and Gap vs Standard
            val ageInWeeks = if (batch.chickBirthDate > 0) ((System.currentTimeMillis() - batch.chickBirthDate) / (TimeUnit.DAYS.toMillis(1) * 7)).toInt() else 0
            val stdRate = getStandardLayingRate(ageWeeks = ageInWeeks)
            layingGapVsStandard.postValue(lastRate - stdRate)

            val history = mutableListOf<Triple<Long, Int, Double>>()
            for (i in 14 downTo 0) {
                val start = todayStart - TimeUnit.DAYS.toMillis(i.toLong())
                val end = start + TimeUnit.DAYS.toMillis(1) - 1
                val dayEntries = entries.filter { it.date in start..end }
                if (i == 0 && dayEntries.isEmpty()) continue
                val dailyTotal = dayEntries.sumOf { it.eggsCount }
                val mortUntilThen = mortalities.filter { it.date <= start }.sumOf { it.count }
                val hensThen = (batch.hensCount - mortUntilThen).coerceAtLeast(1)
                val rateThen = (dailyTotal.toDouble() / hensThen.toDouble()) * 100.0
                history.add(Triple(start, dailyTotal, rateThen))
            }
            weeklyProduction.postValue(history)
        }

        calculateFeedStats(batch, mortalities, feedPurchased)
        updateActiveReminders()
    }

    private fun getStandardLayingRate(ageWeeks: Int): Double {
        return when {
            ageWeeks < 18 -> 0.0; ageWeeks < 20 -> 10.0; ageWeeks < 22 -> 50.0
            ageWeeks < 25 -> 85.0; ageWeeks < 40 -> 92.0; ageWeeks < 60 -> 85.0
            ageWeeks < 80 -> 75.0; else -> 60.0
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
            val dailyFeed = if (batch.feedRation > 0 && batch.feedRation != 0.120) {
                batch.feedRation
            } else {
                val ageInWeeks = ((currentDay - batch.chickBirthDate) / (dayMillis * 7)).toInt()
                if (isChair) {
                    when { ageInWeeks < 2 -> 0.040; ageInWeeks < 4 -> 0.100; ageInWeeks < 6 -> 0.160; else -> 0.200 }
                } else {
                    when { ageInWeeks < 4 -> 0.040; ageInWeeks < 8 -> 0.070; ageInWeeks < 17 -> 0.090; else -> 0.120 }
                }
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
        val dailyFeedPerHen = if (batch.feedRation > 0 && batch.feedRation != 0.120) {
            batch.feedRation
        } else {
            if (isChair) {
                when { currentAgeInWeeks < 2 -> 0.040; currentAgeInWeeks < 4 -> 0.100; currentAgeInWeeks < 6 -> 0.160; else -> 0.200 }
            } else {
                when { currentAgeInWeeks < 4 -> 0.040; currentAgeInWeeks < 8 -> 0.070; currentAgeInWeeks < 17 -> 0.090; else -> 0.120 }
            }
        }

        dailyConsumptionTotalKg.postValue(currentHens * dailyFeedPerHen)
        dailyConsumptionPerHenG.postValue(dailyFeedPerHen * 1000.0)

        if (currentHens > 0 && dailyFeedPerHen > 0) {
            val days = (stockRestant / (currentHens * dailyFeedPerHen)).toInt()
            feedAutonomyDays.postValue(days)
            if (totalPurchased > 0) checkFeedStockAlert(stockRestant, days)
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
