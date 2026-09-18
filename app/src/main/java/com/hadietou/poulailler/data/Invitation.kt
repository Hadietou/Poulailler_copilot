package com.hadietou.poulailler.data

/**
 * Invitation à rejoindre une ferme en tant qu'agent, adressée à un numéro de téléphone
 * (voir la note d'architecture dans FirebaseRepository sur le nouveau parcours d'inscription).
 *
 * L'invitation est visible dans l'app dès que quelqu'un crée un compte avec CE numéro de
 * téléphone et se connecte — que le message WhatsApp envoyé au moment de l'invitation ait
 * été reçu/lu ou non. Le message WhatsApp n'est qu'une incitation à installer l'app ; le
 * mécanisme réel de rattachement à la ferme passe uniquement par la correspondance du numéro
 * de téléphone côté serveur (Firestore), jamais par un lien à jeton.
 */
data class Invitation(
    val phoneNumber: String,
    val farmId: String,
    val farmName: String,
    val invitedByUsername: String,
    val status: String = "PENDING", // PENDING, ACCEPTED, DECLINED
    val createdAt: Long = System.currentTimeMillis(),
    val firestoreId: String? = null
)
