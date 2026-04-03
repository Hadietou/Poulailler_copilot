package com.example.poulailler_copilot.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.poulailler_copilot.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val firebaseRepo = FirebaseRepository()

    fun login(email: String, password: String, onResult: (Boolean, String, String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    val profile = firebaseRepo.getUserProfile(user.uid)
                    if (profile != null) {
                        if (profile.active) onResult(true, profile.role, user.uid)
                        else { auth.signOut(); onResult(false, "INACTIF", "") }
                    } else {
                        onResult(false, "PROFIL_MANQUANT", user.uid)
                    }
                } else onResult(false, "ERREUR", "")
            } catch (e: Exception) {
                Log.e("LoginVM", "Login error", e)
                onResult(false, e.message ?: "Erreur", "")
            }
        }
    }

    fun register(
        email: String, 
        password: String, 
        username: String, 
        role: String, 
        farmName: String, 
        farmCode: String,
        hensCount: Int = 0,
        henBreed: String = "",
        arrivalDate: Long = 0,
        birthDate: Long = 0,
        currency: String = "MRU",
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    processProfileCreation(user.uid, email, username, role, farmName, farmCode, hensCount, henBreed, arrivalDate, birthDate, currency, onResult)
                }
            } catch (e: FirebaseAuthUserCollisionException) {
                try {
                    val loginRes = auth.signInWithEmailAndPassword(email, password).await()
                    val uid = loginRes.user?.uid
                    if (uid != null) {
                        val profile = firebaseRepo.getUserProfile(uid)
                        if (profile == null) {
                            processProfileCreation(uid, email, username, role, farmName, farmCode, hensCount, henBreed, arrivalDate, birthDate, currency, onResult)
                        } else {
                            onResult(false, "Ce compte existe déjà avec un profil valide. Connectez-vous.")
                        }
                    }
                } catch (e2: Exception) {
                    onResult(false, "Cet e-mail est déjà utilisé. Si c'est le vôtre, connectez-vous.")
                }
            } catch (e: Exception) {
                onResult(false, e.message ?: "Erreur d'inscription")
            }
        }
    }

    private suspend fun processProfileCreation(
        uid: String, 
        email: String, 
        username: String, 
        role: String, 
        farmName: String, 
        farmCode: String,
        hensCount: Int,
        henBreed: String,
        arrivalDate: Long,
        birthDate: Long,
        currency: String,
        onResult: (Boolean, String) -> Unit
    ) {
        try {
            if (role == "RESPONSABLE") {
                val code = firebaseRepo.createFarmExtended(farmName, hensCount, henBreed, arrivalDate, birthDate, currency)
                firebaseRepo.createUserProfile(uid, username, email, "RESPONSABLE")
                onResult(true, "Ferme créée ! Code : $code")
            } else {
                if (firebaseRepo.joinFarm(farmCode)) {
                    firebaseRepo.createUserProfile(uid, username, email, "AGENT")
                    onResult(true, "Compte lié à la ferme !")
                } else {
                    onResult(false, "Code de ferme invalide.")
                }
            }
        } catch (e: Exception) {
            onResult(false, "Erreur profil : ${e.message}")
        }
    }
}
