package com.hadietou.poulailler.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = "", // Firebase UID
    val username: String,
    val email: String = "",
    val password: String,
    val role: String,
    val active: Boolean = true,
    val farmId: String? = null,
    val isPending: Boolean = false,
    val createdAt: Long = 0L,
    // Numéro au format E.164 (ex: "+22212345678"), utilisé comme identifiant de connexion
    // pour les nouveaux comptes et comme clé de correspondance des invitations (voir Invitation).
    // Optionnel pour rester compatible avec les comptes créés avant ce champ.
    val phoneNumber: String? = null
) {
    companion object {
        // Rôle temporaire tant que l'utilisateur n'a ni créé ni rejoint de ferme (voir le
        // nouveau parcours d'inscription : la création de compte ne fixe plus immédiatement
        // le rôle — voir la note d'architecture dans FirebaseRepository).
        const val ROLE_UNASSIGNED = "UNASSIGNED"
    }
}
