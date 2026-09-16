package com.hadietou.poulailler.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hadietou.poulailler.R
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.data.AppDatabase
import com.hadietou.poulailler.data.VaccineEntry
import com.hadietou.poulailler.data.HealthReminder
import com.hadietou.poulailler.data.HealthReminderLog
import com.hadietou.poulailler.databinding.ActivityVaccineBinding
import com.hadietou.poulailler.databinding.DialogAddVaccineBinding
import com.hadietou.poulailler.databinding.ItemVaccineBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.hadietou.poulailler.util.DrawerSubmenuController
import com.hadietou.poulailler.util.NavMenuStyler
import com.hadietou.poulailler.util.NetworkStatusMonitor

class VaccineActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityVaccineBinding
    private val calendar = Calendar.getInstance()
    private var selectedDateMs: Long = System.currentTimeMillis()
    private var userRole: String = "AGENT"
    private var userId: String? = null
    private var selectedBatchId: String? = null
    private val firebaseRepo = FirebaseRepository()
    private lateinit var adapter: VaccineAdapter
    private lateinit var reminderAdapter: HealthReminderAdapter
    private lateinit var reminderLogAdapter: HealthReminderLogAdapter

    private var allVaccines: List<VaccineEntry> = emptyList()
    private var allHealthReminders: List<HealthReminder> = emptyList()
    private var isShowingAll = false
    private var isBlocked = false

    private val drawerSubmenus = DrawerSubmenuController(DrawerSubmenuController.Submenu.HEALTH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVaccineBinding.inflate(layoutInflater)
        setContentView(binding.root)
        NetworkStatusMonitor.observe(this, binding.root)

        userRole = intent.getStringExtra("role") ?: "AGENT"
        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid
        selectedBatchId = intent.getStringExtra("selectedBatchId")

        setupNavigation()
        setupRecyclerView()
        setupHealthReminders()
        setupHealthReminderHistory()
        observeVaccines()
        observeHealthReminders()
        observeHealthReminderLogs()
        loadEnhancedSanitaryGuide()
        checkAccessStatus()

        binding.fabAddVaccine.setOnClickListener {
            if (isBlocked) { showBlockingDialog(); return@setOnClickListener }
            showAddVaccineDialog()
        }

        binding.btnShowMore.setOnClickListener {
            isShowingAll = true
            refreshDisplay()
        }
    }

    private fun checkAccessStatus() {
        lifecycleScope.launch {
            val blocked = firebaseRepo.isFarmAccessBlocked()
            isBlocked = blocked
            if (blocked) {
                withContext(Dispatchers.Main) {
                    binding.fabAddVaccine.visibility = View.GONE
                    showBlockingDialog()
                }
            }
        }
    }

    private fun showBlockingDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
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
        menu.findItem(R.id.nav_batches)?.isVisible = userRole == "RESPONSABLE"
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

    private fun setupRecyclerView() {
        adapter = VaccineAdapter { entry ->
            if (isBlocked) { showBlockingDialog(); return@VaccineAdapter }
            if (userRole == "RESPONSABLE") {
                showEditVaccineDialog(entry)
            }
        }
        binding.rvVaccineHistory.layoutManager = LinearLayoutManager(this)
        binding.rvVaccineHistory.adapter = adapter
    }

    private fun setupHealthReminders() {
        reminderAdapter = HealthReminderAdapter { reminder ->
            lifecycleScope.launch {
                try {
                    firebaseRepo.markHealthReminderDone(reminder)
                } catch (e: Exception) {
                    Toast.makeText(this@VaccineActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.rvHealthReminders.layoutManager = LinearLayoutManager(this)
        binding.rvHealthReminders.adapter = reminderAdapter
    }

    private fun setupHealthReminderHistory() {
        reminderLogAdapter = HealthReminderLogAdapter()
        binding.rvHealthReminderHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHealthReminderHistory.adapter = reminderLogAdapter
    }

    private fun observeHealthReminderLogs() {
        lifecycleScope.launch {
            firebaseRepo.getHealthReminderLogsFlow().collectLatest { list ->
                val filtered = if (selectedBatchId != null) list.filter { it.batchId == selectedBatchId } else list
                withContext(Dispatchers.Main) {
                    binding.rvHealthReminderHistory.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
                    binding.tvNoReminderHistory.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                    reminderLogAdapter.submitList(filtered)
                }
            }
        }
    }

    private fun observeVaccines() {
        lifecycleScope.launch {
            firebaseRepo.getVaccinesFlow().collectLatest { list ->
                allVaccines = if (selectedBatchId != null) {
                    list.filter { it.batchId == selectedBatchId }
                } else {
                    list
                }
                withContext(Dispatchers.Main) {
                    refreshDisplay()
                    refreshNextVaccineStat()
                }
            }
        }
    }

    private fun refreshNextVaccineStat() {
        val now = System.currentTimeMillis()
        val next = allVaccines.filter { it.status == "PLANIFIE" && it.date >= now }.minByOrNull { it.date }
        if (next != null) {
            val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())
            binding.tvStatNextVaccine.text = next.name
            binding.tvStatNextVaccineDate.text = "Prévu le ${sdf.format(Date(next.date))}"
        } else {
            binding.tvStatNextVaccine.text = "Aucun soin planifié"
            binding.tvStatNextVaccineDate.text = ""
        }
    }

    private fun observeHealthReminders() {
        lifecycleScope.launch {
            firebaseRepo.getHealthRemindersFlow().collectLatest { list ->
                allHealthReminders = if (selectedBatchId != null) {
                    list.filter { it.batchId == selectedBatchId }
                } else {
                    list
                }
                withContext(Dispatchers.Main) {
                    refreshHealthRemindersDisplay()
                }
            }
        }
    }

    private fun refreshHealthRemindersDisplay() {
        val active = allHealthReminders.filter { !it.isDone }.distinctBy { it.title }

        binding.tvStatRemindersCount.text = active.size.toString()
        binding.tvStatRemindersSubtitle.text = if (active.isEmpty()) "Tout est à jour" else "en attente"

        if (active.isEmpty()) {
            binding.rvHealthReminders.visibility = View.GONE
            binding.cardNoReminders.visibility = View.VISIBLE
        } else {
            binding.rvHealthReminders.visibility = View.VISIBLE
            binding.cardNoReminders.visibility = View.GONE
            reminderAdapter.submitList(active)
        }
    }

    private fun refreshDisplay() {
        val toDisplay = if (isShowingAll) allVaccines else allVaccines.take(10)
        adapter.submitList(toDisplay)
        binding.btnShowMore.visibility = if (!isShowingAll && allVaccines.size > 10) View.VISIBLE else View.GONE
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

    private val vaccineRoutes = listOf(
        "Eau de boisson", "Injection sous-cutanée", "Injection intramusculaire",
        "Oculaire / nasale", "Aspersion (spray)", "Alimentaire", "Autre"
    )

    private val vaccineStatusLabels = linkedMapOf(
        "REALISE" to "✅ Réalisé",
        "PLANIFIE" to "🗓️ Planifié",
        "REPORTE" to "⏸️ Reporté",
        "ANNULE" to "❌ Annulé"
    )

    private fun statusFromLabel(label: String): String =
        vaccineStatusLabels.entries.find { it.value == label }?.key ?: "REALISE"

    private fun setupAdvancedToggle(dialogBinding: DialogAddVaccineBinding) {
        dialogBinding.btnToggleAdvanced.setOnClickListener {
            val isVisible = dialogBinding.layoutAdvancedFields.visibility == View.VISIBLE
            dialogBinding.layoutAdvancedFields.visibility = if (isVisible) View.GONE else View.VISIBLE
            dialogBinding.btnToggleAdvanced.text = if (isVisible) "▸ Détails avancés (optionnel)" else "▾ Détails avancés (optionnel)"
        }
        val routeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, vaccineRoutes)
        dialogBinding.actvRoute.setAdapter(routeAdapter)
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, vaccineStatusLabels.values.toList())
        dialogBinding.actvStatus.setAdapter(statusAdapter)
    }

    private fun showAddVaccineDialog() {
        val dialogBinding = DialogAddVaccineBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        setupAdvancedToggle(dialogBinding)
        dialogBinding.actvStatus.setText(vaccineStatusLabels.getValue("REALISE"), false)

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.btnSelectDate.text = "Date: ${sdf.format(Date())}"
        selectedDateMs = System.currentTimeMillis()
        var expiryDateMs: Long? = null

        dialogBinding.btnSelectDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, dayOfMonth)
                selectedDateMs = tempCal.timeInMillis
                dialogBinding.btnSelectDate.text = "Date: ${sdf.format(tempCal.time)}"
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSelectExpiryDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, dayOfMonth)
                expiryDateMs = tempCal.timeInMillis
                dialogBinding.btnSelectExpiryDate.text = "Péremption: ${sdf.format(tempCal.time)}"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSaveVaccine.setOnClickListener {
            val name = dialogBinding.etVaccineName.text?.toString() ?: ""
            val remarks = dialogBinding.etRemarks.text?.toString() ?: ""
            val status = statusFromLabel(dialogBinding.actvStatus.text?.toString() ?: "")

            if (name.isEmpty()) {
                Toast.makeText(this, "Veuillez saisir le nom du soin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val entry = VaccineEntry(
                        name = name,
                        date = selectedDateMs,
                        remarks = remarks,
                        batchId = selectedBatchId,
                        status = status,
                        manufacturer = dialogBinding.etManufacturer.text?.toString()?.trim()?.ifEmpty { null },
                        lotNumber = dialogBinding.etLotNumber.text?.toString()?.trim()?.ifEmpty { null },
                        expiryDate = expiryDateMs,
                        dose = dialogBinding.etDose.text?.toString()?.trim()?.ifEmpty { null },
                        route = dialogBinding.actvRoute.text?.toString()?.trim()?.ifEmpty { null },
                        targetCount = dialogBinding.etTargetCount.text?.toString()?.toIntOrNull(),
                        administeredBy = dialogBinding.etAdministeredBy.text?.toString()?.trim()?.ifEmpty { null }
                    )
                    firebaseRepo.addVaccine(entry)

                    // Ajouter un rappel Vitamine systématique après un soin/vaccin déjà réalisé
                    // (pas de sens pour un soin simplement planifié pour plus tard).
                    if (status == "REALISE") {
                        firebaseRepo.addHealthReminder(HealthReminder(
                            type = "VITAMINE",
                            title = "Vitamines Post-Soin ($name)",
                            description = "Recommandé : Vitamine A-D-E ou B-complex pour booster l'immunité.",
                            dueDate = System.currentTimeMillis(),
                            batchId = selectedBatchId
                        ))
                    }

                    withContext(Dispatchers.Main) {
                        val msg = if (status == "PLANIFIE") "Soin planifié" else "Soin enregistré + Rappel Vitamines ajouté"
                        Toast.makeText(this@VaccineActivity, msg, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VaccineActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showEditVaccineDialog(entry: VaccineEntry) {
        val dialogBinding = DialogAddVaccineBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        setupAdvancedToggle(dialogBinding)
        dialogBinding.etVaccineName.setText(entry.name)
        dialogBinding.etRemarks.setText(entry.remarks ?: "")
        dialogBinding.actvStatus.setText(vaccineStatusLabels.getValue(entry.status), false)
        dialogBinding.etManufacturer.setText(entry.manufacturer ?: "")
        dialogBinding.etLotNumber.setText(entry.lotNumber ?: "")
        dialogBinding.etDose.setText(entry.dose ?: "")
        dialogBinding.actvRoute.setText(entry.route ?: "", false)
        dialogBinding.etTargetCount.setText(entry.targetCount?.toString() ?: "")
        dialogBinding.etAdministeredBy.setText(entry.administeredBy ?: "")
        if (!entry.manufacturer.isNullOrEmpty() || !entry.lotNumber.isNullOrEmpty() || !entry.dose.isNullOrEmpty() ||
            !entry.route.isNullOrEmpty() || entry.targetCount != null || !entry.administeredBy.isNullOrEmpty() || entry.expiryDate != null) {
            dialogBinding.layoutAdvancedFields.visibility = View.VISIBLE
            dialogBinding.btnToggleAdvanced.text = "▾ Détails avancés (optionnel)"
        }

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dialogBinding.btnSelectDate.text = "Date: ${sdf.format(Date(entry.date))}"
        var editDateMs = entry.date
        var editExpiryMs: Long? = entry.expiryDate
        if (editExpiryMs != null) {
            dialogBinding.btnSelectExpiryDate.text = "Péremption: ${sdf.format(Date(editExpiryMs))}"
        }

        dialogBinding.btnSelectDate.setOnClickListener {
            val dCal = Calendar.getInstance().apply { timeInMillis = entry.date }
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, dayOfMonth)
                editDateMs = tempCal.timeInMillis
                dialogBinding.btnSelectDate.text = "Date: ${sdf.format(tempCal.time)}"
            }, dCal.get(Calendar.YEAR), dCal.get(Calendar.MONTH), dCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSelectExpiryDate.setOnClickListener {
            val dCal = Calendar.getInstance().apply { editExpiryMs?.let { timeInMillis = it } }
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val tempCal = Calendar.getInstance()
                tempCal.set(year, month, dayOfMonth)
                editExpiryMs = tempCal.timeInMillis
                dialogBinding.btnSelectExpiryDate.text = "Péremption: ${sdf.format(tempCal.time)}"
            }, dCal.get(Calendar.YEAR), dCal.get(Calendar.MONTH), dCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        dialogBinding.btnSaveVaccine.text = "MODIFIER"
        dialogBinding.btnSaveVaccine.setOnClickListener {
            val name = dialogBinding.etVaccineName.text?.toString() ?: ""
            val remarks = dialogBinding.etRemarks.text?.toString() ?: ""
            val status = statusFromLabel(dialogBinding.actvStatus.text?.toString() ?: "")

            if (name.isEmpty()) {
                Toast.makeText(this, "Nom obligatoire", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val updatedEntry = entry.copy(
                        date = editDateMs,
                        name = name,
                        remarks = remarks,
                        status = status,
                        manufacturer = dialogBinding.etManufacturer.text?.toString()?.trim()?.ifEmpty { null },
                        lotNumber = dialogBinding.etLotNumber.text?.toString()?.trim()?.ifEmpty { null },
                        expiryDate = editExpiryMs,
                        dose = dialogBinding.etDose.text?.toString()?.trim()?.ifEmpty { null },
                        route = dialogBinding.actvRoute.text?.toString()?.trim()?.ifEmpty { null },
                        targetCount = dialogBinding.etTargetCount.text?.toString()?.toIntOrNull(),
                        administeredBy = dialogBinding.etAdministeredBy.text?.toString()?.trim()?.ifEmpty { null }
                    )
                    firebaseRepo.updateVaccine(updatedEntry)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VaccineActivity, "Soin modifié", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VaccineActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val deleteButton = TextView(this).apply {
            text = "SUPPRIMER CE SOIN"
            setPadding(0, 48, 0, 0)
            setTextColor(getColor(R.color.error))
            gravity = android.view.Gravity.CENTER
            setOnClickListener {
                AlertDialog.Builder(this@VaccineActivity)
                    .setTitle("Suppression")
                    .setMessage("Voulez-vous supprimer cet enregistrement ?")
                    .setPositiveButton("Supprimer") { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                if (entry.firestoreId != null) {
                                    firebaseRepo.deleteVaccine(entry.firestoreId)
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@VaccineActivity, "Soin supprimé", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@VaccineActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
        }
        (dialogBinding.root as ViewGroup).addView(deleteButton)

        dialog.show()
    }

    private fun loadEnhancedSanitaryGuide() {
        val guideHtml = """
            <font color='#FFFFFF'><b>🗓️ PLAN DE VACCINATION TYPE (Hens):</b></font><br/>
            • <b>J1:</b> Bronchite Infectieuse (H120) + Marek (Hatchery)<br/>
            • <b>J7:</b> Newcastle (Peste aviaire) - Souche HB1<br/>
            • <b>J14:</b> Gumboro (1ère dose - Eau de boisson)<br/>
            • <b>J21:</b> Newcastle (Rappel La Sota) + Gumboro (Rappel)<br/>
            • <b>S8:</b> Typhose aviaire (Injection)<br/>
            • <b>S12:</b> Coryza infectieux (Prévention respiratoire)<br/>
            • <b>S16:</b> Newcastle + BI (Inactivé - Protection longue durée)<br/><br/>
            <font color='#FFD700'><b>⚠️ RAPPEL CHALEUR (Sahel):</b></font><br/>
            En Mauritanie, Mali, Sénégal, évitez de vacciner entre 11h et 16h. Utilisez de l'eau fraîche et ajoutez des anti-stress (Vitamines/Électrolytes) avant et après.<br/><br/>
            <font color='#4CAF50'><b>🧼 HYGIÈNE & BIOSÉCURITÉ:</b></font><br/>
            • <b>Pédiluve:</b> Indispensable à l'entrée avec désinfectant renouvelé.<br/>
            • <b>Lutte contre les vecteurs:</b> Grillage fin pour empêcher les oiseaux sauvages (porteurs de grippe aviaire).<br/>
            • <b>Alimentation:</b> Stockage au sec, loin des rongeurs.
        """.trimIndent()
        
        binding.tvProphylaxisInfo.text = Html.fromHtml(guideHtml, Html.FROM_HTML_MODE_LEGACY)
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
            R.id.nav_batches -> {
                val intent = Intent(this, BatchActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
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
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_vaccines -> {
                val intent = Intent(this, HealthDashboardActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_expenses -> {
                val intent = Intent(this, ExpensesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_settings -> {
                val intent = Intent(this, FarmInfoActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                startActivity(intent)
            }
            R.id.nav_sales -> {
                val intent = Intent(this, SalesActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_mortality -> {
                val intent = Intent(this, MortalityActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_treatments -> {
                val intent = Intent(this, TreatmentActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
                startActivity(intent)
            }
            R.id.nav_diseases -> {
                val intent = Intent(this, DiseaseActivity::class.java)
                intent.putExtra("role", userRole)
                intent.putExtra("userIdString", userId)
                intent.putExtra("selectedBatchId", selectedBatchId)
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

    class VaccineAdapter(private val onItemClick: (VaccineEntry) -> Unit) : RecyclerView.Adapter<VaccineAdapter.ViewHolder>() {
        private var items = listOf<VaccineEntry>()

        fun submitList(list: List<VaccineEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemVaccineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(private val binding: ItemVaccineBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: VaccineEntry) {
                val context = binding.root.context
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val datePrefix = if (item.status == "PLANIFIE") "Prévu le " else ""
                binding.tvDate.text = "$datePrefix${sdf.format(Date(item.date))}"
                binding.tvVaccineName.text = item.name
                binding.tvRemarks.text = item.remarks ?: ""

                val details = listOfNotNull(
                    item.lotNumber?.let { "Lot $it" },
                    item.dose,
                    item.route
                ).joinToString(" · ")
                binding.tvVaccineDetails.text = details
                binding.tvVaccineDetails.visibility = if (details.isEmpty()) View.GONE else View.VISIBLE

                val (label, colorRes) = when (item.status) {
                    "PLANIFIE" -> "PLANIFIÉ" to R.color.accent_blue
                    "REPORTE" -> "REPORTÉ" to R.color.earthy_orange
                    "ANNULE" -> "ANNULÉ" to R.color.error
                    else -> "RÉALISÉ" to R.color.emerald_soft
                }
                binding.tvVaccineStatus.text = label
                val color = context.getColor(colorRes)
                binding.tvVaccineStatus.setTextColor(color)
                (binding.tvVaccineStatus.background.mutate() as? android.graphics.drawable.GradientDrawable)?.setColor(
                    android.graphics.Color.argb(30, android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color))
                )
            }
        }
    }
}
