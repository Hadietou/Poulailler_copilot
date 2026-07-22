package com.hadietou.poulailler.data

import java.util.Calendar
import java.util.concurrent.TimeUnit

class HealthReminderManager(private val dao: HealthReminderDao) {

    suspend fun generateRemindersForBatch(batch: Batch) {
        val batchId = batch.firestoreId ?: return
        val arrivalDate = batch.arrivalDate
        if (arrivalDate == 0L) return

        // 1. Newcastle (ND) — tous les 2 mois
        scheduleRecurringReminder(
            batchId,
            "VACCIN",
            "Newcastle (ND)",
            "Vaccination de rappel recommandée (tous les 2 mois).",
            arrivalDate,
            2
        )

        // 2. Bronchite infectieuse (IB) — tous les 2 mois
        scheduleRecurringReminder(
            batchId,
            "VACCIN",
            "Bronchite infectieuse (IB)",
            "Vaccination de rappel recommandée (tous les 2 mois).",
            arrivalDate,
            2
        )

        // 3. Déparasitage interne
        scheduleRecurringReminder(
            batchId,
            "DEPARASITAGE",
            "Déparasitage interne",
            "Traitement contre les vers (tous les 3 mois). Produits : lévamisole, albendazole, fenbendazole. Donner 2 jours de vitamines après traitement.",
            arrivalDate,
            3
        )

        // 4. Déparasitage externe
        scheduleRecurringReminder(
            batchId,
            "DEPARASITAGE",
            "Déparasitage externe",
            "Traitement contre les poux et acariens (tous les 2 mois). Nettoyage + insecticide adapté (perméthrine, deltaméthrine).",
            arrivalDate,
            2
        )
    }

    suspend fun addVitaminReminder(batchId: String, title: String, description: String) {
        val now = System.currentTimeMillis()
        val existing = dao.getReminderByTitle(title, batchId)
        if (existing == null || existing.isDone) {
            dao.insert(HealthReminder(
                type = "VITAMINE",
                title = title,
                description = description,
                dueDate = now,
                batchId = batchId
            ))
        }
    }

    private suspend fun scheduleRecurringReminder(
        batchId: String,
        type: String,
        title: String,
        description: String,
        baseDate: Long,
        monthsInterval: Int
    ) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = baseDate
        
        // Find the most relevant due date (either in the past but not yet done, or in the future)
        while (cal.timeInMillis < now - TimeUnit.DAYS.toMillis(7)) {
            cal.add(Calendar.MONTH, monthsInterval)
        }

        val dueDate = cal.timeInMillis
        val existing = dao.getReminderByTitle(title, batchId)
        
        if (existing == null) {
            dao.insert(HealthReminder(
                type = type,
                title = title,
                description = description,
                dueDate = dueDate,
                batchId = batchId,
                recurring = true,
                frequencyMonths = monthsInterval
            ))
        } else if (existing.isDone && existing.dueDate < now - TimeUnit.DAYS.toMillis(1)) {
            // If the old one is done, we update it for the next occurrence
            dao.update(existing.copy(
                dueDate = dueDate,
                isDone = false
            ))
        }
    }
}
