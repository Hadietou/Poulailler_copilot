package com.hadietou.poulailler.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
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

    /** Couleurs cycliques attribuées aux catégories, dans l'ordre décroissant de dépense. */
    private val CATEGORY_COLORS = listOf(
        R.color.earthy_orange, R.color.accent_blue, R.color.emerald_soft,
        R.color.accent_amber, R.color.accent_rose, R.color.primary
    )

    /** Au-delà de ce nombre, les catégories restantes sont regroupées sur une seule ligne. */
    private const val MAX_VISIBLE_CATEGORIES = 6

    /**
     * Classement des catégories de dépenses sous forme de barres proportionnelles (largeur
     * relative au max, pas un axe partagé) : contrairement à un histogramme classique, une
     * catégorie qui pèse 5% du total reste lisible à côté d'une qui en pèse 60%, sans les
     * problèmes d'un axe linéaire écrasé par la plus grosse valeur ni de libellés tournés qui
     * se chevauchent quand il y a beaucoup de catégories.
     */
    fun renderExpenses(container: ViewGroup, context: Context, expenses: List<CategoryExpense>) {
        container.removeAllViews()
        if (expenses.isEmpty()) return

        val sorted = expenses.sortedByDescending { it.totalAmount }
        val totalExp = sorted.sumOf { it.totalAmount }
        val maxAmount = sorted.first().totalAmount
        val inflater = LayoutInflater.from(context)

        fun addRow(label: String, amount: Double, @androidx.annotation.ColorInt color: Int) {
            val row = inflater.inflate(R.layout.item_category_expense_bar, container, false)
            val percentage = if (totalExp > 0) (amount / totalExp * 100) else 0.0
            val amountK = (amount / 1000).toInt()

            row.findViewById<TextView>(R.id.tvCategoryName).text = label
            row.findViewById<TextView>(R.id.tvCategoryValue).apply {
                text = String.format(Locale.getDefault(), "%d%% · %dk", percentage.toInt(), amountK)
                setTextColor(color)
            }

            // Largeur proportionnelle via un poids de LinearLayout (fill + espaceur) : évite
            // tout calcul de pixels dépendant du layout pass, contrairement à une largeur fixe.
            val fraction = if (maxAmount > 0) (amount / maxAmount).toFloat().coerceIn(0.03f, 1f) else 0f
            val fill = row.findViewById<View>(R.id.viewBarFill)
            fill.backgroundTintList = ColorStateList.valueOf(color)
            (fill.layoutParams as LinearLayout.LayoutParams).weight = fraction
            val spacer = row.findViewById<View>(R.id.viewBarSpacer)
            (spacer.layoutParams as LinearLayout.LayoutParams).weight = 1f - fraction

            container.addView(row)
        }

        val visible = sorted.take(MAX_VISIBLE_CATEGORIES)
        visible.forEachIndexed { index, catExp ->
            addRow(catExp.category, catExp.totalAmount, ContextCompat.getColor(context, CATEGORY_COLORS[index % CATEGORY_COLORS.size]))
        }

        val rest = sorted.drop(MAX_VISIBLE_CATEGORIES)
        if (rest.isNotEmpty()) {
            addRow(
                "+ ${rest.size} autre" + if (rest.size > 1) "s" else "",
                rest.sumOf { it.totalAmount },
                ContextCompat.getColor(context, R.color.text_secondary)
            )
        }
    }
}
