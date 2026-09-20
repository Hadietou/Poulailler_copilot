package com.hadietou.poulailler.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.text.InputType
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.hadietou.poulailler.R
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.data.Batch
import com.hadietou.poulailler.data.FarmInfo
import com.hadietou.poulailler.data.Mortality
import com.hadietou.poulailler.data.FeedRationChange
import com.hadietou.poulailler.data.parseFeedRationHistory
import com.hadietou.poulailler.data.toHistoryString
import com.hadietou.poulailler.databinding.ActivityFarmInfoBinding
import com.hadietou.poulailler.databinding.ItemThresholdRowBinding
import com.hadietou.poulailler.network.RetrofitClient
import com.hadietou.poulailler.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.hadietou.poulailler.util.CountryData
import com.hadietou.poulailler.util.DrawerSubmenuController
import com.hadietou.poulailler.util.NavMenuStyler
import com.hadietou.poulailler.util.NetworkStatusMonitor

class FarmInfoActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityFarmInfoBinding
    private var currentFarmInfo: FarmInfo? = null
    private var currentBatch: Batch? = null
    private val firebaseRepo = FirebaseRepository()

    private val currencies = CountryData.currencies
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private var isBlocked = false
    
    private val drawerSubmenus = DrawerSubmenuController()

    // Gestion des lots (autrefois un écran à part, désormais une section de cette page).
    private val batchViewModel: BatchViewModel by viewModels()
    private lateinit var batchDialogs: BatchDialogs
    private lateinit var batchAdapter: BatchAdapter
    private var allBatches: List<Batch> = emptyList()
    private var mortalities: List<Mortality> = emptyList()

    // Position choisie pour la localité (saisie manuelle géocodée ou sélection sur la carte).
    private var pickedLatitude: Double? = null
    private var pickedLongitude: Double? = null

    private val locationPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val lat = data.getDoubleExtra(LocationPickerActivity.EXTRA_LAT, Double.NaN)
            val lon = data.getDoubleExtra(LocationPickerActivity.EXTRA_LON, Double.NaN)
            if (!lat.isNaN() && !lon.isNaN()) {
                pickedLatitude = lat
                pickedLongitude = lon
                data.getStringExtra(LocationPickerActivity.EXTRA_LOCALITY)?.let { binding.etLocality.setText(it) }
                updatePickedLocationSummary()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFarmInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusMonitor.observe(this, binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid

        setupNavigation()
        setupCurrencyDropdown()
        setupBatchSection(isFirstCreation = savedInstanceState == null)
        loadExistingData()
        observeCurrentBatch()
        observeTeamSize()
        observeMortalities()
        checkAccessStatus()

        binding.btnSaveFarmInfo.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            saveData()
        }

        binding.btnEditInfo.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            showEditMode(true)
        }

        binding.btnCancelEdit.setOnClickListener {
            showEditMode(false)
        }

        val editRation = View.OnClickListener {
            if (isBlocked) showBlockingDialog() else showRationDialog()
        }
        binding.btnEditRation.setOnClickListener(editRation)
        binding.cardFeedRation.setOnClickListener(editRation)

        binding.btnPickOnMap.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            val intent = Intent(this, LocationPickerActivity::class.java).apply {
                pickedLatitude?.let { putExtra(LocationPickerActivity.EXTRA_LAT, it) }
                pickedLongitude?.let { putExtra(LocationPickerActivity.EXTRA_LON, it) }
                putExtra(LocationPickerActivity.EXTRA_LOCALITY, binding.etLocality.text?.toString())
            }
            locationPickerLauncher.launch(intent)
        }

        binding.cardFeedRation.visibility = if (userRole == "RESPONSABLE") View.VISIBLE else View.GONE
    }

    // ── Lots ────────────────────────────────────────────────────────────────────────────

    private fun setupBatchSection(isFirstCreation: Boolean) {
        batchDialogs = BatchDialogs(this, batchViewModel)
        val isResponsable = userRole == "RESPONSABLE"

        batchAdapter = BatchAdapter { batch ->
            when {
                !isResponsable -> Unit
                isBlocked -> showBlockingDialog()
                else -> batchDialogs.showBatchOptions(batch)
            }
        }
        binding.rvBatches.layoutManager = LinearLayoutManager(this)
        binding.rvBatches.adapter = batchAdapter

        binding.btnAddBatch.visibility = if (isResponsable) View.VISIBLE else View.GONE
        binding.btnAddBatch.setOnClickListener {
            if (isBlocked) showBlockingDialog() else batchDialogs.showAddBatchDialog()
        }

        batchViewModel.allBatches.observe(this) { batches ->
            allBatches = batches
            batchAdapter.submitList(batches)
            binding.tvEmptyBatches.visibility = if (batches.isEmpty()) View.VISIBLE else View.GONE
            binding.tvBatchHint.visibility = if (batches.isNotEmpty() && isResponsable) View.VISIBLE else View.GONE
            refreshBatchStats()
        }

        batchViewModel.operationSuccess.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Opération réussie", Toast.LENGTH_SHORT).show()
            } else if (isBlocked) {
                showBlockingDialog()
            } else {
                Toast.makeText(this, "Une erreur est survenue", Toast.LENGTH_SHORT).show()
            }
        }

        // Le tableau de bord d'une ferme sans lot propose directement « lot pondeuses » ou « lot chair ».
        val presetType = intent.getStringExtra("presetType")
        if (isFirstCreation && isResponsable && batchDialogs.isValidType(presetType)) {
            binding.root.post { batchDialogs.showAddBatchDialog(presetType) }
        }
    }

    /** Effectif réellement présent : sujets à l'arrivée moins la mortalité enregistrée sur ce lot. */
    private fun currentHens(batch: Batch): Int {
        val dead = mortalities.filter { it.batchId == batch.firestoreId }.sumOf { it.count }
        return (batch.hensCount - dead).coerceAtLeast(0)
    }

    private fun refreshBatchStats() {
        val active = allBatches.filter { it.status == "ACTIVE" }
        binding.tvStatLots.text = active.size.toString()
        binding.tvStatSubjects.text = active.sumOf { currentHens(it) }.toString()
    }

    private fun observeMortalities() {
        lifecycleScope.launch {
            firebaseRepo.getMortalityFlow().collectLatest { list ->
                mortalities = list
                withContext(Dispatchers.Main) {
                    refreshBatchStats()
                    updateRationCard(currentBatch)
                }
            }
        }
    }

    /** Nombre de personnes rattachées à la ferme (responsable + agents). */
    private fun observeTeamSize() {
        lifecycleScope.launch {
            firebaseRepo.getAllUsersFlow().collectLatest { users ->
                withContext(Dispatchers.Main) { binding.tvStatTeam.text = users.size.toString() }
            }
        }
    }

    private fun observeCurrentBatch() {
        lifecycleScope.launch {
            firebaseRepo.getBatchesFlow().collectLatest { batches ->
                val batch = batches.find { it.status == "ACTIVE" }
                currentBatch = batch
                withContext(Dispatchers.Main) { updateRationCard(batch) }
            }
        }
    }

    private fun updateRationCard(batch: Batch?) {
        if (batch == null) {
            binding.tvRationValue.text = "—"
            binding.tvRationDetail.text = "Aucun lot actif"
            return
        }
        val rationG = batch.feedRation * 1000
        binding.tvRationValue.text = if (rationG % 1.0 == 0.0) rationG.toInt().toString() else "%.1f".format(Locale.US, rationG)
        val hens = currentHens(batch)
        val totalKg = batch.feedRation * hens
        binding.tvRationDetail.text = "${batch.name} · ≈ ${"%.1f".format(Locale.FRANCE, totalKg)} kg par jour pour $hens sujets actuels"
    }

    private fun showRationDialog() {
        val batch = currentBatch ?: run {
            Toast.makeText(this, "Aucun lot actif", Toast.LENGTH_SHORT).show()
            return
        }
        showNumberDialog(
            title = "Ration alimentaire",
            message = "Grammes d'aliment par sujet et par jour pour « ${batch.name} ». Appliquée à partir d'aujourd'hui.",
            currentValue = (batch.feedRation * 1000).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() },
            decimal = true,
            signed = false
        ) { value -> saveManualRation(value) }
    }

    private fun saveManualRation(rationG: Double) {
        val batch = currentBatch ?: run {
            Toast.makeText(this, "Aucun lot sélectionné", Toast.LENGTH_SHORT).show()
            return
        }
        val rationKg = rationG / 1000.0

        // La ration ne doit s'appliquer qu'à partir d'aujourd'hui : on enregistre ce
        // changement dans l'historique (à la date du jour) plutôt que d'écraser la valeur
        // utilisée pour recalculer la consommation depuis le début du lot.
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val history = batch.parseFeedRationHistory().toMutableList()
        val todayIndex = history.indexOfFirst { it.effectiveDate == todayStart }
        if (todayIndex >= 0) {
            history[todayIndex] = FeedRationChange(todayStart, rationKg)
        } else {
            history.add(FeedRationChange(todayStart, rationKg))
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val updatedBatch = batch.copy(feedRation = rationKg, feedRationHistory = history.toHistoryString())
                firebaseRepo.updateBatch(updatedBatch)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FarmInfoActivity, "Ration mise à jour : ${rationG.toInt()}g/sujet", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FarmInfoActivity, "Erreur : ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkAccessStatus() {
        lifecycleScope.launch {
            val blocked = firebaseRepo.isFarmAccessBlocked()
            isBlocked = blocked
            if (blocked) {
                withContext(Dispatchers.Main) {
                    binding.btnEditInfo.visibility = View.GONE
                    binding.btnAddBatch.visibility = View.GONE
                    showBlockingDialog()
                }
            }
        }
    }

    private fun showBlockingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Mode Lecture Seule")
            .setMessage("Veuillez envoyer un email à hadietou@gmail.com pour lui demander de valider la ferme afin de continuer à utiliser l'application. En attendant, vous pouvez uniquement consulter vos données.")
            .setCancelable(true)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupNavigation() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }

        binding.navigationView.setNavigationItemSelectedListener(this)

        rebuildDrawerMenu()
        updateNavHeader()
    }

    /**
     * NavigationView n'affiche pas toujours de manière fiable un item dont on vient
     * de changer la visibilité de groupe (menu.setGroupVisible) une fois déjà rendu à l'écran :
     * on force donc une reconstruction complète du menu avant de réappliquer les états courants.
     */
    private fun rebuildDrawerMenu() {
        val nav = binding.navigationView
        nav.menu.clear()
        nav.inflateMenu(R.menu.drawer_menu)
        val menu = nav.menu
        menu.findItem(R.id.nav_users)?.isVisible = userRole == "RESPONSABLE"
        menu.findItem(R.id.nav_expenses)?.isVisible = userRole == "RESPONSABLE"
        drawerSubmenus.applyGroupVisibility(menu)
        refreshDrawerMenuStyle()
    }

    private fun refreshDrawerMenuStyle() {
        NavMenuStyler.style(
            binding.navigationView,
            this,
            defaultIconTintRes = R.color.text_secondary,
            parents = drawerSubmenus.parentsForStyler(),
            children = DrawerSubmenuController.CHILD_ITEM_IDS
        )
    }

    private fun updateNavHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val tvUsername = headerView.findViewById<TextView>(R.id.tvUsername)
        val tvUserRole = headerView.findViewById<TextView>(R.id.tvUserRole)
        val tvAppVersion = headerView.findViewById<TextView>(R.id.tvAppVersion)

        tvAppVersion?.text = "v${BuildConfig.VERSION_NAME}"

        lifecycleScope.launch {
            val uid = userId ?: FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                val profile = firebaseRepo.getUserProfile(uid)
                withContext(Dispatchers.Main) {
                    tvUsername.text = profile?.username?.uppercase() ?: "UTILISATEUR"
                    tvUserRole.text = userRole
                }
            }
        }
    }

    private fun setupCurrencyDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencies)
        binding.actvCurrency.setAdapter(adapter)
        binding.actvCurrency.setText(currencies[0], false)
    }

    private fun loadExistingData() {
        lifecycleScope.launch {
            val info = firebaseRepo.ensureFarmLocationFromCity()
            withContext(Dispatchers.Main) {
                if (info != null && info.farmName.isNotEmpty()) {
                    currentFarmInfo = info
                    displayInfo(info)
                    if (!isBlocked) showEditMode(false)
                } else {
                    showEditMode(true)
                }
            }
        }
    }

    private fun displayInfo(info: FarmInfo) {
        binding.tvDisplayFarmName.text = info.farmName
        binding.tvDisplayCurrency.text = info.currency
        binding.tvDisplayLocality.text = info.locality?.takeIf { it.isNotEmpty() } ?: "Non définie"

        binding.etFarmName.setText(info.farmName)
        binding.actvCurrency.setText(info.currency, false)
        binding.etLocality.setText(info.locality ?: "")
        pickedLatitude = info.latitude
        pickedLongitude = info.longitude
        updatePickedLocationSummary()

        binding.etEggTraysCritical.setText(info.eggTraysCriticalThreshold.toString())
        binding.etFeedStockCriticalDays.setText(info.feedStockCriticalDays.toString())
        binding.etFeedStockWarningDays.setText(info.feedStockWarningDays.toString())
        binding.etHeatAlertTemp.setText(info.heatAlertTempCelsius.toString())
        binding.etMortalitySpikeMultiplier.setText(info.mortalitySpikeMultiplier.toString())
        binding.etWeatherTempOffset.setText(info.weatherTempOffsetCelsius.toString())
        binding.etLightingHours.setText(info.lightingHoursAfterSunrise.toString())
        binding.etVaccineInterval.setText(info.vaccineIntervalMonths.toString())
        binding.etDewormingInternalInterval.setText(info.dewormingInternalIntervalMonths.toString())
        binding.etDewormingExternalInterval.setText(info.dewormingExternalIntervalMonths.toString())

        renderThresholds(info)

        binding.tvHeroCreated.text = formatCreationDate(info.setupDate)
        binding.tvHeroAge.text = formatFarmAge(info.setupDate)
        binding.tvInfoGps.text = if (info.latitude != null && info.longitude != null) {
            "%.4f, %.4f".format(Locale.US, info.latitude, info.longitude)
        } else {
            "Non définie"
        }
        updateHeroLocation(info.locality, farmSummary?.country ?: farmSummary?.city)
        loadFarmSummary(info)
    }

    private var farmSummary: FirebaseRepository.FarmSummary? = null

    private fun loadFarmSummary(info: FarmInfo) {
        lifecycleScope.launch {
            val summary = firebaseRepo.getFarmSummary() ?: return@launch
            farmSummary = summary
            withContext(Dispatchers.Main) {
                binding.tvInfoOwner.text = summary.ownerName ?: "—"
                updateHeroLocation(info.locality ?: summary.city, summary.country)
            }
        }
    }

    private fun updateHeroLocation(place: String?, country: String?) {
        val text = listOfNotNull(place?.takeIf { it.isNotBlank() }, country?.takeIf { it.isNotBlank() })
            .joinToString(", ")
        binding.tvHeroLocation.text = text.ifEmpty { "Localisation non définie" }
    }

    // ── Seuils d'alerte : lignes tactiles ────────────────────────────────────────────────

    private class ThresholdSpec(
        val label: String,
        val hint: String?,
        val field: EditText,
        val display: (FarmInfo) -> String,
        val decimal: Boolean = false,
        val signed: Boolean = false
    )

    private fun renderThresholds(info: FarmInfo) {
        val stock = listOf(
            ThresholdSpec("Alvéoles d'œufs", "Alerte quand le stock passe sous ce nombre", binding.etEggTraysCritical,
                { "${it.eggTraysCriticalThreshold} alvéoles" }),
            ThresholdSpec("Aliment · seuil critique", "Autonomie en jours (alerte rouge)", binding.etFeedStockCriticalDays,
                { "< ${it.feedStockCriticalDays} j" }),
            ThresholdSpec("Aliment · seuil d'alerte", "Autonomie en jours (alerte orange)", binding.etFeedStockWarningDays,
                { "< ${it.feedStockWarningDays} j" })
        )
        val climate = listOf(
            ThresholdSpec("Alerte canicule", "Notification à partir de cette température", binding.etHeatAlertTemp,
                { "${it.heatAlertTempCelsius} °C" }),
            ThresholdSpec("Correction météo locale", "Ajuste la météo affichée au relevé de terrain", binding.etWeatherTempOffset,
                { "%+.1f °C".format(Locale.US, it.weatherTempOffsetCelsius) }, decimal = true, signed = true),
            ThresholdSpec("Éclairage artificiel", "Heures après le lever du soleil", binding.etLightingHours,
                { "${it.lightingHoursAfterSunrise} h" })
        )
        val health = listOf(
            ThresholdSpec("Mortalité anormale", "Alerte au-delà de ce multiple de la moyenne sur 7 jours", binding.etMortalitySpikeMultiplier,
                { "× ${it.mortalitySpikeMultiplier}" }, decimal = true),
            ThresholdSpec("Rappel vaccins", null, binding.etVaccineInterval,
                { "tous les ${it.vaccineIntervalMonths} mois" }),
            ThresholdSpec("Déparasitage interne", null, binding.etDewormingInternalInterval,
                { "tous les ${it.dewormingInternalIntervalMonths} mois" }),
            ThresholdSpec("Déparasitage externe", null, binding.etDewormingExternalInterval,
                { "tous les ${it.dewormingExternalIntervalMonths} mois" })
        )
        fillThresholdGroup(binding.llThresholdsStock, stock, info, R.color.earthy_container, R.color.earthy_orange)
        fillThresholdGroup(binding.llThresholdsClimate, climate, info, R.color.accent_blue_container, R.color.accent_blue)
        fillThresholdGroup(binding.llThresholdsHealth, health, info, R.color.emerald_container, R.color.primary)
    }

    private fun fillThresholdGroup(
        container: android.widget.LinearLayout,
        specs: List<ThresholdSpec>,
        info: FarmInfo,
        chipBgRes: Int,
        chipTextRes: Int
    ) {
        container.removeAllViews()
        specs.forEachIndexed { index, spec ->
            val row = ItemThresholdRowBinding.inflate(LayoutInflater.from(this), container, false)
            row.tvThresholdLabel.text = spec.label
            row.tvThresholdHint.text = spec.hint
            row.tvThresholdHint.visibility = if (spec.hint == null) View.GONE else View.VISIBLE
            row.tvThresholdValue.text = spec.display(info)
            row.tvThresholdValue.setTextColor(ContextCompat.getColor(this, chipTextRes))
            ViewCompat.setBackgroundTintList(row.tvThresholdValue, ContextCompat.getColorStateList(this, chipBgRes))
            row.root.setOnClickListener {
                if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
                showNumberDialog(
                    title = spec.label,
                    message = spec.hint,
                    currentValue = spec.field.text?.toString().orEmpty(),
                    decimal = spec.decimal,
                    signed = spec.signed
                ) { value ->
                    // Le formulaire de modification porte déjà toute la logique d'enregistrement :
                    // on y dépose la nouvelle valeur puis on enregistre, sans dupliquer la validation.
                    spec.field.setText(if (spec.decimal) value.toString() else value.toInt().toString())
                    saveData()
                }
            }
            container.addView(row.root)
            if (index < specs.lastIndex) {
                container.addView(View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).also { it.marginStart = (16 * resources.displayMetrics.density).toInt() }
                    setBackgroundColor(ContextCompat.getColor(this@FarmInfoActivity, R.color.off_white))
                })
            }
        }
    }

    /** Petite fenêtre de saisie d'un nombre ; [onSave] reçoit une valeur déjà validée. */
    private fun showNumberDialog(
        title: String,
        message: String?,
        currentValue: String,
        decimal: Boolean,
        signed: Boolean,
        onSave: (Double) -> Unit
    ) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0) or
                (if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
            setText(currentValue)
            setSelection(text.length)
            setTextColor(ContextCompat.getColor(this@FarmInfoActivity, R.color.text_primary))
        }
        val holder = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .apply { message?.let { setMessage(it) } }
            .setView(holder)
            .setPositiveButton("Enregistrer", null)
            .setNegativeButton("Annuler", null)
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().replace(',', '.').toDoubleOrNull()
                if (value == null || (!signed && value < 0)) {
                    input.error = "Valeur invalide"
                } else {
                    dialog.dismiss()
                    onSave(value)
                }
            }
        }
        dialog.show()
    }

    private fun formatCreationDate(millis: Long): String =
        if (millis <= 0L) "Date inconnue"
        else SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date(millis))

    private fun formatFarmAge(millis: Long): String {
        if (millis <= 0L) return "—"
        val days = ((System.currentTimeMillis() - millis) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
        return when {
            days == 0 -> "Aujourd'hui"
            days < 31 -> if (days == 1) "1 jour" else "$days jours"
            days < 365 -> "${days / 30} mois"
            else -> {
                val years = days / 365
                val months = (days % 365) / 30
                val y = if (years == 1) "1 an" else "$years ans"
                if (months > 0) "$y et $months mois" else y
            }
        }
    }

    private fun updatePickedLocationSummary() {
        val lat = pickedLatitude
        val lon = pickedLongitude
        binding.tvPickedLocationSummary.text = if (lat != null && lon != null) {
            "Position : %.4f, %.4f".format(Locale.US, lat, lon)
        } else {
            "Aucune position précise définie (météo par défaut : Nouakchott)"
        }
    }

    private fun showEditMode(isEditing: Boolean) {
        if (isEditing) {
            binding.layoutDisplayInfo.visibility = View.GONE
            binding.layoutEditInfo.visibility = View.VISIBLE
            binding.btnCancelEdit.visibility = if (currentFarmInfo != null) View.VISIBLE else View.GONE
        } else {
            binding.layoutDisplayInfo.visibility = View.VISIBLE
            binding.layoutEditInfo.visibility = View.GONE
        }
    }

    private fun saveData() {
        val name = binding.etFarmName.text.toString()
        val currency = binding.actvCurrency.text.toString()
        val localityText = binding.etLocality.text?.toString()?.trim().orEmpty()

        if (name.isEmpty()) {
            Toast.makeText(this, "Veuillez saisir le nom de la ferme", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Si l'utilisateur a tapé/modifié un nom de localité sans passer par la carte,
                // on tente de le géocoder pour obtenir des coordonnées (nécessaires à la météo).
                // Si la position venait déjà de la carte, on la garde telle quelle.
                if (localityText.isNotEmpty() && localityText != currentFarmInfo?.locality) {
                    try {
                        val geocoded = RetrofitClient.geocodingApi.search(localityText).results?.firstOrNull()
                        if (geocoded != null) {
                            pickedLatitude = geocoded.latitude
                            pickedLongitude = geocoded.longitude
                        } else {
                            pickedLatitude = null
                            pickedLongitude = null
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@FarmInfoActivity, "Localité introuvable : la météo utilisera l'emplacement par défaut", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FarmInfoActivity", "Error geocoding locality", e)
                    }
                } else if (localityText.isEmpty()) {
                    pickedLatitude = null
                    pickedLongitude = null
                }

                val info = FarmInfo(
                    id = 1,
                    farmName = name,
                    currency = currency,
                    locality = localityText.ifEmpty { null },
                    latitude = pickedLatitude,
                    longitude = pickedLongitude,
                    eggTraysCriticalThreshold = binding.etEggTraysCritical.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_EGG_TRAYS_CRITICAL,
                    feedStockCriticalDays = binding.etFeedStockCriticalDays.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_FEED_STOCK_CRITICAL_DAYS,
                    feedStockWarningDays = binding.etFeedStockWarningDays.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_FEED_STOCK_WARNING_DAYS,
                    heatAlertTempCelsius = binding.etHeatAlertTemp.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_HEAT_ALERT_TEMP,
                    mortalitySpikeMultiplier = binding.etMortalitySpikeMultiplier.text.toString().toDoubleOrNull()
                        ?.takeIf { it > 0 } ?: FarmInfo.DEFAULT_MORTALITY_SPIKE_MULTIPLIER,
                    weatherTempOffsetCelsius = binding.etWeatherTempOffset.text.toString().toDoubleOrNull()
                        ?: FarmInfo.DEFAULT_WEATHER_TEMP_OFFSET,
                    lightingHoursAfterSunrise = binding.etLightingHours.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_LIGHTING_HOURS,
                    vaccineIntervalMonths = binding.etVaccineInterval.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_VACCINE_INTERVAL_MONTHS,
                    dewormingInternalIntervalMonths = binding.etDewormingInternalInterval.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_DEWORMING_INTERNAL_MONTHS,
                    dewormingExternalIntervalMonths = binding.etDewormingExternalInterval.text.toString().toIntOrNull()
                        ?: FarmInfo.DEFAULT_DEWORMING_EXTERNAL_MONTHS
                )
                firebaseRepo.saveFarmInfo(info)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FarmInfoActivity, "Informations enregistrées", Toast.LENGTH_SHORT).show()
                    loadExistingData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FarmInfoActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (isBlocked && item.itemId != R.id.nav_logout && item.itemId != R.id.nav_dashboard && item.itemId != R.id.nav_delete_account) {
            showBlockingDialog()
            return false
        }
        if (drawerSubmenus.handleParentClick(item.itemId)) {
            rebuildDrawerMenu()
            return true
        }

        when (item.itemId) {
            R.id.nav_dashboard -> {
                val intent = Intent(this, DashboardActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
                finish()
            }
            R.id.nav_users -> {
                val intent = Intent(this, ResponsableActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_collect -> {
                val intent = Intent(this, AgentActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_vaccines -> {
                val intent = Intent(this, HealthDashboardActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_expenses -> {
                val intent = Intent(this, ExpensesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_settings -> {}
            R.id.nav_sales -> {
                val intent = Intent(this, SalesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_mortality -> {
                val intent = Intent(this, MortalityActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_treatments -> {
                val intent = Intent(this, TreatmentActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_diseases -> {
                val intent = Intent(this, DiseaseActivity::class.java)
                intent.putExtra("userIdString", userId)
                intent.putExtra("role", userRole)
                startActivity(intent)
            }
            R.id.nav_delete_account -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.delete_account_url)))
                startActivity(intent)
            }
            R.id.nav_logout -> {
                NavMenuStyler.confirmLogout(this) {
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onResume() {
        super.onResume()
        updateNavHeader()
        checkAccessStatus()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
