package com.example.poulailler_copilot.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.R
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.EggEntry
import com.example.poulailler_copilot.data.FarmInfo
import com.example.poulailler_copilot.databinding.ActivityDashboardBinding
import com.example.poulailler_copilot.repository.FirebaseRepository
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private val numberFormat = NumberFormat.getInstance(Locale.getDefault())
    private var currency: String = "MRU"
    private val firebaseRepo = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid

        setupNavigation()
        updateUIBasedOnRole()
        observeViewModel()
        setupClickListeners()
        
        checkFirstLogin()
        
        binding.toolbar.setOnClickListener {
            generateTestData()
        }
    }

    private fun formatNumber(number: Any): String {
        return numberFormat.format(number)
    }

    private fun setupClickListeners() {
        binding.cardMortality.setOnClickListener {
            startActivity(Intent(this, MortalityHistoryActivity::class.java))
        }

        binding.cardStockFeed.setOnClickListener {
            startActivity(Intent(this, ExpenseHistoryActivity::class.java))
        }

        binding.layoutTotalCollected.setOnClickListener {
            startActivity(Intent(this, CollectionHistoryActivity::class.java))
        }

        binding.layoutTotalBroken.setOnClickListener {
            startActivity(Intent(this, CollectionHistoryActivity::class.java))
        }

        binding.layoutTotalSold.setOnClickListener {
            startActivity(Intent(this, SalesHistoryActivity::class.java))
        }

        binding.cardSalesRevenue.setOnClickListener {
            startActivity(Intent(this, SalesHistoryActivity::class.java))
        }

        binding.cardTotalExpenses.setOnClickListener {
            startActivity(Intent(this, ExpenseHistoryActivity::class.java))
        }
    }

    private fun checkFirstLogin() {
        if (userRole == "RESPONSABLE") {
            lifecycleScope.launch {
                val info = firebaseRepo.getFarmInfo()
                if (info == null || info.farmName.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@DashboardActivity, FarmInfoActivity::class.java)
                        intent.putExtra("role", userRole)
                        intent.putExtra("userIdString", userId)
                        startActivity(intent)
                    }
                }
            }
        }
    }

    private fun generateTestData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@DashboardActivity)
            if (db.farmInfoDao().getInfo() == null) {
                db.farmInfoDao().upsert(FarmInfo(id = 1, farmName = "Ma Ferme Test", hensCount = 1500))
            }

            var currentEggs = 100.0
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -9)

            for (i in 0 until 10) {
                val entry = EggEntry(
                    userId = 0, // Fallback for local DB
                    date = calendar.timeInMillis,
                    eggsCount = currentEggs.toInt(),
                    brokenEggsCount = (currentEggs * 0.02).toInt(),
                    remarks = "Auto-généré jour ${i+1}"
                )
                db.eggEntryDao().insert(entry)
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                currentEggs *= 1.05
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@DashboardActivity, "Données de test générées !", Toast.LENGTH_SHORT).show()
                viewModel.loadData()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadData()
        updateNavHeader()
    }

    private fun setupNavigation() {
        setSupportActionBar(binding.toolbar)
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener(this)
        updateNavHeader()
    }

    private fun updateNavHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val tvUsername = headerView.findViewById<TextView>(R.id.tvUsername)
        val tvUserRole = headerView.findViewById<TextView>(R.id.tvUserRole)

        lifecycleScope.launch {
            val uid = userId ?: FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                val profile = firebaseRepo.getUserProfile(uid)
                withContext(Dispatchers.Main) {
                    tvUsername.text = profile?.username ?: "Utilisateur"
                    tvUserRole.text = userRole
                }
            }
        }
    }

    private fun updateUIBasedOnRole() {
        val menu = binding.navigationView.menu
        menu.findItem(R.id.nav_users).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_expenses).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_vaccines).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_farm_info).isVisible = userRole == "RESPONSABLE"
        
        binding.layoutFinanceSummary.visibility = if (userRole == "RESPONSABLE") View.VISIBLE else View.GONE
        binding.cardStockFeed.visibility = if (userRole == "RESPONSABLE") View.VISIBLE else View.GONE
    }

    private fun observeViewModel() {
        viewModel.farmInfo.observe(this) { info ->
            binding.tvHensAge.text = info?.let { viewModel.calculateWeeksAge(it.chickBirthDate).toString() } ?: "--"
            currency = info?.currency ?: "MRU"
            // Update UI with new currency
            viewModel.totalSales.value?.let { binding.tvSalesRevenue.text = "${formatNumber(it)} $currency" }
            viewModel.totalExpenses.value?.let { binding.tvTotalExpenses.text = "${formatNumber(it)} $currency" }
            viewModel.expensesByCategory.value?.let { updateExpensesBarChart(it) }
        }

        viewModel.effectiveHensCount.observe(this) { count ->
            binding.tvHensCount.text = formatNumber(count)
            viewModel.last5DaysEntries.value?.let { updateLast5DaysTable(it) }
        }

        viewModel.totalMortalityCount.observe(this) { count ->
            binding.tvTotalMortality.text = formatNumber(count)
        }

        viewModel.todayEggs.observe(this) { count ->
            binding.tvTodayEggs.text = "${formatNumber(count)} œufs"
        }

        viewModel.layingRate.observe(this) { rate ->
            binding.tvLayingRate.text = String.format(Locale.getDefault(), "%.1f%%", rate)
        }

        viewModel.totalCollected.observe(this) { count ->
            binding.tvTotalCollected.text = formatNumber(count)
        }

        viewModel.totalBroken.observe(this) { count ->
            binding.tvTotalBroken.text = formatNumber(count)
        }

        viewModel.totalSold.observe(this) { count ->
            binding.tvTotalSold.text = formatNumber(count)
        }

        viewModel.totalRemaining.observe(this) { count ->
            binding.tvTotalRemaining.text = formatNumber(count)
        }

        viewModel.layingRate5d.observe(this) { rate ->
            binding.tvLayingRate5d.text = String.format(Locale.getDefault(), "%.1f%%", rate)
        }

        viewModel.totalSales.observe(this) { amount ->
            binding.tvSalesRevenue.text = "${formatNumber(amount)} $currency"
        }

        viewModel.totalExpenses.observe(this) { amount ->
            binding.tvTotalExpenses.text = "${formatNumber(amount)} $currency"
        }

        viewModel.last5DaysEntries.observe(this) { entries ->
            updateLast5DaysTable(entries)
        }

        viewModel.weeklyProduction.observe(this) { data ->
            updateProductionChart(data)
        }

        viewModel.totalFeedKg.observe(this) { qty ->
            binding.tvTotalFeed.text = "${formatNumber(qty)} kg"
        }

        viewModel.expensesByCategory.observe(this) { data ->
            updateExpensesBarChart(data)
        }

        viewModel.cumulativeMortalityRate.observe(this) { rate ->
            binding.tvCumulativeMortalityRate.text = String.format(Locale.getDefault(), "%.1f%%", rate)
            if (rate > 5.0) {
                binding.tvCumulativeMortalityRate.setTextColor(Color.RED)
            } else {
                binding.tvCumulativeMortalityRate.setTextColor(Color.WHITE)
            }
        }

        viewModel.brokenRate.observe(this) { rate ->
            binding.tvBrokenRate.text = String.format(Locale.getDefault(), "%.1f%%", rate)
        }

        viewModel.nextVaccine.observe(this) { vaccine ->
            binding.tvNextVaccine.text = vaccine
        }

        viewModel.feedAutonomyDays.observe(this) { days ->
            binding.tvFeedAutonomy.text = "$days jours restants"
            if (days < 3) {
                binding.tvFeedAutonomy.setTextColor(Color.RED)
            } else {
                binding.tvFeedAutonomy.setTextColor(Color.WHITE)
            }
        }
    }

    private fun updateProductionChart(data: List<Pair<Long, Int>>) {
        val entries = data.mapIndexed { index, pair -> Entry(index.toFloat(), pair.second.toFloat()) }
        val dataSet = LineDataSet(entries, "Collecte d'œufs").apply {
            color = Color.parseColor("#1E90FF")
            setCircleColor(Color.parseColor("#1E90FF"))
            lineWidth = 3f
            circleRadius = 5f
            setDrawValues(true)
            valueTextColor = Color.parseColor("#757575")
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return formatNumber(value.toInt())
                }
            }
            setDrawFilled(true)
            fillColor = Color.parseColor("#1E90FF")
            fillAlpha = 40
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val dates = data.map { SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(it.first)) }
        binding.productionChart.apply {
            this.data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(dates)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.textColor = Color.parseColor("#757575")
            
            axisLeft.setDrawGridLines(true)
            axisLeft.gridColor = Color.parseColor("#DDDDDD")
            axisLeft.textColor = Color.parseColor("#757575")
            axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return formatNumber(value.toInt())
                }
            }
            
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.textColor = Color.parseColor("#757575")
            animateX(1000)
            invalidate()
        }
    }

    private fun updateExpensesBarChart(data: List<com.example.poulailler_copilot.data.CategoryExpense>) {
        if (data.isEmpty()) {
            binding.expensesBarChart.clear()
            return
        }

        val sortedData = data.sortedByDescending { it.totalAmount }
        val total = sortedData.sumOf { it.totalAmount }

        val entries = sortedData.mapIndexed { index, item -> BarEntry(index.toFloat(), item.totalAmount.toFloat()) }
        val dataSet = BarDataSet(entries, "Dépenses").apply {
            colors = ColorTemplate.VORDIPLOM_COLORS.toList()
            valueTextSize = 11f
            valueTextColor = Color.parseColor("#616161")
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val percentage = (value / total * 100).toInt()
                    return "${formatNumber(value.toInt())} $currency ($percentage%)"
                }
            }
        }

        val categories = sortedData.map { it.category }
        binding.expensesBarChart.apply {
            this.data = BarData(dataSet).apply {
                barWidth = 0.5f
            }
            xAxis.valueFormatter = IndexAxisValueFormatter(categories)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.isGranularityEnabled = true
            xAxis.setDrawGridLines(false)
            xAxis.textColor = Color.parseColor("#757575")
            xAxis.labelRotationAngle = -30f
            
            axisLeft.setDrawGridLines(true)
            axisLeft.gridColor = Color.parseColor("#EEEEEE")
            axisLeft.textColor = Color.parseColor("#757575")
            axisLeft.axisMinimum = 0f
            axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return formatNumber(value.toInt())
                }
            }
            
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = false
            
            setFitBars(true)
            animateY(1000)
            invalidate()
        }
    }

    private fun updateLast5DaysTable(entries: List<EggEntry>) {
        binding.tableLast5Days.removeAllViews()
        if (entries.isEmpty()) return

        val hensCount = viewModel.effectiveHensCount.value ?: 1
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        val sortedEntries = entries.sortedBy { it.date }.takeLast(5)

        val rowDates = TableRow(this)
        rowDates.addView(createCell("Date", true))
        for (entry in sortedEntries) {
            rowDates.addView(createCell(sdf.format(Date(entry.date)), true))
        }
        binding.tableLast5Days.addView(rowDates)

        val rowEggs = TableRow(this)
        rowEggs.addView(createCell("Œufs", true))
        for (entry in sortedEntries) {
            rowEggs.addView(createCell(formatNumber(entry.eggsCount), false))
        }
        binding.tableLast5Days.addView(rowEggs)

        val rowRates = TableRow(this)
        rowRates.addView(createCell("Taux %", true))
        for (entry in sortedEntries) {
            val divisor = if (hensCount > 0) hensCount else 1
            val rate = (entry.eggsCount.toDouble() / divisor.toDouble()) * 100
            rowRates.addView(createCell(String.format(Locale.getDefault(), "%.1f%%", rate), false))
        }
        binding.tableLast5Days.addView(rowRates)
    }

    private fun createCell(text: String, isHeader: Boolean): TextView {
        val params = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        return TextView(this).apply {
            this.text = text
            this.layoutParams = params
            setPadding(2, 12, 2, 12)
            gravity = Gravity.CENTER
            textSize = 11f
            if (isHeader) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            setBackgroundResource(R.drawable.table_border)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> {} // Already here
            R.id.nav_users -> {
                val intent = Intent(this, ResponsableActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_farm_info -> {
                val intent = Intent(this, FarmInfoActivity::class.java)
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
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_logout -> {
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
