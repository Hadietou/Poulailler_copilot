package com.hadietou.poulailler.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.hadietou.poulailler.data.*
import com.hadietou.poulailler.demo.DemoData
import com.hadietou.poulailler.demo.DemoMode
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
        if (DemoMode.enabled) return DemoData.FARM_ID
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
        
        val cityHit = city?.takeIf { it.isNotBlank() }?.let {
            try { RetrofitClient.geocodingApi.search(it).results?.firstOrNull() } catch (e: Exception) { null }
        }
        val initialFarmInfo = hashMapOf(
            "farmName" to farmName,
            "currency" to currency, 
            "setupDate" to System.currentTimeMillis(),
            "country" to country,
            "city" to city,
            "locality" to city?.takeIf { it.isNotBlank() },
            "latitude" to cityHit?.latitude,
            "longitude" to cityHit?.longitude
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
        addOrUpdateMembership(uid, farmId, farmName, "RESPONSABLE")

        sendValidationEmailViaBrevo(farmName, uid, email, country, city)

        _farmIdFlow.value = farmId
        return farmCode
    }

    /**
     * Note d'architecture multi-fermes : un utilisateur peut appartenir à plusieurs fermes
     * (ex. agent chez quelqu'un ET responsable de sa propre ferme, ou agent dans plusieurs
     * fermes). users/{uid}.farmId/role restent la "ferme active" lue par tout le reste de
     * l'app (aucun autre écran n'a besoin de changer) ; users/{uid}/memberships/{farmId}
     * garde la liste complète pour le sélecteur "Changer de ferme".
     */
    private suspend fun addOrUpdateMembership(uid: String, farmId: String, farmName: String, role: String) {
        db.collection("users").document(uid).collection("memberships").document(farmId).set(
            hashMapOf("farmId" to farmId, "farmName" to farmName, "role" to role)
        ).await()
    }

    suspend fun getMemberships(): List<Membership> {
        if (DemoMode.enabled) return emptyList()
        val uid = auth.currentUser?.uid ?: return emptyList()
        return try {
            db.collection("users").document(uid).collection("memberships").get().await().documents.mapNotNull { doc ->
                val fId = doc.getString("farmId") ?: return@mapNotNull null
                Membership(
                    farmId = fId,
                    farmName = doc.getString("farmName") ?: "",
                    role = doc.getString("role") ?: "AGENT"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Bascule la ferme active vers [farmId] (doit être une ferme dont l'utilisateur est déjà membre). */
    suspend fun switchActiveFarm(farmId: String) {
        val uid = auth.currentUser?.uid ?: throw Exception("Non connecté")
        val membership = db.collection("users").document(uid).collection("memberships").document(farmId).get().await()
        val role = membership.getString("role") ?: throw Exception("Ferme introuvable")
        db.collection("users").document(uid).set(
            hashMapOf("farmId" to farmId, "role" to role) as Map<String, Any>,
            SetOptions.merge()
        ).await()
        _farmIdFlow.value = farmId
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
        // Arrondi à l'entier pour rester cohérent avec l'affichage dans l'app (dashboard,
        // Suivi Sanitaire) : sans ça l'email affichait la valeur brute avec sa décimale.
        val roundedTemp = Math.round(temp)

        // Anti-spam : une alerte par (jour d'envoi, jour annoncé). L'ancienne clé unique
        // "lastAlertDate" bloquait TOUTE alerte dès qu'une avait déjà été envoyée ce jour
        // calendaire, quel que soit le jour dont elle parlait : impossible d'envoyer à la
        // fois l'alerte J-1 pour demain ET la reconfirmation du jour même pour aujourd'hui
        // si les deux tombent le même jour calendaire (ce qui arrive dès que deux jours
        // consécutifs dépassent le seuil). La clé est donc désormais spécifique au jour
        // annoncé, pour ne bloquer qu'un renvoi en double de la MÊME information.
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val configRef = db.collection("fermes").document(fId).collection("config").document("heat_alerts")
        val lastSentFieldKey = "lastSentDate_$day"

        try {
            val lastAlert = configRef.get().await()
            if (lastAlert.exists() && lastAlert.getString(lastSentFieldKey) == today) {
                Log.d("HeatAlert", "Alerte pour le $day déjà envoyée aujourd'hui pour cette ferme.")
                return
            }

            Log.d("HeatAlert", "Envoi d'une alerte à $responsibleEmail pour $roundedTemp°C le $day")
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
                            <p>Une température critique de <span style='font-size: 18px; color: #e74c3c; font-weight: bold;'>$roundedTemp°C</span> est prévue le <b>$day</b> pour Nouakchott.</p>
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
                configRef.set(hashMapOf(lastSentFieldKey to today), SetOptions.merge())
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
        if (DemoMode.enabled) return null
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

    suspend fun getUserProfile(uid: String): User? {
        if (DemoMode.enabled) return DemoData.users.find { it.uid == uid } ?: DemoData.currentUser
        return getUserProfileFromFirestore(uid)
    }

    private suspend fun getUserProfileFromFirestore(uid: String): User? = try {
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
                createdAt = doc.getLong("createdAt") ?: 0L,
                phoneNumber = doc.getString("phoneNumber")
            )
        } else null
    } catch (e: Exception) { null }

    suspend fun getCurrentUserProfile(): User? {
        if (DemoMode.enabled) return DemoData.currentUser
        return auth.currentUser?.uid?.let { getUserProfile(it) }
    }

    suspend fun isFarmAccessBlocked(): Boolean {
        if (DemoMode.enabled) return false
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
        if (DemoMode.enabled) throw Exception(DemoMode.READ_ONLY_MESSAGE)
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

    /**
     * Profil minimal créé à l'inscription, avant toute création/adhésion de ferme (voir la
     * note d'architecture ci-dessous) : ni [User.role] ni farmId ne sont fixés — l'utilisateur
     * choisit ensuite, une fois connecté, de créer sa propre ferme ou d'accepter une invitation
     * en attente pour son numéro de téléphone (voir [getPendingInvitationsFlow]).
     *
     * Nouveau parcours d'inscription : le numéro de téléphone est désormais l'identifiant
     * principal des nouveaux comptes (vérifié par SMS), tandis que les comptes existants créés
     * par email continuent de fonctionner tels quels — voir [linkPhoneToCurrentAccount] pour le
     * rattachement d'un numéro à un compte existant sans perte de données (Firebase "account
     * linking" : même uid, donc mêmes documents Firestore).
     */
    suspend fun createMinimalUserProfile(uid: String, username: String, phoneNumber: String?, email: String? = null) {
        val data = hashMapOf<String, Any?>(
            "username" to username,
            "role" to User.ROLE_UNASSIGNED,
            "active" to true,
            "isPending" to false,
            "createdAt" to System.currentTimeMillis(),
            "phoneNumber" to phoneNumber,
            "email" to email
        )
        db.collection("users").document(uid).set(data, SetOptions.merge()).await()
    }

    /**
     * Associe un numéro de téléphone déjà vérifié (credential Firebase Phone Auth) au compte
     * ACTUELLEMENT connecté, via le "account linking" natif de Firebase Auth : l'uid ne change
     * pas, donc toutes les données Firestore existantes (ferme, lots, historique...) restent
     * accessibles. C'est le mécanisme qui permet à un compte créé par email de se connecter
     * ensuite avec son numéro de téléphone, sans rien perdre.
     */
    suspend fun linkPhoneToCurrentAccount(credential: com.google.firebase.auth.PhoneAuthCredential, phoneNumber: String) {
        val user = auth.currentUser ?: throw Exception("Non connecté")
        user.linkWithCredential(credential).await()
        db.collection("users").document(user.uid).update("phoneNumber", phoneNumber).await()
    }


    // ── Invitations (rejoindre une ferme en tant qu'agent) ──────────────────────────────

    /**
     * Crée une invitation pour le numéro de téléphone donné à rejoindre la ferme courante.
     * Ne fait qu'enregistrer l'invitation côté serveur : c'est [getPendingInvitationsFlow],
     * consulté par quiconque se connecte avec ce numéro, qui la rend visible — indépendamment
     * de l'envoi ou de la lecture du message WhatsApp (voir [buildWhatsAppInviteIntent]).
     */
    suspend fun createInvitation(phoneNumber: String, agentName: String = ""): Invitation {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        val info = getFarmInfo()
        val me = getCurrentUserProfile()
        val data = hashMapOf(
            "phoneNumber" to phoneNumber,
            "farmId" to fId,
            "farmName" to (info?.farmName ?: ""),
            "invitedByUsername" to (me?.username ?: ""),
            "agentName" to agentName,
            "status" to "PENDING",
            "createdAt" to System.currentTimeMillis()
        )
        val ref = db.collection("invitations").add(data).await()
        return Invitation(
            phoneNumber = phoneNumber,
            agentName = agentName,
            farmId = fId,
            farmName = info?.farmName ?: "",
            invitedByUsername = me?.username ?: "",
            firestoreId = ref.id
        )
    }

    /** Invitations PENDING adressées à [phoneNumber] — vide si aucun numéro (compte pas encore lié). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getPendingInvitationsFlow(phoneNumber: String?): Flow<List<Invitation>> {
        if (DemoMode.enabled) return flowOf(emptyList())
        if (phoneNumber.isNullOrBlank()) return flowOf(emptyList())
        return callbackFlow {
            val sub = db.collection("invitations")
                .whereEqualTo("phoneNumber", phoneNumber)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.map { mapInvitation(it) }
                        ?.sortedByDescending { it.createdAt } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    private fun mapInvitation(doc: com.google.firebase.firestore.DocumentSnapshot) = Invitation(
        phoneNumber = doc.getString("phoneNumber") ?: "",
        agentName = doc.getString("agentName") ?: "",
        farmId = doc.getString("farmId") ?: "",
        farmName = doc.getString("farmName") ?: "",
        invitedByUsername = doc.getString("invitedByUsername") ?: "",
        status = doc.getString("status") ?: "PENDING",
        createdAt = doc.getLong("createdAt") ?: 0L,
        firestoreId = doc.id
    )

    /**
     * Invitations PENDING envoyées PAR la ferme courante (côté responsable) : permet de voir
     * qui a été invité et n'a pas encore rejoint la ferme, puis de relancer ou d'annuler.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getFarmPendingInvitationsFlow(): Flow<List<Invitation>> {
        if (DemoMode.enabled) return flowOf(emptyList())
        return farmIdFlow.flatMapLatest { fId ->
            val id = fId ?: getFarmId()
            if (id == null) flowOf(emptyList())
            else callbackFlow {
                val sub = db.collection("invitations")
                    .whereEqualTo("farmId", id)
                    .whereEqualTo("status", "PENDING")
                    .addSnapshotListener { s, e ->
                        if (e != null) Log.e("FirebaseRepo", "Farm invitations listener error", e)
                        val list = s?.documents?.map { mapInvitation(it) }
                            ?.sortedByDescending { it.createdAt } ?: emptyList()
                        trySend(list)
                    }
                awaitClose { sub.remove() }
            }
        }
    }

    /** Annule une invitation en attente (le numéro invité ne la verra plus). */
    suspend fun cancelInvitation(invitation: Invitation) {
        checkAndThrowIfBlocked()
        invitation.firestoreId?.let {
            db.collection("invitations").document(it).update("status", "CANCELLED").await()
        }
    }

    /** Rattache l'utilisateur courant à la ferme de l'invitation, en tant qu'agent. */
    suspend fun acceptInvitation(invitation: Invitation) {
        val uid = auth.currentUser?.uid ?: throw Exception("Non connecté")
        db.collection("users").document(uid).set(
            hashMapOf(
                "farmId" to invitation.farmId,
                "role" to "AGENT",
                "active" to true,
                "isPending" to false
            ) as Map<String, Any>,
            SetOptions.merge()
        ).await()
        addOrUpdateMembership(uid, invitation.farmId, invitation.farmName, "AGENT")
        invitation.firestoreId?.let {
            db.collection("invitations").document(it).update("status", "ACCEPTED").await()
        }
        _farmIdFlow.value = invitation.farmId
    }

    suspend fun declineInvitation(invitation: Invitation) {
        invitation.firestoreId?.let {
            db.collection("invitations").document(it).update("status", "DECLINED").await()
        }
    }

    /**
     * Intent WhatsApp pré-rempli invitant [phoneNumber] à installer l'app et rejoindre
     * [farmName]. Simple incitation : le rattachement réel se fait via [createInvitation]
     * + [getPendingInvitationsFlow], pas via un jeton dans ce lien.
     */
    fun buildWhatsAppInviteIntent(phoneNumber: String, farmName: String): Intent {
        val message = "Bonjour ! Vous êtes invité(e) à rejoindre la ferme \"$farmName\" sur KOURKOUROU 🐔\n\n" +
            "1. Installez l'application : https://play.google.com/store/apps/details?id=com.hadietou.poulailler\n" +
            "2. Créez votre compte avec CE numéro de téléphone.\n" +
            "3. Une fois connecté(e), vous verrez l'invitation à rejoindre \"$farmName\"."
        val digitsOnly = phoneNumber.filter { it.isDigit() }
        val uri = Uri.parse("https://wa.me/$digitsOnly?text=${Uri.encode(message)}")
        return Intent(Intent.ACTION_VIEW, uri)
    }

    private fun mapFarmInfo(s: com.google.firebase.firestore.DocumentSnapshot): FarmInfo = FarmInfo(
        id = 1,
        farmName = s.getString("farmName") ?: "",
        currency = s.getString("currency") ?: "MRU",
        setupDate = s.getLong("setupDate") ?: 0L, // 0 = date de création inconnue (fermes anciennes)
        locality = s.getString("locality"),
        latitude = s.getDouble("latitude"),
        longitude = s.getDouble("longitude"),
        eggTraysCriticalThreshold = s.getLong("eggTraysCriticalThreshold")?.toInt() ?: FarmInfo.DEFAULT_EGG_TRAYS_CRITICAL,
        feedStockCriticalDays = s.getLong("feedStockCriticalDays")?.toInt() ?: FarmInfo.DEFAULT_FEED_STOCK_CRITICAL_DAYS,
        feedStockWarningDays = s.getLong("feedStockWarningDays")?.toInt() ?: FarmInfo.DEFAULT_FEED_STOCK_WARNING_DAYS,
        heatAlertTempCelsius = s.getLong("heatAlertTempCelsius")?.toInt() ?: FarmInfo.DEFAULT_HEAT_ALERT_TEMP,
        mortalitySpikeMultiplier = s.getDouble("mortalitySpikeMultiplier") ?: FarmInfo.DEFAULT_MORTALITY_SPIKE_MULTIPLIER,
        weatherTempOffsetCelsius = s.getDouble("weatherTempOffsetCelsius") ?: FarmInfo.DEFAULT_WEATHER_TEMP_OFFSET,
        lightingHoursAfterSunrise = s.getLong("lightingHoursAfterSunrise")?.toInt() ?: FarmInfo.DEFAULT_LIGHTING_HOURS,
        vaccineIntervalMonths = s.getLong("vaccineIntervalMonths")?.toInt() ?: FarmInfo.DEFAULT_VACCINE_INTERVAL_MONTHS,
        dewormingInternalIntervalMonths = s.getLong("dewormingInternalIntervalMonths")?.toInt() ?: FarmInfo.DEFAULT_DEWORMING_INTERNAL_MONTHS,
        dewormingExternalIntervalMonths = s.getLong("dewormingExternalIntervalMonths")?.toInt() ?: FarmInfo.DEFAULT_DEWORMING_EXTERNAL_MONTHS
    )

    /** Informations d'identité de la ferme courante, affichées sur la page « Mon exploitation ». */
    data class FarmSummary(val country: String?, val city: String?, val ownerName: String?)

    suspend fun getFarmSummary(): FarmSummary? {
        if (DemoMode.enabled) return FarmSummary("Mauritanie", "Nouakchott", "Démo")
        val id = getFarmId() ?: return null
        return try {
            val doc = db.collection("fermes").document(id).get().await()
            val ownerId = doc.getString("ownerId")
            val ownerName = ownerId?.let { getUserProfile(it)?.username }?.takeIf { it.isNotBlank() }
            FarmSummary(doc.getString("country"), doc.getString("city"), ownerName)
        } catch (e: Exception) { null }
    }

    /**
     * Sans position GPS choisie, la localité météo est la ville de la ferme : on la géocode une
     * seule fois (Open-Meteo) puis on enregistre localité + coordonnées, pour que la météo, le
     * lever/coucher du soleil et l'alerte canicule utilisent tous la même position.
     * Ne change rien si une position existe déjà, si la ferme n'a pas de ville, ou hors ligne.
     */
    suspend fun ensureFarmLocationFromCity(): FarmInfo? {
        val info = getFarmInfo() ?: return null
        if (DemoMode.enabled || (info.latitude != null && info.longitude != null)) return info
        val place = info.locality?.takeIf { it.isNotBlank() }
            ?: getFarmSummary()?.city?.takeIf { it.isNotBlank() }
            ?: return info
        return try {
            val hit = RetrofitClient.geocodingApi.search(place).results?.firstOrNull() ?: return info
            val located = info.copy(locality = place, latitude = hit.latitude, longitude = hit.longitude)
            try {
                val fId = requireFarmId()
                db.collection("fermes").document(fId).collection("config").document("farm_info").set(
                    hashMapOf("locality" to place, "latitude" to hit.latitude, "longitude" to hit.longitude),
                    SetOptions.merge()
                ).await()
            } catch (e: Exception) {
                Log.w("FirebaseRepo", "Position de la ville non enregistrée", e)
            }
            located
        } catch (e: Exception) {
            info
        }
    }

    suspend fun getFarmInfo(): FarmInfo? {
        if (DemoMode.enabled) return DemoData.farmInfo
        val id = getFarmId() ?: return null
        return try {
            val s = db.collection("fermes").document(id).collection("config").document("farm_info").get().await()
            if (s.exists()) mapFarmInfo(s) else null
        } catch (e: Exception) { null }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getBatchesFlow(): Flow<List<Batch>> = if (DemoMode.enabled) flowOf(DemoData.batches) else farmIdFlow.flatMapLatest { fId ->
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
    fun getEggEntriesFlow(): Flow<List<EggEntry>> = if (DemoMode.enabled) flowOf(DemoData.eggEntries) else farmIdFlow.flatMapLatest { fId ->
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
    fun getMortalityFlow(): Flow<List<Mortality>> = if (DemoMode.enabled) flowOf(DemoData.mortalities) else farmIdFlow.flatMapLatest { fId ->
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
                            cause = doc.getString("cause"),
                            zone = doc.getString("zone"),
                            confirmedCause = doc.getString("confirmedCause")
                        )
                    }?.sortedByDescending { it.date } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getSalesFlow(): Flow<List<EggSale>> = if (DemoMode.enabled) flowOf(DemoData.sales) else farmIdFlow.flatMapLatest { fId ->
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
    fun getExpensesFlow(): Flow<List<Expense>> = if (DemoMode.enabled) flowOf(DemoData.expenses) else farmIdFlow.flatMapLatest { fId ->
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
    fun getVaccinesFlow(): Flow<List<VaccineEntry>> = if (DemoMode.enabled) flowOf(DemoData.vaccines) else farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("vaccines").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        VaccineEntry(
                            id = 0L,
                            name = doc.getString("name") ?: "",
                            date = doc.getLong("date") ?: 0L,
                            remarks = doc.getString("remarks"),
                            firestoreId = doc.id,
                            farmId = id,
                            batchId = doc.getString("batchId"),
                            status = doc.getString("status") ?: "REALISE",
                            manufacturer = doc.getString("manufacturer"),
                            lotNumber = doc.getString("lotNumber"),
                            expiryDate = doc.getLong("expiryDate"),
                            dose = doc.getString("dose"),
                            route = doc.getString("route"),
                            targetCount = doc.getLong("targetCount")?.toInt(),
                            administeredBy = doc.getString("administeredBy")
                        )
                    }?.sortedByDescending { it.date } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getHealthRemindersFlow(): Flow<List<HealthReminder>> = if (DemoMode.enabled) flowOf(DemoData.healthReminders) else farmIdFlow.flatMapLatest { fId ->
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
                            doneDate = doc.getLong("doneDate"),
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

    /**
     * Historique permanent des rappels de santé marqués FAIT (voir [HealthReminderLog]).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getHealthReminderLogsFlow(): Flow<List<HealthReminderLog>> = if (DemoMode.enabled) flowOf(DemoData.healthReminderLogs) else farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("health_reminder_logs").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        HealthReminderLog(
                            title = doc.getString("title") ?: "",
                            type = doc.getString("type") ?: "VACCIN",
                            description = doc.getString("description"),
                            dueDate = doc.getLong("dueDate") ?: 0L,
                            doneDate = doc.getLong("doneDate") ?: 0L,
                            batchId = doc.getString("batchId"),
                            firestoreId = doc.id
                        )
                    }?.sortedByDescending { it.doneDate } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getFarmInfoFlow(): Flow<FarmInfo?> = if (DemoMode.enabled) flowOf(DemoData.farmInfo) else farmIdFlow.flatMapLatest { fId ->
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
                "mortalitySpikeMultiplier" to info.mortalitySpikeMultiplier,
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

    suspend fun addMortality(c: Int, d: Long, batchId: String?, cause: String? = null, zone: String? = null) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("mortality").add(hashMapOf(
            "count" to c,
            "date" to d,
            "farmId" to fId,
            "batchId" to batchId,
            "cause" to cause,
            "zone" to zone
        )).await()
    }

    suspend fun updateMortality(m: Mortality) {
        checkAndThrowIfBlocked()
        m.firestoreId?.let {
            db.collection("mortality").document(it).update(hashMapOf(
                "count" to m.count,
                "date" to m.date,
                "cause" to m.cause,
                "zone" to m.zone,
                "confirmedCause" to m.confirmedCause
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
            "batchId" to v.batchId,
            "status" to v.status,
            "manufacturer" to v.manufacturer,
            "lotNumber" to v.lotNumber,
            "expiryDate" to v.expiryDate,
            "dose" to v.dose,
            "route" to v.route,
            "targetCount" to v.targetCount,
            "administeredBy" to v.administeredBy
        )).await()
    }

    suspend fun updateVaccine(v: VaccineEntry) {
        checkAndThrowIfBlocked()
        v.firestoreId?.let {
            db.collection("vaccines").document(it).update(hashMapOf(
                "name" to v.name,
                "date" to v.date,
                "remarks" to v.remarks,
                "status" to v.status,
                "manufacturer" to v.manufacturer,
                "lotNumber" to v.lotNumber,
                "expiryDate" to v.expiryDate,
                "dose" to v.dose,
                "route" to v.route,
                "targetCount" to v.targetCount,
                "administeredBy" to v.administeredBy
            ) as Map<String, Any>).await()
        }
    }

    suspend fun deleteVaccine(id: String) {
        checkAndThrowIfBlocked()
        db.collection("vaccines").document(id).delete().await()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getTreatmentsFlow(): Flow<List<Treatment>> = if (DemoMode.enabled) flowOf(DemoData.treatments) else farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("treatments").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        Treatment(
                            id = 0L,
                            medicationName = doc.getString("medicationName") ?: "",
                            reason = doc.getString("reason"),
                            startDate = doc.getLong("startDate") ?: 0L,
                            endDate = doc.getLong("endDate"),
                            dose = doc.getString("dose"),
                            route = doc.getString("route"),
                            targetCount = doc.getLong("targetCount")?.toInt(),
                            prescriber = doc.getString("prescriber"),
                            status = doc.getString("status") ?: "EN_COURS",
                            eggWithdrawalDays = doc.getLong("eggWithdrawalDays")?.toInt(),
                            slaughterWithdrawalDays = doc.getLong("slaughterWithdrawalDays")?.toInt(),
                            notes = doc.getString("notes"),
                            firestoreId = doc.id,
                            farmId = id,
                            batchId = doc.getString("batchId")
                        )
                    }?.sortedByDescending { it.startDate } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    suspend fun addTreatment(t: Treatment) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("treatments").add(hashMapOf(
            "medicationName" to t.medicationName,
            "reason" to t.reason,
            "startDate" to t.startDate,
            "endDate" to t.endDate,
            "dose" to t.dose,
            "route" to t.route,
            "targetCount" to t.targetCount,
            "prescriber" to t.prescriber,
            "status" to t.status,
            "eggWithdrawalDays" to t.eggWithdrawalDays,
            "slaughterWithdrawalDays" to t.slaughterWithdrawalDays,
            "notes" to t.notes,
            "farmId" to fId,
            "batchId" to t.batchId
        )).await()
    }

    suspend fun updateTreatment(t: Treatment) {
        checkAndThrowIfBlocked()
        t.firestoreId?.let {
            db.collection("treatments").document(it).update(hashMapOf(
                "medicationName" to t.medicationName,
                "reason" to t.reason,
                "startDate" to t.startDate,
                "endDate" to t.endDate,
                "dose" to t.dose,
                "route" to t.route,
                "targetCount" to t.targetCount,
                "prescriber" to t.prescriber,
                "status" to t.status,
                "eggWithdrawalDays" to t.eggWithdrawalDays,
                "slaughterWithdrawalDays" to t.slaughterWithdrawalDays,
                "notes" to t.notes
            ) as Map<String, Any>).await()
        }
    }

    suspend fun deleteTreatment(id: String) {
        checkAndThrowIfBlocked()
        db.collection("treatments").document(id).delete().await()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getDiseaseCasesFlow(): Flow<List<DiseaseCase>> = if (DemoMode.enabled) flowOf(DemoData.diseaseCases) else farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("disease_cases").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        @Suppress("UNCHECKED_CAST")
                        DiseaseCase(
                            id = 0L,
                            suspectedDisease = doc.getString("suspectedDisease"),
                            symptoms = (doc.get("symptoms") as? List<String>) ?: emptyList(),
                            status = doc.getString("status") ?: "SYMPTOME_OBSERVE",
                            severity = doc.getString("severity") ?: "MOYENNE",
                            dateReported = doc.getLong("dateReported") ?: 0L,
                            affectedCount = doc.getLong("affectedCount")?.toInt(),
                            deathCount = doc.getLong("deathCount")?.toInt(),
                            zone = doc.getString("zone"),
                            observerNotes = doc.getString("observerNotes"),
                            vetDiagnosis = doc.getString("vetDiagnosis"),
                            actionsTaken = doc.getString("actionsTaken"),
                            evolutionNotes = doc.getString("evolutionNotes"),
                            firestoreId = doc.id,
                            farmId = id,
                            batchId = doc.getString("batchId")
                        )
                    }?.sortedByDescending { it.dateReported } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    suspend fun addDiseaseCase(d: DiseaseCase) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("disease_cases").add(hashMapOf(
            "suspectedDisease" to d.suspectedDisease,
            "symptoms" to d.symptoms,
            "status" to d.status,
            "severity" to d.severity,
            "dateReported" to d.dateReported,
            "affectedCount" to d.affectedCount,
            "deathCount" to d.deathCount,
            "zone" to d.zone,
            "observerNotes" to d.observerNotes,
            "vetDiagnosis" to d.vetDiagnosis,
            "actionsTaken" to d.actionsTaken,
            "evolutionNotes" to d.evolutionNotes,
            "farmId" to fId,
            "batchId" to d.batchId
        )).await()
    }

    suspend fun updateDiseaseCase(d: DiseaseCase) {
        checkAndThrowIfBlocked()
        d.firestoreId?.let {
            db.collection("disease_cases").document(it).update(hashMapOf(
                "suspectedDisease" to d.suspectedDisease,
                "symptoms" to d.symptoms,
                "status" to d.status,
                "severity" to d.severity,
                "dateReported" to d.dateReported,
                "affectedCount" to d.affectedCount,
                "deathCount" to d.deathCount,
                "zone" to d.zone,
                "observerNotes" to d.observerNotes,
                "vetDiagnosis" to d.vetDiagnosis,
                "actionsTaken" to d.actionsTaken,
                "evolutionNotes" to d.evolutionNotes
            ) as Map<String, Any>).await()
        }
    }

    suspend fun deleteDiseaseCase(id: String) {
        checkAndThrowIfBlocked()
        db.collection("disease_cases").document(id).delete().await()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getHealthObservationsFlow(): Flow<List<HealthObservation>> = if (DemoMode.enabled) flowOf(DemoData.healthObservations) else farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("health_observations").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        HealthObservation(
                            id = 0L,
                            date = doc.getLong("date") ?: 0L,
                            behavior = doc.getString("behavior") ?: "NORMAL",
                            feed = doc.getString("feed") ?: "NORMAL",
                            water = doc.getString("water") ?: "NORMAL",
                            respiration = doc.getString("respiration") ?: "NORMAL",
                            droppings = doc.getString("droppings") ?: "NORMAL",
                            appearance = doc.getString("appearance") ?: "NORMAL",
                            notes = doc.getString("notes"),
                            recordedBy = doc.getString("recordedBy"),
                            firestoreId = doc.id,
                            farmId = id,
                            batchId = doc.getString("batchId")
                        )
                    }?.sortedByDescending { it.date } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    suspend fun addHealthObservation(o: HealthObservation) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("health_observations").add(hashMapOf(
            "date" to o.date,
            "behavior" to o.behavior,
            "feed" to o.feed,
            "water" to o.water,
            "respiration" to o.respiration,
            "droppings" to o.droppings,
            "appearance" to o.appearance,
            "notes" to o.notes,
            "recordedBy" to o.recordedBy,
            "farmId" to fId,
            "batchId" to o.batchId
        )).await()
    }

    suspend fun deleteHealthObservation(id: String) {
        checkAndThrowIfBlocked()
        db.collection("health_observations").document(id).delete().await()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getVetVisitsFlow(): Flow<List<VetVisit>> = if (DemoMode.enabled) flowOf(DemoData.vetVisits) else farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("vet_visits").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        VetVisit(
                            id = 0L,
                            date = doc.getLong("date") ?: 0L,
                            vetName = doc.getString("vetName") ?: "",
                            reason = doc.getString("reason"),
                            observations = doc.getString("observations"),
                            diagnosis = doc.getString("diagnosis"),
                            recommendations = doc.getString("recommendations"),
                            vaccinationsDone = doc.getString("vaccinationsDone"),
                            analysesRequested = doc.getString("analysesRequested"),
                            nextVisitDate = doc.getLong("nextVisitDate"),
                            firestoreId = doc.id,
                            farmId = id,
                            batchId = doc.getString("batchId")
                        )
                    }?.sortedByDescending { it.date } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    suspend fun addVetVisit(v: VetVisit) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("vet_visits").add(hashMapOf(
            "date" to v.date,
            "vetName" to v.vetName,
            "reason" to v.reason,
            "observations" to v.observations,
            "diagnosis" to v.diagnosis,
            "recommendations" to v.recommendations,
            "vaccinationsDone" to v.vaccinationsDone,
            "analysesRequested" to v.analysesRequested,
            "nextVisitDate" to v.nextVisitDate,
            "farmId" to fId,
            "batchId" to v.batchId
        )).await()
    }

    suspend fun deleteVetVisit(id: String) {
        checkAndThrowIfBlocked()
        db.collection("vet_visits").document(id).delete().await()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getLabAnalysesFlow(): Flow<List<LabAnalysis>> = if (DemoMode.enabled) flowOf(DemoData.labAnalyses) else farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("lab_analyses").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        LabAnalysis(
                            id = 0L,
                            type = doc.getString("type") ?: "AUTRE",
                            date = doc.getLong("date") ?: 0L,
                            resultSummary = doc.getString("resultSummary"),
                            notes = doc.getString("notes"),
                            firestoreId = doc.id,
                            farmId = id,
                            batchId = doc.getString("batchId")
                        )
                    }?.sortedByDescending { it.date } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    suspend fun addLabAnalysis(a: LabAnalysis) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("lab_analyses").add(hashMapOf(
            "type" to a.type,
            "date" to a.date,
            "resultSummary" to a.resultSummary,
            "notes" to a.notes,
            "farmId" to fId,
            "batchId" to a.batchId
        )).await()
    }

    suspend fun deleteLabAnalysis(id: String) {
        checkAndThrowIfBlocked()
        db.collection("lab_analyses").document(id).delete().await()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getBiosecurityTasksFlow(): Flow<List<BiosecurityTask>> = if (DemoMode.enabled) flowOf(DemoData.biosecurityTasks) else farmIdFlow.flatMapLatest { fId ->
        val id = fId ?: getFarmId()
        if (id == null) flowOf(emptyList())
        else callbackFlow {
            val sub = db.collection("biosecurity_tasks").whereEqualTo("farmId", id)
                .addSnapshotListener { s, e ->
                    val list = s?.documents?.mapNotNull { doc ->
                        BiosecurityTask(
                            id = 0L,
                            task = doc.getString("task") ?: "",
                            frequency = doc.getString("frequency") ?: "EVENEMENT",
                            lastDoneDate = doc.getLong("lastDoneDate"),
                            doneBy = doc.getString("doneBy"),
                            firestoreId = doc.id,
                            farmId = id
                        )
                    } ?: emptyList()
                    trySend(list)
                }
            awaitClose { sub.remove() }
        }
    }

    suspend fun addBiosecurityTask(t: BiosecurityTask) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        db.collection("biosecurity_tasks").add(hashMapOf(
            "task" to t.task,
            "frequency" to t.frequency,
            "lastDoneDate" to t.lastDoneDate,
            "doneBy" to t.doneBy,
            "farmId" to fId
        )).await()
    }

    suspend fun updateBiosecurityTask(t: BiosecurityTask) {
        checkAndThrowIfBlocked()
        t.firestoreId?.let {
            db.collection("biosecurity_tasks").document(it).update(hashMapOf(
                "lastDoneDate" to t.lastDoneDate,
                "doneBy" to t.doneBy
            ) as Map<String, Any>).await()
        }
    }

    suspend fun deleteBiosecurityTask(id: String) {
        checkAndThrowIfBlocked()
        db.collection("biosecurity_tasks").document(id).delete().await()
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
            "doneDate" to r.doneDate,
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
                "isDone" to r.isDone,
                "doneDate" to r.doneDate
            ) as Map<String, Any>).await()
        }
    }

    suspend fun deleteHealthReminder(id: String) {
        checkAndThrowIfBlocked()
        db.collection("health_reminders").document(id).delete().await()
    }

    /**
     * Marque un rappel de santé comme FAIT : met à jour son état courant et,
     * surtout, conserve une trace permanente dans l'historique ([HealthReminderLog]),
     * seul endroit où retrouver la liste et les dates des rappels déjà effectués
     * (le document [HealthReminder] lui-même est réinitialisé au cycle suivant
     * pour les rappels récurrents).
     */
    suspend fun markHealthReminderDone(r: HealthReminder) {
        checkAndThrowIfBlocked()
        val fId = requireFarmId()
        val now = System.currentTimeMillis()

        r.firestoreId?.let {
            db.collection("health_reminders").document(it).update(hashMapOf(
                "isDone" to true,
                "doneDate" to now
            ) as Map<String, Any>).await()
        }

        db.collection("health_reminder_logs").add(hashMapOf(
            "title" to r.title,
            "type" to r.type,
            "description" to r.description,
            "dueDate" to r.dueDate,
            "doneDate" to now,
            "batchId" to r.batchId,
            "farmId" to fId
        )).await()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getAllUsersFlow(): Flow<List<User>> = if (DemoMode.enabled) flowOf(DemoData.users) else farmIdFlow.flatMapLatest { fId ->
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
    fun getLoginHistoryFlow(): Flow<List<LoginEntry>> = if (DemoMode.enabled) flowOf(DemoData.loginHistory) else farmIdFlow.flatMapLatest { fId ->
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
