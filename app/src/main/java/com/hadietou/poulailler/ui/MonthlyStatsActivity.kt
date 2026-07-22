package com.hadietou.poulailler.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.hadietou.poulailler.R
import com.hadietou.poulailler.databinding.ActivityMonthlyStatsBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MonthlyStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonthlyStatsBinding
    private val repository = FirebaseRepository()
    private var statsType: String = "COLLECTION"
    private var batchId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonthlyStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        statsType = intent.getStringExtra("statsType") ?: "COLLECTION"
        batchId = intent.getStringExtra("selectedBatchId")

        setupToolbar()
        loadData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        val title = when (statsType) {
            "COLLECTION" -> "Collecte d'œufs (Mois)"
            "SALES" -> "Ventes d'œufs (Tablettes / Mois)"
            "REVENUE" -> "Recette (Mois)"
            "BROKEN" -> "Œufs cassés (Mois)"
            "MORTALITY" -> "Mortalité (Mois)"
            "EXPENSES" -> "Dépenses (Mois)"
            else -> "Statistiques Mensuelles"
        }
        binding.tvChartTitle.text = title
        supportActionBar?.title = ""
    }

    private fun loadData() {
        lifecycleScope.launch {
            when (statsType) {
                "COLLECTION", "BROKEN" -> observeCollection()
                "SALES" -> observeSales()
                "REVENUE" -> observeRevenue()
                "MORTALITY" -> observeMortality()
                "EXPENSES" -> observeExpenses()
            }
        }
    }

    private fun observeCollection() {
        lifecycleScope.launch {
            if (statsType == "COLLECTION") {
                combine(repository.getEggEntriesFlow(), repository.getSalesFlow()) { entries, sales ->
                    entries to sales
                }.collectLatest { (entries, sales) ->
                    val filteredEntries = entries.filter { (batchId == null || it.batchId == batchId) }
                    val currentMonthEntries = filteredEntries.filter { isCurrentMonth(it.date) }
                    
                    val dailyData = aggregateDaily(currentMonthEntries.map { it.date to it.eggsCount.toDouble() })
                    setupLineChart(dailyData, "Œufs")

                    val totalMensuel = currentMonthEntries.sumOf { it.eggsCount }
                    val totalGeneral = filteredEntries.sumOf { it.eggsCount }
                    
                    val filteredSales = sales.filter { (batchId == null || it.batchId == batchId) }
                    val totalSold = filteredSales.sumOf { it.quantity }
                    val totalBroken = filteredEntries.sumOf { it.brokenEggsCount }
                    val totalAvailable = totalGeneral - totalSold - totalBroken

                    binding.tvStatsSummary.text = "Total mensuel : $totalMensuel"
                    
                    binding.tvTotalGeneral.visibility = View.VISIBLE
                    binding.tvTotalGeneral.text = "Total général : $totalGeneral"
                    
                    binding.tvTotalAvailable.visibility = View.VISIBLE
                    val availableTablettes = totalAvailable / 30
                    val remainingEggs = totalAvailable % 30
                    binding.tvTotalAvailable.text = "Total d'œufs disponible : $totalAvailable ($availableTablettes Tab et $remainingEggs)"
                }
            } else {
                repository.getEggEntriesFlow().collectLatest { entries ->
                    val filtered = entries.filter { (batchId == null || it.batchId == batchId) && isCurrentMonth(it.date) }
                    val dailyData = aggregateDaily(filtered.map { it.date to it.brokenEggsCount.toDouble() })
                    setupLineChart(dailyData, "Cassés")
                    val totalMensuel = filtered.sumOf { it.brokenEggsCount }
                    binding.tvStatsSummary.text = "Total mensuel : $totalMensuel"
                    binding.tvTotalGeneral.visibility = View.GONE
                    binding.tvTotalAvailable.visibility = View.GONE
                }
            }
        }
    }

    private fun observeSales() {
        lifecycleScope.launch {
            repository.getSalesFlow().collectLatest { sales ->
                val filtered = sales.filter { (batchId == null || it.batchId == batchId) && isCurrentMonth(it.date) }
                // Convert to tablettes (quantity / 30)
                val dailyData = aggregateDaily(filtered.map { it.date to (it.quantity.toDouble() / 30.0) })
                setupLineChart(dailyData, "Tablettes vendues")
                binding.tvTotalGeneral.visibility = View.GONE
                binding.tvTotalAvailable.visibility = View.GONE
            }
        }
    }

    private fun observeRevenue() {
        lifecycleScope.launch {
            repository.getSalesFlow().collectLatest { sales ->
                val filtered = sales.filter { (batchId == null || it.batchId == batchId) && isCurrentMonth(it.date) }
                val dailyData = aggregateDaily(filtered.map { it.date to it.totalPrice })
                setupLineChart(dailyData, "Recette")
                binding.tvTotalGeneral.visibility = View.GONE
                binding.tvTotalAvailable.visibility = View.GONE
            }
        }
    }

    private fun observeMortality() {
        lifecycleScope.launch {
            repository.getMortalityFlow().collectLatest { list ->
                val filtered = list.filter { (batchId == null || it.batchId == batchId) && isCurrentMonth(it.date) }
                val dailyData = aggregateDaily(filtered.map { it.date to it.count.toDouble() })
                setupLineChart(dailyData, "Morts")
                binding.tvTotalGeneral.visibility = View.GONE
                binding.tvTotalAvailable.visibility = View.GONE
            }
        }
    }

    private fun observeExpenses() {
        lifecycleScope.launch {
            repository.getExpensesFlow().collectLatest { list ->
                val filtered = list.filter { (batchId == null || it.batchId == batchId) && isCurrentMonth(it.date) }
                val dailyData = aggregateDaily(filtered.map { it.date to it.amount })
                setupLineChart(dailyData, "Dépenses")
                
                val categoryData = filtered.groupBy { it.category }.map { it.key to it.value.sumOf { e -> e.amount } }
                if (categoryData.isNotEmpty()) {
                    binding.monthlyBarChart.visibility = View.VISIBLE
                    setupBarChart(categoryData)
                }
                binding.tvTotalGeneral.visibility = View.GONE
                binding.tvTotalAvailable.visibility = View.GONE
            }
        }
    }

    private fun isCurrentMonth(dateMs: Long): Boolean {
        val cal = Calendar.getInstance()
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        val entryCal = Calendar.getInstance()
        entryCal.timeInMillis = dateMs
        return entryCal.get(Calendar.MONTH) == currentMonth && entryCal.get(Calendar.YEAR) == currentYear
    }

    private fun aggregateDaily(data: List<Pair<Long, Double>>): List<Pair<Long, Double>> {
        val cal = Calendar.getInstance()
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        
        val map = mutableMapOf<Int, Double>()
        data.forEach { (date, value) ->
            val dCal = Calendar.getInstance()
            dCal.timeInMillis = date
            val day = dCal.get(Calendar.DAY_OF_MONTH)
            map[day] = (map[day] ?: 0.0) + value
        }
        
        val result = mutableListOf<Pair<Long, Double>>()
        val resCal = Calendar.getInstance()
        resCal.set(Calendar.MONTH, currentMonth)
        resCal.set(Calendar.YEAR, currentYear)
        for (day in 1..daysInMonth) {
            resCal.set(Calendar.DAY_OF_MONTH, day)
            result.add(resCal.timeInMillis to (map[day] ?: 0.0))
        }
        return result
    }

    private fun setupLineChart(data: List<Pair<Long, Double>>, label: String) {
        val entries = data.mapIndexed { index, pair ->
            Entry((index + 1).toFloat(), pair.second.toFloat())
        }

        val color = when(statsType) {
            "MORTALITY", "BROKEN" -> getColor(R.color.error)
            "SALES", "EXPENSES" -> getColor(R.color.earthy_orange)
            else -> getColor(R.color.primary)
        }

        val dataSet = LineDataSet(entries, label).apply {
            this.color = color
            setCircleColor(color)
            lineWidth = 2f
            setDrawFilled(true)
            fillAlpha = 50
            fillColor = color
            setDrawValues(false)
        }

        binding.monthlyLineChart.apply {
            this.data = LineData(dataSet)
            description.isEnabled = false
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.setLabelCount(10, false)
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = value.toInt().toString()
            }
            axisRight.isEnabled = false
            animateX(1000)
            invalidate()
        }
        
        val total = data.sumOf { it.second }
        val unit = when (statsType) {
            "REVENUE", "EXPENSES" -> " MRU"
            "SALES" -> " Tab"
            else -> ""
        }
        val format = if (statsType == "SALES") "%.1f" else "%.0f"
        binding.tvStatsSummary.text = "Total mensuel : ${String.format(Locale.getDefault(), format, total)}$unit"
    }

    private fun setupBarChart(data: List<Pair<String, Double>>) {
        val entries = data.mapIndexed { index, pair ->
            BarEntry(index.toFloat(), pair.second.toFloat())
        }

        val dataSet = BarDataSet(entries, "Par catégorie").apply {
            colors = listOf(getColor(R.color.primary), getColor(R.color.earthy_orange), getColor(R.color.accent_blue))
            valueTextSize = 10f
        }

        binding.monthlyBarChart.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = if (value.toInt() in data.indices) data[value.toInt()].first else ""
            }
            axisRight.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }
}
