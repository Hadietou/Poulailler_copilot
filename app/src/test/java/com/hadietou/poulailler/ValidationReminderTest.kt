package com.hadietou.poulailler

import com.hadietou.poulailler.data.FarmValidation
import com.hadietou.poulailler.data.ValidationReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ValidationReminderTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val start = 1_000_000_000_000L
    private val pending = FarmValidation("f1", "Ferme", FarmValidation.STATUS_PENDING, start + 365 * day)

    @Test
    fun `aucun rappel avant 3 mois`() {
        assertNull(ValidationReminder.dueIndex(pending, start + 89 * day, 0))
    }

    @Test
    fun `premier rappel a 3 mois puis plus rien tant que le suivant n'est pas du`() {
        assertEquals(1, ValidationReminder.dueIndex(pending, start + 90 * day, 0))
        assertNull(ValidationReminder.dueIndex(pending, start + 120 * day, 1))
        assertEquals(2, ValidationReminder.dueIndex(pending, start + 181 * day, 1))
    }

    @Test
    fun `un rappel manque n'est montre qu'une fois avec le numero courant`() {
        // Appli non ouverte pendant 7 mois : un seul rappel (le 2), pas un par période manquée.
        assertEquals(2, ValidationReminder.dueIndex(pending, start + 200 * day, 0))
    }

    @Test
    fun `pas de rappel pour une ferme validee ou suspendue`() {
        assertNull(ValidationReminder.dueIndex(pending.copy(status = FarmValidation.STATUS_ACTIVE), start + 200 * day, 0))
        assertNull(ValidationReminder.dueIndex(pending.copy(status = FarmValidation.STATUS_SUSPENDED), start + 200 * day, 0))
    }

    @Test
    fun `pas de rappel sans date d'essai`() {
        assertNull(ValidationReminder.dueIndex(pending.copy(trialEndsAt = null), start + 200 * day, 0))
    }

    @Test
    fun `blocage apres un an sans validation seulement`() {
        assertFalse(pending.isBlocked(start + 364 * day))
        assertTrue(pending.isBlocked(start + 366 * day))
        assertFalse(pending.copy(status = FarmValidation.STATUS_ACTIVE).isBlocked(start + 800 * day))
        assertTrue(pending.copy(status = FarmValidation.STATUS_SUSPENDED).isBlocked(start))
    }
}
