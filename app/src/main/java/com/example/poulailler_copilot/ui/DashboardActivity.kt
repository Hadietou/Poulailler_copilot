package com.example.poulailler_copilot.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
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
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()
    private var userRole: String = "AGENT"
    private var userId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getLongExtra("userId", -1)

        setupNavigation()
        updateUIBasedOnRole()
        observeViewModel()
        
        checkFirstLogin()
        
        binding.toolbar.setOnClickListener {
            generateTestData()
        }
    }

    private fun checkFirstLogin() {
        if (userRole == "RESPONSABLE") {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(this@DashboardActivity)
                val info = db.farmInfoDao().getInfo()
                if (info == null || info.farmName.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        startActivity(Intent(this@DashboardActivity, FarmInfoActivity::class.java))
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
                    userId = userId,
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
    }

    private fun updateUIBasedOnRole() {
        val menu = binding.navigationView.menu
        menu.findItem(R.id.nav_users).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_expenses).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_vaccines).isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_farm_info)?.isVisible = userRole == "RESPONSABLE"
        
        binding.layoutFinanceSummary.visibility = if (userRole == "RESPONSABLE") View.VISIBLE else View.GONE
    }

    private fun observeViewModel() {
        viewModel.farmInfo.observe(this) { info ->
            binding.tvHensCount.text = info?.hensCount?.toString() ?: "--"
            binding.tvHensAge.text = info?.let { viewModel.calculateWeeksAge(it.chickBirthDate).toString() } ?: "--"
        }

        viewModel.todayEggs.observe(this) { count ->
            binding.tvTodayEggs.text = "$count œufs"
        }

        viewModel.layingRate.observe(this) { rate ->
            binding.tvLayingRate.text = String.format(Locale.getDefault(), "%.1f%%", rate)
        }

        viewModel.totalCollected.observe(this) { count ->
            binding.tvTotalCollected.text = count.toString()
        }

        viewModel.totalBroken.observe(this) { count ->
            binding.tvTotalBroken.text = count.toString()
        }

        viewModel.totalSold.observe(this) { count ->
            binding.tvTotalSold.text = count.toString()
        }

        viewModel.totalRemaining.observe(this) { count ->
            binding.tvTotalRemaining.text = count.toString()
        }

        viewModel.layingRate5d.observe(this) { rate ->
            binding.tvLayingRate5d.text = String.format(Locale.getDefault(), "%.1f%%", rate)
        }

        viewModel.totalSales.observe(this) { amount ->
            binding.tvSalesRevenue.text = String.format(Locale.getDefault(), "%.2f $", amount)
        }

        viewModel.totalExpenses.observe(this) { amount ->
            binding.tvTotalExpenses.text = String.format(Locale.getDefault(), "%.2f $", amount)
        }

        viewModel.last5DaysEntries.observe(this) { entries ->
            updateLast5DaysTable(entries)
        }

        viewModel.weeklyProduction.observe(this) { data ->
            updateProductionChart(data)
        }

        viewModel.totalFeedKg.observe(this) { qty ->
            binding.tvTotalFeed.text = String.format(Locale.getDefault(), "%.1f kg", qty)
        }

        viewModel.expensesByCategory.observe(this) { data ->
            updateExpensesBarChart(data)
        }

        viewModel.lastVaccines.observe(this) { vaccines ->
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val displayList = vaccines.map { "${sdf.format(Date(it.date))} - ${it.name}" }
            binding.lvRecentVaccines.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
        }
    }

    private fun updateProductionChart(data: List<Pair<Long, Int>>) {
        val entries = data.mapIndexed { index, pair -> Entry(index.toFloat(), pair.second.toFloat()) }
        val dataSet = LineDataSet(entries, "Collecte d'œufs").apply {
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(true)
            valueTextColor = Color.WHITE
            valueTextSize = 10f
        }

        val dates = data.map { SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(it.first)) }
        binding.productionChart.apply {
            this.data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(dates)
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.textColor = Color.WHITE
            axisLeft.textColor = Color.WHITE
            axisRight.textColor = Color.WHITE
            legend.textColor = Color.WHITE
            description.isEnabled = false
            animateX(1000)
            invalidate()
        }
    }

    private fun updateExpensesBarChart(data: List<com.example.poulailler_copilot.data.CategoryExpense>) {
        val entries = data.mapIndexed { index, item -> BarEntry(index.toFloat(), item.totalAmount.toFloat()) }
        val dataSet = BarDataSet(entries, "Montant par catégorie").apply {
            colors = ColorTemplate.JOYFUL_COLORS.toList()
            valueTextColor = Color.BLACK
            valueTextSize = 12f
        }

        val categories = data.map { it.category }
        binding.expensesBarChart.apply {
            this.data = BarData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(categories)
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.isGranularityEnabled = true
            xAxis.textColor = Color.BLACK
            axisLeft.textColor = Color.BLACK
            axisRight.textColor = Color.BLACK
            description.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    private fun updateLast5DaysTable(entries: List<EggEntry>) {
        binding.tableLast5Days.removeAllViews()
        if (entries.isEmpty()) return

        val hensCount = viewModel.farmInfo.value?.hensCount ?: 1
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
            rowEggs.addView(createCell(entry.eggsCount.toString(), false))
        }
        binding.tableLast5Days.addView(rowEggs)

        val rowRates = TableRow(this)
        rowRates.addView(createCell("Taux %", true))
        for (entry in sortedEntries) {
            val rate = (entry.eggsCount.toDouble() / hensCount.toDouble()) * 100
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
            R.id.nav_users -> startActivity(Intent(this, ResponsableActivity::class.java))
            R.id.nav_farm_info -> startActivity(Intent(this, FarmInfoActivity::class.java))
            R.id.nav_collect -> {
                val intent = Intent(this, AgentActivity::class.java)
                intent.putExtra("userId", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_vaccines -> startActivity(Intent(this, VaccineActivity::class.java))
            R.id.nav_expenses -> startActivity(Intent(this, ExpensesActivity::class.java))
            R.id.nav_sales -> {
                val intent = Intent(this, SalesActivity::class.java)
                intent.putExtra("userId", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_logout -> finish()
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
