package com.hadietou.poulailler.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hadietou.poulailler.data.Batch
import com.hadietou.poulailler.repository.FirebaseRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BatchViewModel(application: Application) : AndroidViewModel(application) {
    private val firebaseRepo = FirebaseRepository()
    val allBatches = MutableLiveData<List<Batch>>(emptyList())
    val operationSuccess = MutableLiveData<Boolean>()

    init {
        loadBatches()
    }

    private fun loadBatches() {
        viewModelScope.launch {
            firebaseRepo.getBatchesFlow().collectLatest {
                allBatches.postValue(it)
            }
        }
    }

    fun addBatch(name: String, count: Int, breed: String, arrival: Long, birth: Long, typeLot: String) {
        viewModelScope.launch {
            try {
                val batch = Batch(
                    name = name,
                    hensCount = count,
                    henBreed = breed,
                    arrivalDate = arrival,
                    chickBirthDate = birth,
                    status = "ACTIVE",
                    typeLot = typeLot
                )
                firebaseRepo.addBatch(batch)
                operationSuccess.postValue(true)
            } catch (e: Exception) {
                operationSuccess.postValue(false)
            }
        }
    }

    fun toggleBatchStatus(batch: Batch) {
        viewModelScope.launch {
            try {
                val newStatus = if (batch.status == "ACTIVE") "ARCHIVED" else "ACTIVE"
                firebaseRepo.updateBatch(batch.copy(status = newStatus))
                operationSuccess.postValue(true)
            } catch (e: Exception) {
                operationSuccess.postValue(false)
            }
        }
    }

    fun deleteBatch(firestoreId: String) {
        viewModelScope.launch {
            try {
                firebaseRepo.deleteBatch(firestoreId)
                operationSuccess.postValue(true)
            } catch (e: Exception) {
                operationSuccess.postValue(false)
            }
        }
    }
}
