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
import java.text.SimpleDateFormat
import java.util.*

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
        username: String, email: String,
        country: String? = null, city: String? = null
    ): String {
        val uid = auth.currentUser?.uid ?: throw Exception("Non connecté")
        val farmCode = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        val farmRef = db.collection("fermes").document()
        val farmId = farmRef.id
        
        val farmData = hashMapOf(
            "id" to farmId, 
            "name" to farmName, 
            "code" to farmCode, 
            "ownerId" to uid,
            "country" to country,
            "city" to city
        )
        farmRef.set(farmData).await()
        
        val initialFarmInfo = hashMapOf(
            "farmName" to farmName,
            "currency" to currency, 
            "setupDate" to System.currentTimeMillis(),
            "country" to country,
            "city" to city
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

        sendValidationEmailViaBrevo(farmName, uid, email, country, city)
        
        _farmIdFlow.value = farmId
        return farmCode
    }

    private suspend fun sendValidationEmailViaBrevo(farmName: String, uid: String, email: String, country: String? = null, city: String? = null) {
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
                            <li><b>Lieu :</b> ${city ?: "N/A"}, ${country ?: "N/A"}</li>
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

    suspend fun sendHeatAlertEmail(responsibleEmail: String, farmName: String, day: String, temp: Double) {
        val fId = getFarmId() ?: return
        
        // Anti-spam : vérifier si une alerte a déjà été envoyée aujourd'hui
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val configRef = db.collection("fermes").document(fId).collection("config").document("heat_alerts")
        
        try {
            val lastAlert = configRef.get().await()
            if (lastAlert.exists() && lastAlert.getString("lastAlertDate") == today) {
                Log.d("HeatAlert", "Alerte déjà envoyée aujourd'hui pour cette ferme.")
                return
            }

            Log.d("HeatAlert", "Envoi d'une alerte à $responsibleEmail pour $temp°C le $day")
            val emailRequest = BrevoEmailRequest(
                sender = BrevoSender("KOURKOUROU App", "kourkourou@gmail.com"), 
                to = listOf(BrevoReceiver(responsibleEmail)),
                subject = "⚠️ ALERTE CHALEUR - $farmName",
                htmlContent = """
                    <html>
                    <body style='font-family: sans-serif; padding: 20px;'>
                        <div style='background-color: #fdf2f2; border-left: 5px solid #e74c3c; padding: 15px;'>
                            <h1 style='color: #e74c3c; margin-top: 0;'>🔥 Alerte Température Élevée</h1>
                            <p>Bonjour,</p>
                            <p>Une température critique de <span style='font-size: 18px; color: #e74c3c; font-weight: bold;'>$temp°C</span> est prévue le <b>$day</b> pour Nouakchott.</p>
                            <p>Vous recevez cette alerte à l'avance afin de prendre les précautions nécessaires avant l'arrivée de la chaleur.</p>
                            <p><b>Mesures recommandées :</b></p>
                            <ul>
                                <li>Renforcer la ventilation du poulailler.</li>
                                <li>Assurer la disponibilité d'eau très fraîche.</li>
                                <li>Distribuer des anti-stress (vitamine C ou électrolytes).</li>
                                <li>Éviter de manipuler les oiseaux aux heures les plus chaudes.</li>
                            </ul>
                            <br/>
                            <p style='font-size: 12px; color: #7f8c8d;'>Ceci est une alerte automatique générée par votre application de gestion de poulailler KOURKOUROU.</p>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
            )

            val response = RetrofitClient.brevoApi.sendEmail(BREVO_API_KEY, emailRequest)
            if (response.isSuccessful) {
                Log.d("HeatAlert", "Alerte envoyée avec succès : ${response.body()?.messageId}")
                // Marquer comme envoyé SEULEMENT si l'API Brevo a répondu OK
                configRef.set(hashMapOf("lastAlertDate" to today), SetOptions.merge())
            } else {
                Log.e("HeatAlert", "Échec Brevo : ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("HeatAlert", "Exception lors de l'alerte", e)
        }
    }

    suspend fun sendFeedStockAlertEmail(responsibleEmail: String, farmName: String, stockKg: Double, autonomyDays: Int) {
        val fId = getFarmId() ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val configRef = db.collection("fermes").document(fId).collection("config").document("stock_alerts")
        
        try {
            val lastAlert = configRef.get().await()
            if (lastAlert.exists() && lastAlert.getString("lastAlertDate") == today) {
                Log.d("StockAlert", "Alerte stock déjà envoyée aujourd'hui.")
                return
            }

            Log.d("StockAlert", "Envoi d'une alerte stock à $responsibleEmail ($autonomyDays jours restants)")
            val emailRequest = BrevoEmailRequest(
                sender = BrevoSender("KOURKOUROU App", "kourkourou@gmail.com"),
                to = listOf(BrevoReceiver(responsibleEmail)),
                subject = "⚠️ ALERTE STOCK ALIMENT - $farmName",
                htmlContent = """
                    <html>
                    <body style='font-family: sans-serif; padding: 20px;'>
                        <div style='background-color: #fff3cd; border-left: 5px solid #ffc107; padding: 10px;'>
                            <h1 style='color: #856404; margin-top: 0;'>📦 Stock Critique d'Aliment</h1>
                            <p>Bonjour,</p>
                            <p>Votre stock d'aliment est presque épuisé.</p>
                            <ul>
                                <li><b>Stock restant estimé :</b> ${String.format("%.1f", stockKg)} kg</li>
                                <li><b>Autonomie estimée :</b> <span style='font-weight: bold; color: #d9534f;'>$autonomyDays jours</span></li>
                            </ul>
                            <p>Veuillez prévoir un approvisionnement rapidement pour éviter toute rupture.</p>
                            <br/>
                            <p style='font-size: 12px; color: #7f8c8d;'>Ceci est une alerte automatique générée par votre application KOURKOUROU.</p>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
            )

            val response = RetrofitClient.brevoApi.sendEmail(BREVO_API_KEY, emailRequest)
            if (response.isSuccessful) {
                Log.d("StockAlert", "Alerte stock envoyée avec succès.")
                // Marquer comme envoyé SEULEMENT après confirmation de Brevo
                configRef.set(hashMapOf("lastAlertDate" to today), SetOptions.merge())
            } else {
                Log.e("StockAlert", "Échec Brevo : ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e("StockAlert", "Exception lors de l'alerte stock", e)
        }
    }

    suspend fun getResponsibleEmail(): String? {
        val fId = getFarmId() ?: run {
            Log.e("HeatAlert", "Impossible de récupérer l'ID de la ferme")
            return null
        }
        return try {
            val farmDoc = db.collection("fermes").document(fId).get().await()
            val ownerId = farmDoc.getString("ownerId") ?: run {
                Log.e("HeatAlert", "ownerId introuvable pour la ferme $fId")
                return null
            }
            val ownerDoc = db.collection("users").document(ownerId).get().await()
            val email = ownerDoc.getString("email")
            if (email == null) {
                Log.e("HeatAlert", "Email introuvable pour le responsable (UID: $ownerId)")
            } else {
                Log.d("HeatAlert", "Email responsable trouvé : $email")
            }
            email
        } catch (e: Exception) {
            Log.e("HeatAlert", "Erreur Firestore lors de la récupération de l'email", e)
            null
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
                email = doc.getString("email") ?: "",
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
                val twentyDaysMillis = 20L * 24 * 60 * 60 * 1000
                if (System.currentTimeMillis() - createdAt > twentyDaysMillis) {
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
            throw Exception("Accès bloqué : validation requise après 20 jours.")
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

    private fun mapFarmInfo(s: com.google.firebase.firestore.DocumentSnapshot): FarmInfo = FarmInfo(
        id = 1,
        farmName = s.getString("farmName") ?: "",
        currency = s.getString("currency") ?: "MRU",
        setupDate = s.getLong("setupDate") ?: System.currentTimeMillis(),
        locality = s.getString("locality"),
        latitude = s.getDouble("latitude"),
        longitude = s.getDouble("longitude"),
        eggTraysCriticalThreshold = s.getLong("eggTraysCriticalThreshold")?.toInt() ?: FarmInfo.DEFAULT_EGG_TRAYS_CRITICAL,
        feedStockCriticalDays = s.getLong("feedStockCriticalDays")?.toInt() ?: FarmInfo.DEFAULT_FEED_STOCK_CRITICAL_DAYS,
        feedStockWarningDays = s.getLong("feedStockWarningDays")?.toInt() ?: FarmInfo.DEFAULT_FEED_STOCK_WARNING_DAYS,
        heatAlertTempCelsius = s.getLong("heatAlertTempCelsius")?.toInt() ?: FarmInfo.DEFAULT_HEAT_ALERT_TEMP,
        weatherTempOffsetCelsius = s.getDouble("weatherTempOffsetCelsius") ?: FarmInfo.DEFAULT_WEATHER_TEMP_OFFSET,
        lightingHoursAfterSunrise = s.getLong("lightingHoursAfterSunrise")?.toInt() ?: FarmInfo.DEFAULT_LIGHTING_HOURS,
        vaccineIntervalMonths = s.getLong("vaccineIntervalMonths")?.toInt() ?: FarmInfo.DEFAULT_VACCINE_INTERVAL_MONTHS,
        dewormingInternalIntervalMonths = s.getLong("dewormingInternalIntervalMonths")?.toInt() ?: FarmInfo.DEFAULT_DEWORMING_INTERNAL_MONTHS,
        dewormingExternalIntervalMonths = s.getLong("dewormingExternalIntervalMonths")?.toInt() ?: FarmInfo.DEFAULT_DEWORMING_EXTERNAL_MONTHS
    )

    suspend fun getFarmInfo(): FarmInfo? {
        val id = getFarmId() ?: return null
        return try {
            val s = db.collection("fermes").document(id).collection("config").document("farm_info").get().await()
            if (s.exists()) mapFarmInfo(s) else null
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
                            farmId = id,
                            feedRation = doc.getDouble("feedRation") ?: 0.120,
                            feedRationHistory = doc.getString("feedRationHistory") ?: ""
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
                        Mortality(
                            id = 0L, 
                            count = doc.getLong("count")?.toInt() ?: 0, 
                            date = doc.getLong("date") ?: 0L, 
                            firestoreId = doc.id, 
                            farmId = id, 
                            batchId = doc.getString("batchId"),
                            cause = doc.getString("cause")
                        )
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
                        EggSale(0L, doc.getString("userId") ?: "", doc.getLong("date") ?: 0L, doc.getLong("quantity")?.toInt() ?: 0, doc.getDouble("pricePerUnit") ?: 0.0, doc.getDouble("totalPrice") ?: 0.0, doc.getString("buyer"), doc.getString("phoneNumber"), doc.getBoolean("isPaid") ?: false, doc.id, id, doc.getString("batchId"))
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
                        Expense(0L, doc.getLong("date") ?: 0L, doc.getString("category") ?: "", doc.getDouble("amount") ?: 0.0, doc.getDouble("quantityKg"), doc.getString("description"), doc.id, id, doc.getString("batchId"), doc.getString("subCategory"))
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
    fun getHealthRemindersFlow(): Flow<List<HealthReminder>> = farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("health_reminders").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        HealthReminder(
                            id = 0L,
                            type = doc.getString("type") ?: "VACCIN",
                            title = doc.getString("title") ?: "",
                            description = doc.getString("description"),
                            dueDate = doc.getLong("dueDate") ?: 0L,
                            isDone = doc.getBoolean("isDone") ?: false,
                            batchId = doc.getString("batchId"),
                            recurring = doc.getBoolean("recurring") ?: false,
                            frequencyMonths = doc.getLong("frequencyMonths")?.toInt(),
                            firestoreId = doc.id
                        )
                    }?.sortedBy { it.dueDate } ?: emptyList()
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
                    val info = if (s != null && s.exists()) mapFarmInfo(s) else null
                    trySend(info)
                }
            awaitClose { sub.remove() }
        }
    }

    suspend fun saveFarmInfo(info: FarmInfo) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("fermes").document(fId).collection("config").document("farm_info").set(
            hashMapOf(
                "farmName" to info.farmName,
                "currency" to info.currency,
                "locality" to info.locality,
                "latitude" to info.latitude,
                "longitude" to info.longitude,
                "eggTraysCriticalThreshold" to info.eggTraysCriticalThreshold,
                "feedStockCriticalDays" to info.feedStockCriticalDays,
                "feedStockWarningDays" to info.feedStockWarningDays,
                "heatAlertTempCelsius" to info.heatAlertTempCelsius,
                "weatherTempOffsetCelsius" to info.weatherTempOffsetCelsius,
                "lightingHoursAfterSunrise" to info.lightingHoursAfterSunrise,
                "vaccineIntervalMonths" to info.vaccineIntervalMonths,
                "dewormingInternalIntervalMonths" to info.dewormingInternalIntervalMonths,
                "dewormingExternalIntervalMonths" to info.dewormingExternalIntervalMonths
            ),
            SetOptions.merge()
        ).await()
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
            "typeLot" to batch.typeLot,
            "feedRation" to batch.feedRation,
            "feedRationHistory" to batch.feedRationHistory
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
                "typeLot" to batch.typeLot,
                "feedRation" to batch.feedRation,
                "feedRationHistory" to batch.feedRationHistory
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

    suspend fun addMortality(c: Int, d: Long, batchId: String?, cause: String? = null) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("mortality").add(hashMapOf(
            "count" to c, 
            "date" to d, 
            "farmId" to fId, 
            "batchId" to batchId,
            "cause" to cause
        )).await()
    }

    suspend fun updateMortality(m: Mortality) {
        checkAndThrowIfBlocked()
        m.firestoreId?.let {
            db.collection("mortality").document(it).update(hashMapOf(
                "count" to m.count,
                "date" to m.date,
                "cause" to m.cause
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
        db.collection("sales").add(hashMapOf("userId" to auth.currentUser?.uid, "date" to s.date, "quantity" to s.quantity, "pricePerUnit" to s.pricePerUnit, "totalPrice" to s.totalPrice, "buyer" to s.buyer, "phoneNumber" to s.phoneNumber, "isPaid" to s.isPaid, "farmId" to fId, "batchId" to s.batchId)).await()
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
                "phoneNumber" to s.phoneNumber,
                "isPaid" to s.isPaid
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
            "subCategory" to e.subCategory,
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
                "subCategory" to e.subCategory,
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

    suspend fun addHealthReminder(r: HealthReminder) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("health_reminders").add(hashMapOf(
            "type" to r.type,
            "title" to r.title,
            "description" to r.description,
            "dueDate" to r.dueDate,
            "isDone" to r.isDone,
            "batchId" to r.batchId,
            "recurring" to r.recurring,
            "frequencyMonths" to r.frequencyMonths,
            "farmId" to fId
        )).await()
    }

    suspend fun updateHealthReminder(r: HealthReminder) {
        checkAndThrowIfBlocked()
        r.firestoreId?.let {
            db.collection("health_reminders").document(it).update(hashMapOf(
                "dueDate" to r.dueDate,
                "isDone" to r.isDone
            ) as Map<String, Any>).await()
        }
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
                            email = doc.getString("email") ?: "",
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
