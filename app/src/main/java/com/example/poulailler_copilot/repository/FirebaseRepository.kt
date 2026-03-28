package com.example.poulailler_copilot.repository

import com.example.poulailler_copilot.data.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // --- Users ---
    suspend fun getUserProfile(uid: String): User? {
        return try {
            val doc = db.collection("users").document(uid).get().await()
            if (doc.exists()) {
                User(
                    id = 0,
                    username = doc.getString("username") ?: doc.getString("email")?.split("@")?.get(0) ?: "Utilisateur",
                    password = "",
                    role = doc.getString("role") ?: "AGENT",
                    active = doc.getBoolean("active") ?: true
                )
            } else null
        } catch (e: Exception) { null }
    }

    suspend fun createUserProfile(uid: String, username: String, email: String, role: String) {
        val data = hashMapOf(
            "username" to username,
            "email" to email,
            "role" to role,
            "active" to true,
            "uid" to uid
        )
        db.collection("users").document(uid).set(data).await()
    }

    fun getAllUsersFlow(): Flow<List<Map<String, Any>>> = callbackFlow {
        val subscription = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["uid"] = doc.id
                    if (data["username"] == null) {
                        data["username"] = data["email"]?.toString()?.split("@")?.get(0) ?: "Inconnu"
                    }
                    data as Map<String, Any>
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun updateUserStatus(uid: String, active: Boolean) {
        db.collection("users").document(uid).update("active", active).await()
    }

    // --- Login History ---
    suspend fun recordLogin(uid: String, username: String) {
        val data = hashMapOf(
            "uid" to uid,
            "username" to username,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("login_history").add(data).await()
    }

    fun getLoginHistoryFlow(): Flow<List<Map<String, Any>>> = callbackFlow {
        val subscription = db.collection("login_history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.data } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    // --- Egg Entries ---
    fun getEggEntriesFlow(): Flow<List<EggEntry>> = callbackFlow {
        val subscription = db.collection("egg_entries")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    EggEntry(
                        id = 0,
                        userId = 0,
                        date = doc.getLong("date") ?: 0L,
                        eggsCount = doc.getLong("eggsCount")?.toInt() ?: 0,
                        brokenEggsCount = doc.getLong("brokenEggsCount")?.toInt() ?: 0,
                        remarks = doc.getString("remarks")
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addEggEntry(entry: EggEntry) {
        val data = hashMapOf(
            "userId" to auth.currentUser?.uid,
            "date" to entry.date,
            "eggsCount" to entry.eggsCount,
            "brokenEggsCount" to entry.brokenEggsCount,
            "remarks" to entry.remarks
        )
        db.collection("egg_entries").add(data).await()
    }

    // --- Mortality ---
    fun getMortalityFlow(): Flow<List<Mortality>> = callbackFlow {
        val subscription = db.collection("mortality")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    Mortality(
                        id = 0,
                        count = doc.getLong("count")?.toInt() ?: 0,
                        date = doc.getLong("date") ?: 0L
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addMortality(count: Int, date: Long) {
        val data = hashMapOf(
            "count" to count,
            "date" to date
        )
        db.collection("mortality").add(data).await()
    }

    // --- Sales ---
    fun getSalesFlow(): Flow<List<EggSale>> = callbackFlow {
        val subscription = db.collection("sales")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    EggSale(
                        id = 0,
                        userId = 0,
                        date = doc.getLong("date") ?: 0L,
                        quantity = doc.getLong("quantity")?.toInt() ?: 0,
                        pricePerUnit = doc.getDouble("pricePerUnit") ?: 0.0,
                        totalPrice = doc.getDouble("totalPrice") ?: 0.0,
                        buyer = doc.getString("buyer"),
                        phoneNumber = doc.getString("phoneNumber")
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addSale(sale: EggSale) {
        val data = hashMapOf(
            "userId" to auth.currentUser?.uid,
            "date" to sale.date,
            "quantity" to sale.quantity,
            "pricePerUnit" to sale.pricePerUnit,
            "totalPrice" to sale.totalPrice,
            "buyer" to sale.buyer,
            "phoneNumber" to sale.phoneNumber
        )
        db.collection("sales").add(data).await()
    }

    // --- Expenses ---
    fun getExpensesFlow(): Flow<List<Expense>> = callbackFlow {
        val subscription = db.collection("expenses")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    Expense(
                        id = 0,
                        date = doc.getLong("date") ?: 0L,
                        category = doc.getString("category") ?: "",
                        description = doc.getString("description") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        quantityKg = doc.getDouble("quantityKg")
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addExpense(expense: Expense) {
        val data = hashMapOf(
            "date" to expense.date,
            "category" to expense.category,
            "description" to expense.description,
            "amount" to expense.amount,
            "quantityKg" to expense.quantityKg
        )
        db.collection("expenses").add(data).await()
    }

    // --- Farm Info ---
    suspend fun getFarmInfo(): FarmInfo? {
        return try {
            val doc = db.collection("config").document("farm_info").get().await()
            if (doc.exists()) {
                FarmInfo(
                    id = 1,
                    farmName = doc.getString("farmName") ?: "",
                    hensCount = doc.getLong("hensCount")?.toInt() ?: 0,
                    henBreed = doc.getString("henBreed") ?: "",
                    arrivalDate = doc.getLong("arrivalDate") ?: 0L,
                    chickBirthDate = doc.getLong("chickBirthDate") ?: 0L,
                    currency = doc.getString("currency") ?: "MRU"
                )
            } else null
        } catch (e: Exception) { null }
    }

    fun getFarmInfoFlow(): Flow<FarmInfo?> = callbackFlow {
        val subscription = db.collection("config").document("farm_info")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val info = if (snapshot != null && snapshot.exists()) {
                    FarmInfo(
                        id = 1,
                        farmName = snapshot.getString("farmName") ?: "",
                        hensCount = snapshot.getLong("hensCount")?.toInt() ?: 0,
                        henBreed = snapshot.getString("henBreed") ?: "",
                        arrivalDate = snapshot.getLong("arrivalDate") ?: 0L,
                        chickBirthDate = snapshot.getLong("chickBirthDate") ?: 0L,
                        currency = snapshot.getString("currency") ?: "MRU"
                    )
                } else null
                trySend(info)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun saveFarmInfo(info: FarmInfo) {
        val data = hashMapOf(
            "farmName" to info.farmName,
            "hensCount" to info.hensCount,
            "henBreed" to info.henBreed,
            "arrivalDate" to info.arrivalDate,
            "chickBirthDate" to info.chickBirthDate,
            "currency" to info.currency
        )
        db.collection("config").document("farm_info").set(data).await()
    }
}
