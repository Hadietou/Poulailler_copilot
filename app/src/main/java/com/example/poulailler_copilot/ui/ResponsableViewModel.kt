package com.example.poulailler_copilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.User
import com.example.poulailler_copilot.repository.EggRepository
import com.example.poulailler_copilot.repository.FarmRepository
import com.example.poulailler_copilot.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResponsableViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val userRepo = UserRepository(db.userDao())
    private val eggRepo = EggRepository(db.eggEntryDao())
    private val farmRepo = FarmRepository(db.farmInfoDao())

    val agents = MutableLiveData<List<User>>()
    val totalEggs = MutableLiveData<Int>()

    fun loadAgents() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = userRepo.getAgents()
            withContext(Dispatchers.Main) {
                agents.value = list
            }
        }
    }

    fun createAgent(username: String, password: String, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            userRepo.createAgent(username, password)
            loadAgents()
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    fun setAgentActive(id: Long, active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            userRepo.setAgentActive(id, active)
            loadAgents()
        }
    }

    fun resetPassword(id: Long, newPassword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            userRepo.resetPassword(id, newPassword)
        }
    }

    fun refreshStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val total = eggRepo.getTotalEggs()
            withContext(Dispatchers.Main) {
                totalEggs.value = total
            }
        }
    }

    fun saveFarmInfo(
        hensCount: Int,
        feedInfo: String,
        mortality: Int,
        expenses: Double,
        onDone: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            farmRepo.saveInfo(hensCount, feedInfo, mortality, expenses)
            withContext(Dispatchers.Main) { onDone() }
        }
    }
}
