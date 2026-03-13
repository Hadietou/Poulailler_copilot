package com.example.poulailler_copilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.EggEntry
import com.example.poulailler_copilot.repository.EggRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val eggRepo = EggRepository(db.eggEntryDao())

    val entries = MutableLiveData<List<EggEntry>>()

    fun loadEntries(userId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = eggRepo.getByUser(userId)
            withContext(Dispatchers.Main) {
                entries.value = list
            }
        }
    }

    fun addEntry(userId: Long, date: Long, eggs: Int, broken: Int, remarks: String?, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            eggRepo.addEntry(userId, date, eggs, broken, remarks)
            loadEntries(userId)
            withContext(Dispatchers.Main) { onDone() }
        }
    }
}
