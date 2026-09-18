package com.hadietou.poulailler.demo

/**
 * Mode démonstration : permet à quiconque n'a pas de compte de parcourir l'application avec
 * des données fictives (voir [DemoData]), en lecture seule, sans jamais toucher Firebase.
 *
 * Activé depuis LoginActivity ("Voir une démo"). [FirebaseRepository] court-circuite chacune
 * de ses méthodes de lecture/écriture quand [enabled] est vrai (retourne les données fictives
 * pour les lectures, lève une exception conviviale pour les écritures — capturée par les
 * try/catch déjà présents dans chaque écran). Réinitialisé à `false` dès qu'on revient sur
 * LoginActivity (déconnexion ou fermeture de la démo), pour ne jamais fuiter vers une vraie
 * session connectée.
 */
object DemoMode {
    var enabled: Boolean = false

    /** Message affiché (via Toast, dans les catch existants) à toute tentative d'écriture. */
    const val READ_ONLY_MESSAGE = "Ceci est une démonstration en lecture seule. Créez votre ferme pour enregistrer vos propres données."
}
