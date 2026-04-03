package com.example.poulailler_copilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.poulailler_copilot.repository.FirebaseRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ResponsableViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseRepo = FirebaseRepository()

    val agents = MutableLiveData<List<Map<String, Any>>>()
    val loginHistory = MutableLiveData<List<Map<String, Any>>>()
    val farmCode = MutableLiveData<String>()

    fun observeAgents() {
        viewModelScope.launch {
            firebaseRepo.getAllUsersFlow().collectLatest { list ->
                agents.postValue(list)
            }
        }
    }

    fun observeLoginHistory() {
        viewModelScope.launch {
            firebaseRepo.getLoginHistoryFlow().collectLatest { list ->
                loginHistory.postValue(list)
            }
        }
    }

    fun loadFarmCode() {
        viewModelScope.launch {
            val code = firebaseRepo.getFarmCode()
            farmCode.postValue(code ?: "------")
        }
    }

    fun setAgentActive(uid: String, active: Boolean) {
        viewModelScope.launch {
            firebaseRepo.updateUserStatus(uid, active)
        }
    }
}
