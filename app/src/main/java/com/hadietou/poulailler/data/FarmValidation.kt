package com.hadietou.poulailler.data

import java.util.concurrent.TimeUnit

/**
 * Statut de validation d'une ferme, porté par le document `fermes/{id}` (et non par l'utilisateur) :
 *
 * - [STATUS_PENDING] : ferme en période d'essai (1 an à partir de sa création) ;
 * - [STATUS_ACTIVE]  : ferme validée, sans limite de durée ;
 * - [STATUS_SUSPENDED] : ferme suspendue, lecture seule.
 *
 * Seul l'administrateur peut modifier `status` / `trialEndsAt` (console Firebase ou script) ;
 * les règles Firestore (voir firebase/firestore.rules) l'interdisent aux clients ET refusent
 * toute écriture sur une ferme dont l'essai est terminé. Les contrôles faits dans l'app
 * ([isBlocked]) ne servent qu'à prévenir l'utilisateur avant que le serveur ne refuse.
 */
data class FarmValidation(
    val farmId: String,
    val farmName: String,
    val status: String,
    /** Fin de l'essai (ms), ou null si inconnue. */
    val trialEndsAt: Long?
) {
    val isActive get() = status == STATUS_ACTIVE
    val isSuspended get() = status == STATUS_SUSPENDED

    /** Début de l'essai, déduit de sa fin (la durée d'essai est fixe). */
    val trialStart: Long? get() = trialEndsAt?.minus(TimeUnit.DAYS.toMillis(TRIAL_DAYS))

    fun isTrialExpired(now: Long) = !isActive && trialEndsAt != null && now > trialEndsAt

    /** Lecture seule : ferme suspendue, ou essai terminé sans validation. */
    fun isBlocked(now: Long) = isSuspended || isTrialExpired(now)

    companion object {
        /**
         * Phase actuelle : application GRATUITE et entièrement accessible, sans essai ni blocage.
         * Passer à `true` réactive, côté app, le statut de validation, la pastille « Essai »,
         * les rappels de validation et le mode lecture seule. À utiliser le jour où un modèle
         * payant est mis en place (voir aussi firebase/firestore.rules, fonction farmWritable).
         */
        const val ENFORCEMENT_ENABLED = false

        const val STATUS_PENDING = "PENDING"
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_SUSPENDED = "SUSPENDED"

        /** Durée de l'essai offert à la création d'une ferme. */
        const val TRIAL_DAYS = 365L

        /** Intervalle entre deux rappels « pensez à faire valider votre ferme ». */
        const val REMINDER_EVERY_DAYS = 90L
    }
}

/** Rappels de validation : un tous les 3 mois d'essai (3, 6, 9, 12 mois…) tant que la ferme n'est pas validée. */
object ValidationReminder {

    /**
     * Numéro du rappel à afficher maintenant (1 = 3 mois après le début de l'essai, 2 = 6 mois…),
     * ou null si aucun n'est dû. [lastShownIndex] est le dernier numéro déjà montré sur cet appareil :
     * un même rappel n'est jamais affiché deux fois.
     */
    fun dueIndex(validation: FarmValidation, now: Long, lastShownIndex: Int): Int? {
        if (validation.isActive || validation.isSuspended) return null
        val start = validation.trialStart ?: return null
        val elapsed = now - start
        if (elapsed < 0) return null
        val index = (elapsed / TimeUnit.DAYS.toMillis(FarmValidation.REMINDER_EVERY_DAYS)).toInt()
        return if (index >= 1 && index > lastShownIndex) index else null
    }
}
