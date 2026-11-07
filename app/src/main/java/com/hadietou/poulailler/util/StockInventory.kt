package com.hadietou.poulailler.util

import com.hadietou.poulailler.data.StockAdjustment
import kotlin.math.abs
import kotlin.math.roundToLong

/** Les trois stocks qui se contrôlent physiquement, avec les motifs d'écart les plus courants. */
enum class StockType(val label: String, val reasons: List<String>) {
    FEED(
        "Aliment",
        listOf(
            "Aliment donné en plus que la ration prévue",
            "Perte ou gaspillage",
            "Aliment abîmé ou humide",
            "Aliment donné ou prêté",
            "Erreur de saisie d'un achat",
            "Autre"
        )
    ),
    TRAYS(
        "Alvéoles",
        listOf(
            "Alvéole en plus pour couvrir les plateaux vendus",
            "Alvéoles abîmées ou perdues",
            "Alvéoles données ou prêtées",
            "Erreur de saisie d'un achat",
            "Autre"
        )
    ),
    EGGS(
        "Œufs en stock",
        listOf(
            "Œufs détériorés, non vendables",
            "Œufs cassés non déclarés",
            "Œufs offerts ou consommés",
            "Erreur de comptage ou de saisie",
            "Autre"
        )
    );

    /** Valeur lisible : « 380 kg », « 35 alvéoles », « 12 plateaux + 7 œufs ». */
    fun format(value: Double): String = when (this) {
        FEED -> "${NumberFormats.plain().format(value)} kg"
        TRAYS -> "${value.roundToLong()} alvéole${if (abs(value.roundToLong()) > 1) "s" else ""}"
        EGGS -> formatEggs(value.roundToLong())
    }

    /** Écart avec son signe : « +1 alvéole », « −32 kg ». */
    fun formatDifference(value: Double): String {
        val sign = if (value > 0) "+" else if (value < 0) "−" else ""
        return sign + format(abs(value))
    }

    private fun formatEggs(eggs: Long): String {
        val trays = eggs / 30
        val extra = eggs % 30
        return when {
            eggs == 0L -> "0 œuf"
            trays == 0L -> "$extra œuf${if (extra > 1) "s" else ""}"
            extra == 0L -> "$trays plateau${if (trays > 1) "x" else ""}"
            else -> "$trays plateau${if (trays > 1) "x" else ""} + $extra œuf${if (extra > 1) "s" else ""}"
        }
    }

    companion object {
        fun fromKey(key: String?): StockType? = entries.firstOrNull { it.name == key }
    }
}

object StockAdjustments {
    /**
     * Somme des écarts d'inventaire à ajouter au stock calculé. Les stocks aliment et œufs se calculent
     * par lot ; les alvéoles pour toute la ferme (leurs inventaires n'ont pas de lot).
     */
    fun offset(adjustments: List<StockAdjustment>?, type: StockType, batchId: String?): Double =
        adjustments.orEmpty()
            .filter { it.type == type.name && (type == StockType.TRAYS || batchId == null || it.batchId == batchId) }
            .sumOf { it.difference }
}
