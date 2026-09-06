package com.hadietou.poulailler.ui

import android.graphics.Color
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
import java.text.NumberFormat
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
            "COLLECTION" -> "Suivi de la production d'œufs"
            "SALES" -> "Suivi des ventes"
            "REVENUE" -> "Recette (Mois)"
            "BROKEN" -> "Œufs cassés (Mois)"
            "MORTALITY" -> "Mortalité (Mois)"
            "EXPENSES" -> "Suivi des dépenses"
            else -> "Statistiques Mensuelles"
        }
        binding.tvChartTitle.text = title
        supportActionBar?.title = ""

        val summaryLabel = when (statsType) {
            "COLLECTION" -> "Production Mensuelle d'œufs"
            "BROKEN" -> "Œufs cassés ce mois"
            "SALES" -> "Ventes ce mois"
            "REVENUE" -> "Recette ce mois"
            "MORTALITY" -> "Mortalité ce mois"
            "EXPENSES" -> "Dépenses ce mois"
            else -> "Total mensuel"
        }
        binding.tvStatsSummaryLabel.text = summaryLabel.uppercase(Locale.getDefault())
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

                    val totalMensuelTablettes = totalMensuel / 30
                    val totalGeneralTablettes = totalGeneral / 30
                    val numberFormat = NumberFormat.getInstance(Locale.getDefault())

                    binding.tvStatsSummary.text = totalMensuelTablettes.toString()
                    binding.tvStatsSummaryUnit.visibility = View.VISIBLE
                    binding.tvStatsSummarySubtitle.visibility = View.VISIBLE
                    binding.tvStatsSummarySubtitle.text = "≈ ${numberFormat.format(totalMensuel)} œufs"

                    binding.cardTotalGeneral.visibility = View.VISIBLE
                    binding.tvTotalGeneral.text = totalGeneralTablettes.toString()
                    binding.tvTotalGeneralSubtitle.text = "≈ ${numberFormat.format(totalGeneral)} œufs"

                    binding.cardTotalAvailable.visibility = View.VISIBLE
                    val availableTablettes = totalAvailable / 30
                    val remainingEggs = totalAvailable % 30
                    binding.tvTotalAvailable.text = availableTablettes.toString()
                    binding.tvTotalAvailableSubtitle.text = "${numberFormat.format(totalAvailable)} œufs (dont $remainingEggs hors plateau)"

                    val totalBrokenMensuel = currentMonthEntries.sumOf { it.brokenEggsCount }
                    binding.cardBrokenEggs.visibility = View.VISIBLE
                    binding.tvBrokenMonthly.text = numberFormat.format(totalBrokenMensuel)
                    binding.tvBrokenGeneral.text = numberFormat.format(totalBroken)

                    val historicalData = aggregateByMonth(filteredEntries.map { it.date to (it.eggsCount.toDouble() / 30.0) })
                    setupHistoricalBarChart(historicalData, "Tablettes collectées / mois")
                }
            } else {
                repository.getEggEntriesFlow().collectLatest { entries ->
                    val filteredAll = entries.filter { batchId == null || it.batchId == batchId }
                    val filteredCurrent = filteredAll.filter { isCurrentMonth(it.date) }
                    
                    val dailyData = aggregateDaily(filteredCurrent.map { it.date to it.brokenEggsCount.toDouble() })
                    setupLineChart(dailyData, "Cassés")
                    val totalMensuel = filteredCurrent.sumOf { it.brokenEggsCount }
                    binding.tvStatsSummary.text = NumberFormat.getInstance(Locale.getDefault()).format(totalMensuel)
                    binding.cardTotalGeneral.visibility = View.GONE
                    binding.cardTotalAvailable.visibility = View.GONE

                    val historicalData = aggregateByMonth(filteredAll.map { it.date to it.brokenEggsCount.toDouble() })
                    setupHistoricalBarChart(historicalData, "Œufs cassés / mois")
                }
            }
        }
    }

    private fun observeSales() {
        lifecycleScope.launch {
            repository.getSalesFlow().collectLatest { sales ->
                val filteredAll = sales.filter { batchId == null || it.batchId == batchId }
                val filteredCurrent = filteredAll.filter { isCurrentMonth(it.date) }

                val dailyData = aggregateDaily(filteredCurrent.map { it.date to (it.quantity.toDouble() / 30.0) })
                setupLineChart(dailyData, "Tablettes vendues")
                binding.cardTotalGeneral.visibility = View.GONE
                binding.cardTotalAvailable.visibility = View.GONE

                val historicalData = aggregateByMonth(filteredAll.map { it.date to (it.quantity.toDouble() / 30.0) })
                setupHistoricalBarChart(historicalData, "Tablettes vendues / mois")

                // Case "Vente Total" : total toutes périodes + détail par prix de vente
                // (car toutes les tablettes ne sont pas vendues au même prix)
                val numberFormat = NumberFormat.getInstance(Locale.getDefault())
                binding.cardSalesTotal.visibility = View.VISIBLE

                val totalQuantityAll = filteredAll.sumOf { it.quantity }
                val totalRevenueAll = filteredAll.sumOf { it.totalPrice }
                binding.tvSalesTotalValue.text = (totalQuantityAll / 30).toString()
                binding.tvSalesTotalSubtitle.text = "≈ ${numberFormat.format(totalRevenueAll.toInt())} MRU de recette totale"
                binding.tvSalesPriceBreakdown.text = buildPriceBreakdown(filteredAll, numberFormat)

                // Même détail, mais uniquement pour les ventes du mois en cours
                if (filteredCurrent.isEmpty()) {
                    binding.dividerStatsSummary.visibility = View.GONE
                    binding.tvStatsSummaryBreakdownLabel.visibility = View.GONE
                    binding.tvStatsSummaryBreakdown.visibility = View.GONE
                } else {
                    binding.dividerStatsSummary.visibility = View.VISIBLE
                    binding.tvStatsSummaryBreakdownLabel.visibility = View.VISIBLE
                    binding.tvStatsSummaryBreakdownLabel.text = "DÉTAIL PAR PRIX DE VENTE"
                    binding.tvStatsSummaryBreakdown.visibility = View.VISIBLE
                    binding.tvStatsSummaryBreakdown.text = buildPriceBreakdown(filteredCurrent, numberFormat)
                }
            }
        }
    }

    /** Regroupe les ventes par prix de plateau et retourne un résumé "X plateaux à Y MRU/plateau" par ligne. */
    private fun buildPriceBreakdown(sales: List<com.hadietou.poulailler.data.EggSale>, numberFormat: NumberFormat): String {
        val breakdown = sales
            .groupBy { Math.round(it.pricePerUnit * 30).toInt() }
            .map { (trayPrice, list) -> trayPrice to (list.sumOf { it.quantity } / 30) }
            .sortedByDescending { it.second }
        return if (breakdown.isEmpty()) {
            "Aucune vente enregistrée"
        } else {
            breakdown.joinToString("\n") { (trayPrice, tablettes) ->
                "$tablettes plateaux à ${numberFormat.format(trayPrice)} MRU/plateau"
            }
        }
    }

    private fun observeRevenue() {
        lifecycleScope.launch {
            repository.getSalesFlow().collectLatest { sales ->
                val filteredAll = sales.filter { batchId == null || it.batchId == batchId }
                val filteredCurrent = filteredAll.filter { isCurrentMonth(it.date) }
                
                val dailyData = aggregateDaily(filteredCurrent.map { it.date to it.totalPrice })
                setupLineChart(dailyData, "Recette")
                binding.cardTotalGeneral.visibility = View.GONE
                binding.cardTotalAvailable.visibility = View.GONE

                val historicalData = aggregateByMonth(filteredAll.map { it.date to it.totalPrice })
                setupHistoricalBarChart(historicalData, "Recette / mois")
            }
        }
    }

    private fun observeMortality() {
        lifecycleScope.launch {
            repository.getMortalityFlow().collectLatest { list ->
                val filteredAll = list.filter { batchId == null || it.batchId == batchId }
                val filteredCurrent = filteredAll.filter { isCurrentMonth(it.date) }
                
                val dailyData = aggregateDaily(filteredCurrent.map { it.date to it.count.toDouble() })
                setupLineChart(dailyData, "Morts")
                binding.cardTotalGeneral.visibility = View.GONE
                binding.cardTotalAvailable.visibility = View.GONE

                val historicalData = aggregateByMonth(filteredAll.map { it.date to it.count.toDouble() })
                setupHistoricalBarChart(historicalData, "Mortalité / mois")
            }
        }
    }

    private fun observeExpenses() {
        lifecycleScope.launch {
            combine(repository.getExpensesFlow(), repository.getSalesFlow()) { expenses, sales ->
                expenses to sales
            }.collectLatest { (expenses, sales) ->
                val filteredAllExp = expenses.filter { batchId == null || it.batchId == batchId }
                val filteredAllSales = sales.filter { batchId == null || it.batchId == batchId }
                
                val filteredCurrent = filteredAllExp.filter { isCurrentMonth(it.date) }
                
                val dailyData = aggregateDaily(filteredCurrent.map { it.date to it.amount })
                setupLineChart(dailyData, "Dépenses")
                
                val categoryData = filteredCurrent.groupBy { it.category }.map { it.key to it.value.sumOf { e -> e.amount } }
                if (categoryData.isNotEmpty()) {
                    binding.monthlyBarChart.visibility = View.VISIBLE
                    setupBarChart(categoryData)
                }
                binding.cardTotalGeneral.visibility = View.GONE
                binding.cardTotalAvailable.visibility = View.GONE

                val historicalExpData = aggregateByMonth(filteredAllExp.map { it.date to it.amount })
                val historicalSalesData = aggregateByMonth(filteredAllSales.map { it.date to it.totalPrice })

                setupHistoricalComparisonChart(historicalExpData, historicalSalesData)

                // Détail par catégorie des dépenses du mois, dans la case résumé dynamique
                val numberFormat = NumberFormat.getInstance(Locale.getDefault())
                if (filteredCurrent.isEmpty()) {
                    binding.dividerStatsSummary.visibility = View.GONE
                    binding.tvStatsSummaryBreakdownLabel.visibility = View.GONE
                    binding.tvStatsSummaryBreakdown.visibility = View.GONE
                } else {
                    binding.dividerStatsSummary.visibility = View.VISIBLE
                    binding.tvStatsSummaryBreakdownLabel.visibility = View.VISIBLE
                    binding.tvStatsSummaryBreakdownLabel.text = "DÉTAIL PAR CATÉGORIE"
                    binding.tvStatsSummaryBreakdown.visibility = View.VISIBLE
                    binding.tvStatsSummaryBreakdown.text = buildExpenseBreakdown(filteredCurrent, numberFormat)
                }

                // Case "Dépenses Total" : total toutes périodes + détail par catégorie
                binding.cardExpensesTotal.visibility = View.VISIBLE
                val totalExpensesAll = filteredAllExp.sumOf { it.amount }
                binding.tvExpensesTotalValue.text = "≈ ${numberFormat.format(totalExpensesAll.toInt())} MRU"
                binding.tvExpensesTotalBreakdown.text = buildExpenseBreakdown(filteredAllExp, numberFormat)
            }
        }
    }

    /** Regroupe les dépenses par catégorie et retourne un résumé "Catégorie : X MRU (Y%)" par ligne. */
    private fun buildExpenseBreakdown(expenses: List<com.hadietou.poulailler.data.Expense>, numberFormat: NumberFormat): String {
        val total = expenses.sumOf { it.amount }
        val breakdown = expenses
            .groupBy { it.category }
            .map { (category, list) -> category to list.sumOf { it.amount } }
            .sortedByDescending { it.second }
        return if (breakdown.isEmpty()) {
            "Aucune dépense enregistrée"
        } else {
            breakdown.joinToString("\n") { (category, amount) ->
                val percent = if (total > 0) (amount / total * 100).toInt() else 0
                "$category : ${numberFormat.format(amount.toInt())} MRU ($percent%)"
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

    private fun aggregateByMonth(data: List<Pair<Long, Double>>): List<Pair<String, Double>> {
        val sdf = SimpleDateFormat("MM/yy", Locale.getDefault())
        val map = mutableMapOf<String, Double>()
        val sortedData = data.sortedBy { it.first }
        sortedData.forEach { (date, value) ->
            val key = sdf.format(Date(date))
            map[key] = (map[key] ?: 0.0) + value
        }
        return map.toList().sortedBy { sdf.parse(it.first)?.time ?: 0L }
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
        val textColor = getColor(R.color.text_primary)

        val dataSet = LineDataSet(entries, label).apply {
            this.color = color
            setCircleColor(color)
            lineWidth = 2f
            setDrawFilled(true)
            fillAlpha = 50
            fillColor = color
            setDrawValues(true)
            valueTextColor = textColor
            valueTextSize = 8f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    if (value <= 0) return ""
                    return if (statsType == "SALES") String.format(Locale.getDefault(), "%.1f", value)
                    else value.toInt().toString()
                }
            }
        }

        binding.monthlyLineChart.apply {
            this.data = LineData(dataSet)
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(false)
            
            xAxis.apply {
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                labelCount = 10
                labelRotationAngle = -45f
                this.textColor = textColor
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = value.toInt().toString()
                }
            }
            axisLeft.textColor = textColor
            axisRight.isEnabled = false
            legend.textColor = textColor
            
            // Permet de zoomer un peu par défaut pour éviter le chevauchement
            setVisibleXRangeMaximum(12f)
            moveViewToX(data.size.toFloat())

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
        binding.tvStatsSummary.text = "${String.format(Locale.getDefault(), format, total)}$unit"
    }

    private fun setupBarChart(data: List<Pair<String, Double>>) {
        val entries = data.mapIndexed { index, pair ->
            BarEntry(index.toFloat(), pair.second.toFloat())
        }
        val textColor = getColor(R.color.text_primary)

        val dataSet = BarDataSet(entries, "Par catégorie").apply {
            colors = listOf(getColor(R.color.primary), getColor(R.color.earthy_orange), getColor(R.color.accent_blue))
            valueTextSize = 10f
            valueTextColor = textColor
        }

        binding.monthlyBarChart.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                this.textColor = textColor
                labelRotationAngle = -45f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = if (value.toInt() in data.indices) data[value.toInt()].first else ""
                }
            }
            axisLeft.textColor = textColor
            axisRight.isEnabled = false
            legend.textColor = textColor
            animateY(1000)
            invalidate()
        }
    }

    private fun setupHistoricalBarChart(data: List<Pair<String, Double>>, label: String) {
        if (data.isEmpty()) {
            binding.historicalMonthlyChart.clear()
            return
        }

        val entries = data.mapIndexed { index, pair ->
            BarEntry(index.toFloat(), pair.second.toFloat())
        }

        val color = when(statsType) {
            "MORTALITY", "BROKEN" -> getColor(R.color.error)
            "SALES", "EXPENSES" -> getColor(R.color.earthy_orange)
            else -> getColor(R.color.primary)
        }
        val textColor = getColor(R.color.text_primary)

        val dataSet = BarDataSet(entries, label).apply {
            this.color = color
            valueTextSize = 10f
            valueTextColor = textColor
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (statsType == "REVENUE" || statsType == "EXPENSES") {
                        if (value >= 1000) String.format(Locale.getDefault(), "%.1fk", value / 1000) else value.toInt().toString()
                    } else if (statsType == "COLLECTION" || statsType == "SALES") {
                        String.format(Locale.getDefault(), "%.1f", value)
                    } else {
                        value.toInt().toString()
                    }
                }
            }
        }

        binding.historicalMonthlyChart.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)

            xAxis.apply {
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                this.textColor = textColor
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index in data.indices) data[index].first else ""
                    }
                }
            }
            axisLeft.apply {
                axisMinimum = 0f
                this.textColor = textColor
            }
            axisRight.isEnabled = false
            legend.textColor = textColor
            
            setVisibleXRangeMaximum(4f)
            moveViewToX(data.size.toFloat())
            
            animateY(1000)
            invalidate()
        }
    }

    private fun setupHistoricalComparisonChart(expData: List<Pair<String, Double>>, salesData: List<Pair<String, Double>>) {
        val sdf = SimpleDateFormat("MM/yy", Locale.getDefault())
        val allMonths = (expData.map { it.first } + salesData.map { it.first })
            .distinct()
            .sortedBy { sdf.parse(it)?.time ?: 0L }

        if (allMonths.isEmpty()) {
            binding.historicalMonthlyChart.clear()
            return
        }

        val entriesExp = allMonths.mapIndexed { index, month ->
            val value = expData.find { it.first == month }?.second ?: 0.0
            BarEntry(index.toFloat(), value.toFloat())
        }

        val entriesSales = allMonths.mapIndexed { index, month ->
            val value = salesData.find { it.first == month }?.second ?: 0.0
            BarEntry(index.toFloat(), value.toFloat())
        }

        val textColor = getColor(R.color.text_primary)
        
        val setExp = BarDataSet(entriesExp, "Dépenses").apply {
            setColor(Color.parseColor("#FF4444")) // Rouge pur et forcé
            valueTextColor = textColor
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = if (value >= 1000) String.format(Locale.getDefault(), "%.1fk", value / 1000) else value.toInt().toString()
            }
        }

        val setSales = BarDataSet(entriesSales, "Recettes").apply {
            setColor(Color.parseColor("#00C851")) // Vert pur et forcé
            valueTextColor = textColor
            valueTextSize = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = if (value >= 1000) String.format(Locale.getDefault(), "%.1fk", value / 1000) else value.toInt().toString()
            }
        }

        val data = BarData(setExp, setSales)
        val groupSpace = 0.3f
        val barSpace = 0.05f
        val barWidth = 0.3f
        data.barWidth = barWidth

        binding.historicalMonthlyChart.apply {
            this.data = data
            description.isEnabled = false
            
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)

            xAxis.apply {
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                this.textColor = textColor
                setCenterAxisLabels(true)
                granularity = 1f
                axisMinimum = 0f
                axisMaximum = allMonths.size.toFloat()
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        return if (index in allMonths.indices) allMonths[index] else ""
                    }
                }
            }
            axisLeft.apply {
                axisMinimum = 0f
                this.textColor = textColor
            }
            axisRight.isEnabled = false
            legend.apply {
                this.textColor = textColor
                verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
                isEnabled = true
                form = com.github.mikephil.charting.components.Legend.LegendForm.SQUARE
                formSize = 10f
                xEntrySpace = 15f
            }
            groupBars(0f, groupSpace, barSpace)
            
            setVisibleXRangeMaximum(4f)
            moveViewToX(allMonths.size.toFloat())

            animateY(1000)
            invalidate()
        }
    }
}
