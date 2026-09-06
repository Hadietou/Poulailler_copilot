package com.hadietou.poulailler.network

data class GeocodingResponse(
    val results: List<GeocodingResult>? = null
)

data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null
) {
    /** Libellé affiché dans la liste de résultats, ex: "Rosso, Trarza, Mauritanie". */
    val displayLabel: String
        get() = listOfNotNull(name, admin1, country).joinToString(", ")
}
