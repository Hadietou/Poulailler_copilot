package com.hadietou.poulailler.util

/**
 * Pays d'Afrique proposés à la création d'une ferme, avec la devise associée.
 *
 * Les pays de la zone franc (UEMOA et CEMAC) partagent le code "CFA" — convention déjà utilisée
 * par les fermes existantes — plutôt que XOF/XAF, afin de ne pas mélanger deux notations.
 */
object CountryData {

    data class Country(val name: String, val currency: String)

    val africa: List<Country> = listOf(
        Country("Mauritanie", "MRU"),
        Country("Sénégal", "CFA"),
        Country("Mali", "CFA"),
        Country("Guinée", "GNF"),
        Country("Côte d'Ivoire", "CFA"),
        Country("Bénin", "CFA"),
        Country("Togo", "CFA"),
        Country("Burkina Faso", "CFA"),
        Country("Niger", "CFA"),
        Country("Afrique du Sud", "ZAR"),
        Country("Algérie", "DZD"),
        Country("Angola", "AOA"),
        Country("Botswana", "BWP"),
        Country("Burundi", "BIF"),
        Country("Cameroun", "CFA"),
        Country("Cap-Vert", "CVE"),
        Country("Comores", "KMF"),
        Country("Congo", "CFA"),
        Country("Djibouti", "DJF"),
        Country("Égypte", "EGP"),
        Country("Érythrée", "ERN"),
        Country("Eswatini", "SZL"),
        Country("Éthiopie", "ETB"),
        Country("Gabon", "CFA"),
        Country("Gambie", "GMD"),
        Country("Ghana", "GHS"),
        Country("Guinée-Bissau", "CFA"),
        Country("Guinée équatoriale", "CFA"),
        Country("Kenya", "KES"),
        Country("Lesotho", "LSL"),
        Country("Liberia", "LRD"),
        Country("Libye", "LYD"),
        Country("Madagascar", "MGA"),
        Country("Malawi", "MWK"),
        Country("Maroc", "MAD"),
        Country("Maurice", "MUR"),
        Country("Mozambique", "MZN"),
        Country("Namibie", "NAD"),
        Country("Nigeria", "NGN"),
        Country("Ouganda", "UGX"),
        Country("République centrafricaine", "CFA"),
        Country("République démocratique du Congo", "CDF"),
        Country("Rwanda", "RWF"),
        Country("Sao Tomé-et-Principe", "STN"),
        Country("Seychelles", "SCR"),
        Country("Sierra Leone", "SLE"),
        Country("Somalie", "SOS"),
        Country("Soudan", "SDG"),
        Country("Soudan du Sud", "SSP"),
        Country("Tanzanie", "TZS"),
        Country("Tchad", "CFA"),
        Country("Tunisie", "TND"),
        Country("Zambie", "ZMW"),
        Country("Zimbabwe", "ZWL")
    )

    val countryNames: Array<String> = africa.map { it.name }.toTypedArray()

    /** Toutes les devises proposées, sans doublon, en gardant l'ordre de la liste des pays. */
    val currencies: Array<String> = africa.map { it.currency }.distinct().toTypedArray()

    /** Devise du pays donné ; MRU (défaut historique de l'app) si le pays est inconnu. */
    fun currencyFor(countryName: String): String =
        africa.firstOrNull { it.name == countryName }?.currency ?: "MRU"
}
