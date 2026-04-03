package com.example.poulailler_copilot.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.poulailler_copilot.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.*

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private val _farmIdFlow = MutableStateFlow<String?>(null)
        val farmIdFlow: StateFlow<String?> = _farmIdFlow.asStateFlow()
    }

    suspend fun getFarmId(): String? {
        val current = _farmIdFlow.value
        if (current != null) return current
        
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val userDoc = db.collection("users").document(uid).get().await()
            val id = userDoc.getString("farmId")
            if (id != null) {
                _farmIdFlow.value = id
            }
            id
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun requireFarmId(): String {
        return getFarmId() ?: throw Exception("ID de ferme introuvable.")
    }

    fun logout() {
        auth.signOut()
        _farmIdFlow.value = null
    }

    suspend fun createFarmExtended(
        farmName: String, hensCount: Int, henBreed: String,
        arrivalDate: Long, birthDate: Long, currency: String
    ): String {
        val uid = auth.currentUser?.uid ?: throw Exception("Non connecté")
        val farmCode = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        val farmRef = db.collection("fermes").document()
        val farmId = farmRef.id
        
        val farmData = hashMapOf("id" to farmId, "name" to farmName, "code" to farmCode, "ownerId" to uid)
        farmRef.set(farmData).await()
        
        val initialFarmInfo = hashMapOf(
            "farmName" to farmName, "hensCount" to hensCount, "henBreed" to henBreed,
            "arrivalDate" to arrivalDate, "chickBirthDate" to birthDate,
            "currency" to currency, "setupDate" to System.currentTimeMillis()
        )
        db.collection("fermes").document(farmId).collection("config").document("farm_info").set(initialFarmInfo).await()
        
        db.collection("users").document(uid).set(hashMapOf("farmId" to farmId, "role" to "RESPONSABLE", "active" to true), SetOptions.merge()).await()
        
        _farmIdFlow.value = farmId
        return farmCode
    }

    suspend fun joinFarm(farmCode: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val farmQuery = db.collection("fermes").whereEqualTo("code", farmCode.uppercase().trim()).get().await()
        if (farmQuery.isEmpty) return false
        
        val farmId = farmQuery.documents[0].id
        val userLink = hashMapOf("farmId" to farmId, "role" to "AGENT", "active" to true)
        db.collection("users").document(uid).set(userLink, SetOptions.merge()).await()
        _farmIdFlow.value = farmId
        return true
    }

    suspend fun getUserProfile(uid: String): User? = try {
        val doc = db.collection("users").document(uid).get().await()
        if (doc.exists()) {
            val fId = doc.getString("farmId")
            if (fId != null) _farmIdFlow.value = fId
            User(0L, uid, doc.getString("username") ?: "Utilisateur", "", doc.getString("role") ?: "AGENT", doc.getBoolean("active") ?: true, fId)
        } else null
    } catch (e: Exception) { null }

    suspend fun getCurrentUserProfile(): User? = auth.currentUser?.uid?.let { getUserProfile(it) }

    suspend fun createUserProfile(uid: String, username: String, email: String, role: String) {
        val data = hashMapOf("username" to username, "email" to email, "role" to role, "active" to true)
        db.collection("users").document(uid).set(data, SetOptions.merge()).await()
    }

    suspend fun getFarmInfo(): FarmInfo? {
        val id = getFarmId() ?: return null
        return try {
            val s = db.collection("fermes").document(id).collection("config").document("farm_info").get().await()
            if (s.exists()) {
                FarmInfo(1, s.getString("farmName") ?: "", s.getLong("hensCount")?.toInt() ?: 0, s.getString("henBreed") ?: "", s.getLong("arrivalDate") ?: 0L, s.getLong("chickBirthDate") ?: 0L, s.getLong("setupDate") ?: System.currentTimeMillis(), s.getString("feedInfo") ?: "", s.getLong("mortality")?.toInt() ?: 0, s.getDouble("expenses") ?: 0.0, s.getString("currency") ?: "MRU")
            } else null
        } catch (e: Exception) { null }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getFarmInfoFlow(): Flow<FarmInfo?> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(null)
        else callbackFlow {
            val sub = db.collection("fermes").document(id).collection("config").document("farm_info")
                .addSnapshotListener { s, e ->
                    val info = if (s != null && s.exists()) {
                        FarmInfo(1, s.getString("farmName") ?: "", s.getLong("hensCount")?.toInt() ?: 0, s.getString("henBreed") ?: "", s.getLong("arrivalDate") ?: 0L, s.getLong("chickBirthDate") ?: 0L, s.getLong("setupDate") ?: System.currentTimeMillis(), s.getString("feedInfo") ?: "", s.getLong("mortality")?.toInt() ?: 0, s.getDouble("expenses") ?: 0.0, s.getString("currency") ?: "MRU")
                    } else null
                    trySend(info)
                }
            awaitClose { sub.remove() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getEggEntriesFlow(): Flow<List<EggEntry>> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("egg_entries").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        EggEntry(0L, doc.getString("userId") ?: "", doc.getLong("date") ?: 0L, doc.getLong("eggsCount")?.toInt() ?: 0, doc.getLong("brokenEggsCount")?.toInt() ?: 0, doc.getString("remarks"), doc.id, id)
                    }?.sortedByDescending { it.date } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getMortalityFlow(): Flow<List<Mortality>> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("mortality").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        Mortality(0L, doc.getLong("count")?.toInt() ?: 0, doc.getLong("date") ?: 0L, doc.id, id)
                    }?.sortedByDescending { it.date } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getSalesFlow(): Flow<List<EggSale>> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("sales").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        EggSale(0L, doc.getString("userId") ?: "", doc.getLong("date") ?: 0L, doc.getLong("quantity")?.toInt() ?: 0, doc.getDouble("pricePerUnit") ?: 0.0, doc.getDouble("totalPrice") ?: 0.0, doc.getString("buyer"), doc.getString("phoneNumber"), doc.id, id)
                    }?.sortedByDescending { it.date } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getExpensesFlow(): Flow<List<Expense>> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("expenses").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        Expense(0L, doc.getLong("date") ?: 0L, doc.getString("category") ?: "", doc.getDouble("amount") ?: 0.0, doc.getDouble("quantityKg"), doc.getString("description"), doc.id, id)
                    }?.sortedByDescending { it.date } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getVaccinesFlow(): Flow<List<VaccineEntry>> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("vaccines").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        VaccineEntry(0L, doc.getString("name") ?: "", doc.getLong("date") ?: 0L, doc.getString("remarks"), doc.id, id)
                    }?.sortedByDescending { it.date } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    suspend fun saveFarmInfo(info: FarmInfo) {
        val fId = requireFarmId()
        db.collection("fermes").document(fId).collection("config").document("farm_info").set(hashMapOf("farmName" to info.farmName, "hensCount" to info.hensCount, "henBreed" to info.henBreed, "arrivalDate" to info.arrivalDate, "chickBirthDate" to info.chickBirthDate, "currency" to info.currency), SetOptions.merge()).await()
    }

    suspend fun addEggEntry(e: EggEntry) {
        val fId = requireFarmId()
        db.collection("egg_entries").add(hashMapOf("userId" to auth.currentUser?.uid, "date" to e.date, "eggsCount" to e.eggsCount, "brokenEggsCount" to e.brokenEggsCount, "remarks" to e.remarks, "farmId" to fId)).await()
    }

    suspend fun addMortality(c: Int, d: Long) {
        val fId = requireFarmId()
        db.collection("mortality").add(hashMapOf("count" to c, "date" to d, "farmId" to fId)).await()
    }

    suspend fun addSale(s: EggSale) {
        val fId = requireFarmId()
        db.collection("sales").add(hashMapOf("userId" to auth.currentUser?.uid, "date" to s.date, "quantity" to s.quantity, "pricePerUnit" to s.pricePerUnit, "totalPrice" to s.totalPrice, "buyer" to s.buyer, "phoneNumber" to s.phoneNumber, "farmId" to fId)).await()
    }

    suspend fun addExpense(e: Expense) {
        val fId = requireFarmId()
        db.collection("expenses").add(hashMapOf("date" to e.date, "category" to e.category, "description" to e.description, "amount" to e.amount, "quantityKg" to e.quantityKg, "farmId" to fId)).await()
    }

    suspend fun addVaccine(v: VaccineEntry) {
        val fId = requireFarmId()
        db.collection("vaccines").add(hashMapOf("name" to v.name, "date" to v.date, "remarks" to v.remarks, "farmId" to fId)).await()
    }

    suspend fun updateEggEntry(e: EggEntry) { e.firestoreId?.let { db.collection("egg_entries").document(it).update(hashMapOf("date" to e.date, "eggsCount" to e.eggsCount, "brokenEggsCount" to e.brokenEggsCount, "remarks" to e.remarks) as Map<String, Any>).await() } }
    suspend fun updateMortality(m: Mortality) { m.firestoreId?.let { db.collection("mortality").document(it).update(hashMapOf("count" to m.count, "date" to m.date) as Map<String, Any>).await() } }
    suspend fun updateSale(s: EggSale) { s.firestoreId?.let { db.collection("sales").document(it).update(hashMapOf("date" to s.date, "quantity" to s.quantity, "pricePerUnit" to s.pricePerUnit, "totalPrice" to s.totalPrice, "buyer" to s.buyer, "phoneNumber" to s.phoneNumber) as Map<String, Any>).await() } }
    suspend fun updateVaccine(v: VaccineEntry) { v.firestoreId?.let { db.collection("vaccines").document(it).update(hashMapOf("name" to v.name, "date" to v.date, "remarks" to v.remarks) as Map<String, Any>).await() } }
    suspend fun updateExpense(e: Expense) { e.firestoreId?.let { db.collection("expenses").document(it).update(hashMapOf("date" to e.date, "category" to e.category, "description" to e.description, "amount" to e.amount, "quantityKg" to e.quantityKg) as Map<String, Any>).await() } }

    suspend fun deleteEggEntry(id: String) = db.collection("egg_entries").document(id).delete().await()
    suspend fun deleteMortality(id: String) = db.collection("mortality").document(id).delete().await()
    suspend fun deleteSale(id: String) = db.collection("sales").document(id).delete().await()
    suspend fun deleteVaccine(id: String) = db.collection("vaccines").document(id).delete().await()
    suspend fun deleteExpense(id: String) = db.collection("expenses").document(id).delete().await()

    suspend fun getFarmCode(): String? = requireFarmId().let { db.collection("fermes").document(it).get().await().getString("code") }
    suspend fun recordLogin(uid: String, username: String) { 
        getFarmId()?.let { fId -> db.collection("login_history").add(hashMapOf("uid" to uid, "username" to username, "timestamp" to System.currentTimeMillis(), "farmId" to fId)).await() }
    }
    suspend fun updateUserStatus(uid: String, active: Boolean) { db.collection("users").document(uid).update("active", active).await() }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getAllUsersFlow(): Flow<List<Map<String, Any>>> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("users").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["uid"] = doc.id
                        data as Map<String, Any>
                    } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getLoginHistoryFlow(): Flow<List<Map<String, Any>>> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("login_history").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e -> trySend(s?.documents?.mapNotNull { it.data } ?: emptyList()) }
            awaitClose { sub.remove() }
        }
    }

    fun shareInviteCode(context: Context, farmCode: String) {
        // Lien vers l'APK sur Google Drive pour les tests
        val apkDownloadLink = "https://drive.google.com/file/d/1oC4RejQmRnzNCDyOxV6GH50A8AHCR7Sf/view?usp=sharing"
        val appLink = "poulaillerpro://join?code=$farmCode"
        
        val message = """
            🐔 Rejoignez mon exploitation sur l'application KOURKOUROU!
            
            1. Téléchargez et installez l'application (Fichier APK) :
            $apkDownloadLink
            
            2. Une fois installée, utilisez ce code pour rejoindre ma ferme :
            👉 $farmCode 👈
            
          
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Invitation exploitation")
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Partager l'invitation"))
    }
}
