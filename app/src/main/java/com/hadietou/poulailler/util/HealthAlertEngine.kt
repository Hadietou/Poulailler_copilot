package com.hadietou.poulailler.util

import com.hadietou.poulailler.data.HealthReminder
import com.hadietou.poulailler.data.Mortality
import com.hadietou.poulailler.data.Treatment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AlertLevel { CRITIQUE, ATTENTION }

data class HealthAlert(
    val level: AlertLevel,
    val title: String,
    val message: String
)

/**
 * Règles de détection de base pour le fil d'alertes du tableau de bord sanitaire (Phase 1).
 * Chaque règle explique ce qui a été détecté et pourquoi c'est important, sans jamais poser
 * de diagnostic — uniquement des seuils sur les données déjà saisies (mortalité, rappels).
 */
object HealthAlertEngine {

    private const val DAY_MS = 86_400_000L

    private fun startOfDay(timeMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Total des morts par jour (regroupe les éventuelles saisies multiples le même jour). */
    private fun dailyTotals(entries: List<Mortality>): Map<Long, Int> =
        entries.groupBy { startOfDay(it.date) }.mapValues { (_, v) -> v.sumOf { it.count } }

    /** 🔴 Mortalité du jour au moins 2x la moyenne des 7 jours précédents. */
    fun checkMortalitySpike(entries: List<Mortality>, now: Long = System.currentTimeMillis()): HealthAlert? {
        val totals = dailyTotals(entries)
        val todayStart = startOfDay(now)
        val todayCount = totals[todayStart] ?: return null
        if (todayCount < 3) return null // évite de déclencher pour 1-2 pertes isolées

        val previous7 = (1..7).mapNotNull { totals[todayStart - it * DAY_MS] }
        if (previous7.isEmpty()) return null
        val avg = previous7.average()
        if (avg <= 0.0) return null

        return if (todayCount >= avg * 2) HealthAlert(
            AlertLevel.CRITIQUE,
            "Mortalité anormalement élevée",
            "$todayCount morts aujourd'hui contre ${"%.1f".format(avg)}/jour en moyenne cette semaine — vérifier température, eau et signes de maladie."
        ) else null
    }

    /** 🟠 Hausse progressive sur 3 jours consécutifs. */
    fun checkMortalityTrend(entries: List<Mortality>, now: Long = System.currentTimeMillis()): HealthAlert? {
        val totals = dailyTotals(entries)
        val todayStart = startOfDay(now)
        val d0 = totals[todayStart] ?: return null
        val d1 = totals[todayStart - DAY_MS] ?: return null
        val d2 = totals[todayStart - 2 * DAY_MS] ?: return null
        return if (d0 > d1 && d1 > d2 && d0 > 0) HealthAlert(
            AlertLevel.ATTENTION,
            "Mortalité en hausse",
            "Hausse progressive depuis 3 jours ($d2 → $d1 → $d0) — à surveiller de près."
        ) else null
    }

    /** 🔴 Rappels (vaccins, vitamines...) en retard, 🟠 ceux à échéance sous 48h. */
    fun checkReminders(reminders: List<HealthReminder>, now: Long = System.currentTimeMillis()): List<HealthAlert> {
        val alerts = mutableListOf<HealthAlert>()
        reminders.filter { !it.isDone }.forEach {
            val diff = it.dueDate - now
            when {
                diff < 0 -> {
                    val daysLate = (-diff / DAY_MS).toInt()
                    alerts += HealthAlert(
                        AlertLevel.CRITIQUE,
                        "${it.title} en retard",
                        if (daysLate <= 0) "Prévu aujourd'hui, toujours pas fait." else "Prévu il y a $daysLate jour(s), toujours pas fait."
                    )
                }
                diff <= 2 * DAY_MS -> alerts += HealthAlert(
                    AlertLevel.ATTENTION,
                    "${it.title} à venir",
                    "Prévu dans moins de 48h."
                )
            }
        }
        return alerts
    }

    /** 🔴 Délai d'attente (œufs ou abattage) toujours en cours pour un traitement. */
    fun checkActiveWithdrawals(treatments: List<Treatment>, now: Long = System.currentTimeMillis()): List<HealthAlert> {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val alerts = mutableListOf<HealthAlert>()
        treatments.forEach { t ->
            t.eggWithdrawalUntil?.let { until ->
                if (until > now) alerts += HealthAlert(
                    AlertLevel.CRITIQUE,
                    "Délai d'attente œufs en cours",
                    "${t.medicationName} : ne pas commercialiser les œufs avant le ${sdf.format(Date(until))}."
                )
            }
            t.slaughterWithdrawalUntil?.let { until ->
                if (until > now) alerts += HealthAlert(
                    AlertLevel.CRITIQUE,
                    "Délai d'attente abattage en cours",
                    "${t.medicationName} : éviter l'abattage avant le ${sdf.format(Date(until))}."
                )
            }
        }
        return alerts
    }

    /** Calcule toutes les alertes actives, triées par sévérité (critique d'abord). */
    fun computeAll(
        mortalities: List<Mortality>,
        reminders: List<HealthReminder>,
        treatments: List<Treatment> = emptyList(),
        now: Long = System.currentTimeMillis()
    ): List<HealthAlert> {
        val alerts = mutableListOf<HealthAlert>()
        checkMortalitySpike(mortalities, now)?.let { alerts += it }
        checkMortalityTrend(mortalities, now)?.let { alerts += it }
        alerts += checkReminders(reminders, now)
        alerts += checkActiveWithdrawals(treatments, now)
        return alerts.sortedBy { if (it.level == AlertLevel.CRITIQUE) 0 else 1 }
    }
}
