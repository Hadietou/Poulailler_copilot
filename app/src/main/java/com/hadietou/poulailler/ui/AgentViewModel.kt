package com.hadietou.poulailler.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hadietou.poulailler.data.EggEntry
import com.hadietou.poulailler.repository.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseRepo = FirebaseRepository()

    val entries = MutableLiveData<List<EggEntry>>()
    val isAccessBlocked = MutableLiveData<Boolean>(false)

    init {
        observeFirebaseEntries()
        checkAccessStatus()
    }

    private fun observeFirebaseEntries() {
        viewModelScope.launch {
            firebaseRepo.getEggEntriesFlow().collectLatest { list ->
                entries.postValue(list)
            }
        }
    }

    fun checkAccessStatus() {
        viewModelScope.launch {
            val blocked = firebaseRepo.isFarmAccessBlocked()
            isAccessBlocked.postValue(blocked)
        }
    }

    fun addEntry(userId: String, date: Long, eggs: Int, broken: Int, remarks: String?, batchId: String?, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Double vérification de sécurité
                if (firebaseRepo.isFarmAccessBlocked()) {
                    return@launch
                }

                val entry = EggEntry(
                    userId = userId,
                    date = date,
                    eggsCount = eggs,
                    brokenEggsCount = broken,
                    remarks = remarks,
                    batchId = batchId
                )
                // Firebase save
                firebaseRepo.addEggEntry(entry)

                withContext(Dispatchers.Main) { onDone() }
            } catch (e: Exception) {
                // Géré par checkAndThrowIfBlocked dans le repo
            }
        }
    }
}
