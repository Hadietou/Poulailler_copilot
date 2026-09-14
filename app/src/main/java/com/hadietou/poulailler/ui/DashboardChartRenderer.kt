package com.hadietou.poulailler.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.hadietou.poulailler.R
import com.hadietou.poulailler.data.CategoryExpense
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Configuration des deux graphiques du tableau de bord (production hebdomadaire, dépenses
 * par catégorie). Extrait de DashboardActivity pour l'alléger : c'est de la pure
 * configuration MPAndroidChart, sans logique métier (les calculs restent dans
 * DashboardViewModel, qui n'a aucune dépendance vers ces classes de graphique).
 */
object DashboardChartRenderer {

    fun renderProduction(chart: LineChart, context: Context, production: List<Triple<Long, Int, Double>>) {
        if (production.isEmpty()) {
            chart.clear()
            return
        }

        fun color(resId: Int) = ContextCompat.getColor(context, resId)

        val entriesEggs = production.mapIndexed { index, triple ->
            Entry(index.toFloat(), triple.second.toFloat())
        }

        val entriesRate = production.mapIndexed { index, triple ->
            Entry(index.toFloat(), triple.third.toFloat())
        }

        val dataSetEggs = LineDataSet(entriesEggs, "Œufs").apply {
            color = color(R.color.emerald_soft)
            setCircleColor(color(R.color.primary))
            lineWidth = 3f
            circleRadius = 4f
            setDrawCircleHole(true)
            circleHoleColor = color(R.color.white)
            setDrawValues(false)
            mode = LineDataSet.Mode.HORIZONTAL_BEZIER
            setDrawFilled(true)
            fillColor = color(R.color.emerald_container)
            fillAlpha = 60
            axisDependency = YAxis.AxisDependency.LEFT
        }

        val orangeColor = color(R.color.earthy_orange)
        val dataSetRate = LineDataSet(entriesRate, "% Ponte").apply {
            this.color = orangeColor
            setDrawCircles(false)
            lineWidth = 2f
            enableDashedLine(10f, 10f, 0f)
            setDrawValues(false)
            mode = LineDataSet.Mode.HORIZONTAL_BEZIER
            setDrawFilled(false)
            axisDependency = YAxis.AxisDependency.RIGHT
        }

        chart.apply {
            data = LineData(dataSetEggs, dataSetRate)
            description.isEnabled = false
            setDrawGridBackground(false)
            setTouchEnabled(true)
            setPinchZoom(false)
            setScaleEnabled(false)

            val textColor = color(R.color.text_primary)
            val gridColor = color(R.color.off_white)

            xAxis.apply {
                this.textColor = textColor
                position = XAxis.XAxisPosition.BOTTOM
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

    fun renderExpenses(chart: BarChart, context: Context, expenses: List<CategoryExpense>) {
        if (expenses.isEmpty()) {
            chart.clear()
            return
        }

        fun color(resId: Int) = ContextCompat.getColor(context, resId)

        val sortedExpenses = expenses.sortedByDescending { it.totalAmount }
        val totalExp = sortedExpenses.sumOf { it.totalAmount }

        val entries = sortedExpenses.mapIndexed { index, catExp ->
            BarEntry(index.toFloat(), catExp.totalAmount.toFloat())
        }

        val colorsList = listOf(
            color(R.color.earthy_orange),
            color(R.color.accent_blue),
            color(R.color.emerald_soft),
            color(R.color.accent_amber),
            color(R.color.accent_rose),
            color(R.color.primary)
        )

        val textColor = color(R.color.text_primary)

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

        chart.apply {
            data = BarData(dataSet)
            data.barWidth = 0.5f
            description.isEnabled = false

            xAxis.apply {
                this.textColor = textColor
                position = XAxis.XAxisPosition.BOTTOM
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
}
