package com.hadietou.poulailler.ui

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hadietou.poulailler.repository.FirebaseRepository
import com.hadietou.poulailler.util.PasswordHasher
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val firebaseRepo = FirebaseRepository()

    // État de la vérification SMS en cours (voir startPhoneVerification / verifyOtpForSignup).
    private var verificationId: String? = null

    /**
     * Ce numéro tenait-il déjà un compte avant cette vérification, ou vient-il d'être créé ?
     * Un compte téléphone n'a pas de mot de passe : se reconnecter plus tard repasse par ce
     * même écran d'inscription (un nouveau code SMS, jamais un mot de passe) — ce indicateur
     * sert à adapter le message affiché après coup (LoginActivity) plutôt que de prétendre à
     * tort qu'un nouveau compte est créé à chaque connexion.
     */
    var lastPhoneLoginWasNewAccount: Boolean = false
        private set

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

    // ── Inscription par numéro de téléphone (nouveau parcours) ──────────────────────────
    // Voir la note d'architecture dans FirebaseRepository : le numéro de téléphone, vérifié
    // par SMS, est désormais l'identifiant des nouveaux comptes. Contrairement à l'ancienne
    // inscription par email, aucun mot de passe n'est demandé ici — la preuve de possession
    // du numéro (code SMS) tient lieu d'authentification, comme le veut Firebase Phone Auth.

    /**
     * Démarre la vérification SMS pour [phoneNumber] (format E.164, ex: "+22212345678").
     * [onAutoVerified] peut être appelé directement sur certains appareils (récupération
     * automatique du SMS par Google Play Services), sans même passer par [onCodeSent].
     */
    fun startPhoneVerification(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: () -> Unit,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
        onFailed: (String) -> Unit
    ) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    onAutoVerified(credential)
                }
                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e("LoginVM", "Phone verification failed", e)
                    onFailed(e.message ?: "Échec de la vérification du numéro.")
                }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    onCodeSent()
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    /** À appeler une fois le code SMS saisi par l'utilisateur, pour finaliser l'inscription. */
    fun verifyOtpForSignup(code: String, username: String, phoneNumber: String, email: String?, onResult: (Boolean, String, String) -> Unit) {
        val vId = verificationId
        if (vId == null) {
            onResult(false, "Session expirée, redemandez un code.", "")
            return
        }
        signInWithPhoneCredential(PhoneAuthProvider.getCredential(vId, code), username, phoneNumber, email, onResult)
    }

    /** Cas de récupération automatique du SMS (pas de saisie manuelle du code nécessaire). */
    fun signUpWithAutoVerifiedCredential(credential: PhoneAuthCredential, username: String, phoneNumber: String, email: String?, onResult: (Boolean, String, String) -> Unit) {
        signInWithPhoneCredential(credential, username, phoneNumber, email, onResult)
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential, username: String, phoneNumber: String, email: String?, onResult: (Boolean, String, String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = auth.signInWithCredential(credential).await()
                val user = result.user
                if (user != null) {
                    val existing = firebaseRepo.getUserProfile(user.uid)
                    lastPhoneLoginWasNewAccount = existing == null
                    if (existing == null) {
                        firebaseRepo.createMinimalUserProfile(user.uid, username, phoneNumber, email)
                    }
                    handleSuccessfulAuth(user.uid, existing?.email ?: "", onResult)
                } else onResult(false, "Erreur de connexion", "")
            } catch (e: Exception) {
                Log.e("LoginVM", "Phone sign-in error", e)
                onResult(false, e.message ?: "Code invalide.", "")
            }
        }
    }

    /**
     * Lie le numéro vérifié au compte ACTUELLEMENT connecté (voir
     * [FirebaseRepository.linkPhoneToCurrentAccount]) : permet à un utilisateur existant
     * (créé par email) de se connecter désormais aussi par téléphone, sans perdre ses données.
     */
    fun verifyOtpForLinking(code: String, phoneNumber: String, onResult: (Boolean, String) -> Unit) {
        val vId = verificationId
        if (vId == null) {
            onResult(false, "Session expirée, redemandez un code.")
            return
        }
        viewModelScope.launch {
            try {
                firebaseRepo.linkPhoneToCurrentAccount(PhoneAuthProvider.getCredential(vId, code), phoneNumber)
                onResult(true, "Numéro de téléphone ajouté à votre compte.")
            } catch (e: Exception) {
                Log.e("LoginVM", "Phone linking error", e)
                onResult(false, e.message ?: "Erreur lors de la liaison du numéro.")
            }
        }
    }
}
