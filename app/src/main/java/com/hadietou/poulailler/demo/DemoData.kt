package com.hadietou.poulailler.demo

import com.hadietou.poulailler.data.*

/**
 * Jeu de données fictives pour le mode démo (voir [DemoMode]). Toutes les dates sont
 * calculées relativement à "maintenant" à chaque accès, pour que la démo reste toujours
 * d'actualité (lot "en cours", mortalité "ce mois-ci", etc.) quel que soit le jour où
 * quelqu'un l'ouvre.
 */
object DemoData {
    private const val DAY = 86_400_000L
    const val FARM_ID = "demo-farm"
    const val BATCH_ID = "demo-batch-1"
    const val USER_ID = "demo-user"

    private fun now() = System.currentTimeMillis()

    val currentUser: User
        get() = User(
            uid = USER_ID,
            username = "Amina (Démo)",
            email = "demo@kourkourou.app",
            password = "",
            role = "RESPONSABLE",
            active = true,
            farmId = FARM_ID,
            isPending = false,
            createdAt = now() - 300 * DAY
        )

    val farmInfo: FarmInfo
        get() = FarmInfo(
            farmName = "Ferme Démo Kourkourou",
            currency = "MRU",
            setupDate = now() - 300 * DAY,
            locality = "Nouakchott",
            latitude = 18.0858,
            longitude = -15.9785
        )

    val batch: Batch
        get() = Batch(
            name = "Lot Démo 001",
            hensCount = 320,
            henBreed = "Lohmann Brown",
            arrivalDate = now() - 280 * DAY,
            chickBirthDate = now() - 280 * DAY,
            status = "ACTIVE",
            typeLot = "PONDEUSE",
            firestoreId = BATCH_ID,
            farmId = FARM_ID,
            feedRation = 0.115,
            providerName = "Couvoir El Baraka",
            providerPhone = "22 33 44 55"
        )

    val batches: List<Batch> get() = listOf(batch)

    val mortalities: List<Mortality>
        get() {
            val n = now()
            // Tendance en hausse sur les 3 derniers jours (comme la vraie alerte
            // "Mortalité en hausse" observée en usage réel), reste du mois plus calme.
            val recent = listOf(
                Triple(0, 6, "Stress thermique (Chaleur)"),
                Triple(1, 5, "Stress thermique (Chaleur)"),
                Triple(2, 3, "Inconnue"),
                Triple(4, 2, "Coccidiose"),
                Triple(7, 1, "Inconnue"),
                Triple(10, 2, "Stress thermique (Chaleur)"),
                Triple(14, 1, "Prédation"),
                Triple(18, 3, "Coccidiose"),
                Triple(23, 1, "Inconnue")
            )
            return recent.mapIndexed { i, (daysAgo, count, cause) ->
                Mortality(
                    count = count,
                    date = n - daysAgo * DAY,
                    firestoreId = "demo-mortality-$i",
                    farmId = FARM_ID,
                    batchId = BATCH_ID,
                    cause = cause,
                    zone = if (i % 3 == 0) "Bâtiment A" else null,
                    confirmedCause = if (daysAgo == 4) "Coccidiose" else null
                )
            }
        }

    val eggEntries: List<EggEntry>
        get() {
            val n = now()
            val base = 215
            return (0 until 15).map { d ->
                val variation = listOf(0, -8, 4, -3, 6, -12, 2, 5, -5, 3, 8, -6, 1, -18, 4)[d]
                EggEntry(
                    userId = USER_ID,
                    date = n - d * DAY,
                    eggsCount = (base + variation).coerceAtLeast(150),
                    brokenEggsCount = (2..6).random(),
                    remarks = null,
                    firestoreId = "demo-egg-$d",
                    farmId = FARM_ID,
                    batchId = BATCH_ID
                )
            }
        }

    val sales: List<EggSale>
        get() {
            val n = now()
            val buyers = listOf("Boutique Sidi", "Marché Capitale", "Superette Teyarett", "Grossiste Ksar")
            return (0 until 8).map { i ->
                val daysAgo = (i * 3.5).toLong()
                val qty = listOf(22, 18, 25, 20, 16, 24, 19, 21)[i]
                val price = 1850.0
                EggSale(
                    userId = USER_ID,
                    date = n - daysAgo * DAY,
                    quantity = qty,
                    pricePerUnit = price,
                    totalPrice = qty * price,
                    buyer = buyers[i % buyers.size],
                    phoneNumber = "2${(2000_0000..4999_9999).random()}",
                    isPaid = i != 0,
                    firestoreId = "demo-sale-$i",
                    farmId = FARM_ID,
                    batchId = BATCH_ID
                )
            }
        }

    val expenses: List<Expense>
        get() {
            val n = now()
            data class E(val daysAgo: Long, val cat: String, val sub: String?, val amount: Double, val kg: Double?, val desc: String?)
            val items = listOf(
                // Constitution du stock initial à l'arrivée du lot (couvre la ration croissante
                // sur toute la durée déjà écoulée) + achats d'appoint récents.
                E(278, "Aliment", "Démarrage", 10_600_000.0, 20000.0, "Constitution du stock initial (démarrage à ponte)"),
                E(2, "Aliment", "Ponte", 185_000.0, 350.0, "Sac aliment ponte x14"),
                E(6, "Aliment", "Ponte", 92_000.0, 175.0, "Complément"),
                E(1, "Cheptel & Santé", "Vaccins", 28_000.0, null, "Vaccins Newcastle"),
                E(9, "Cheptel & Santé", "Traitements", 15_500.0, null, "Amoxicilline"),
                E(4, "Main d'œuvre", null, 45_000.0, null, "Salaire gardien"),
                E(12, "Transport", null, 12_000.0, null, "Livraison aliment"),
                E(3, "Consommables", null, 8_500.0, null, "Litière"),
                E(20, "Équipement", null, 22_000.0, null, "Mangeoires"),
                E(15, "Aliment", "Ponte", 178_000.0, 340.0, "Sac aliment ponte x13"),
                // Alvéoles à œufs : catégorie dédiée utilisée par le calcul du stock global
                // (achats - collectes / 30), voir DashboardViewModel.refreshAllStats().
                E(270, "Alvéoles à œufs", null, 180_000.0, 400.0, "Achat alvéoles (stock initial)"),
                E(10, "Alvéoles à œufs", null, 45_000.0, 100.0, "Réassort alvéoles")
            )
            return items.mapIndexed { i, e ->
                Expense(
                    date = n - e.daysAgo * DAY,
                    category = e.cat,
                    amount = e.amount,
                    quantityKg = e.kg,
                    description = e.desc,
                    firestoreId = "demo-expense-$i",
                    farmId = FARM_ID,
                    batchId = BATCH_ID,
                    subCategory = e.sub
                )
            }
        }

    val vaccines: List<VaccineEntry>
        get() {
            val n = now()
            return listOf(
                VaccineEntry(
                    name = "Newcastle (Rappel La Sota)", date = n - 60 * DAY, remarks = "RAS",
                    firestoreId = "demo-vac-1", farmId = FARM_ID, batchId = BATCH_ID, status = "REALISE",
                    manufacturer = "MSD", lotNumber = "NC-2291", route = "Eau de boisson", targetCount = 320, administeredBy = "Amina"
                ),
                VaccineEntry(
                    name = "Typhose aviaire", date = n - 20 * DAY, remarks = null,
                    firestoreId = "demo-vac-2", farmId = FARM_ID, batchId = BATCH_ID, status = "REALISE",
                    manufacturer = "Ceva", route = "Injection", targetCount = 315
                ),
                VaccineEntry(
                    name = "Newcastle (Rappel trimestriel)", date = n + 5 * DAY, remarks = "À prévoir",
                    firestoreId = "demo-vac-3", farmId = FARM_ID, batchId = BATCH_ID, status = "PLANIFIE"
                )
            )
        }

    val healthReminders: List<HealthReminder>
        get() {
            val n = now()
            return listOf(
                HealthReminder(
                    type = "DEPARASITAGE", title = "Déparasitage interne",
                    description = "Traitement contre les vers (tous les 3 mois).",
                    dueDate = n - 2 * DAY, batchId = BATCH_ID, recurring = true, frequencyMonths = 3,
                    firestoreId = "demo-reminder-1"
                ),
                HealthReminder(
                    type = "VACCIN", title = "Newcastle (ND)",
                    description = "Vaccination de rappel recommandée (tous les 2 mois).",
                    dueDate = n + 5 * DAY, batchId = BATCH_ID, recurring = true, frequencyMonths = 2,
                    firestoreId = "demo-reminder-2"
                )
            )
        }

    val healthReminderLogs: List<HealthReminderLog>
        get() {
            val n = now()
            return listOf(
                HealthReminderLog(
                    title = "Bronchite infectieuse (IB)", type = "VACCIN",
                    description = "Vaccination de rappel effectuée.",
                    dueDate = n - 62 * DAY, doneDate = n - 60 * DAY, batchId = BATCH_ID,
                    firestoreId = "demo-log-1"
                ),
                HealthReminderLog(
                    title = "Déparasitage externe", type = "DEPARASITAGE",
                    description = "Traitement effectué.",
                    dueDate = n - 92 * DAY, doneDate = n - 90 * DAY, batchId = BATCH_ID,
                    firestoreId = "demo-log-2"
                )
            )
        }

    val treatments: List<Treatment>
        get() {
            val n = now()
            return listOf(
                Treatment(
                    medicationName = "Amoxicilline", reason = "Toux et éternuements observés",
                    startDate = n - 4 * DAY, endDate = n - 1 * DAY, dose = "1g / L d'eau", route = "Eau de boisson",
                    targetCount = 320, prescriber = "Dr Vétérinaire Sy", status = "TERMINE",
                    eggWithdrawalDays = 7, slaughterWithdrawalDays = 5,
                    notes = "Amélioration progressive.",
                    firestoreId = "demo-treatment-1", farmId = FARM_ID, batchId = BATCH_ID
                ),
                Treatment(
                    medicationName = "Vitamines + Électrolytes", reason = "Stress thermique",
                    startDate = n - 1 * DAY, endDate = null, dose = "Selon notice", route = "Eau de boisson",
                    prescriber = null, status = "EN_COURS", notes = "Pic de chaleur en cours.",
                    firestoreId = "demo-treatment-2", farmId = FARM_ID, batchId = BATCH_ID
                )
            )
        }

    val diseaseCases: List<DiseaseCase>
        get() {
            val n = now()
            return listOf(
                DiseaseCase(
                    suspectedDisease = "Coryza infectieux",
                    symptoms = listOf("Toux", "Éternuements", "Écoulements"),
                    status = "MALADIE_SUSPECTEE", severity = "MOYENNE",
                    dateReported = n - 4 * DAY, affectedCount = 12, deathCount = 0,
                    zone = "Bâtiment A",
                    observerNotes = "Plusieurs sujets touchés dans la même zone.",
                    actionsTaken = "Traitement Amoxicilline débuté.",
                    evolutionNotes = "Amélioration progressive après traitement.",
                    firestoreId = "demo-disease-1", farmId = FARM_ID, batchId = BATCH_ID
                )
            )
        }

    val healthObservations: List<HealthObservation>
        get() {
            val n = now()
            return listOf(
                HealthObservation(
                    date = n, behavior = "AGRESSIVITE", firestoreId = "demo-obs-1",
                    farmId = FARM_ID, batchId = BATCH_ID, recordedBy = "Amina",
                    notes = "Picage observé sur quelques sujets."
                ),
                HealthObservation(
                    date = n - 1 * DAY, firestoreId = "demo-obs-2",
                    farmId = FARM_ID, batchId = BATCH_ID, recordedBy = "Amina"
                ),
                HealthObservation(
                    date = n - 2 * DAY, firestoreId = "demo-obs-3",
                    farmId = FARM_ID, batchId = BATCH_ID, recordedBy = "Amina"
                )
            )
        }

    val vetVisits: List<VetVisit>
        get() = listOf(
            VetVisit(
                date = now() - 20 * DAY, vetName = "Dr Vétérinaire Sy",
                reason = "Visite de routine trimestrielle",
                observations = "Bon état général du lot.",
                diagnosis = null,
                recommendations = "Poursuivre le programme de vaccination en cours.",
                vaccinationsDone = "Typhose aviaire",
                analysesRequested = null,
                nextVisitDate = now() + 70 * DAY,
                firestoreId = "demo-vet-1", farmId = FARM_ID, batchId = BATCH_ID
            )
        )

    val labAnalyses: List<LabAnalysis>
        get() = listOf(
            LabAnalysis(
                type = "EAU", date = now() - 30 * DAY,
                resultSummary = "Conforme aux normes.",
                notes = "Analyse annuelle de l'eau de boisson.",
                firestoreId = "demo-lab-1", farmId = FARM_ID, batchId = BATCH_ID
            )
        )

    val biosecurityTasks: List<BiosecurityTask>
        get() {
            val n = now()
            // Une seule tâche volontairement en retard (Désinfection) pour montrer l'alerte
            // sans donner l'impression que toute la biosécurité est négligée.
            val doneRecently = mapOf(
                "Pédiluve à l'entrée" to 0L,
                "Nettoyage des abreuvoirs" to 0L,
                "Nettoyage des mangeoires" to 1L,
                "Contrôle des visiteurs" to 0L,
                "Nettoyage du poulailler" to 5L,
                "Lutte contre les rongeurs" to 20L,
                "Contrôle de l'accès au poulailler" to 0L,
                "Contrôle des équipements" to 3L,
                "Lutte contre les insectes" to 12L,
                "Gestion des cadavres" to 0L,
                "Quarantaine des nouveaux animaux" to 45L
            )
            return BiosecurityTask.COMMON_TASKS.mapIndexed { i, task ->
                val frequency = when (task) {
                    "Pédiluve à l'entrée", "Nettoyage des abreuvoirs", "Nettoyage des mangeoires", "Contrôle de l'accès au poulailler" -> "QUOTIDIEN"
                    "Nettoyage du poulailler", "Contrôle des visiteurs", "Contrôle des équipements" -> "HEBDOMADAIRE"
                    "Désinfection", "Lutte contre les rongeurs", "Lutte contre les insectes" -> "MENSUEL"
                    else -> "EVENEMENT"
                }
                val daysAgo = doneRecently[task]
                BiosecurityTask(
                    task = task,
                    frequency = frequency,
                    lastDoneDate = daysAgo?.let { n - it * DAY },
                    doneBy = if (daysAgo != null) "Amina" else null,
                    firestoreId = "demo-biosec-$i",
                    farmId = FARM_ID
                )
            }
        }

    val users: List<User>
        get() = listOf(
            currentUser,
            User(
                uid = "demo-agent-1", username = "Moussa", email = "moussa@fermedemo.com",
                password = "", role = "AGENT", active = true, farmId = FARM_ID,
                isPending = false, createdAt = now() - 200 * DAY
            )
        )

    val loginHistory: List<LoginEntry>
        get() {
            val n = now()
            return listOf(
                LoginEntry(userId = USER_ID, username = "Amina (Démo)", timestamp = n - 2 * DAY),
                LoginEntry(userId = "demo-agent-1", username = "Moussa", timestamp = n - 3 * DAY),
                LoginEntry(userId = USER_ID, username = "Amina (Démo)", timestamp = n - 5 * DAY)
            )
        }
}
