package com.hadietou.poulailler.data

/**
 * Une des fermes auxquelles l'utilisateur courant appartient (voir la note d'architecture
 * multi-fermes dans FirebaseRepository) — stockée dans users/{uid}/memberships/{farmId}.
 * Le doc users/{uid} lui-même garde farmId/role comme "ferme active" (celle affichée dans
 * tout le reste de l'app), pour ne rien changer aux écrans déjà écrits contre ces deux champs.
 */
data class Membership(
    val farmId: String,
    val farmName: String,
    val role: String
)
