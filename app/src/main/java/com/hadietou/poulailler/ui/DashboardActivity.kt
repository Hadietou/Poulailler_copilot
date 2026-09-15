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
import androidx.core.view.drawToBitmap
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hadietou.poulailler.R
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.databinding.ActivityDashboardBinding
import com.hadietou.poulailler.repository.FirebaseRepository
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
import com.hadietou.poulailler.util.ActionMenuItem
import com.hadietou.poulailler.util.ActionMenuPopup
import com.hadietou.poulailler.util.DrawerSubmenuController
import com.hadietou.poulailler.util.NavMenuStyler
import com.hadietou.poulailler.util.NetworkStatusMonitor
import com.hadietou.poulailler.network.WeatherUtils

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private val firebaseRepo = FirebaseRepository()
    private var isBlocked = false
    private lateinit var reminderAdapter: HealthReminderAdapter
    
    private val drawerSubmenus = DrawerSubmenuController()

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
            fetchTodayTemperature()

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

    private fun fetchTodayTemperature() {
        lifecycleScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { firebaseRepo.getFarmInfo() }
                val response = withContext(Dispatchers.IO) { WeatherUtils.fetchForecast(info?.latitude, info?.longitude) }
                val offset = info?.weatherTempOffsetCelsius ?: FarmInfo.DEFAULT_WEATHER_TEMP_OFFSET
                val todayTemp = response.daily.maxTemperatures.firstOrNull()?.plus(offset)
                if (todayTemp != null) {
                    val emoji = if (todayTemp >= 35) "🔥" else "☀️"
                    binding.tvDashHealthTemperature.text = "${Math.round(todayTemp)}°C $emoji"
                } else {
                    binding.tvDashHealthTemperature.text = "--"
                }
            } catch (e: Exception) {
                Log.e("Dashboard", "Error fetching weather", e)
                binding.tvDashHealthTemperature.text = "--"
            }
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
            parents = drawerSubmenus.parentsForStyler(),
            children = DrawerSubmenuController.CHILD_ITEM_IDS
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
            val expChart = if (binding.expensesBarChart.visibility == View.VISIBLE) binding.expensesBarChart.drawToBitmap() else null
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
        
        binding.cardEggProduction.setOnClickListener { showEggProductionMenu(it) }
        binding.cardEggSales.setOnClickListener { showSalesMenu(it) }

        binding.cardDashExpenses.setOnClickListener { showExpensesMenu(it) }
        binding.cardDashFeed.setOnClickListener { showFeedMenu(it) }
        binding.cardDashHealth.setOnClickListener { showHealthMenu(it) }
        binding.layoutDashHealthMortalityRate.setOnClickListener { navigateToStats("MORTALITY") }
        binding.layoutDashHealthWeather.setOnClickListener { navigateTo(HealthDashboardActivity::class.java) }
        binding.layoutDashHealthTreatment.setOnClickListener { navigateTo(HealthDashboardActivity::class.java) }

        binding.cardNetProfit.setOnClickListener { navigateTo(SalesActivity::class.java) }
    }

    private fun showEggProductionMenu(anchor: View) = ActionMenuPopup.show(anchor, listOf(
        ActionMenuItem(R.drawable.ic_egg, "+ Saisir la ponte") { navigateTo(AgentActivity::class.java) },
        ActionMenuItem(R.drawable.ic_chart_line, "Suivi de la production") { navigateToStats("COLLECTION") },
        ActionMenuItem(R.drawable.ic_chart_line, "Suivi des œufs cassés") { navigateToStats("BROKEN") }
    ))

    private fun showSalesMenu(anchor: View) = ActionMenuPopup.show(anchor, listOf(
        ActionMenuItem(R.drawable.ic_sell, "Saisie des ventes") { navigateTo(SalesActivity::class.java) },
        ActionMenuItem(R.drawable.ic_chart_line, "Suivi des ventes") { navigateToStats("SALES") },
        ActionMenuItem(R.drawable.ic_finance, "Suivi de la recette") { navigateToStats("REVENUE") }
    ))

    private fun showExpensesMenu(anchor: View) = ActionMenuPopup.show(anchor, listOf(
        ActionMenuItem(R.drawable.ic_finance, "+ Ajouter une dépense") { navigateTo(ExpensesActivity::class.java) },
        ActionMenuItem(R.drawable.ic_chart_line, "Suivi des dépenses") { navigateToStats("EXPENSES") }
    ))

    private fun showFeedMenu(anchor: View) = ActionMenuPopup.show(anchor, listOf(
        ActionMenuItem(R.drawable.ic_finance, "Saisie des dépenses") { navigateTo(ExpensesActivity::class.java) },
        ActionMenuItem(R.drawable.ic_feed, "Suivi de la consommation") { navigateTo(FeedConsumptionActivity::class.java) }
    ))

    private fun showHealthMenu(anchor: View) = ActionMenuPopup.show(anchor, listOf(
        ActionMenuItem(R.drawable.ic_mortality, "+ Enregistrer une mortalité") { navigateTo(MortalityActivity::class.java) },
        ActionMenuItem(R.drawable.ic_vaccine, "Vaccins & rappels") { navigateTo(VaccineActivity::class.java) },
        ActionMenuItem(R.drawable.ic_chart_line, "Suivi de la mortalité") { navigateToStats("MORTALITY") }
    ))

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
        binding.cardEggProduction.visibility = eggVisibility
        
        binding.cardFeedStockCircle.visibility = View.VISIBLE
        
        val menu = binding.navigationView.menu
        
        menu.findItem(R.id.nav_egg_management)?.isVisible = !isChair
        menu.findItem(R.id.nav_health_management)?.isVisible = true
        drawerSubmenus.applyGroupVisibility(menu, eggVisible = !isChair)
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

        viewModel.monthlySalesTablettes.observe(this) { tablettes ->
            binding.tvSalesMonthlyValue.text = tablettes.toString()
        }

        viewModel.monthlyCollectedCount.observe(this) { count ->
            binding.tvEggProdMonthlyValue.text = "${NumberFormat.getInstance().format(count)} œufs produits ce mois-ci"
        }
        viewModel.monthlySalesAmount.observe(this) { amount ->
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvSalesRevenueValue.text = "${getString(R.string.k_format, amount / 1000.0)} $curr de recette ce mois-ci"
        }
        viewModel.monthlyExpensesAmount.observe(this) { amount ->
            binding.tvDashExpensesMonthly.text = getString(R.string.k_format, amount / 1000.0)
        }

        viewModel.layingGapVsStandard.observe(this) { gap ->
            val prefix = if (gap > 0) "+" else ""
            binding.tvDashGapStd.text = String.format(Locale.getDefault(), "Écart vs standard : %s%.1f%%", prefix, gap)
            binding.tvDashGapStd.setTextColor(if (gap >= 0) getColor(R.color.emerald_soft) else getColor(R.color.error))
        }

        viewModel.cumulativeMortalityRate.observe(this) { rate ->
            val color = when {
                rate > 10.0 -> R.color.error
                rate > 5.0 -> R.color.earthy_orange
                else -> R.color.emerald_soft
            }
            binding.tvDashHealthMortalityRate.text = String.format(Locale.getDefault(), "%.1f%%", rate)
            binding.tvDashHealthMortalityRate.setTextColor(getColor(color))
        }
        viewModel.monthlyMortalityCount.observe(this) { count ->
            binding.tvDashHealthMortality.text = count.toString()
        }

        viewModel.currentStockKg.observe(this) {
            binding.tvDashFeedStock.text = it.toInt().toString()
        }
        viewModel.feedAutonomyDays.observe(this) { days ->
            binding.tvFeedStockCircleValue.text = "Stock : ${days}j"
            binding.tvDashFeedAutonomy.text = "Autonomie : $days jours"

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
            binding.tvEggProdStockValue.text = tablettes.toString()
        }

        viewModel.totalSales.observe(this) {
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvTotalRevenueValue.text = getString(R.string.currency_format, NumberFormat.getInstance().format(it), curr)
        }

        viewModel.totalExpenses.observe(this) {
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvTotalExpensesValue.text = getString(R.string.currency_format, NumberFormat.getInstance().format(it), curr)
            binding.tvDashExpensesTotal.text = "${getString(R.string.k_format, it / 1000.0)} $curr au total"
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
            DashboardChartRenderer.renderProduction(binding.productionChart, this, list)
        }

        viewModel.expensesByCategory.observe(this) { list ->
            DashboardChartRenderer.renderExpenses(binding.expensesBarChart, this, list)
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

            val count = list?.size ?: 0
            binding.tvDashHealthReminders.text = if (count > 0) "$count en attente" else "Aucun"
            binding.tvDashHealthReminders.setTextColor(getColor(if (count > 0) R.color.error else R.color.emerald_soft))
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

    private fun updateHensAgeHeader() {
        val batch = viewModel.selectedBatch.value ?: return
        val count = viewModel.effectiveHensCount.value ?: batch.hensCount
        val ageFormatted = viewModel.getFormattedAge(batch.chickBirthDate)
        
        binding.tvDashboardHensCount.text = NumberFormat.getInstance().format(count)
        binding.tvDashboardAge.text = ageFormatted
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (drawerSubmenus.handleParentClick(item.itemId)) {
            rebuildDrawerMenu()
            return true
        }
        when (item.itemId) {
            R.id.nav_dashboard -> {}
            R.id.nav_batches -> navigateTo(BatchActivity::class.java)
            R.id.nav_users -> navigateTo(ResponsableActivity::class.java)
            R.id.nav_expenses -> navigateTo(ExpensesActivity::class.java)
            R.id.nav_settings -> navigateTo(FarmInfoActivity::class.java)

            R.id.nav_collect -> navigateTo(AgentActivity::class.java)
            R.id.nav_sales -> navigateTo(SalesActivity::class.java)
            R.id.nav_vaccines -> navigateTo(HealthDashboardActivity::class.java)
            R.id.nav_mortality -> navigateTo(MortalityActivity::class.java)
            R.id.nav_treatments -> navigateTo(TreatmentActivity::class.java)
            R.id.nav_diseases -> navigateTo(DiseaseActivity::class.java)

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
