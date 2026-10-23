package com.hadietou.poulailler

import com.hadietou.poulailler.data.Expense
import com.hadietou.poulailler.data.Mortality
import com.hadietou.poulailler.data.Treatment
import com.hadietou.poulailler.util.AlertLevel
import com.hadietou.poulailler.util.HealthAlertEngine
import com.hadietou.poulailler.util.MedicationPurchases
import com.hadietou.poulailler.util.MedicationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationAlertsTest {

    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    private fun treatment(name: String, type: String?, status: String = "EN_COURS", start: Long = now - day, end: Long? = null, eggDays: Int? = null) =
        Treatment(medicationName = name, startDate = start, endDate = end, status = status, eggWithdrawalDays = eggDays, medicationType = type, firestoreId = name)

    @Test
    fun antibioticInProgress_withoutDelay_isCritical() {
        val alerts = HealthAlertEngine.checkOngoingTreatments(listOf(treatment("Amoxicilline", "ANTIBIOTIQUE")), now)
        assertEquals(1, alerts.size)
        assertEquals(AlertLevel.CRITIQUE, alerts[0].level)
        assertTrue(alerts[0].message.contains("Délai d'attente non renseigné"))
    }

    @Test
    fun supplementInProgress_raisesNoAlert() {
        val alerts = HealthAlertEngine.checkOngoingTreatments(listOf(treatment("Électrolytes", "ELECTROLYTES")), now)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun withdrawalLevel_dependsOnType() {
        val end = now - 2 * day
        val antibiotic = treatment("Amoxicilline", "ANTIBIOTIQUE", "TERMINE", end = end, eggDays = 7)
        val vitamin = treatment("Vitamine C", "VITAMINE_C", "TERMINE", end = end, eggDays = 7)
        assertEquals(AlertLevel.CRITIQUE, HealthAlertEngine.checkActiveWithdrawals(listOf(antibiotic), now).first().level)
        assertEquals(AlertLevel.ATTENTION, HealthAlertEngine.checkActiveWithdrawals(listOf(vitamin), now).first().level)
    }

    @Test
    fun legacyTreatmentWithoutType_keepsCriticalWithdrawal() {
        val legacy = treatment("Ancien", null, "TERMINE", end = now - day, eggDays = 7)
        assertEquals(AlertLevel.CRITIQUE, HealthAlertEngine.checkActiveWithdrawals(listOf(legacy), now).first().level)
        assertTrue(HealthAlertEngine.checkOngoingTreatments(listOf(legacy), now).isEmpty())
    }

    @Test
    fun mortalitySpike_mentionsOngoingTreatmentType() {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val today = cal.apply { set(java.util.Calendar.HOUR_OF_DAY, 12) }.timeInMillis
        val entries = listOf(Mortality(date = today, count = 10)) +
            (1..7).map { Mortality(date = today - it * day, count = 2) }
        val withAntibiotic = HealthAlertEngine.computeAll(entries, emptyList(), listOf(treatment("Amoxicilline", "ANTIBIOTIQUE")), now = today)
        val withSupplement = HealthAlertEngine.computeAll(entries, emptyList(), listOf(treatment("Vitamines", "VITAMINES")), now = today)
        assertTrue(withAntibiotic.first { it.key.startsWith("spike") }.message.contains("Traitement déjà en cours"))
        assertTrue(withSupplement.first { it.key.startsWith("spike") }.message.contains("Seuls des compléments"))
    }

    @Test
    fun purchases_matchByNameIgnoringCase_andGiveType() {
        val purchase = Expense(
            date = now, category = "Cheptel & Santé", amount = 15_500.0, subCategory = "Médicaments",
            productName = "Amoxicilline", medicationType = "ANTIBIOTIQUE"
        )
        assertEquals(purchase, MedicationPurchases.lastPurchase(listOf(purchase), "  amoxicilline "))
        assertNull(MedicationPurchases.lastPurchase(listOf(purchase), "Autre"))
        assertEquals(MedicationType.ANTIBIOTIQUE, MedicationPurchases.knownType(listOf(purchase), emptyList(), "AMOXICILLINE"))
        assertTrue(MedicationPurchases.knownNames(listOf(purchase), listOf(treatment("amoxicilline", null))).size == 1)
    }
}
