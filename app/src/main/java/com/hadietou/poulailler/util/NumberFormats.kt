package com.hadietou.poulailler.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/**
 * Formats numériques de l'application : milliers séparés par une espace insécable (11 417 000),
 * jamais par une virgule, qui se confond avec la virgule décimale.
 */
object NumberFormats {
    fun plain(): NumberFormat {
        val symbols = DecimalFormatSymbols(Locale.getDefault()).apply { groupingSeparator = ' ' }
        return DecimalFormat("#,##0.###", symbols)
    }
}
