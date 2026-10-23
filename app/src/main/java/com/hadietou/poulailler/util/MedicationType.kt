package com.hadietou.poulailler.util

import com.hadietou.poulailler.data.Expense
import com.hadietou.poulailler.data.Treatment

/**
 * Type d'un médicament ou complément administré. Il sert aux alertes sanitaires : un antibiotique
 * impose un délai d'attente avant de vendre les œufs, alors que des vitamines ou des électrolytes
 * n'en demandent normalement pas et ne doivent pas déclencher d'alerte du même niveau.
 */
enum class MedicationType(
    val label: String,
    /** Médicament vétérinaire pouvant imposer un délai d'attente (œufs / abattage) d'après sa notice. */
    val needsWithdrawal: Boolean
) {
    ANTIBIOTIQUE("Antibiotique", true),
    ANTIPARASITAIRE("Antiparasitaire (vermifuge)", true),
    ANTICOCCIDIEN("Anticoccidien", true),
    AUTRE_MEDICAMENT("Autre médicament", true),
    VITAMINES("Vitamines (A, E, B…)", false),
    VITAMINE_D3("Vitamine D3", false),
    VITAMINE_C("Vitamine C", false),
    CALCIUM("Calcium / minéraux", false),
    ELECTROLYTES("Électrolytes", false),
    PROBIOTIQUE("Probiotiques", false);

    /** Complément nutritionnel (vitamines, minéraux…) : n'est pas un traitement contre une maladie. */
    val isSupplement: Boolean get() = !needsWithdrawal

    companion object {
        fun fromKey(key: String?): MedicationType? = entries.firstOrNull { it.name == key }

        fun fromLabel(label: String?): MedicationType? = entries.firstOrNull { it.label == label }
    }
}

/**
 * Rapprochement entre les achats « Cheptel & Santé » (médicaments, vitamines) et les traitements,
 * fait sur le nom du produit (sans tenir compte de la casse ni des espaces autour).
 */
object MedicationPurchases {

    private val PURCHASE_SUBCATEGORIES = setOf("Médicaments", "Vitamines")

    private fun normalize(name: String?): String = name.orEmpty().trim().lowercase()

    private fun isMedicationPurchase(e: Expense): Boolean =
        e.category == "Cheptel & Santé" && e.subCategory in PURCHASE_SUBCATEGORIES && !e.productName.isNullOrBlank()

    /** Noms déjà achetés (le plus récent d'abord), puis noms déjà utilisés dans un traitement. */
    fun knownNames(expenses: List<Expense>, treatments: List<Treatment>): List<String> {
        val fromPurchases = expenses.filter(::isMedicationPurchase).sortedByDescending { it.date }.mapNotNull { it.productName?.trim() }
        val fromTreatments = treatments.map { it.medicationName.trim() }.filter { it.isNotEmpty() }
        return (fromPurchases + fromTreatments).distinctBy { it.lowercase() }
    }

    /** Dernier achat portant ce nom. */
    fun lastPurchase(expenses: List<Expense>, name: String?): Expense? {
        val key = normalize(name)
        if (key.isEmpty()) return null
        return expenses.filter(::isMedicationPurchase)
            .filter { normalize(it.productName) == key }
            .maxByOrNull { it.date }
    }

    /** Type connu pour ce nom : celui de l'achat le plus récent, sinon celui d'un traitement précédent. */
    fun knownType(expenses: List<Expense>, treatments: List<Treatment>, name: String?): MedicationType? {
        val key = normalize(name)
        if (key.isEmpty()) return null
        val fromPurchase = lastPurchase(expenses, name)?.medicationType?.let { MedicationType.fromKey(it) }
        if (fromPurchase != null) return fromPurchase
        return treatments.filter { normalize(it.medicationName) == key }
            .sortedByDescending { it.startDate }
            .firstNotNullOfOrNull { MedicationType.fromKey(it.medicationType) }
    }
}
