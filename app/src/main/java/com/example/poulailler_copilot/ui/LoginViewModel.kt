package com.example.poulailler_copilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.poulailler_copilot.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val firebaseRepo = FirebaseRepository()

    fun login(
        email: String, // Firebase utilise l'email, on adaptera l'UI
        password: String,
        onResult: (Boolean, String, String) -> Unit // success, role, uid
    ) {
        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                val user = result.user
                if (user != null) {
                    val profile = firebaseRepo.getUserProfile(user.uid)
                    if (profile != null && profile.active) {
                        onResult(true, profile.role, user.uid)
                    } else {
                        auth.signOut()
                        onResult(false, "", "")
                    }
                } else {
                    onResult(false, "", "")
                }
            } catch (e: Exception) {
                onResult(false, "", "")
            }
        }
    }
}
