package com.hadietou.poulailler.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hadietou.poulailler.repository.FirebaseRepository
import com.hadietou.poulailler.util.PasswordHasher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val firebaseRepo = FirebaseRepository()

    /**
     * Reprend automatiquement une session Firebase déjà active (ex: après une simple
     * mise à jour/réinstallation de l'app) au lieu de forcer une reconnexion manuelle.
     * Ne fait rien si l'utilisateur s'est explicitement déconnecté (currentUser == null).
     */
    fun checkExistingSession(onResult: (Boolean, String, String) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false, "", "")
            return
        }
        viewModelScope.launch {
            try {
                handleSuccessfulAuth(user.uid, user.email ?: "", onResult)
            } catch (e: Exception) {
                Log.e("LoginVM", "checkExistingSession error", e)
                onResult(false, "", "")
            }
        }
    }

    fun login(emailInput: String, password: String, onResult: (Boolean, String, String) -> Unit) {
        val email = emailInput.trim().lowercase()
        
        viewModelScope.launch {
            try {
                // 1. On tente la connexion normale
                try {
                    val result = auth.signInWithEmailAndPassword(email, password).await()
                    val user = result.user
                    if (user != null) {
                        handleSuccessfulAuth(user.uid, email, onResult)
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.d("LoginVM", "Normal auth failed, checking pre-created...")
                }

                // 2. Vérification compte pré-créé
                checkPreCreatedAgent(email, password, onResult)

            } catch (e: Exception) {
                Log.e("LoginVM", "Global login error", e)
                onResult(false, "Erreur de connexion", "")
            }
        }
    }

    private suspend fun handleSuccessfulAuth(uid: String, email: String, onResult: (Boolean, String, String) -> Unit) {
        val profile = firebaseRepo.getUserProfile(uid)
        if (profile != null) {
            // Mise à jour de l'email si manquant ou différent pour assurer le bon fonctionnement des alertes
            if (profile.email != email) {
                firebaseRepo.createUserProfile(uid, profile.username, email, profile.role, profile.isPending)
            }
            
            if (!profile.active) {
                auth.signOut()
                onResult(false, "COMPTE_DESACTIVE", "")
                return
            }
            if (profile.role == "RESPONSABLE" && profile.isPending) {
                val now = System.currentTimeMillis()
                val twentyDaysMillis = 20L * 24 * 60 * 60 * 1000
                if (now - profile.createdAt > twentyDaysMillis) {
                    auth.signOut()
                    onResult(false, "VALIDATION_REQUIS_EXPIRRE", "")
                    return
                }
            }
            onResult(true, profile.role, uid)
        } else {
            // Créer un profil si manquant (cas rare)
            firebaseRepo.createUserProfile(uid, email.split("@")[0], email, "RESPONSABLE")
            onResult(true, "RESPONSABLE", uid)
        }
    }

    private suspend fun checkPreCreatedAgent(email: String, password: String, onResult: (Boolean, String, String) -> Unit) {
        try {
            val doc = db.collection("users").document(email).get().await()
            
            if (doc.exists() && doc.getBoolean("isPreCreated") == true) {
                val storedPass = doc.getString("password")
                // Le mot de passe stocké est haché (PasswordHasher) depuis ce correctif ;
                // looksHashed() permet de rester compatible avec d'anciens documents encore
                // en clair (créés avant), sans casser leur première connexion.
                val matches = storedPass != null && (
                    if (PasswordHasher.looksHashed(storedPass)) PasswordHasher.verify(password, storedPass)
                    else storedPass == password
                )
                if (matches) {
                    val createRes = auth.createUserWithEmailAndPassword(email, password).await()
                    val newUid = createRes.user?.uid
                    
                    if (newUid != null) {
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["isPreCreated"] = false
                        data.remove("password")
                        
                        db.collection("users").document(newUid).set(data).await()
                        db.collection("users").document(email).delete().await()
                        
                        onResult(true, "AGENT", newUid)
                    } else onResult(false, "Erreur création compte", "")
                } else {
                    onResult(false, "Mot de passe incorrect", "")
                }
            } else {
                onResult(false, "Identifiants incorrects", "")
            }
        } catch (e: Exception) {
            Log.e("LoginVM", "PreCreated Error", e)
            onResult(false, "Vérification impossible. Vérifiez votre connexion.", "")
        }
    }

    fun register(
        emailInput: String, 
        password: String, 
        username: String, 
        role: String, 
        farmName: String, 
        farmCode: String, 
        currency: String = "MRU", 
        country: String? = null,
        city: String? = null,
        onResult: (Boolean, String) -> Unit
    ) {
        val email = emailInput.trim().lowercase()
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val uid = result.user?.uid
                if (uid != null) {
                    if (role == "RESPONSABLE") {
                        val code = firebaseRepo.createFarmExtended(farmName, currency, username, email, country, city)
                        onResult(true, "Ferme créée ! Code : $code")
                    } else {
                        if (firebaseRepo.joinFarm(farmCode)) {
                            firebaseRepo.createUserProfile(uid, username, email, "AGENT")
                            onResult(true, "Compte lié !")
                        } else onResult(false, "Code ferme invalide")
                    }
                }
            } catch (e: Exception) { onResult(false, e.message ?: "Erreur") }
        }
    }
}
