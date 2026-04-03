package com.example.poulailler_copilot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.EggEntry
import com.example.poulailler_copilot.repository.EggRepository
import com.example.poulailler_copilot.repository.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val eggRepo = EggRepository(db.eggEntryDao())
    private val firebaseRepo = FirebaseRepository()

    val entries = MutableLiveData<List<EggEntry>>()

    init {
        observeFirebaseEntries()
    }

    private fun observeFirebaseEntries() {
        viewModelScope.launch {
            firebaseRepo.getEggEntriesFlow().collectLatest { list ->
                entries.postValue(list)
            }
        }
    }

    fun addEntry(userId: String, date: Long, eggs: Int, broken: Int, remarks: String?, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = EggEntry(
                userId = userId,
                date = date,
                eggsCount = eggs,
                brokenEggsCount = broken,
                remarks = remarks
            )
            // Local save
            eggRepo.addEntry(userId, date, eggs, broken, remarks)
            // Firebase save
            firebaseRepo.addEggEntry(entry)

            withContext(Dispatchers.Main) { onDone() }
        }
    }
}
