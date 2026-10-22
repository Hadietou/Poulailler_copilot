package com.hadietou.poulailler.util

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.hadietou.poulailler.data.HealthReminder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object HealthReminderUtils {

    /**
     * Prochaine échéance d'un rappel cyclique, calculée comme le fait la génération
     * automatique des rappels (DashboardViewModel.checkAndGenerateReminders) :
     * on avance de [HealthReminder.frequencyMonths] mois jusqu'à dépasser la date du jour
     * (avec la même tolérance de 7 jours). Null pour un rappel ponctuel.
     */
    fun nextDueDate(r: HealthReminder, now: Long = System.currentTimeMillis()): Long? {
        val months = r.frequencyMonths ?: return null
        if (!r.recurring || months <= 0) return null
        val cal = Calendar.getInstance().apply { timeInMillis = r.dueDate }
        cal.add(Calendar.MONTH, months)
        while (cal.timeInMillis < now - TimeUnit.DAYS.toMillis(7)) {
            cal.add(Calendar.MONTH, months)
        }
        return cal.timeInMillis
    }

    fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))

    /** Demande confirmation avant d'ignorer un rappel, en indiquant quand il reviendra. */
    fun confirmIgnore(context: Context, r: HealthReminder, onConfirm: () -> Unit) {
        val next = nextDueDate(r)
        val message = if (next != null) {
            "Ce rappel ne sera pas marqué comme fait. Comme il est cyclique, il reviendra pour la prochaine échéance, le ${formatDate(next)}."
        } else {
            "Ce rappel sera retiré de la liste sans être marqué comme fait. Il restera visible dans l'historique."
        }
        AlertDialog.Builder(context)
            .setTitle("Ignorer « ${r.title} » ?")
            .setMessage(message)
            .setPositiveButton("Ignorer") { _, _ -> onConfirm() }
            .setNegativeButton("Annuler", null)
            .show()
    }

    /** Message court affiché après l'action. */
    fun actionMessage(r: HealthReminder, ignored: Boolean): String {
        val base = if (ignored) "Rappel ignoré" else "Soin marqué comme fait"
        val next = nextDueDate(r) ?: return base
        return "$base — prochain rappel le ${formatDate(next)}"
    }
}
