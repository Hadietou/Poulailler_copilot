package com.hadietou.poulailler.ui

import android.content.Intent
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
import com.hadietou.poulailler.R
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.databinding.ActivityDashboardBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.hadietou.poulailler.data.CategoryExpense
import com.hadietou.poulailler.util.SunUtils
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

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private val firebaseRepo = FirebaseRepository()
    private var isBlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityDashboardBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId == null) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return
            }

            setupNavigation()
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

    private fun updateLightingIndicator() {
        val extinctionTime = SunUtils.getExtinctionTime()
        binding.tvLightingTime.text = getString(R.string.lighting_extinguish_at, extinctionTime)
    }

    private fun setupNavigation() {
        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navigationView.setNavigationItemSelectedListener(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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
    }

    private fun setupDashboardCardListeners() {
        binding.cardLightingIndicator.setOnClickListener {
            val intent = Intent(this, VaccineActivity::class.java)
            intent.putExtra("scrollToLighting", true)
            intent.putExtra("role", userRole)
            intent.putExtra("userIdString", userId)
            intent.putExtra("selectedBatchId", viewModel.selectedBatch.value?.firestoreId)
            startActivity(intent)
        }
        binding.cardLayingRate.setOnClickListener { navigateTo(AgentActivity::class.java) }
        
        binding.layoutCollected.setOnClickListener { navigateTo(AgentActivity::class.java) }
        binding.layoutSold.setOnClickListener { navigateTo(SalesActivity::class.java) }

        binding.cardMortality.setOnClickListener { navigateTo(MortalityActivity::class.java) }
        binding.cardStockFeed.setOnClickListener { navigateTo(ExpensesActivity::class.java) }
        binding.cardNetProfit.setOnClickListener { navigateTo(SalesActivity::class.java) }
        binding.cardProductionChart.setOnClickListener { navigateTo(AgentActivity::class.java) }
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
        menu.findItem(R.id.nav_vaccines)?.isVisible = true
        
        binding.titleFinance.visibility = if (isResp) View.VISIBLE else View.GONE
        binding.cardNetProfit.visibility = if (isResp) View.VISIBLE else View.GONE
        binding.cardExpensesChart.visibility = if (isResp) View.VISIBLE else View.GONE
        
        val headerView = binding.navigationView.getHeaderView(0)
        headerView?.findViewById<View>(R.id.tvFarmNameNav)?.visibility = View.GONE
        
        updateUIBasedOnBatchType()
    }

    private fun updateUIBasedOnBatchType() {
        val batch = viewModel.selectedBatch.value ?: return
        val isChair = batch.typeLot == "CHAIR"
        
        val eggVisibility = if (isChair) View.GONE else View.VISIBLE
        binding.cardLayingRate.visibility = eggVisibility
        binding.titleEggStock.visibility = eggVisibility
        binding.cardEggStock.visibility = eggVisibility
        binding.titleProduction.visibility = eggVisibility
        binding.cardProductionChart.visibility = eggVisibility
        binding.cardLightingIndicator.visibility = eggVisibility
        
        // Monthly KPIs
        binding.titleMonthlyKPI.visibility = eggVisibility
        binding.cardMonthlyKPI.visibility = eggVisibility

        binding.navigationView.menu.findItem(R.id.nav_collect)?.isVisible = !isChair
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

        // Monthly KPIs Observations
        viewModel.monthlyProduction.observe(this) { prod ->
            val valueK = prod / 1000.0
            binding.tvMonthlyProdValue.text = getString(R.string.k_format, valueK)
        }
        viewModel.monthlySales.observe(this) { sales ->
            val valueK = sales / 1000.0
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvMonthlySalesValue.text = "${getString(R.string.k_format, valueK)} $curr"
        }
        viewModel.monthlyLayingRate.observe(this) { rate ->
            binding.tvMonthlyRateValue.text = "${rate.toInt()}%"
        }

        viewModel.prodTrend.observe(this) { trend -> updateTrendUI(binding.tvMonthlyProdTrend, trend) }
        viewModel.salesTrend.observe(this) { trend -> updateTrendUI(binding.tvMonthlySalesTrend, trend) }
        viewModel.rateTrend.observe(this) { trend -> updateTrendUI(binding.tvMonthlyRateTrend, trend) }
        
        viewModel.lastCollectedCount.observe(this) { 
            binding.tvTodayEggsDetail.text = getString(R.string.eggs_collected_count, it) 
        }
        viewModel.totalMortalityCount.observe(this) { binding.tvTotalMortality.text = it.toString() }
        viewModel.currentStockKg.observe(this) { 
            binding.tvTotalFeed.text = getString(R.string.kg_unit, it.toInt()) 
        }
        viewModel.feedAutonomyDays.observe(this) { 
            binding.tvFeedAutonomy.text = getString(R.string.feed_autonomy, it) 
        }
        
        viewModel.totalCollected.observe(this) { total ->
            val tablettes = total / 30
            binding.tvTotalCollectedTablettes.text = getString(R.string.tablettes_count, tablettes)
            binding.tvTotalCollectedEggs.text = getString(R.string.eggs_count_format, NumberFormat.getInstance().format(total))
        }
        viewModel.totalSold.observe(this) { total ->
            val tablettes = total / 30
            binding.tvTotalSoldTablettes.text = getString(R.string.tablettes_count, tablettes)
            binding.tvTotalSoldEggs.text = getString(R.string.eggs_count_format, NumberFormat.getInstance().format(total))
        }
        viewModel.totalRemaining.observe(this) { total ->
            val tablettes = total / 30
            binding.tvAvailableTablettes.text = getString(R.string.tablettes_count, tablettes)
            binding.tvAvailableEggs.text = getString(R.string.eggs_count_format, NumberFormat.getInstance().format(total))
        }

        viewModel.netProfit.observe(this) { 
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvNetProfit.text = getString(R.string.currency_format, NumberFormat.getInstance().format(it), curr)
        }
        viewModel.totalSales.observe(this) {
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvTotalRevenue.text = getString(R.string.currency_format, NumberFormat.getInstance().format(it), curr)
        }
        viewModel.totalExpenses.observe(this) {
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvTotalExpenses.text = getString(R.string.currency_format, NumberFormat.getInstance().format(it), curr)
        }
        
        viewModel.totalFeedPurchasedKg.observe(this) {
            binding.tvTotalPurchasedLabel.text = getString(R.string.feed_cumul, it.toInt())
        }

        viewModel.weeklyProduction.observe(this) { list ->
            setupLineChart(list)
        }

        viewModel.expensesByCategory.observe(this) { list ->
            setupBarChart(list)
        }
    }

    private fun updateTrendUI(textView: TextView, trend: Int) {
        when {
            trend > 0 -> {
                textView.text = "▲ ${getString(R.string.vs_last_month)}"
                textView.setTextColor(getColor(R.color.emerald_soft))
            }
            trend < 0 -> {
                textView.text = "▼ ${getString(R.string.vs_last_month)}"
                textView.setTextColor(getColor(R.color.error))
            }
            else -> {
                textView.text = "-- ${getString(R.string.vs_last_month)}"
                textView.setTextColor(getColor(R.color.text_secondary))
            }
        }
    }

    private fun showBlockingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Mode Lecture Seule")
            .setMessage("Veuillez envoyer un email à hadietou@gmail.com pour lui demander de valider la ferme afin de continuer à utiliser l'application. En attendant, vous pouvez uniquement consulter vos données.")
            .setCancelable(true)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupLineChart(production: List<Pair<Long, Int>>) {
        if (production.isEmpty()) {
            binding.productionChart.clear()
            return
        }
        
        val entries = production.mapIndexed { index, pair ->
            Entry(index.toFloat(), pair.second.toFloat())
        }

        val dataSet = LineDataSet(entries, "Œufs collectés").apply {
            color = getColor(R.color.emerald_soft)
            setCircleColor(getColor(R.color.primary))
            lineWidth = 3f
            circleRadius = 5f
            setDrawValues(true)
            valueTextColor = getColor(R.color.text_primary)
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = getColor(R.color.emerald_container)
            fillAlpha = 50
        }

        binding.productionChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            
            val textColor = getColor(R.color.text_primary)
            
            xAxis.apply {
                this.textColor = textColor
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
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
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisRight.isEnabled = false
            legend.textColor = textColor
            
            setExtraOffsets(5f, 5f, 5f, 15f)
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
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = textColor
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = value.toInt().toString()
        }

        binding.expensesBarChart.apply {
            data = BarData(dataSet)
            data.barWidth = 0.6f
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
            
            setExtraOffsets(5f, 5f, 5f, 25f)
            animateY(1000)
            invalidate()
        }
    }

    private fun updateHensAgeHeader() {
        val batch = viewModel.selectedBatch.value ?: return
        val count = viewModel.effectiveHensCount.value ?: batch.hensCount
        val ageFormatted = viewModel.getFormattedAge(batch.chickBirthDate)
        binding.tvAppTitle.text = getString(R.string.subjects_age_format, NumberFormat.getInstance().format(count), ageFormatted)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> {}
            R.id.nav_batches -> navigateTo(BatchActivity::class.java)
            R.id.nav_users -> navigateTo(ResponsableActivity::class.java)
            R.id.nav_expenses -> navigateTo(ExpensesActivity::class.java)
            R.id.nav_vaccines -> navigateTo(VaccineActivity::class.java)
            R.id.nav_collect -> navigateTo(AgentActivity::class.java)
            R.id.nav_sales -> navigateTo(SalesActivity::class.java)
            R.id.nav_mortality -> navigateTo(MortalityActivity::class.java)
            R.id.nav_delete_account -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.delete_account_url)))
                startActivity(intent)
            }
            R.id.nav_logout -> {
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
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
