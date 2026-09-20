package com.hadietou.poulailler.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hadietou.poulailler.data.FarmValidation
import com.hadietou.poulailler.data.Invitation
import com.hadietou.poulailler.repository.FirebaseRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ResponsableViewModel(application: Application) : AndroidViewModel(application) {

    private val firebaseRepo = FirebaseRepository()

    val agents = MutableLiveData<List<Map<String, Any>>>()
    val loginHistory = MutableLiveData<List<Map<String, Any>>>()
    val pendingInvitations = MutableLiveData<List<Invitation>>(emptyList())
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

    fun observePendingInvitations() {
        viewModelScope.launch {
            firebaseRepo.getFarmPendingInvitationsFlow().collectLatest { pendingInvitations.postValue(it) }
        }
    }

    fun cancelInvitation(invitation: Invitation) {
        viewModelScope.launch {
            try {
                firebaseRepo.cancelInvitation(invitation)
            } catch (e: Exception) {
                createAgentStatus.postValue(Pair(false, e.message ?: "Impossible d'annuler l'invitation"))
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

    fun checkUserStatus() {
        viewModelScope.launch {
            isUserPending.postValue(firebaseRepo.getFarmValidation()?.status == FarmValidation.STATUS_PENDING)
            
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

}
