package com.hadietou.poulailler.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hadietou.poulailler.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ResponsableViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseRepo = FirebaseRepository()
    private val db = FirebaseFirestore.getInstance()

    val agents = MutableLiveData<List<Map<String, Any>>>()
    val loginHistory = MutableLiveData<List<Map<String, Any>>>()
    val farmCode = MutableLiveData<String>()
    val farmName = MutableLiveData<String>()
    val createAgentStatus = MutableLiveData<Pair<Boolean, String>>()
    val isUserPending = MutableLiveData<Boolean>(true)
    val isAccessBlocked = MutableLiveData<Boolean>(false)

    fun observeAgents() {
        viewModelScope.launch {
            firebaseRepo.getAllUsersFlow().collectLatest { list ->
                agents.postValue(list.map { user ->
                    mapOf(
                        "uid" to user.uid,
                        "username" to user.username,
                        "role" to user.role,
                        "active" to user.active
                    )
                })
            }
        }
    }

    fun observeLoginHistory() {
        viewModelScope.launch {
            firebaseRepo.getLoginHistoryFlow().collectLatest { list ->
                loginHistory.postValue(list.map { entry ->
                    mapOf(
                        "username" to entry.username,
                        "timestamp" to entry.timestamp
                    )
                })
            }
        }
    }

    fun loadFarmCode() {
        viewModelScope.launch {
            val code = firebaseRepo.getFarmCode()
            farmCode.postValue(code ?: "------")
            
            val info = firebaseRepo.getFarmInfo()
            farmName.postValue(info?.farmName ?: "ferme")
        }
    }

    fun checkUserStatus() {
        viewModelScope.launch {
            val profile = firebaseRepo.getCurrentUserProfile()
            isUserPending.postValue(profile?.isPending ?: true)
            
            val blocked = firebaseRepo.isFarmAccessBlocked()
            isAccessBlocked.postValue(blocked)
        }
    }

    fun setAgentActive(uid: String, active: Boolean) {
        viewModelScope.launch {
            try {
                firebaseRepo.updateUserStatus(uid, active)
            } catch (e: Exception) {
                createAgentStatus.postValue(Pair(false, e.message ?: "Erreur lors de la mise à jour"))
            }
        }
    }

    fun createAgentSimplified(agentName: String, onComplete: (String, String) -> Unit) {
        viewModelScope.launch {
            try {
                // Vérification du blocage avant création
                if (firebaseRepo.isFarmAccessBlocked()) {
                    createAgentStatus.postValue(Pair(false, "Action impossible : Votre ferme doit être validée pour continuer."))
                    return@launch
                }

                val currentFarmName = farmName.value?.replace(" ", "_")?.lowercase() ?: "ferme"
                val cleanAgentName = agentName.replace(" ", "_").lowercase()
                val login = "${cleanAgentName}@${currentFarmName}.com"
                
                val password = (100000..999999).random().toString()
                
                val fId = firebaseRepo.getFarmId() ?: throw Exception("ID de ferme non trouvé")
                
                val agentData = hashMapOf(
                    "username" to agentName,
                    "email" to login,
                    "password" to password,
                    "role" to "AGENT",
                    "farmId" to fId,
                    "active" to true,
                    "isPreCreated" to true,
                    "createdAt" to System.currentTimeMillis()
                )
                
                db.collection("users").document(login).set(agentData).await()
                onComplete(login, password)
                
            } catch (e: Exception) {
                createAgentStatus.postValue(Pair(false, e.message ?: "Erreur lors de la création de l'agent"))
            }
        }
    }
}
