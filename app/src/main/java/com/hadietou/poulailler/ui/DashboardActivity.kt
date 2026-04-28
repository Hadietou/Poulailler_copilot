package com.hadietou.poulailler.ui

import android.content.Intent
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.hadietou.poulailler.R
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.databinding.ActivityDashboardBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.hadietou.poulailler.data.CategoryExpense
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
            setupBatchSpinner()
            observeViewModel()
            
            // Apply default role visibility immediately
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
        
        // Agents now have access to Sanitary Follow-up (Vaccines)
        menu.findItem(R.id.nav_vaccines)?.isVisible = true
        
        binding.titleFinance.visibility = if (isResp) View.VISIBLE else View.GONE
        binding.cardNetProfit.visibility = if (isResp) View.VISIBLE else View.GONE
        binding.cardExpensesChart.visibility = if (isResp) View.VISIBLE else View.GONE
        
        // Hide farm name info on the dashboard and header for agents as requested
        binding.tvDashboardFarmName.visibility = if (isResp) View.VISIBLE else View.GONE
        val headerView = binding.navigationView.getHeaderView(0)
        headerView?.findViewById<View>(R.id.tvFarmNameNav)?.visibility = if (isResp) View.VISIBLE else View.GONE
    }

    private fun observeViewModel() {
        viewModel.userName.observe(this) { binding.tvWelcome.text = "BONJOUR ${it.uppercase()} !" }
        
        viewModel.farmInfo.observe(this) { info ->
            if (info != null) {
                binding.tvDashboardFarmName.text = info.farmName
                val headerView = binding.navigationView.getHeaderView(0)
                headerView?.findViewById<TextView>(R.id.tvFarmNameNav)?.text = info.farmName
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

        viewModel.selectedBatch.observe(this) { updateHensAgeHeader() }

        viewModel.effectiveHensCount.observe(this) { 
            binding.tvHensCount.text = "Poules: ${NumberFormat.getInstance().format(it)}"
            updateHensAgeHeader()
        }
        
        viewModel.layingRate.observe(this) { 
            val r = it.toInt().coerceIn(0, 100)
            binding.progressLayingRate.setProgress(r, true)
            binding.tvLayingRateValue.text = "$r%"
        }

        viewModel.layingTrend.observe(this) { trend ->
            val sign = if (trend >= 0) "+" else ""
            binding.tvLayingTrend.text = "$sign${trend.toInt()}% vs hier"
            if (trend >= 0) {
                binding.tvLayingTrend.setTextColor(getColor(R.color.emerald_soft))
                binding.tvLayingTrend.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.emerald_container))
            } else {
                binding.tvLayingTrend.setTextColor(getColor(R.color.error))
                binding.tvLayingTrend.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.error_container))
            }
        }
        
        viewModel.todayEggs.observe(this) { binding.tvTodayEggsDetail.text = "$it œufs aujourd'hui" }
        viewModel.totalMortalityCount.observe(this) { binding.tvTotalMortality.text = it.toString() }
        viewModel.currentStockKg.observe(this) { binding.tvTotalFeed.text = "${it.toInt()} kg" }
        viewModel.feedAutonomyDays.observe(this) { binding.tvFeedAutonomy.text = "$it j restants" }
        
        // Egg Management
        viewModel.totalCollected.observe(this) { binding.tvTotalCollected.text = it.toString() }
        viewModel.totalSold.observe(this) { binding.tvTotalSold.text = it.toString() }
        viewModel.totalRemaining.observe(this) { binding.tvAvailableStock.text = it.toString() }

        // Finances
        viewModel.netProfit.observe(this) { 
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvNetProfit.text = "${NumberFormat.getInstance().format(it)} $curr"
        }
        viewModel.totalSales.observe(this) {
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvTotalRevenue.text = "${NumberFormat.getInstance().format(it)} $curr"
        }
        viewModel.totalExpenses.observe(this) {
            val curr = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvTotalExpenses.text = "${NumberFormat.getInstance().format(it)} $curr"
        }
        
        viewModel.totalFeedPurchasedKg.observe(this) {
            binding.tvTotalPurchasedLabel.text = "Cumul:\n${it.toInt()} kg"
        }

        viewModel.weeklyProduction.observe(this) { list ->
            setupLineChart(list)
        }

        viewModel.expensesByCategory.observe(this) { list ->
            setupBarChart(list)
        }
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
            
            axisLeft.textColor = textColor
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
                axisMinimum = 0f
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
        val age = viewModel.getFormattedAge(batch.chickBirthDate)
        binding.tvHensAgeHeader.text = "${batch.name} • $count poules • $age"
    }

    private fun setupClickListeners() {
        binding.cardLayingRate.setOnClickListener {
            val intent = Intent(this, AgentActivity::class.java)
            intent.putExtra("userIdString", userId)
            intent.putExtra("role", userRole)
            startActivity(intent)
        }
        binding.cardNetProfit.setOnClickListener {
            val intent = Intent(this, SalesActivity::class.java)
            intent.putExtra("role", userRole)
            intent.putExtra("userIdString", userId)
            startActivity(intent)
        }
        binding.cardExpensesChart.setOnClickListener {
            val intent = Intent(this, ExpensesActivity::class.java)
            intent.putExtra("role", userRole)
            intent.putExtra("userIdString", userId)
            startActivity(intent)
        }
        binding.cardMortality.setOnClickListener {
            val intent = Intent(this, AgentActivity::class.java)
            intent.putExtra("userIdString", userId)
            intent.putExtra("role", userRole)
            intent.putExtra("openTab", 1) // 1 for mortality
            startActivity(intent)
        }
        binding.cardStockFeed.setOnClickListener {
             val intent = Intent(this, ExpensesActivity::class.java)
             intent.putExtra("role", userRole)
             intent.putExtra("userIdString", userId)
             startActivity(intent)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> {}
            R.id.nav_users -> {
                val intent = Intent(this, ResponsableActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_collect -> {
                val intent = Intent(this, AgentActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_batches -> {
                val intent = Intent(this, BatchActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_vaccines -> {
                val intent = Intent(this, VaccineActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_expenses -> {
                val intent = Intent(this, ExpensesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_sales -> {
                val intent = Intent(this, SalesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_mortality -> {
                val intent = Intent(this, MortalityActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_logout -> {
                FirebaseAuth.getInstance().signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}
