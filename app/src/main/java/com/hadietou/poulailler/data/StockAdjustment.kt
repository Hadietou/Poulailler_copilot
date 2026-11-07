package com.hadietou.poulailler.data

/**
 * Inventaire d'un stock (aliment, alvéoles ou œufs) : l'éleveur compte physiquement et enregistre
 * la quantité trouvée. L'écart avec le stock affiché ([counted] - [theoretical]) s'ajoute au calcul
 * du stock, sans modifier les achats, collectes ou ventes déjà saisis.
 *
 * [type] est la clé de [com.hadietou.poulailler.util.StockType]. [batchId] est vide pour les alvéoles,
 * dont le stock se calcule pour toute la ferme.
 */
data class StockAdjustment(
    val firestoreId: String? = null,
    val type: String,
    val date: Long,
    val theoretical: Double,
    val counted: Double,
    val reason: String,
    val notes: String? = null,
    val batchId: String? = null,
    val farmId: String? = null
) {
    val difference: Double get() = counted - theoretical
}
