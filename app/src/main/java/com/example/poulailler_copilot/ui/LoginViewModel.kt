package com.example.poulailler_copilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val userRepo = UserRepository(db.userDao())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            userRepo.createResponsableIfNotExists()
        }
    }

    fun login(
        username: String,
        password: String,
        onResult: (Boolean, String, Long) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = userRepo.login(username, password)
            withContext(Dispatchers.Main) {
                if (user != null) {
                    onResult(true, user.role, user.id)
                } else {
                    onResult(false, "", -1)
                }
            }
        }
    }
}
