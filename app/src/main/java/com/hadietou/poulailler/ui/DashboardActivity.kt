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
import android.widget.Toast
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
import com.hadietou.poulailler.databinding.DialogCreateFarmBinding
import com.hadietou.poulailler.databinding.DialogLinkPhoneBinding
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
    private val loginVm: LoginViewModel by viewModels()
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private val firebaseRepo = FirebaseRepository()
    private var isBlocked = false
    // Un compte créé par email n'a pas de numéro : lui proposer d'en lier un (voir
    // nav_link_phone) pour pouvoir aussi se connecter par téléphone à l'avenir.
    private var hasPhoneNumber = false
    private lateinit var reminderAdapter: HealthReminderAdapter
    
    private val drawerSubmenus = DrawerSubmenuController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityDashboardBinding.inflate(layoutInflater)
            setContentView(binding.root)
            NetworkStatusMonitor.observe(this, binding.root)

            userId = if (com.hadietou.poulailler.demo.DemoMode.enabled) {
                com.hadietou.poulailler.demo.DemoData.USER_ID
            } else {
                FirebaseAuth.getInstance().currentUser?.uid
            }
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
                        hasPhoneNumber = !profile.phoneNumber.isNullOrBlank()
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
        val info = viewModel.farmInfo.value
        val lightingHours = info?.lightingHoursAfterSunrise ?: FarmInfo.DEFAULT_LIGHTING_HOURS
        val extinctionTime = if (info?.latitude != null && info.longitude != null) {
            SunUtils.getExtinctionTime(lightingHours, info.latitude, info.longitude)
        } else {
            SunUtils.getExtinctionTime(lightingHours)
        }
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
        // updateUIBasedOnRole() appelle déjà updateUIBasedOnBatchType() en interne (et
        // réapplique les restrictions de rôle APRÈS, voir ce commentaire là-bas) : un second
        // appel ici resterait juste après, retoggle la visibilité des groupes de sous-menu
        // SANS réappliquer ensuite les restrictions de rôle qui vivent dans ces groupes.
        updateUIBasedOnRole()
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
            val intent = Intent(this, WeatherActivity::class.java)
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
        binding.layoutOpenHealthDashboard.setOnClickListener { navigateTo(HealthDashboardActivity::class.java) }
        binding.layoutDashHealthMortalityRate.setOnClickListener { navigateToStats("MORTALITY") }
        binding.layoutDashHealthWeather.setOnClickListener { navigateTo(WeatherActivity::class.java) }
        binding.layoutDashHealthTreatment.setOnClickListener { navigateTo(TreatmentActivity::class.java) }

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
        menu.findItem(R.id.nav_expenses)?.isVisible = isResp
        menu.findItem(R.id.nav_batches)?.isVisible = isResp

        binding.titleFinance.visibility = if (isResp) View.VISIBLE else View.GONE
        binding.cardNetProfit.visibility = if (isResp) View.VISIBLE else View.GONE

        val headerView = binding.navigationView.getHeaderView(0)
        headerView?.findViewById<View>(R.id.tvFarmNameNav)?.visibility = View.GONE

        binding.toolbar.findViewById<View>(R.id.ivHeaderReport)?.visibility = if (isResp) View.VISIBLE else View.GONE

        // updateUIBasedOnBatchType() applique la visibilité des groupes de sous-menu via
        // Menu.setGroupVisible (voir DrawerSubmenuController.applyGroupVisibility) — qui, par
        // conception de l'API Android, n'est qu'un raccourci pour appeler item.setVisible sur
        // chaque item du groupe : il n'existe PAS de drapeau "groupe" séparé qui persisterait.
        // nav_users/nav_create_own_farm/nav_switch_farm vivent tous dans group_account_submenu :
        // leurs restrictions par rôle doivent donc être réappliquées APRÈS cet appel (sans quoi
        // elles disparaissent dès que "Compte" est déplié), ET combinées avec l'état
        // déplié/replié lui-même (sans quoi elles réapparaissent même "Compte" replié, dès que
        // le rôle/les fermes de l'utilisateur les rendent à nouveau applicables).
        updateUIBasedOnBatchType()
        val accountExpanded = drawerSubmenus.expanded == DrawerSubmenuController.Submenu.ACCOUNT
        menu.findItem(R.id.nav_users)?.isVisible = accountExpanded && isResp
        // Un responsable gère déjà sa propre ferme ; proposer d'en créer une autre ne
        // concerne que les agents pour l'instant (voir FirebaseRepository.createFarmExtended
        // et la note d'architecture multi-fermes : la ferme active bascule vers la nouvelle,
        // sans perdre l'appartenance précédente — voir nav_switch_farm/refreshFarmSwitcherMenuItem).
        menu.findItem(R.id.nav_create_own_farm)?.isVisible = accountExpanded && !isResp
        menu.findItem(R.id.nav_link_phone)?.isVisible = accountExpanded && !hasPhoneNumber
        refreshFarmSwitcherMenuItem(accountExpanded)
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
            // Passe par updateUIBasedOnRole() plutôt qu'un appel direct à
            // updateUIBasedOnBatchType() : ce dernier retoggle la visibilité des groupes de
            // sous-menu (voir le commentaire dans updateUIBasedOnRole), ce qui doit toujours
            // être suivi d'une réapplication des restrictions de rôle.
            updateUIBasedOnRole()
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
            binding.tvFeedStockCircleValue.text = "Aliment : ${days}j"
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

            R.id.nav_create_own_farm -> showCreateOwnFarmDialog()
            R.id.nav_switch_farm -> showFarmSwitcherDialog()
            R.id.nav_link_phone -> showLinkPhoneDialog()

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

    /**
     * Permet à un agent de créer sa propre ferme et d'en devenir responsable (voir
     * FirebaseRepository.createFarmExtended). Un utilisateur n'ayant qu'une seule ferme à la
     * fois, cela remplace son affectation d'agent actuelle.
     */
    private fun showCreateOwnFarmDialog() {
        val countries = arrayOf("Mauritanie", "Sénégal", "Mali", "Guinée", "Côte d'Ivoire", "Bénin", "Togo", "Burkina Faso", "Niger")
        val currencies = arrayOf("MRU", "CFA")

        val dialogBinding = DialogCreateFarmBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        val countryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, countries)
        dialogBinding.actvCountry.setAdapter(countryAdapter)
        dialogBinding.actvCountry.setText(countries[0], false)

        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencies)
        dialogBinding.actvCurrency.setAdapter(currencyAdapter)
        dialogBinding.actvCurrency.setText(currencies[0], false)

        dialogBinding.actvCountry.setOnItemClickListener { _, _, position, _ ->
            dialogBinding.actvCurrency.setText(if (countries[position] == "Mauritanie") "MRU" else "CFA", false)
        }

        dialogBinding.btnConfirmCreateFarm.setOnClickListener {
            val farmName = dialogBinding.etFarmName.text.toString().trim()
            val city = dialogBinding.etCity.text.toString().trim()
            val country = dialogBinding.actvCountry.text.toString()
            val currency = dialogBinding.actvCurrency.text.toString()

            if (farmName.isEmpty() || city.isEmpty()) {
                Toast.makeText(this, "Nom de ferme et ville requis", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val uid = userId ?: return@launch
                    val profile = firebaseRepo.getUserProfile(uid)
                    val code = firebaseRepo.createFarmExtended(
                        farmName, currency, profile?.username ?: "Responsable", profile?.email ?: "", country, city
                    )
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        Toast.makeText(this@DashboardActivity, "Ferme créée ! Code : $code", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@DashboardActivity, DashboardActivity::class.java)
                        intent.putExtra("role", "RESPONSABLE")
                        intent.putExtra("userIdString", uid)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DashboardActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    /** N'affiche "Changer de ferme" que si "Compte" est déplié ET que l'utilisateur a plus d'une ferme. */
    private fun refreshFarmSwitcherMenuItem(accountExpanded: Boolean) {
        lifecycleScope.launch {
            val memberships = withContext(Dispatchers.IO) { firebaseRepo.getMemberships() }
            binding.navigationView.menu.findItem(R.id.nav_switch_farm)?.isVisible = accountExpanded && memberships.size > 1
        }
    }

    /** Liste les fermes de l'utilisateur et bascule la ferme active vers celle choisie. */
    private fun showFarmSwitcherDialog() {
        lifecycleScope.launch {
            val memberships = withContext(Dispatchers.IO) { firebaseRepo.getMemberships() }
            if (memberships.size < 2) return@launch

            val currentFarmId = withContext(Dispatchers.IO) { firebaseRepo.getFarmId() }
            val labels = memberships.map { "${it.farmName} (${if (it.role == "RESPONSABLE") "Responsable" else "Agent"})" }.toTypedArray()
            val selectedIndex = memberships.indexOfFirst { it.farmId == currentFarmId }.coerceAtLeast(0)

            AlertDialog.Builder(this@DashboardActivity)
                .setTitle("Changer de ferme")
                .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                    val target = memberships[which]
                    dialog.dismiss()
                    if (target.farmId == currentFarmId) return@setSingleChoiceItems
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            firebaseRepo.switchActiveFarm(target.farmId)
                            withContext(Dispatchers.Main) {
                                val intent = Intent(this@DashboardActivity, DashboardActivity::class.java)
                                intent.putExtra("role", target.role)
                                intent.putExtra("userIdString", userId)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@DashboardActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private val linkPhoneCountryDialOptions = listOf(
        "🇲🇷 +222" to "+222",
        "🇸🇳 +221" to "+221",
        "🇲🇱 +223" to "+223",
        "🇬🇳 +224" to "+224",
        "🇨🇮 +225" to "+225",
        "🇧🇯 +229" to "+229",
        "🇹🇬 +228" to "+228",
        "🇧🇫 +226" to "+226",
        "🇳🇪 +227" to "+227",
        "🇩🇿 +213" to "+213",
        "🇦🇴 +244" to "+244",
        "🇧🇼 +267" to "+267",
        "🇧🇮 +257" to "+257",
        "🇨🇻 +238" to "+238",
        "🇨🇲 +237" to "+237",
        "🇨🇫 +236" to "+236",
        "🇹🇩 +235" to "+235",
        "🇰🇲 +269" to "+269",
        "🇨🇬 +242" to "+242",
        "🇨🇩 +243" to "+243",
        "🇩🇯 +253" to "+253",
        "🇪🇬 +20" to "+20",
        "🇬🇶 +240" to "+240",
        "🇪🇷 +291" to "+291",
        "🇸🇿 +268" to "+268",
        "🇪🇹 +251" to "+251",
        "🇬🇦 +241" to "+241",
        "🇬🇲 +220" to "+220",
        "🇬🇭 +233" to "+233",
        "🇬🇼 +245" to "+245",
        "🇰🇪 +254" to "+254",
        "🇱🇸 +266" to "+266",
        "🇱🇷 +231" to "+231",
        "🇱🇾 +218" to "+218",
        "🇲🇬 +261" to "+261",
        "🇲🇼 +265" to "+265",
        "🇲🇺 +230" to "+230",
        "🇲🇦 +212" to "+212",
        "🇲🇿 +258" to "+258",
        "🇳🇦 +264" to "+264",
        "🇳🇬 +234" to "+234",
        "🇷🇼 +250" to "+250",
        "🇸🇹 +239" to "+239",
        "🇸🇨 +248" to "+248",
        "🇸🇱 +232" to "+232",
        "🇸🇴 +252" to "+252",
        "🇿🇦 +27" to "+27",
        "🇸🇸 +211" to "+211",
        "🇸🇩 +249" to "+249",
        "🇹🇿 +255" to "+255",
        "🇹🇳 +216" to "+216",
        "🇺🇬 +256" to "+256",
        "🇿🇲 +260" to "+260",
        "🇿🇼 +263" to "+263",
        "🇨🇭 +41" to "+41",
        "🇫🇷 +33" to "+33"
    )

    /**
     * Permet à un utilisateur créé par email (sans numéro) de lier un numéro vérifié par SMS
     * à son compte, pour pouvoir aussi se connecter par téléphone à l'avenir (voir
     * FirebaseRepository.linkPhoneToCurrentAccount et nav_link_phone).
     */
    private fun showLinkPhoneDialog() {
        val dialogBinding = DialogLinkPhoneBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        val codeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, linkPhoneCountryDialOptions.map { it.first })
        dialogBinding.actvLinkCountryCode.setAdapter(codeAdapter)
        dialogBinding.actvLinkCountryCode.setText(linkPhoneCountryDialOptions[0].first, false)

        var pendingPhone: String? = null

        fun onPhoneLinked() {
            hasPhoneNumber = true
            updateUIBasedOnRole()
            dialog.dismiss()
        }

        dialogBinding.btnLinkSendCode.setOnClickListener {
            val dialCode = linkPhoneCountryDialOptions.find { it.first == dialogBinding.actvLinkCountryCode.text.toString() }?.second
                ?: linkPhoneCountryDialOptions[0].second
            val digits = dialogBinding.etLinkPhone.text.toString().filter { it.isDigit() }
            if (digits.isEmpty()) {
                Toast.makeText(this, "Numéro de téléphone requis", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val phone = "$dialCode$digits"
            pendingPhone = phone
            dialogBinding.btnLinkSendCode.isEnabled = false
            loginVm.startPhoneVerification(
                activity = this,
                phoneNumber = phone,
                onCodeSent = {
                    runOnUiThread {
                        dialogBinding.btnLinkSendCode.isEnabled = true
                        dialogBinding.layoutLinkOtpSection.visibility = View.VISIBLE
                        dialogBinding.tvLinkOtpSentTo.text = "Code envoyé au $phone"
                        Toast.makeText(this, "Code SMS envoyé", Toast.LENGTH_SHORT).show()
                    }
                },
                onAutoVerified = { credential ->
                    runOnUiThread { dialogBinding.btnLinkSendCode.isEnabled = true }
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            firebaseRepo.linkPhoneToCurrentAccount(credential, phone)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@DashboardActivity, "Numéro lié avec succès !", Toast.LENGTH_SHORT).show()
                                onPhoneLinked()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@DashboardActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onFailed = { message ->
                    runOnUiThread {
                        dialogBinding.btnLinkSendCode.isEnabled = true
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        dialogBinding.btnLinkVerifyCode.setOnClickListener {
            val phone = pendingPhone
            val code = dialogBinding.etLinkOtpCode.text.toString().trim()
            if (phone == null) {
                Toast.makeText(this, "Redemandez un code.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (code.isEmpty()) {
                Toast.makeText(this, "Code requis", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            loginVm.verifyOtpForLinking(code, phone) { success, message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    if (success) onPhoneLinked()
                }
            }
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAccessStatus()
        viewModel.refreshAllStats()
    }
}
