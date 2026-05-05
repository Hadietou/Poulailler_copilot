package com.hadietou.poulailler.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import com.hadietou.poulailler.data.*
import com.hadietou.poulailler.network.*
import com.hadietou.poulailler.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val BREVO_API_KEY = BuildConfig.BREVO_API_KEY

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
        farmName: String, currency: String,
        username: String, email: String
    ): String {
        val uid = auth.currentUser?.uid ?: throw Exception("Non connecté")
        val farmCode = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        val farmRef = db.collection("fermes").document()
        val farmId = farmRef.id
        
        val farmData = hashMapOf("id" to farmId, "name" to farmName, "code" to farmCode, "ownerId" to uid)
        farmRef.set(farmData).await()
        
        val initialFarmInfo = hashMapOf(
            "farmName" to farmName,
            "currency" to currency, 
            "setupDate" to System.currentTimeMillis()
        )
        db.collection("fermes").document(farmId).collection("config").document("farm_info").set(initialFarmInfo).await()

        val userData = hashMapOf(
            "username" to username,
            "email" to email,
            "farmId" to farmId, 
            "role" to "RESPONSABLE", 
            "active" to true,
            "isPending" to true,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("users").document(uid).set(userData, SetOptions.merge()).await()

        sendValidationEmailViaBrevo(farmName, uid, email)
        
        _farmIdFlow.value = farmId
        return farmCode
    }

    private suspend fun sendValidationEmailViaBrevo(farmName: String, uid: String, email: String) {
        try {
            val emailRequest = BrevoEmailRequest(
                sender = BrevoSender("KOURKOUROU App", "kourkourou@gmail.com"),
                to = listOf(BrevoReceiver("hadietou@gmail.com", "Hadietou")),
                subject = "Nouvelle demande de création de ferme : $farmName",
                htmlContent = """
                    <html>
                    <body>
                        <h1>Nouvelle Ferme Créée</h1>
                        <p>Une nouvelle ferme a été créée et attend votre validation.</p>
                        <ul>
                            <li><b>Nom de la ferme :</b> $farmName</li>
                            <li><b>ID Responsable :</b> $uid</li>
                            <li><b>Email Responsable :</b> $email</li>
                        </ul>
                        <p>Pour valider, connectez-vous à la console Firebase et passez <b>isPending</b> à <b>false</b> pour cet utilisateur.</p>
                        <p> https://console.firebase.google.com/u/0/project/pondeuses-eec3f/firestore/databases/-default-/data/~2Fusers~2F$uid </p>
                    </body>
                    </html>
                """.trimIndent()
            )

            val response = RetrofitClient.brevoApi.sendEmail(BREVO_API_KEY, emailRequest)
            if (response.isSuccessful) {
                Log.d("Brevo", "Email envoyé avec succès : ${response.body()?.messageId}")
            } else {
                Log.e("Brevo", "Erreur lors de l'envoi : ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("Brevo", "Exception lors de l'envoi de l'email", e)
        }
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
            User(
                id = 0L, 
                uid = uid, 
                username = doc.getString("username") ?: "Utilisateur", 
                password = "", 
                role = doc.getString("role") ?: "AGENT", 
                active = doc.getBoolean("active") ?: true, 
                farmId = fId,
                isPending = doc.getBoolean("isPending") ?: false,
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        } else null
    } catch (e: Exception) { null }

    suspend fun getCurrentUserProfile(): User? = auth.currentUser?.uid?.let { getUserProfile(it) }

    suspend fun isFarmAccessBlocked(): Boolean {
        val fId = getFarmId() ?: return false
        return try {
            val farmDoc = db.collection("fermes").document(fId).get().await()
            val ownerId = farmDoc.getString("ownerId") ?: return false
            val ownerDoc = db.collection("users").document(ownerId).get().await()
            
            val isPending = ownerDoc.getBoolean("isPending") ?: false
            val createdAt = ownerDoc.getLong("createdAt") ?: 0L
            
            if (isPending) {
                val fiveMinutesMillis = 5L * 60 * 1000
                if (System.currentTimeMillis() - createdAt > fiveMinutesMillis) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun checkAndThrowIfBlocked() {
        if (isFarmAccessBlocked()) {
            throw Exception("Accès bloqué : validation requise après 5 minutes.")
        }
    }

    suspend fun createUserProfile(uid: String, username: String, email: String, role: String, isPending: Boolean = false) {
        val data = hashMapOf(
            "username" to username, 
            "email" to email, 
            "role" to role, 
            "active" to true,
            "isPending" to isPending,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("users").document(uid).set(data, SetOptions.merge()).await()
    }

    suspend fun getFarmInfo(): FarmInfo? {
        val id = getFarmId() ?: return null
        return try {
            val s = db.collection("fermes").document(id).collection("config").document("farm_info").get().await()
            if (s.exists()) {
                FarmInfo(1, s.getString("farmName") ?: "", s.getString("currency") ?: "MRU", s.getLong("setupDate") ?: System.currentTimeMillis())
            } else null
        } catch (e: Exception) { null }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getBatchesFlow(): Flow<List<Batch>> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("fermes").document(id).collection("batches")
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        Batch(
                            id = 0L,
                            name = doc.getString("name") ?: "",
                            hensCount = doc.getLong("hensCount")?.toInt() ?: 0,
                            henBreed = doc.getString("henBreed") ?: "",
                            arrivalDate = doc.getLong("arrivalDate") ?: 0L,
                            chickBirthDate = doc.getLong("chickBirthDate") ?: 0L,
                            status = doc.getString("status") ?: "ACTIVE",
                            typeLot = doc.getString("typeLot") ?: "PONDEUSE",
                            firestoreId = doc.id,
                            farmId = id
                        )
                    } ?: emptyList()
                    trySend(list)
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
                        EggEntry(0L, doc.getString("userId") ?: "", doc.getLong("date") ?: 0L, doc.getLong("eggsCount")?.toInt() ?: 0, doc.getLong("brokenEggsCount")?.toInt() ?: 0, doc.getString("remarks"), doc.id, id, doc.getString("batchId"))
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
                        Mortality(0L, doc.getLong("count")?.toInt() ?: 0, doc.getLong("date") ?: 0L, doc.id, id, doc.getString("batchId"))
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
                        EggSale(0L, doc.getString("userId") ?: "", doc.getLong("date") ?: 0L, doc.getLong("quantity")?.toInt() ?: 0, doc.getDouble("pricePerUnit") ?: 0.0, doc.getDouble("totalPrice") ?: 0.0, doc.getString("buyer"), doc.getString("phoneNumber"), doc.id, id, doc.getString("batchId"))
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
                        Expense(0L, doc.getLong("date") ?: 0L, doc.getString("category") ?: "", doc.getDouble("amount") ?: 0.0, doc.getDouble("quantityKg"), doc.getString("description"), doc.id, id, doc.getString("batchId"))
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
                        VaccineEntry(0L, doc.getString("name") ?: "", doc.getLong("date") ?: 0L, doc.getString("remarks"), doc.id, id, doc.getString("batchId"))
                    }?.sortedByDescending { it.date } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getFarmInfoFlow(): Flow<FarmInfo?> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(null)
        else callbackFlow {
            val sub = db.collection("fermes").document(id).collection("config").document("farm_info")
                .addSnapshotListener { s, e ->
                    val info = if (s != null && s.exists()) {
                        FarmInfo(1, s.getString("farmName") ?: "", s.getString("currency") ?: "MRU", s.getLong("setupDate") ?: System.currentTimeMillis())
                    } else null
                    trySend(info)
                }
            awaitClose { sub.remove() }
        }
    }

    suspend fun saveFarmInfo(info: FarmInfo) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("fermes").document(fId).collection("config").document("farm_info").set(hashMapOf("farmName" to info.farmName, "currency" to info.currency), SetOptions.merge()).await()
    }

    suspend fun addBatch(batch: Batch) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("fermes").document(fId).collection("batches").add(hashMapOf(
            "name" to batch.name,
            "hensCount" to batch.hensCount,
            "henBreed" to batch.henBreed,
            "arrivalDate" to batch.arrivalDate,
            "chickBirthDate" to batch.chickBirthDate,
            "status" to batch.status,
            "typeLot" to batch.typeLot
        )).await()
    }

    suspend fun updateBatch(batch: Batch) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        batch.firestoreId?.let { 
            db.collection("fermes").document(fId).collection("batches").document(it).update(hashMapOf(
                "name" to batch.name,
                "hensCount" to batch.hensCount,
                "henBreed" to batch.henBreed,
                "arrivalDate" to batch.arrivalDate,
                "chickBirthDate" to batch.chickBirthDate,
                "status" to batch.status,
                "typeLot" to batch.typeLot
            ) as Map<String, Any>).await()
        }
    }

    suspend fun deleteBatch(batchId: String) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("fermes").document(fId).collection("batches").document(batchId).delete().await()
    }

    suspend fun addEggEntry(e: EggEntry) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("egg_entries").add(hashMapOf("userId" to auth.currentUser?.uid, "date" to e.date, "eggsCount" to e.eggsCount, "brokenEggsCount" to e.brokenEggsCount, "remarks" to e.remarks, "farmId" to fId, "batchId" to e.batchId)).await()
    }
    
    suspend fun updateEggEntry(e: EggEntry) {
        checkAndThrowIfBlocked()
        e.firestoreId?.let {
            db.collection("egg_entries").document(it).update(hashMapOf(
                "date" to e.date,
                "eggsCount" to e.eggsCount,
                "brokenEggsCount" to e.brokenEggsCount,
                "remarks" to e.remarks
            ) as Map<String, Any>).await()
        }
    }
    
    suspend fun deleteEggEntry(id: String) {
        checkAndThrowIfBlocked()
        db.collection("egg_entries").document(id).delete().await()
    }

    suspend fun addMortality(c: Int, d: Long, batchId: String?) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("mortality").add(hashMapOf("count" to c, "date" to d, "farmId" to fId, "batchId" to batchId)).await()
    }

    suspend fun updateMortality(m: Mortality) {
        checkAndThrowIfBlocked()
        m.firestoreId?.let {
            db.collection("mortality").document(it).update(hashMapOf(
                "count" to m.count,
                "date" to m.date
            ) as Map<String, Any>).await()
        }
    }

    suspend fun deleteMortality(id: String) {
        checkAndThrowIfBlocked()
        db.collection("mortality").document(id).delete().await()
    }

    suspend fun addSale(s: EggSale) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("sales").add(hashMapOf("userId" to auth.currentUser?.uid, "date" to s.date, "quantity" to s.quantity, "pricePerUnit" to s.pricePerUnit, "totalPrice" to s.totalPrice, "buyer" to s.buyer, "phoneNumber" to s.phoneNumber, "farmId" to fId, "batchId" to s.batchId)).await()
    }
    
    suspend fun updateSale(s: EggSale) {
        checkAndThrowIfBlocked()
        s.firestoreId?.let {
            db.collection("sales").document(it).update(hashMapOf(
                "date" to s.date,
                "quantity" to s.quantity,
                "pricePerUnit" to s.pricePerUnit,
                "totalPrice" to s.totalPrice,
                "buyer" to s.buyer,
                "phoneNumber" to s.phoneNumber
            ) as Map<String, Any>).await()
        }
    }

    suspend fun deleteSale(saleId: String) {
        checkAndThrowIfBlocked()
        db.collection("sales").document(saleId).delete().await()
    }

    suspend fun addExpense(e: Expense) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("expenses").add(hashMapOf(
            "date" to e.date,
            "category" to e.category,
            "amount" to e.amount,
            "quantityKg" to e.quantityKg,
            "description" to e.description,
            "farmId" to fId,
            "batchId" to e.batchId
        )).await()
    }

    suspend fun updateExpense(e: Expense) {
        checkAndThrowIfBlocked()
        e.firestoreId?.let {
            db.collection("expenses").document(it).update(hashMapOf(
                "date" to e.date,
                "category" to e.category,
                "amount" to e.amount,
                "quantityKg" to e.quantityKg,
                "description" to e.description
            ) as Map<String, Any>).await()
        }
    }

    suspend fun deleteExpense(id: String) {
        checkAndThrowIfBlocked()
        db.collection("expenses").document(id).delete().await()
    }

    suspend fun addVaccine(v: VaccineEntry) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("vaccines").add(hashMapOf(
            "name" to v.name,
            "date" to v.date,
            "remarks" to v.remarks,
            "farmId" to fId,
            "batchId" to v.batchId
        )).await()
    }

    suspend fun updateVaccine(v: VaccineEntry) {
        checkAndThrowIfBlocked()
        v.firestoreId?.let {
            db.collection("vaccines").document(it).update(hashMapOf(
                "name" to v.name,
                "date" to v.date,
                "remarks" to v.remarks
            ) as Map<String, Any>).await()
        }
    }

    suspend fun deleteVaccine(id: String) {
        checkAndThrowIfBlocked()
        db.collection("vaccines").document(id).delete().await()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getAllUsersFlow(): Flow<List<User>> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("users").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        User(
                            id = 0L,
                            uid = doc.id,
                            username = doc.getString("username") ?: "",
                            password = "",
                            role = doc.getString("role") ?: "AGENT",
                            active = doc.getBoolean("active") ?: true,
                            farmId = id,
                            isPending = doc.getBoolean("isPending") ?: false,
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getLoginHistoryFlow(): Flow<List<LoginEntry>> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("login_history").whereEqualTo("farmId", id)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        LoginEntry(
                            id = 0L,
                            userId = doc.getString("userId") ?: "",
                            username = doc.getString("username") ?: "",
                            timestamp = doc.getLong("timestamp") ?: 0L
                        )
                    } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    suspend fun getFarmCode(): String? {
        val fId = requireFarmId()
        return try {
            val doc = db.collection("fermes").document(fId).get().await()
            doc.getString("code")
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUserStatus(userId: String, active: Boolean) {
        checkAndThrowIfBlocked()
        db.collection("users").document(userId).update("active", active).await()
    }

    fun shareAgentCredentials(context: Context, login: String, pass: String) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Identifiants KOURKOUROU")
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Voici vos identifiants de connexion :\n\nLogin : $login\nMot de passe : $pass")
        context.startActivity(Intent.createChooser(shareIntent, "Partager via"))
    }
}
