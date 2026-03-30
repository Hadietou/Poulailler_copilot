package com.example.poulailler_copilot.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.R
import com.example.poulailler_copilot.data.CategoryExpense
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
import java.util.Date
import java.util.Locale

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityDashboardBinding
    private val viewModel: DashboardViewModel by viewModels()
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private val numberFormat = NumberFormat.getInstance(Locale.getDefault())
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
    }

    private fun formatNumber(number: Any): String {
        return numberFormat.format(number)
    }

    private fun setupClickListeners() {
        binding.cardMortality.setOnClickListener {
            startActivity(Intent(this, MortalityActivity::class.java).apply {
                putExtra("role", userRole)
                putExtra("userIdString", userId)
            })
        }

        binding.cardStockFeed.setOnClickListener {
            startActivity(Intent(this, ExpensesActivity::class.java).apply {
                putExtra("role", userRole)
                putExtra("userIdString", userId)
            })
        }

        binding.cardLayingRate.setOnClickListener {
            startActivity(Intent(this, AgentActivity::class.java).apply {
                putExtra("role", userRole)
                putExtra("userIdString", userId)
            })
        }

        binding.cardNetProfit.setOnClickListener {
            startActivity(Intent(this, SalesActivity::class.java).apply {
                putExtra("role", userRole)
                putExtra("userIdString", userId)
            })
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
                    tvUsername.text = profile?.username?.uppercase() ?: "UTILISATEUR"
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
        
        val isResponsable = userRole == "RESPONSABLE"
        binding.titleFinance.visibility = if (isResponsable) View.VISIBLE else View.GONE
        binding.cardNetProfit.visibility = if (isResponsable) View.VISIBLE else View.GONE
        binding.cardExpensesChart.visibility = if (isResponsable) View.VISIBLE else View.GONE
    }

    private fun observeViewModel() {
        viewModel.userName.observe(this) { name ->
            binding.tvWelcome.text = "BONJOUR ${name.uppercase()} !"
        }

        viewModel.farmInfo.observe(this) { info ->
            val weeks = info?.let { viewModel.calculateWeeksAge(it.chickBirthDate) } ?: 0
            binding.tvHensAgeHeader.text = "Lot actuel : $weeks semaines"
        }

        viewModel.layingRate.observe(this) { rate ->
            val progress = rate.toInt().coerceIn(0, 100)
            binding.progressLayingRate.setProgress(progress, true)
            binding.tvLayingRateValue.text = String.format(Locale.getDefault(), "%d%%", progress)
        }

        viewModel.layingTrend.observe(this) { trend ->
            val color = if (trend >= 0) ContextCompat.getColor(this, R.color.emerald_soft) 
                        else ContextCompat.getColor(this, R.color.earthy_orange)
            binding.tvLayingTrend.setTextColor(color)
            val sign = if (trend >= 0) "+" else ""
            binding.tvLayingTrend.text = String.format(Locale.getDefault(), "%s%.1f%% vs hier", sign, trend)
        }

        viewModel.todayEggs.observe(this) { count ->
            binding.tvTodayEggsDetail.text = "$count œufs collectés aujourd'hui"
        }

        viewModel.totalMortalityCount.observe(this) { count ->
            binding.tvTotalMortality.text = formatNumber(count)
        }

        viewModel.totalFeedKg.observe(this) { qty ->
            binding.tvTotalFeed.text = "${formatNumber(qty)} kg"
        }

        viewModel.feedAutonomyDays.observe(this) { days ->
            binding.tvFeedAutonomy.text = "$days jours restants"
            binding.tvFeedAutonomy.setTextColor(if (days < 3) ContextCompat.getColor(this, R.color.error) 
                                               else ContextCompat.getColor(this, R.color.earthy_orange))
        }

        viewModel.netProfit.observe(this) { profit ->
            val currency = viewModel.farmInfo.value?.currency ?: "MRU"
            binding.tvNetProfit.text = "${formatNumber(profit)} $currency"
            binding.tvNetProfit.setTextColor(if (profit >= 0) ContextCompat.getColor(this, R.color.emerald_soft) 
                                            else ContextCompat.getColor(this, R.color.error))
        }

        viewModel.weeklyProduction.observe(this) { data ->
            updateProductionChart(data)
        }

        viewModel.expensesByCategory.observe(this) { data ->
            updateExpensesBarChart(data)
        }
    }

    private fun updateProductionChart(data: List<Pair<Long, Int>>) {
        if (data.isEmpty()) return
        val visibleTextColor = if (isDarkMode()) Color.WHITE else Color.BLACK

        val entries = data.mapIndexed { index, pair -> Entry(index.toFloat(), pair.second.toFloat()) }
        val dataSet = LineDataSet(entries, "Production").apply {
            color = ContextCompat.getColor(this@DashboardActivity, R.color.emerald_soft)
            setCircleColor(color)
            lineWidth = 3f
            setDrawFilled(true)
            fillColor = color
            fillAlpha = 30
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(true)
            valueTextColor = visibleTextColor
            valueTextSize = 10f
        }

        val dates = data.map { SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(it.first)) }
        binding.productionChart.apply {
            this.data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(dates)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.textColor = visibleTextColor
            
            axisLeft.setDrawGridLines(true)
            axisLeft.textColor = visibleTextColor
            
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.textColor = visibleTextColor
            animateX(800)
            invalidate()
        }
    }

    private fun updateExpensesBarChart(data: List<CategoryExpense>) {
        if (data.isEmpty()) return
        val visibleTextColor = if (isDarkMode()) Color.WHITE else Color.BLACK

        val entries = data.mapIndexed { index, item -> BarEntry(index.toFloat(), item.totalAmount.toFloat()) }
        val dataSet = BarDataSet(entries, "Dépenses").apply {
            colors = ColorTemplate.MATERIAL_COLORS.toList()
            valueTextColor = visibleTextColor
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return formatNumber(value.toInt())
                }
            }
        }

        val categories = data.map { it.category }
        binding.expensesBarChart.apply {
            this.data = BarData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(categories)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.textColor = visibleTextColor
            xAxis.granularity = 1f
            
            axisLeft.setDrawGridLines(true)
            axisLeft.textColor = visibleTextColor
            axisLeft.axisMinimum = 0f
            
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.textColor = visibleTextColor
            animateY(1000)
            invalidate()
        }
    }

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> {}
            R.id.nav_users -> startActivity(Intent(this, ResponsableActivity::class.java).apply {
                putExtra("role", userRole); putExtra("userIdString", userId)
            })
            R.id.nav_farm_info -> startActivity(Intent(this, FarmInfoActivity::class.java).apply {
                putExtra("role", userRole); putExtra("userIdString", userId)
            })
            R.id.nav_collect -> startActivity(Intent(this, AgentActivity::class.java).apply {
                putExtra("role", userRole); putExtra("userIdString", userId)
            })
            R.id.nav_vaccines -> startActivity(Intent(this, VaccineActivity::class.java).apply {
                putExtra("role", userRole); putExtra("userIdString", userId)
            })
            R.id.nav_expenses -> startActivity(Intent(this, ExpensesActivity::class.java).apply {
                putExtra("role", userRole); putExtra("userIdString", userId)
            })
            R.id.nav_sales -> startActivity(Intent(this, SalesActivity::class.java).apply {
                putExtra("role", userRole); putExtra("userIdString", userId)
            })
            R.id.nav_mortality -> startActivity(Intent(this, MortalityActivity::class.java).apply {
                putExtra("role", userRole); putExtra("userIdString", userId)
            })
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
