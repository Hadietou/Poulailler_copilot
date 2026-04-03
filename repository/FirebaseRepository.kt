package com.example.poulailler_copilot.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.poulailler_copilot.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    companion object {
        private var cachedFarmId: String? = null
    }

    suspend fun getFarmId(): String? {
        if (cachedFarmId != null) return cachedFarmId
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val userDoc = db.collection("users").document(uid).get().await()
            val id = userDoc.getString("farmId")
            cachedFarmId = id
            id
        } catch (e: Exception) {
            Log.e("FirebaseRepo", "Error getting farmId", e)
            null
        }
    }

    suspend fun createFarm(farmName: String): String {
        val uid = auth.currentUser?.uid ?: throw Exception("Non connecté")
        val farmCode = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        val farmRef = db.collection("fermes").document()
        val farmId = farmRef.id
        
        val farmData = hashMapOf("id" to farmId, "name" to farmName, "code" to farmCode, "ownerId" to uid)
        farmRef.set(farmData).await()
        
        val userLink = hashMapOf("farmId" to farmId, "role" to "RESPONSABLE", "active" to true)
        db.collection("users").document(uid).set(userLink, SetOptions.merge()).await()
        cachedFarmId = farmId
        return farmCode
    }

    suspend fun joinFarm(farmCode: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val farmQuery = db.collection("fermes").whereEqualTo("code", farmCode.uppercase().trim()).get().await()
        if (farmQuery.isEmpty) return false
        
        val farmId = farmQuery.documents[0].id
        val userLink = hashMapOf("farmId" to farmId, "role" to "AGENT", "active" to true)
        db.collection("users").document(uid).set(userLink, SetOptions.merge()).await()
        cachedFarmId = farmId
        return true
    }

    suspend fun getUserProfile(uid: String): User? {
        return try {
            val doc = db.collection("users").document(uid).get().await()
            if (doc.exists()) {
                User(
                    id = 0,
                    username = doc.getString("username") ?: doc.getString("email")?.split("@")?.get(0) ?: "Utilisateur",
                    password = "",
                    role = doc.getString("role") ?: "AGENT",
                    active = doc.getBoolean("active") ?: true,
                    farmId = doc.getString("farmId")
                )
            } else null
        } catch (e: Exception) { null }
    }

    suspend fun getCurrentUserProfile(): User? {
        val uid = auth.currentUser?.uid ?: return null
        return getUserProfile(uid)
    }

    suspend fun createUserProfile(uid: String, username: String, email: String, role: String) {
        val data = hashMapOf("username" to username, "email" to email, "role" to role, "active" to true, "uid" to uid)
        db.collection("users").document(uid).set(data, SetOptions.merge()).await()
    }

    suspend fun recordLogin(uid: String, username: String) {
        val fId = getFarmId()
        val data = hashMapOf("uid" to uid, "username" to username, "timestamp" to System.currentTimeMillis(), "farmId" to fId)
        db.collection("login_history").add(data).await()
    }

    fun getLoginHistoryFlow(): Flow<List<Map<String, Any>>> = callbackFlow {
        val fId = getFarmId() ?: ""
        val sub = db.collection("login_history").whereEqualTo("farmId", fId).orderBy("timestamp", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { s, e ->
                trySend(s?.documents?.mapNotNull { it.data } ?: emptyList())
            }
        awaitClose { sub.remove() }
    }

    fun getAllUsersFlow(): Flow<List<Map<String, Any>>> = callbackFlow {
        val fId = getFarmId() ?: ""
        val sub = db.collection("users").whereEqualTo("farmId", fId)
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

    suspend fun updateUserStatus(uid: String, active: Boolean) {
        db.collection("users").document(uid).update("active", active).await()
    }

    fun shareInviteCode(context: Context, farmCode: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Invitation à rejoindre ma ferme")
            val message = "Rejoignez ma ferme sur l'application Poulailler !\n\nCode de la ferme : $farmCode\nLien d'installation : https://play.google.com/store/apps/details?id=${context.packageName}"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Partager via"))
    }

    // --- Data Flows ---
    fun getFarmInfoFlow(): Flow<FarmInfo?> = callbackFlow {
        val fId = getFarmId() ?: ""
        if (fId.isEmpty()) { trySend(null); return@callbackFlow }
        val sub = db.collection("fermes").document(fId).collection("config").document("farm_info")
            .addSnapshotListener { s, e ->
                val info = if (s != null && s.exists()) {
                    FarmInfo(1, s.getString("farmName") ?: "", s.getLong("hensCount")?.toInt() ?: 0, s.getString("henBreed") ?: "", s.getLong("arrivalDate") ?: 0L, s.getLong("chickBirthDate") ?: 0L, s.getString("currency") ?: "MRU")
                } else null
                trySend(info)
            }
        awaitClose { sub.remove() }
    }

    suspend fun getFarmInfo(): FarmInfo? {
        val fId = getFarmId() ?: return null
        return try {
            val s = db.collection("fermes").document(fId).collection("config").document("farm_info").get().await()
            if (s.exists()) {
                FarmInfo(1, s.getString("farmName") ?: "", s.getLong("hensCount")?.toInt() ?: 0, s.getString("henBreed") ?: "", s.getLong("arrivalDate") ?: 0L, s.getLong("chickBirthDate") ?: 0L, s.getString("currency") ?: "MRU")
            } else null
        } catch (e: Exception) { null }
    }

    suspend fun saveFarmInfo(info: FarmInfo) {
        val fId = getFarmId() ?: return
        val data = hashMapOf("farmName" to info.farmName, "hensCount" to info.hensCount, "henBreed" to info.henBreed, "arrivalDate" to info.arrivalDate, "chickBirthDate" to info.chickBirthDate, "currency" to info.currency)
        db.collection("fermes").document(fId).collection("config").document("farm_info").set(data).await()
    }

    suspend fun getFarmCode(): String? {
        val fId = getFarmId() ?: return null
        return try {
            db.collection("fermes").document(fId).get().await().getString("code")
        } catch (e: Exception) { null }
    }

    // --- Egg Entries ---
    fun getEggEntriesFlow(): Flow<List<EggEntry>> = callbackFlow {
        val fId = getFarmId() ?: ""
        val sub = db.collection("egg_entries").whereEqualTo("farmId", fId).orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { s, e ->
                val list = s?.documents?.mapNotNull { doc ->
                    EggEntry(0, 0, doc.getLong("date") ?: 0, doc.getLong("eggsCount")?.toInt() ?: 0, doc.getLong("brokenEggsCount")?.toInt() ?: 0, doc.getString("remarks"), doc.id, fId)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { sub.remove() }
    }
    suspend fun addEggEntry(e: EggEntry) = db.collection("egg_entries").add(hashMapOf("userId" to auth.currentUser?.uid, "date" to e.date, "eggsCount" to e.eggsCount, "brokenEggsCount" to e.brokenEggsCount, "remarks" to e.remarks, "farmId" to getFarmId())).await()
    suspend fun updateEggEntry(e: EggEntry) { e.firestoreId?.let { db.collection("egg_entries").document(it).update(hashMapOf("date" to e.date, "eggsCount" to e.eggsCount, "brokenEggsCount" to e.brokenEggsCount, "remarks" to e.remarks) as Map<String, Any>).await() } }
    suspend fun deleteEggEntry(id: String) = db.collection("egg_entries").document(id).delete().await()

    // --- Mortality ---
    fun getMortalityFlow(): Flow<List<Mortality>> = callbackFlow {
        val fId = getFarmId() ?: ""
        val sub = db.collection("mortality").whereEqualTo("farmId", fId).orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { s, e ->
                val list = s?.documents?.mapNotNull { doc -> Mortality(0, doc.getLong("count")?.toInt() ?: 0, doc.getLong("date") ?: 0, doc.id, fId) } ?: emptyList()
                trySend(list)
            }
        awaitClose { sub.remove() }
    }
    suspend fun addMortality(c: Int, d: Long) = db.collection("mortality").add(hashMapOf("count" to c, "date" to d, "farmId" to getFarmId())).await()
    suspend fun updateMortality(m: Mortality) { m.firestoreId?.let { db.collection("mortality").document(it).update(hashMapOf("count" to m.count, "date" to m.date) as Map<String, Any>).await() } }
    suspend fun deleteMortality(id: String) = db.collection("mortality").document(id).delete().await()

    // --- Sales ---
    fun getSalesFlow(): Flow<List<EggSale>> = callbackFlow {
        val fId = getFarmId() ?: ""
        val sub = db.collection("sales").whereEqualTo("farmId", fId).orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { s, e ->
                val list = s?.documents?.mapNotNull { doc ->
                    EggSale(0, 0, doc.getLong("date") ?: 0, doc.getLong("quantity")?.toInt() ?: 0, doc.getDouble("pricePerUnit") ?: 0.0, doc.getDouble("totalPrice") ?: 0.0, doc.getString("buyer"), doc.getString("phoneNumber"), doc.id, fId)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { sub.remove() }
    }
    suspend fun addSale(s: EggSale) = db.collection("sales").add(hashMapOf("userId" to auth.currentUser?.uid, "date" to s.date, "quantity" to s.quantity, "pricePerUnit" to s.pricePerUnit, "totalPrice" to s.totalPrice, "buyer" to s.buyer, "phoneNumber" to s.phoneNumber, "farmId" to getFarmId())).await()
    suspend fun updateSale(s: EggSale) { s.firestoreId?.let { db.collection("sales").document(it).update(hashMapOf("date" to s.date, "quantity" to s.quantity, "pricePerUnit" to s.pricePerUnit, "totalPrice" to s.totalPrice, "buyer" to s.buyer, "phoneNumber" to s.phoneNumber) as Map<String, Any>).await() } }
    suspend fun deleteSale(id: String) = db.collection("sales").document(id).delete().await()

    // --- Vaccines ---
    fun getVaccinesFlow(): Flow<List<VaccineEntry>> = callbackFlow {
        val fId = getFarmId() ?: ""
        val sub = db.collection("vaccines").whereEqualTo("farmId", fId).orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { s, e ->
                val list = s?.documents?.mapNotNull { doc -> VaccineEntry(0, doc.getString("name") ?: "", doc.getLong("date") ?: 0, doc.getString("remarks"), doc.id, fId) } ?: emptyList()
                trySend(list)
            }
        awaitClose { sub.remove() }
    }
    suspend fun addVaccine(v: VaccineEntry) = db.collection("vaccines").add(hashMapOf("name" to v.name, "date" to v.date, "remarks" to v.remarks, "farmId" to getFarmId())).await()
    suspend fun updateVaccine(v: VaccineEntry) { v.firestoreId?.let { db.collection("vaccines").document(it).update(hashMapOf("name" to v.name, "date" to v.date, "remarks" to v.remarks) as Map<String, Any>).await() } }
    suspend fun deleteVaccine(id: String) = db.collection("vaccines").document(id).delete().await()

    // --- Expenses ---
    fun getExpensesFlow(): Flow<List<Expense>> = callbackFlow {
        val fId = getFarmId() ?: ""
        val sub = db.collection("expenses").whereEqualTo("farmId", fId).orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { s, e ->
                val list = s?.documents?.mapNotNull { doc ->
                    Expense(0, doc.getLong("date") ?: 0, doc.getString("category") ?: "", doc.getDouble("amount") ?: 0.0, doc.getDouble("quantityKg"), doc.getString("description"), doc.id, fId)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { sub.remove() }
    }
    suspend fun addExpense(e: Expense) = db.collection("expenses").add(hashMapOf("date" to e.date, "category" to e.category, "description" to e.description, "amount" to e.amount, "quantityKg" to e.quantityKg, "farmId" to getFarmId())).await()
    suspend fun updateExpense(e: Expense) { e.firestoreId?.let { db.collection("expenses").document(it).update(hashMapOf("date" to e.date, "category" to e.category, "description" to e.description, "amount" to e.amount, "quantityKg" to e.quantityKg) as Map<String, Any>).await() } }
    suspend fun deleteExpense(id: String) = db.collection("expenses").document(id).delete().await()
}
