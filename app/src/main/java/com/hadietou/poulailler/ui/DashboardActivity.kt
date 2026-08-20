package com.hadietou.poulailler.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hadietou.poulailler.R
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.databinding.ActivityDashboardBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.hadietou.poulailler.data.CategoryExpense
import com.hadietou.poulailler.data.FarmInfo
import com.hadietou.poulailler.util.SunUtils
import com.hadietou.poulailler.util.ReportUtils
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.*
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import android.content.res.ColorStateList
import com.hadietou.poulailler.util.NavMenuStyler
import com.hadietou.poulailler.util.NetworkStatusMonitor

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private val firebaseRepo = FirebaseRepository()
    private var isBlocked = false
    private lateinit var reminderAdapter: HealthReminderAdapter
    
    private var isEggMenuExpanded = false
    private var isHealthMenuExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityDashboardBinding.inflate(layoutInflater)
            setContentView(binding.root)
            NetworkStatusMonitor.observe(this, binding.root)

            userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId == null) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }

            setupNavigation()
            setupHealthReminders()
            setupClickListeners()
            setupDashboardCardListeners()
            setupBatchSpinner()
            observeViewModel()
            updateLightingIndicator()
            
            updateUIBasedOnRole()
            
            lifecycleScope.launch {
                try {
                    val profile = firebaseRepo.getUserProfile(userId!!)
                    if (profile != null) {
                        userRole = profile.role
                        withContext(Dispatchers.Main) {
                            updateUIBasedOnRole()
                            updateNavHeader(profile.username, profile.role)
                            viewModel.loadData()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Dashboard", "Error loading profile", e)
                }
            }
        } catch (e: Exception) {
            Log.e("Dashboard", "Crash in onCreate", e)
        }
    }

    private fun setupHealthReminders() {
        reminderAdapter = HealthReminderAdapter { reminder ->
            viewModel.markReminderDone(reminder)
        }
        binding.rvHealthReminders.layoutManager = LinearLayoutManager(this)
        binding.rvHealthReminders.adapter = reminderAdapter

        binding.btnToggleReminders.setOnClickListener {
            val isVisible = binding.rvHealthReminders.visibility == View.VISIBLE
            binding.rvHealthReminders.visibility = if (isVisible) View.GONE else View.VISIBLE
            binding.btnToggleReminders.text = if (isVisible) getString(R.string.details_button) else getString(R.string.collapse_button)
        }
    }

    private fun updateLightingIndicator() {
        val lightingHours = viewModel.farmInfo.value?.lightingHoursAfterSunrise ?: FarmInfo.DEFAULT_LIGHTING_HOURS
        val extinctionTime = SunUtils.getExtinctionTime(lightingHours)
        binding.tvLightingTime.text = getString(R.string.lighting_extinguish_at, extinctionTime)
    }

    private fun setupNavigation() {
        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener(this)

        refreshDrawerMenuStyle()
    }

    /**
     * NavigationView n'affiche pas toujours de manière fiable un item dont on vient
     * de changer la visibilité de groupe (menu.setGroupVisible) une fois déjà rendu à l'écran :
     * on force donc une reconstruction complète du menu avant de réappliquer les états courants.
     */
    private fun rebuildDrawerMenu() {
        binding.navigationView.menu.clear()
        binding.navigationView.inflateMenu(R.menu.drawer_menu)
        updateUIBasedOnRole()
        updateUIBasedOnBatchType()
        refreshDrawerMenuStyle()
    }

    private fun refreshDrawerMenuStyle() {
        NavMenuStyler.style(
            binding.navigationView,
            this,
            defaultIconTintRes = R.color.text_secondary,
            parents = listOf(
                R.id.nav_egg_management to isEggMenuExpanded,
                R.id.nav_health_management to isHealthMenuExpanded
            ),
            children = listOf(R.id.nav_collect, R.id.nav_sales, R.id.nav_vaccines, R.id.nav_mortality)
        )
    }

    private fun setupClickListeners() {
        binding.ivSelectBatch.setOnClickListener {
            binding.spinnerBatches.performClick()
        }
        
        binding.toolbar.findViewById<View>(R.id.ivHeaderHome)?.setOnClickListener {
            binding.nestedScrollView.smoothScrollTo(0, 0)
        }
        
        binding.toolbar.findViewById<View>(R.id.ivHeaderProfile)?.setOnClickListener {
            startActivity(Intent(this, FarmInfoActivity::class.java))
        }

        binding.toolbar.findViewById<View>(R.id.ivHeaderReport)?.setOnClickListener {
            val prodChart = if (binding.productionChart.visibility == View.VISIBLE) binding.productionChart.chartBitmap else null
            val expChart = if (binding.expensesBarChart.visibility == View.VISIBLE) binding.expensesBarChart.chartBitmap else null
            ReportUtils.generateAndShareReport(this, viewModel, prodChart, expChart)
        }
    }

    private fun setupDashboardCardListeners() {
        binding.cardLightingAlert.setOnClickListener {
            val intent = Intent(this, VaccineActivity::class.java)
            intent.putExtra("scrollToLighting", true)
            intent.putExtra("role", userRole)
            intent.putExtra("userIdString", userId)
            intent.putExtra("selectedBatchId", viewModel.selectedBatch.value?.firestoreId)
            startActivity(intent)
        }
        binding.cardLayingRate.setOnClickListener { navigateTo(AgentActivity::class.java) }
        
        binding.cardStockCircle.setOnClickListener { navigateTo(SalesActivity::class.java) }
        binding.cardFeedStockCircle.setOnClickListener { navigateTo(ExpensesActivity::class.java) }
        binding.cardEggTraysStock.setOnClickListener { navigateTo(ExpensesActivity::class.java) }
        
        binding.layoutMonthlyCollected.setOnClickListener { navigateToStats("COLLECTION") }
        binding.layoutMonthlySold.setOnClickListener { navigateToStats("SALES") }
        binding.layoutMonthlyRevenue.setOnClickListener { navigateToStats("REVENUE") }
        binding.layoutMonthlyBroken.setOnClickListener { navigateToStats("BROKEN") }
        binding.layoutMonthlyMortality.setOnClickListener { navigateToStats("MORTALITY") }
        binding.layoutMonthlyExpenses.setOnClickListener { navigateToStats("EXPENSES") }

        binding.cardFeed.setOnClickListener { navigateTo(ExpensesActivity::class.java) }
        binding.cardNetProfit.setOnClickListener { navigateTo(SalesActivity::class.java) }
        
        binding.cardTechHealth.setOnClickListener { navigateTo(VaccineActivity::class.java) }
        binding.cardSanitaryAlert.setOnClickListener { navigateTo(VaccineActivity::class.java) }
    }

    private fun navigateToStats(type: String) {
        val intent = Intent(this, MonthlyStatsActivity::class.java)
        intent.putExtra("statsType", type)
        intent.putExtra("selectedBatchId", viewModel.selectedBatch.value?.firestoreId)
        startActivity(intent)
    }

    private fun <T> navigateTo(cls: Class<T>) {
        val intent = Intent(this, cls)
        intent.putExtra("role", userRole)
        intent.putExtra("userIdString", userId)
        intent.putExtra("selectedBatchId", viewModel.selectedBatch.value?.firestoreId)
        startActivity(intent)
    }

    private fun setupBatchSpinner() {
        binding.spinnerBatches.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val batch = viewModel.allBatches.value?.get(position)
                batch?.let { viewModel.selectBatch(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateNavHeader(username: String, role: String) {
        val headerView = binding.navigationView.getHeaderView(0) ?: return
        val nameToShow = if (username == "Utilisateur" || username.isEmpty()) role else username
        headerView.findViewById<TextView>(R.id.tvUsername)?.text = nameToShow.uppercase()
        headerView.findViewById<TextView>(R.id.tvUserRole)?.text = role
        headerView.findViewById<TextView>(R.id.tvAppVersion)?.text = "v${BuildConfig.VERSION_NAME}"
    }

    private fun updateUIBasedOnRole() {
        val isResp = userRole == "RESPONSABLE"
        val menu = binding.navigationView.menu
        menu.findItem(R.id.nav_users)?.isVisible = isResp
        menu.findItem(R.id.nav_expenses)?.isVisible = isResp
        menu.findItem(R.id.nav_batches)?.isVisible = isResp
        
        binding.titleFinance.visibility = if (isResp) View.VISIBLE else View.GONE
        binding.cardNetProfit.visibility = if (isResp) View.VISIBLE else View.GONE
        
        val headerView = binding.navigationView.getHeaderView(0)
        headerView?.findViewById<View>(R.id.tvFarmNameNav)?.visibility = View.GONE

        binding.toolbar.findViewById<View>(R.id.ivHeaderReport)?.visibility = if (isResp) View.VISIBLE else View.GONE
        
        updateUIBasedOnBatchType()
    }

    private fun updateUIBasedOnBatchType() {
        val batch = viewModel.selectedBatch.value
        val isChair = batch?.typeLot == "CHAIR"
        
        val eggVisibility = if (isChair) View.GONE else View.VISIBLE
        binding.cardLayingRate.visibility = eggVisibility
        binding.cardLightingAlert.visibility = eggVisibility
        binding.cardStockCircle.visibility = eggVisibility
        binding.cardEggTraysStock.visibility = eggVisibility
        
        binding.cardFeedStockCircle.visibility = View.VISIBLE
        
        val menu = binding.navigationView.menu
        
        menu.findItem(R.id.nav_egg_management)?.isVisible = !isChair
        menu.setGroupVisible(R.id.group_egg_submenu, !isChair && isEggMenuExpanded)
        
        menu.findItem(R.id.nav_health_management)?.isVisible = true
        menu.setGroupVisible(R.id.group_health_submenu, isHealthMenuExpanded)
    }

    private fun observeViewModel() {
        viewModel.isAccessBlocked.observe(this) { blocked ->
            isBlocked = blocked
            if (blocked) {
                showBlockingDialog()
            }
        }
        
        viewModel.farmInfo.observe(this) { info ->
            if (info != null) {
                binding.tvWelcome.text = info.farmName.uppercase()
            }
            updateLightingIndicator()
        }

        viewModel.allBatches.observe(this) { batches ->
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, batches.map { it.name })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerBatches.adapter = adapter

            val selected = viewModel.selectedBatch.value
            if (selected != null) {
                val index = batches.indexOfFirst { it.firestoreId == selected.firestoreId }
                if (index >= 0) binding.spinnerBatches.setSelection(index)
            }
        }

        viewModel.selectedBatch.observe(this) { batch ->
            if (batch != null) {
                binding.tvDashboardBatchName.text = batch.name
            } else {
                binding.tvDashboardBatchName.text = ""
            }
            updateHensAgeHeader()
            updateUIBasedOnBatchType()
        }

        viewModel.effectiveHensCount.observe(this) {
            updateHensAgeHeader()
        }
        
        viewModel.layingRate.observe(this) { 
            val r = it.toInt().coerceIn(0, 100)
            binding.progressLayingRate.setProgress(r, true)
            binding.tvLayingRateValue.text = "$r%"
        }

        viewModel.layingTrend.observe(this) { trend ->
            val trendInt = trend.toInt()
            binding.tvLayingTrend.text = if (trendInt >= 0) {
                getString(R.string.laying_trend_positive, trendInt)
            } else {
                getString(R.string.laying_trend_negative, trendInt)
            }

            when {
                trendInt > 0 -> {
                    binding.tvLayingTrend.setTextColor(getColor(R.color.white))
                    binding.tvLayingTrend.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.emerald_soft))
                }
                trendInt < 0 -> {
                    binding.tvLayingTrend.setTextColor(getColor(R.color.white))
                    binding.tvLayingTrend.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.error))
                }
                else -> {
                    binding.tvLayingTrend.setTextColor(getColor(R.color.text_secondary))
                    binding.tvLayingTrend.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.off_white))
                }
            }
        }

        viewModel.monthlyBrokenCount.observe(this) { count ->
            binding.tvMonthlyBroken.text = count.toString()
        }
        viewModel.monthlySalesTablettes.observe(this) { tablettes ->
            binding.tvMonthlySoldTablettes.text = getString(R.string.tablettes_count, tablettes)
        }

        viewModel.monthlyCollectedCount.observe(this) { count ->
            val tablettes = count / 30
            binding.tvMonthlyCollectedTablettes.text = getString(R.string.tablettes_count, tablettes)
        }
        viewModel.monthlySalesAmount.observe(this) { amount ->
            binding.tvMonthlySalesAmount.text = getString(R.string.k_format, amount / 1000.0)
        }
        viewModel.monthlyExpensesAmount.observe(this) { amount ->
            binding.tvMonthlyExpensesAmount.text = getString(R.string.k_format, amount / 1000.0)
        }
        
        viewModel.feedConversionRatio.observe(this) { ic ->
            binding.tvICValue.text = String.format(Locale.getDefault(), "%.2f", ic)
        }
        viewModel.layingGapVsStandard.observe(this) { gap ->
            val prefix = if (gap > 0) "+" else ""
            binding.tvGapStdValue.text = String.format(Locale.getDefault(), "%s%.1f%%", prefix, gap)
            binding.tvGapStdValue.setTextColor(if (gap >= 0) getColor(R.color.emerald_soft) else getColor(R.color.error))
        }

        viewModel.survivalRate.observe(this) { rate ->
            binding.tvSurvivalRate.text = String.format(Locale.getDefault(), "%.1f%%", rate)
            val color = when {
                rate < 90.0 -> R.color.error
                rate < 95.0 -> R.color.earthy_orange
                else -> R.color.emerald_soft
            }
            binding.tvSurvivalRate.setTextColor(getColor(color))
        }
        viewModel.monthlyMortalityCount.observe(this) { count ->
            binding.tvMonthlyMortality.text = count.toString()
        }

        viewModel.nextVaccine.observe(this) { vaccine ->
            if (vaccine != null) {
                val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
                val dateStr = sdf.format(Date(vaccine.date))
                binding.tvNextVaccine.text = "${vaccine.name} - $dateStr"
            } else {
                binding.tvNextVaccine.text = getString(R.string.no_vaccine_planned)
            }
        }

        viewModel.dailyConsumptionPerHenG.observe(this) {
            binding.tvConsumptionPerHen.text = getString(R.string.gram_unit, it)
        }
        viewModel.currentStockKg.observe(this) {
            binding.tvStockAvailable.text = getString(R.string.kg_unit, it.toInt())
        }
        viewModel.feedAutonomyDays.observe(this) { days ->
            binding.tvFeedAutonomyDashboard.text = getString(R.string.feed_autonomy, days)
            binding.tvFeedStockCircleValue.text = "Stock : ${days}j"

            val info = viewModel.farmInfo.value
            val criticalDays = info?.feedStockCriticalDays ?: FarmInfo.DEFAULT_FEED_STOCK_CRITICAL_DAYS
            val warningDays = info?.feedStockWarningDays ?: FarmInfo.DEFAULT_FEED_STOCK_WARNING_DAYS

            if (days < criticalDays) {
                binding.cardFeedStockCircle.setCardBackgroundColor(getColor(R.color.error))
            } else if (days < warningDays) {
                binding.cardFeedStockCircle.setCardBackgroundColor(getColor(R.color.earthy_orange))
            } else {
                binding.cardFeedStockCircle.setCardBackgroundColor(getColor(R.color.emerald_soft))
            }
        }

        viewModel.lastCollectedCount.observe(this) {
            binding.tvTodayEggsDetail.text = getString(R.string.eggs_collected_count, it)
        }

        viewModel.totalRemaining.observe(this) { total ->
            val tablettes = total / 30
            binding.tvStockCircleValue.text = "${tablettes} Tab dispo"
        }

        viewModel.totalSales.observe(this) {
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvTotalRevenueValue.text = getString(R.string.currency_format, NumberFormat.getInstance().format(it), curr)
        }

        viewModel.totalExpenses.observe(this) {
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvTotalExpensesValue.text = getString(R.string.currency_format, NumberFormat.getInstance().format(it), curr)
        }

        viewModel.netProfit.observe(this) {
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvNetProfit.text = getString(R.string.currency_format, NumberFormat.getInstance().format(it), curr)
            if (it < 0) {
                binding.tvNetProfit.setTextColor(getColor(R.color.error))
            } else {
                binding.tvNetProfit.setTextColor(getColor(R.color.text_primary))
            }
        }

        viewModel.eggTraysStock.observe(this) { stock ->
            if (stock == null) return@observe // On ignore tant que ce n'est pas chargé

            binding.tvEggTraysStockValue.text = "Alvéoles : $stock"
            val criticalThreshold = viewModel.farmInfo.value?.eggTraysCriticalThreshold ?: FarmInfo.DEFAULT_EGG_TRAYS_CRITICAL
            if (stock < criticalThreshold) {
                binding.cardEggTraysStock.setCardBackgroundColor(getColor(R.color.error))
                showLowEggTraysAlert(stock)
            } else {
                binding.cardEggTraysStock.setCardBackgroundColor(getColor(R.color.accent_amber))
            }
        }
        
        viewModel.weeklyProduction.observe(this) { list ->
            setupLineChart(list)
        }

        viewModel.expensesByCategory.observe(this) { list ->
            setupBarChart(list)
        }

        viewModel.activeHealthReminders.observe(this) { list ->
            if (list.isNullOrEmpty()) {
                binding.layoutHealthReminders.visibility = View.GONE
            } else {
                binding.layoutHealthReminders.visibility = View.VISIBLE
                val summary = if (list.size > 1) {
                    getString(R.string.health_reminders_summary_plural, list.size)
                } else {
                    getString(R.string.health_reminders_summary_singular)
                }
                binding.tvHealthRemindersSummary.text = summary
                reminderAdapter.submitList(list)
            }
        }
    }

    private var hasShownLowTraysAlert = false
    private fun showLowEggTraysAlert(stock: Int) {
        if (hasShownLowTraysAlert) return
        hasShownLowTraysAlert = true
        AlertDialog.Builder(this)
            .setTitle("Stock d'alvéoles critique")
            .setMessage("Il ne vous reste que $stock alvéoles à œufs. Veuillez en commander pour ne pas interrompre la mise en tablettes.")
            .setPositiveButton("Commander") { _, _ -> navigateTo(ExpensesActivity::class.java) }
            .setNegativeButton("Plus tard", null)
            .show()
    }

    private fun showBlockingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Mode Lecture Seule")
            .setMessage("Veuillez envoyer un email à hadietou@gmail.com pour lui demander de valider la ferme afin de continuer à utiliser l'application. En attendant, vous pouvez uniquement consulter vos données.")
            .setCancelable(true)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupLineChart(production: List<Triple<Long, Int, Double>>) {
        if (production.isEmpty()) {
            binding.productionChart.clear()
            return
        }

        val entriesEggs = production.mapIndexed { index, triple ->
            Entry(index.toFloat(), triple.second.toFloat())
        }
        
        val entriesRate = production.mapIndexed { index, triple ->
            Entry(index.toFloat(), triple.third.toFloat())
        }

        val dataSetEggs = LineDataSet(entriesEggs, "Œufs").apply {
            color = getColor(R.color.emerald_soft)
            setCircleColor(getColor(R.color.primary))
            lineWidth = 3f
            circleRadius = 4f
            setDrawCircleHole(true)
            circleHoleColor = getColor(R.color.white)
            setDrawValues(false)
            mode = LineDataSet.Mode.HORIZONTAL_BEZIER
            setDrawFilled(true)
            fillColor = getColor(R.color.emerald_container)
            fillAlpha = 60
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
        }

        val orangeColor = getColor(R.color.earthy_orange)
        val dataSetRate = LineDataSet(entriesRate, "% Ponte").apply {
            color = orangeColor
            setDrawCircles(false)
            lineWidth = 2f
            enableDashedLine(10f, 10f, 0f)
            setDrawValues(false)
            mode = LineDataSet.Mode.HORIZONTAL_BEZIER
            setDrawFilled(false)
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT
        }

        binding.productionChart.apply {
            data = LineData(dataSetEggs, dataSetRate)
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            setPinchZoom(false)
            setScaleEnabled(false)

            val textColor = getColor(R.color.text_primary)
            val gridColor = getColor(R.color.off_white)

            xAxis.apply {
                this.textColor = textColor
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setLabelCount(5, false)
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    private val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index in production.indices) {
                            sdf.format(Date(production[index].first))
                        } else ""
                    }
                }
            }

            axisLeft.apply {
                this.textColor = textColor
                axisMinimum = 0f
                setDrawGridLines(true)
                this.gridColor = gridColor
                val maxVal = production.maxOfOrNull { it.second } ?: 100
                axisMaximum = (maxVal * 1.4f).coerceAtLeast(50f)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = value.toInt().toString()
                }
            }
            
            axisRight.apply {
                isEnabled = true
                this.textColor = orangeColor
                axisMinimum = 0f
                axisMaximum = 110f
                setDrawGridLines(false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = "${value.toInt()}%"
                }
            }
            
            legend.isEnabled = false
            setExtraOffsets(0f, 0f, 0f, 0f)
            animateX(1000)
            invalidate()
        }
    }

    private fun setupBarChart(expenses: List<CategoryExpense>) {
        if (expenses.isEmpty()) {
            binding.expensesBarChart.clear()
            return
        }

        val sortedExpenses = expenses.sortedByDescending { it.totalAmount }
        val totalExp = sortedExpenses.sumOf { it.totalAmount }

        val entries = sortedExpenses.mapIndexed { index, catExp ->
            BarEntry(index.toFloat(), catExp.totalAmount.toFloat())
        }

        val colorsList = listOf(
            getColor(R.color.earthy_orange),
            getColor(R.color.accent_blue),
            getColor(R.color.emerald_soft),
            getColor(R.color.accent_amber),
            getColor(R.color.accent_rose),
            getColor(R.color.primary)
        )

        val textColor = getColor(R.color.text_primary)

        val dataSet = BarDataSet(entries, "")
        dataSet.setColors(colorsList)
        dataSet.valueTextSize = 10f
        dataSet.valueTextColor = textColor
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val amountK = (value / 1000f).toInt()
                val percentage = if (totalExp > 0) (value.toDouble() / totalExp * 100).toInt() else 0
                return String.format(Locale.getDefault(), "%d%% | %dk", percentage, amountK)
            }
        }

        binding.expensesBarChart.apply {
            data = BarData(dataSet)
            data.barWidth = 0.5f
            description.isEnabled = false

            xAxis.apply {
                this.textColor = textColor
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                labelRotationAngle = -45f
                setLabelCount(sortedExpenses.size)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index in sortedExpenses.indices) sortedExpenses[index].category else ""
                    }
                }
            }

            axisLeft.apply {
                this.textColor = textColor
                setDrawGridLines(true)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = value.toInt().toString()
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false

            setExtraOffsets(5f, 5f, 5f, 15f)
            animateY(1000)
            invalidate()
        }
    }

    private fun updateHensAgeHeader() {
        val batch = viewModel.selectedBatch.value ?: return
        val count = viewModel.effectiveHensCount.value ?: batch.hensCount
        val ageFormatted = viewModel.getFormattedAge(batch.chickBirthDate)
        
        binding.tvDashboardHensCount.text = NumberFormat.getInstance().format(count)
        binding.tvDashboardAge.text = ageFormatted
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> {}
            R.id.nav_batches -> navigateTo(BatchActivity::class.java)
            R.id.nav_users -> navigateTo(ResponsableActivity::class.java)
            R.id.nav_expenses -> navigateTo(ExpensesActivity::class.java)
            R.id.nav_settings -> navigateTo(FarmInfoActivity::class.java)

            R.id.nav_egg_management -> {
                isEggMenuExpanded = !isEggMenuExpanded
                if (isEggMenuExpanded) isHealthMenuExpanded = false
                rebuildDrawerMenu()
                return true
            }

            R.id.nav_health_management -> {
                isHealthMenuExpanded = !isHealthMenuExpanded
                if (isHealthMenuExpanded) isEggMenuExpanded = false
                rebuildDrawerMenu()
                return true
            }

            R.id.nav_collect -> navigateTo(AgentActivity::class.java)
            R.id.nav_sales -> navigateTo(SalesActivity::class.java)
            R.id.nav_vaccines -> navigateTo(VaccineActivity::class.java)
            R.id.nav_mortality -> navigateTo(MortalityActivity::class.java)

            R.id.nav_delete_account -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.delete_account_url)))
                startActivity(intent)
            }
            R.id.nav_logout -> {
                NavMenuStyler.confirmLogout(this) {
                    FirebaseAuth.getInstance().signOut()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAccessStatus()
        viewModel.refreshAllStats()
    }
}
